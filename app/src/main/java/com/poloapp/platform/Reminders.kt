package com.poloapp.platform

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import com.poloapp.data.*
import com.poloapp.domain.CarCalculations
import com.poloapp.domain.MaintenanceState
import java.time.LocalDate
import java.util.concurrent.TimeUnit

object ReminderScheduler {
    /** Repeated calls update a single periodic job and coalesce immediate checks after local edits. */
    fun schedule(context: Context) {
        val manager = WorkManager.getInstance(context.applicationContext)
        manager.enqueueUniquePeriodicWork("polo-reminders", ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<ReminderWorker>(12, TimeUnit.HOURS).build())
        manager.enqueueUniqueWork("polo-reminders-now", ExistingWorkPolicy.KEEP, OneTimeWorkRequestBuilder<ReminderWorker>().build())
        PoloWidget.update(context)
    }
}

class ReminderWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val context = applicationContext
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return Result.success()
        val notifications = NotificationManagerCompat.from(context)
        if (!notifications.areNotificationsEnabled()) return Result.success()
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Avisos del vehículo", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "ITV, seguro, mantenimiento y recordatorios personales"
        })
        if (manager.getNotificationChannel(CHANNEL)?.importance == NotificationManager.IMPORTANCE_NONE) return Result.success()
        return try {
            val snapshot = CarRepository(context).currentSnapshot()
            val prefs = context.getSharedPreferences("polo-reminders", Context.MODE_PRIVATE)
            val today = LocalDate.now()
            snapshot.vehicles.forEach { vehicle ->
                val records = snapshot.recordsFor(vehicle.id)
                val candidates = mutableListOf<Notice>()
                listOf(RecordKind.ITV, RecordKind.INSURANCE).forEach { kind ->
                    val latest = records.firstOrNull { it.kind == kind }
                    val due = latest?.text("dueDate")?.toLongOrNull()
                    if (latest != null && due != null) {
                        val days = due - today.toEpochDay()
                        milestone(days)?.let { stage ->
                            candidates += Notice("${kind.name}:${vehicle.id}:$due:$stage", "${kind.label}: ${remaining(days)}")
                        }
                    }
                }
                CarCalculations.maintenanceStatuses(vehicle, records, snapshot.rulesFor(vehicle.id), today).forEach { status ->
                    if (status.state == MaintenanceState.SOON || status.state == MaintenanceState.OVERDUE) {
                        val rule = status.rule
                        val cycle = status.lastRecord?.let { "${it.id}:${it.date}:${it.odometer}" } ?: "${rule.baselineDate}:${rule.baselineKm}"
                        val km = status.remainingKm
                        val days = status.remainingDays
                        val kmStage = when { km == null -> null; km <= 0 -> "km-overdue"; km <= rule.warningKm -> "km-soon"; else -> null }
                        val timeStage = days?.takeIf { it <= rule.warningDays }?.let { milestone(it) ?: "custom-warning" }
                        val stage = if (status.state == MaintenanceState.OVERDUE) "overdue" else listOfNotNull(kmStage, timeStage).joinToString("-")
                        val distance = km?.let { if (it <= 0) "${-it.toLong()} km vencido" else "quedan $it km" }
                        val time = days?.let(::remaining)
                        candidates += Notice("maintenance:${rule.id}:$cycle:${rule.intervalKm}:${rule.intervalMonths}:$stage", "${rule.title}: ${listOfNotNull(distance, time).joinToString(" · ")}")
                    }
                }
                records.filter { it.kind == RecordKind.NOTE }.forEach { note ->
                    val due = note.text("reminderDate").toLongOrNull()
                    if (due != null && due <= today.toEpochDay()) candidates += Notice("note:${note.id}:$due", note.title.ifBlank { "Recordatorio del vehículo" })
                }
                val fresh = candidates.filterNot { prefs.contains(it.key) }
                if (fresh.isNotEmpty()) {
                    val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                    val pending = intent?.let { PendingIntent.getActivity(context, vehicle.id.hashCode(), it, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE) }
                    val visible = fresh.take(6).joinToString("\n") { "• ${it.message}" } + if (fresh.size > 6) "\nY ${fresh.size - 6} avisos más. Abre Polo App." else ""
                    val notification = NotificationCompat.Builder(context, CHANNEL)
                        .setSmallIcon(android.R.drawable.ic_menu_info_details)
                        .setContentTitle("${vehicle.name} · ${fresh.size} ${if (fresh.size == 1) "aviso" else "avisos"}")
                        .setContentText(fresh.first().message)
                        .setStyle(NotificationCompat.BigTextStyle().bigText(visible))
                        .setContentIntent(pending)
                        .setAutoCancel(true).setOnlyAlertOnce(false)
                        .setCategory(NotificationCompat.CATEGORY_REMINDER)
                        // Hide note contents, policy dates and vehicle details on the locked screen.
                        .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).build()
                    notifications.notify(vehicle.id.hashCode(), notification)
                    prefs.edit().apply { fresh.forEach { putLong(it.key, today.toEpochDay()) } }.apply()
                }
            }
            // Retain enough history to avoid repeat notifications, without unbounded preference growth.
            val stale = prefs.all.filterValues { (it as? Long)?.let { day -> today.toEpochDay() - day > 730 } == true }.keys
            if (stale.isNotEmpty()) prefs.edit().apply { stale.forEach(::remove) }.apply()
            PoloWidget.update(context)
            Result.success()
        } catch (_: SecurityException) {
            // Permission can be revoked between the preflight check and notify().
            Result.success()
        } catch (_: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    private data class Notice(val key: String, val message: String)
    private fun milestone(days: Long): String? = when {
        days < 0 -> "overdue"; days == 0L -> "today"; days <= 1 -> "one-day"; days <= 7 -> "one-week"; days <= 30 -> "one-month"; else -> null
    }
    private fun remaining(days: Long): String = when {
        days < 0 -> "vencido hace ${-days} ${if (days == -1L) "día" else "días"}"
        days == 0L -> "vence hoy"
        days == 1L -> "vence mañana"
        else -> "faltan $days días"
    }
    companion object { private const val CHANNEL = "vehicle_reminders" }
}
