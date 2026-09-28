package com.poloapp

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.poloapp.data.*
import com.poloapp.domain.GarageValidation
import com.poloapp.platform.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class PoloViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application
    private val repository = (application as PoloApplication).repository
    private val preferences = context.getSharedPreferences("polo_settings", 0)
    val busy = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)
    val notice = MutableStateFlow<String?>(null)
    val ready = MutableStateFlow(false)
    val selectedVehicleId = MutableStateFlow(preferences.getString("vehicle", null))
    val theme = MutableStateFlow(preferences.getString("theme", "dark") ?: "dark")
    val financeLock = MutableStateFlow(preferences.getBoolean("finance_lock", false))
    val pendingRestore = MutableStateFlow<GarageSnapshot?>(null)
    val snapshot: StateFlow<GarageSnapshot> = repository.snapshot.onEach { ready.value = true }
        .catch { error.value = "No se ha podido abrir el historial: ${it.localizedMessage}" }
        .stateIn(viewModelScope, SharingStarted.Eagerly, GarageSnapshot())

    fun selectVehicle(id: String) { selectedVehicleId.value = id; preferences.edit().putString("vehicle", id).apply(); runCatching { PoloWidget.update(context) } }
    fun setTheme(value: String) { theme.value = value; preferences.edit().putString("theme", value).apply() }
    fun setFinanceLock(value: Boolean) { financeLock.value = value; preferences.edit().putBoolean("finance_lock", value).apply() }
    fun clearError() { error.value = null }
    fun reportError(message: String) { error.value = message }
    private fun perform(success: (() -> Unit)? = null, block: suspend () -> Unit) {
        if (busy.value) return
        busy.value = true; error.value = null
        viewModelScope.launch {
            try { block(); success?.invoke() }
            catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e; error.value = e.localizedMessage ?: "No se ha podido completar la operación." }
            finally { busy.value = false }
        }
    }
    private fun refreshPlatform() {
        runCatching { ReminderScheduler.schedule(context); PoloWidget.update(context) }
            .onFailure { notice.value = "Datos guardados. No se han podido actualizar los avisos; vuelve a abrir la app." }
    }
    fun saveVehicle(vehicle: Vehicle, done: () -> Unit) = perform(done) { repository.saveVehicle(vehicle); selectVehicle(vehicle.id); refreshPlatform() }
    fun saveWorkshop(workshop: Workshop, done: () -> Unit) = perform(done) { repository.saveWorkshop(workshop) }
    fun saveRule(rule: MaintenanceRule, done: () -> Unit) = perform(done) { repository.saveRule(rule); refreshPlatform() }

    fun saveRecord(record: CarRecord, uris: List<Uri>, kept: List<Attachment>, done: () -> Unit) = perform(done) {
        val copied = mutableListOf<Attachment>()
        val old = repository.currentSnapshot().attachments.filter { it.recordId == record.id }
        try {
            withContext(Dispatchers.IO) { uris.forEach { copied += copyAttachment(record.id, it) } }
            repository.saveRecordWithAttachments(record, kept + copied)
        } catch (e: Exception) { withContext(Dispatchers.IO) { copied.forEach { safeDelete(it.localPath) } }; throw e }
        withContext(Dispatchers.IO) { old.filter { previous -> kept.none { it.id == previous.id } }.forEach { safeDelete(it.localPath) } }
        refreshPlatform()
    }
    fun deleteRecord(record: CarRecord, done: () -> Unit) = perform(done) {
        val old = repository.currentSnapshot().attachments.filter { it.recordId == record.id }
        repository.deleteRecord(record)
        withContext(Dispatchers.IO) { old.forEach { safeDelete(it.localPath) } }
        refreshPlatform()
    }
    private fun copyAttachment(recordId: String, uri: Uri): Attachment {
        val resolver = context.contentResolver
        val mime = resolver.getType(uri) ?: throw IllegalArgumentException("No se ha podido reconocer el archivo.")
        val extension = mapOf("image/jpeg" to "jpg", "image/png" to "png", "image/webp" to "webp", "application/pdf" to "pdf")[mime]
            ?: throw IllegalArgumentException("Selecciona una foto JPEG, PNG, WebP o un PDF.")
        val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null } ?: "Adjunto.$extension"
        val directory = File(context.filesDir, "attachments").apply { mkdirs() }
        val target = File(directory, "${UUID.randomUUID()}.$extension")
        try {
            resolver.openInputStream(uri)?.use { input -> target.outputStream().use { output ->
                val buffer = ByteArray(16_384); var total = 0L
                while (true) { val count = input.read(buffer); if (count < 0) break; total += count; require(total <= 25L * 1024 * 1024) { "Cada archivo debe ocupar como máximo 25 MB." }; output.write(buffer, 0, count) }
                require(total > 0) { "El archivo está vacío." }
            } } ?: throw IllegalArgumentException("No se puede leer el archivo seleccionado.")
            if (mime.startsWith("image/")) {
                val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                android.graphics.BitmapFactory.decodeFile(target.path, bounds)
                require(bounds.outWidth > 0 && bounds.outHeight > 0) { "La fotografía está dañada o no es compatible." }
            } else {
                val signature = ByteArray(5)
                val count = target.inputStream().use { it.read(signature) }
                require(count == 5 && signature.toString(Charsets.US_ASCII) == "%PDF-") { "El archivo seleccionado no es un PDF válido." }
            }
            return Attachment(recordId = recordId, localPath = target.absolutePath, mimeType = mime, displayName = name.take(200))
        } catch (e: Exception) { target.delete(); throw e }
    }
    private fun safeDelete(path: String) {
        runCatching {
            val file = File(path)
            if (file.canonicalPath.startsWith(context.filesDir.canonicalPath + File.separator)) file.delete()
        }
    }
    fun exportPdf(uri: Uri, vehicleId: String, options: PdfOptions) = perform {
        val current = repository.currentSnapshot()
        val vehicle = current.vehicles.firstOrNull { it.id == vehicleId } ?: throw IllegalArgumentException("El vehículo ya no está disponible.")
        PdfExporter(context).export(current, vehicle, uri, options)
        notice.value = "Informe PDF guardado."
    }
    fun exportBackup(uri: Uri) = perform {
        BackupManager(context).export(repository.currentSnapshot(), uri)
        notice.value = "Copia completa guardada con todos los vehículos y sus archivos."
    }
    fun prepareRestore(uri: Uri) = perform {
        val manager = BackupManager(context)
        val imported = manager.import(uri)
        try { GarageValidation.validate(imported); pendingRestore.value = imported }
        catch (e: Exception) { manager.discardImported(imported); throw e }
    }
    fun cancelRestore() = perform {
        pendingRestore.value?.let { BackupManager(context).discardImported(it) }
        pendingRestore.value = null
    }
    fun commitRestore() = perform {
        val imported = pendingRestore.value ?: return@perform
        var committed = false
        try {
            val previous = repository.currentSnapshot()
            repository.replaceAll(imported)
            committed = true
            withContext(Dispatchers.IO) { previous.attachments.forEach { safeDelete(it.localPath) } }
            imported.vehicles.firstOrNull()?.let { selectVehicle(it.id) }
            notice.value = "Copia restaurada: ${imported.records.size} registros."
            refreshPlatform()
        } catch (e: Exception) { if (!committed) BackupManager(context).discardImported(imported); throw e }
        finally { pendingRestore.value = null }
    }
}
