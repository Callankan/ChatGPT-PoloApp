package com.poloapp.platform

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.poloapp.R
import com.poloapp.data.CarRepository
import com.poloapp.data.RecordKind
import com.poloapp.data.text
import com.poloapp.domain.CarCalculations
import com.poloapp.domain.MaintenanceState
import kotlinx.coroutines.*
import java.text.NumberFormat
import java.time.LocalDate
import java.util.Locale

/** A compact, offline widget. Each broadcast keeps its receiver alive until Room has been read. */
class PoloWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pending = goAsync()
        scope.launch {
            try { withTimeout(8_000) { render(context.applicationContext, appWidgetManager, appWidgetIds) } }
            finally { pending.finish() }
        }
    }

    companion object {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        fun update(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, PoloWidget::class.java))
            if (ids.isNotEmpty()) {
                val intent = Intent(context, PoloWidget::class.java).setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                context.sendBroadcast(intent)
            }
        }

        private suspend fun render(context: Context, manager: AppWidgetManager, ids: IntArray) {
            val views = RemoteViews(context.packageName, R.layout.widget_polo)
            try {
                val snapshot = CarRepository(context).currentSnapshot()
                val selected = context.getSharedPreferences("polo_settings", Context.MODE_PRIVATE).getString("vehicle", null)
                val vehicle = snapshot.vehicles.firstOrNull { it.id == selected } ?: snapshot.vehicles.firstOrNull()
                if (vehicle == null) {
                    views.setTextViewText(R.id.widget_name, "Tu coche, al día")
                    views.setTextViewText(R.id.widget_km, "Añade tu vehículo")
                    views.setTextViewText(R.id.widget_itv, "Abre Polo App para comenzar")
                    views.setTextViewText(R.id.widget_service, "Todo el historial en tu móvil")
                } else {
                    val records = snapshot.recordsFor(vehicle.id)
                    views.setTextViewText(R.id.widget_name, vehicle.name)
                    views.setTextViewText(R.id.widget_km, "${NumberFormat.getIntegerInstance(Locale.forLanguageTag("es-ES")).format(CarCalculations.currentKm(vehicle, records))} km")
                    val itv = records.firstOrNull { it.kind == RecordKind.ITV }?.text("dueDate")?.toLongOrNull()
                    val days = itv?.minus(LocalDate.now().toEpochDay())
                    views.setTextViewText(R.id.widget_itv, when { days == null -> "ITV · Pendiente de registrar"; days < 0 -> "ITV · Vencida hace ${-days} días"; days == 0L -> "ITV · Vence hoy"; else -> "ITV · Faltan $days días" })
                    val statuses = CarCalculations.maintenanceStatuses(vehicle, records, snapshot.rulesFor(vehicle.id))
                    val service = statuses.firstOrNull { it.state != MaintenanceState.UNKNOWN }
                    views.setTextViewText(R.id.widget_service, service?.let {
                        "${it.rule.title} · " + when {
                            it.state == MaintenanceState.OVERDUE -> "Pendiente"
                            it.remainingKm != null -> "${it.remainingKm} km"
                            it.remainingDays != null -> "${it.remainingDays} días"
                            else -> "Al día"
                        }
                    } ?: "Mantenimiento · Añade una referencia")
                }
            } catch (_: Exception) {
                views.setTextViewText(R.id.widget_name, "Polo App")
                views.setTextViewText(R.id.widget_km, "Abre tu garaje")
                views.setTextViewText(R.id.widget_itv, "Actualiza los datos en la app")
                views.setTextViewText(R.id.widget_service, "Tu historial permanece guardado")
            }
            context.packageManager.getLaunchIntentForPackage(context.packageName)?.let { launch ->
                views.setOnClickPendingIntent(R.id.widget_root, PendingIntent.getActivity(context, 0, launch, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            }
            ids.forEach { manager.updateAppWidget(it, views) }
        }
    }
}
