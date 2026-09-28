package com.poloapp.platform

import android.content.Context
import android.net.Uri
import com.poloapp.data.*
import com.poloapp.domain.GarageValidation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** A portable, versioned backup. Only app-owned attachments can leave the sandbox. */
class BackupManager(private val context: Context) {
    suspend fun export(snapshot: GarageSnapshot, uri: Uri) = withContext(Dispatchers.IO) {
        validateSnapshot(snapshot)
        val attachments = JSONArray()
        val files = snapshot.attachments.mapIndexed { index, attachment ->
            val source = ownedFile(attachment.localPath)
            require(source.isFile && source.length() in 1..MAX_FILE) { "Falta el adjunto ${attachment.displayName}. La copia no se ha creado." }
            val extension = extension(attachment.mimeType)
            require(validSignature(source, attachment.mimeType)) { "El formato de ${attachment.displayName} no coincide con su tipo." }
            val archivePath = "attachments/$index.$extension"
            attachments.put(JSONObject().put("id", attachment.id).put("recordId", attachment.recordId)
                .put("archivePath", archivePath).put("mimeType", attachment.mimeType)
                .put("displayName", attachment.displayName).put("size", source.length()).put("sha256", sha256(source)))
            archivePath to source
        }
        require(files.sumOf { it.second.length() } <= MAX_TOTAL) { "La copia supera el límite de 500 MB." }
        val manifest = encode(snapshot).put("attachments", attachments).toString().toByteArray(Charsets.UTF_8)
        require(manifest.size <= MAX_MANIFEST) { "El historial es demasiado grande para esta versión del formato." }
        val output = context.contentResolver.openOutputStream(uri, "wt") ?: error("No se puede escribir la copia.")
        ZipOutputStream(output.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json")); zip.write(manifest); zip.closeEntry()
            files.forEach { (path, file) ->
                zip.putNextEntry(ZipEntry(path)); file.inputStream().use { it.copyTo(zip) }; zip.closeEntry()
            }
        }
    }

    /** No Room mutation occurs here. The caller commits the returned snapshot in one transaction. */
    suspend fun import(uri: Uri): GarageSnapshot = withContext(Dispatchers.IO) {
        val staging = File(context.filesDir, "restores/${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val seen = mutableSetOf<String>()
            var total = 0L
            var manifest: JSONObject? = null
            val input = context.contentResolver.openInputStream(uri) ?: error("No se puede abrir la copia.")
            ZipInputStream(input.buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val path = entry.name
                    require(!entry.isDirectory && (path == "manifest.json" || FILE_ENTRY.matches(path))) { "La copia contiene una ruta no permitida." }
                    require(seen.add(path) && seen.size <= MAX_ATTACHMENTS + 1) { "La copia tiene entradas duplicadas o demasiados adjuntos." }
                    if (path == "manifest.json") {
                        val bytes = zip.readBounded(MAX_MANIFEST.toLong())
                        total += bytes.size
                        manifest = JSONObject(bytes.toString(Charsets.UTF_8))
                    } else {
                        val destination = File(staging, path).canonicalFile
                        require(destination.path.startsWith(staging.canonicalPath + File.separator)) { "Ruta de adjunto no válida." }
                        destination.parentFile?.mkdirs()
                        destination.outputStream().use { total += zip.copyBounded(it, MAX_FILE) }
                    }
                    require(total <= MAX_TOTAL + MAX_MANIFEST) { "La copia supera el límite de 500 MB." }
                    zip.closeEntry()
                }
            }
            val json = requireNotNull(manifest) { "No se ha encontrado el manifiesto de Polo App." }
            require(json.getString("format") == "PoloAppBackup" && json.getInt("version") == 1) { "Formato de copia no compatible. Actualiza Polo App." }
            val attachmentJson = json.getJSONArray("attachments")
            val expected = mutableSetOf("manifest.json")
            val attachments = (0 until attachmentJson.length()).map { index ->
                val item = attachmentJson.getJSONObject(index)
                val path = item.getString("archivePath")
                val mime = item.getString("mimeType")
                require(FILE_ENTRY.matches(path) && path.substringAfterLast('.') == extension(mime) && expected.add(path)) { "Referencia de adjunto no válida." }
                val file = File(staging, path)
                require(file.isFile && file.length() == item.getLong("size") && file.length() in 1..MAX_FILE) { "Falta un adjunto o está incompleto." }
                require(sha256(file) == item.getString("sha256") && validSignature(file, mime)) { "Un adjunto está dañado o tiene un formato incorrecto." }
                Attachment(item.getString("id"), item.getString("recordId"), file.absolutePath, mime, item.getString("displayName"))
            }
            require(seen == expected) { "La copia contiene adjuntos sin referencia o incompletos." }
            decode(json).copy(attachments = attachments).also {
                validateSnapshot(it)
                if (attachments.isEmpty()) staging.deleteRecursively()
            }
        } catch (error: Throwable) {
            staging.deleteRecursively()
            throw error
        }
    }

    /** Call only after a failed database transaction, never after a successful restore. */
    fun discardImported(snapshot: GarageSnapshot) {
        val root = File(context.filesDir, "restores").canonicalFile
        snapshot.attachments.map { File(it.localPath).canonicalFile }.filter { it.path.startsWith(root.path + File.separator) }
            .mapNotNull { it.parentFile?.parentFile }.distinct().filter { it.parentFile == root }.forEach { it.deleteRecursively() }
    }

    private fun ownedFile(path: String): File = File(path).canonicalFile.also {
        require(it.path.startsWith(context.filesDir.canonicalPath + File.separator)) { "Adjunto fuera del almacenamiento de Polo App." }
    }

    private fun encode(s: GarageSnapshot) = JSONObject().put("format", "PoloAppBackup").put("version", 1)
        .put("createdAt", System.currentTimeMillis())
        .put("vehicles", array(s.vehicles) { v -> JSONObject().put("id", v.id).put("name", v.name).put("make", v.make).put("model", v.model)
            .put("powerHp", v.powerHp).put("registrationDate", v.registrationDate).put("plate", v.plate).put("vin", v.vin).put("color", v.color)
            .put("purchaseDate", v.purchaseDate).put("purchaseKm", v.purchaseKm).put("initialDebtCents", v.initialDebtCents)
            .put("debtStartDate", v.debtStartDate).put("owner", v.owner).put("specs", JSONObject(v.specs)) })
        .put("records", array(s.records) { r -> JSONObject().put("id", r.id).put("vehicleId", r.vehicleId).put("kind", r.kind.name)
            .put("date", r.date).put("title", r.title).put("notes", r.notes).put("odometer", r.odometer ?: JSONObject.NULL)
            .put("amountCents", r.amountCents).put("details", JSONObject(r.details)).put("createdAt", r.createdAt) })
        .put("rules", array(s.rules) { r -> JSONObject().put("id", r.id).put("vehicleId", r.vehicleId).put("title", r.title).put("partKey", r.partKey)
            .put("intervalKm", r.intervalKm ?: JSONObject.NULL).put("intervalMonths", r.intervalMonths ?: JSONObject.NULL)
            .put("baselineKm", r.baselineKm ?: JSONObject.NULL).put("baselineDate", r.baselineDate ?: JSONObject.NULL)
            .put("warningKm", r.warningKm).put("warningDays", r.warningDays).put("enabled", r.enabled) })
        .put("workshops", array(s.workshops) { w -> JSONObject().put("id", w.id).put("name", w.name).put("phone", w.phone).put("address", w.address).put("rating", w.rating) })

    private fun decode(j: JSONObject) = GarageSnapshot(
        vehicles = objects(j, "vehicles").map { v -> Vehicle(v.getString("id"), v.getString("name"), v.getString("make"), v.getString("model"),
            v.getInt("powerHp"), v.getLong("registrationDate"), v.getString("plate"), v.getString("vin"), v.getString("color"),
            v.getLong("purchaseDate"), v.getInt("purchaseKm"), v.getLong("initialDebtCents"), v.getLong("debtStartDate"), v.getString("owner"), map(v.getJSONObject("specs"))) },
        records = objects(j, "records").map { r -> CarRecord(r.getString("id"), r.getString("vehicleId"), RecordKind.valueOf(r.getString("kind")),
            r.getLong("date"), r.getString("title"), r.getString("notes"), r.nullInt("odometer"), r.getLong("amountCents"), map(r.getJSONObject("details")), r.getLong("createdAt")) },
        rules = objects(j, "rules").map { r -> MaintenanceRule(r.getString("id"), r.getString("vehicleId"), r.getString("title"), r.getString("partKey"),
            r.nullInt("intervalKm"), r.nullInt("intervalMonths"), r.nullInt("baselineKm"), r.nullLong("baselineDate"), r.getInt("warningKm"), r.getInt("warningDays"), r.getBoolean("enabled")) },
        workshops = objects(j, "workshops").map { w -> Workshop(w.getString("id"), w.getString("name"), w.getString("phone"), w.getString("address"), w.getInt("rating")) }
    )

    private fun validateSnapshot(s: GarageSnapshot) {
        GarageValidation.validate(s)
        require(s.vehicles.size <= 100 && s.records.size <= 100_000 && s.rules.size <= 10_000 && s.workshops.size <= 10_000 && s.attachments.size <= MAX_ATTACHMENTS) { "La copia contiene demasiados registros." }
        val ids = listOf(s.vehicles.map { it.id }, s.records.map { it.id }, s.rules.map { it.id }, s.workshops.map { it.id }, s.attachments.map { it.id })
        require(ids.all { group -> group.size == group.toSet().size && group.all { ID.matches(it) } }) { "Identificadores de la copia no válidos." }
        val vehicleIds = s.vehicles.map { it.id }.toSet(); val recordIds = s.records.map { it.id }.toSet()
        require(s.records.all { it.vehicleId in vehicleIds } && s.rules.all { it.vehicleId in vehicleIds } && s.attachments.all { it.recordId in recordIds }) { "La copia tiene relaciones incompletas." }
        fun date(value: Long) = value in -25567L..84005L // 1900–2199, safely supported by date arithmetic.
        require(s.vehicles.all { it.purchaseKm >= 0 && it.initialDebtCents in 0..MAX_MONEY && date(it.purchaseDate) && date(it.registrationDate) && date(it.debtStartDate) }) { "Los datos del vehículo no son válidos." }
        require(s.records.all { (it.odometer == null || it.odometer >= 0) && it.amountCents in 0..MAX_MONEY && date(it.date) && it.details.size <= 100 }) { "La copia tiene registros no válidos." }
        require(s.rules.all { (it.intervalKm == null || it.intervalKm > 0) && (it.intervalMonths == null || it.intervalMonths in 1..1200) && (it.baselineKm == null || it.baselineKm >= 0) && (it.baselineDate == null || date(it.baselineDate)) && it.warningKm >= 0 && it.warningDays >= 0 }) { "La copia tiene intervalos no válidos." }
        require(s.workshops.all { it.rating in 0..5 }) { "La valoración de un taller no es válida." }
    }

    private fun objects(j: JSONObject, key: String): List<JSONObject> {
        val a = j.getJSONArray(key)
        require(a.length() <= 100_000) { "Demasiados elementos en la copia." }
        return (0 until a.length()).map(a::getJSONObject)
    }
    private fun map(j: JSONObject): Map<String, String> {
        require(j.length() <= 100) { "Demasiados campos en un registro." }
        return j.keys().asSequence().associateWith { j.getString(it).also { value -> require(value.length <= 100_000) { "Texto demasiado largo." } } }
    }
    private fun JSONObject.nullInt(key: String) = if (isNull(key)) null else getInt(key)
    private fun JSONObject.nullLong(key: String) = if (isNull(key)) null else getLong(key)
    private fun <T> array(values: List<T>, encode: (T) -> JSONObject) = JSONArray().apply { values.forEach { put(encode(it)) } }
    private fun extension(mime: String) = when (mime) { "image/jpeg" -> "jpg"; "image/png" -> "png"; "image/webp" -> "webp"; "application/pdf" -> "pdf"; else -> error("Formato de adjunto no compatible: $mime") }
    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input -> val buffer = ByteArray(8192); while (true) { val n = input.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) } }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
    private fun validSignature(file: File, mime: String): Boolean {
        val b = ByteArray(12); val n = file.inputStream().use { it.read(b) }
        return when (mime) {
            "image/jpeg" -> n >= 3 && b[0] == 0xff.toByte() && b[1] == 0xd8.toByte() && b[2] == 0xff.toByte()
            "image/png" -> n >= 8 && b.take(8) == listOf(137, 80, 78, 71, 13, 10, 26, 10).map(Int::toByte)
            "image/webp" -> n >= 12 && String(b, 0, 4, Charsets.US_ASCII) == "RIFF" && String(b, 8, 4, Charsets.US_ASCII) == "WEBP"
            "application/pdf" -> n >= 5 && String(b, 0, 5, Charsets.US_ASCII) == "%PDF-"
            else -> false
        }
    }
    private fun InputStream.readBounded(limit: Long): ByteArray = java.io.ByteArrayOutputStream().use { output -> copyBounded(output, limit); output.toByteArray() }
    private fun InputStream.copyBounded(output: OutputStream, limit: Long): Long {
        val buffer = ByteArray(8192); var total = 0L
        while (true) { val size = read(buffer); if (size < 0) break; total += size; require(total <= limit) { "Un archivo de la copia es demasiado grande." }; output.write(buffer, 0, size) }
        return total
    }
    companion object {
        private const val MAX_MANIFEST = 32 * 1024 * 1024
        private const val MAX_FILE = 50L * 1024 * 1024
        private const val MAX_TOTAL = 500L * 1024 * 1024
        private const val MAX_ATTACHMENTS = 2000
        private const val MAX_MONEY = 1_000_000_000_000L
        private val FILE_ENTRY = Regex("attachments/[0-9]{1,6}\\.(jpg|png|webp|pdf)")
        private val ID = Regex("[A-Za-z0-9._-]{1,128}")
    }
}
