package com.poloapp.platform

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.net.Uri
import com.poloapp.data.*
import com.poloapp.domain.CarCalculations
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.min

data class PdfOptions(val includePrices: Boolean = true, val includePersonalData: Boolean = false, val includePhotos: Boolean = false)

/** Explicit allowlists keep financial and private records out of a buyer's report. */
class PdfExporter(private val context: Context) {
    suspend fun export(snapshot: GarageSnapshot, vehicle: Vehicle, uri: Uri, options: PdfOptions = PdfOptions()) = withContext(Dispatchers.IO) {
        val all = snapshot.recordsFor(vehicle.id)
        val records = all.filter { it.kind in PUBLIC_KINDS }.sortedWith(compareBy<CarRecord> { it.date }.thenBy { it.createdAt })
        val money = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-ES"))
        fun price(cents: Long) = money.format(cents / 100.0)
        val report = Report(vehicle.name)
        try {
            report.cover(vehicle, CarCalculations.currentKm(vehicle, all), options)
            report.section("01", "Historial de kilometraje")
            report.paragraph("Lecturas introducidas por el propietario. Este informe no certifica el kilometraje ni sustituye la documentación oficial, las facturas o una revisión independiente.", muted = true)
            report.row(date(vehicle.purchaseDate), "Compra del vehículo", "${km(vehicle.purchaseKm)} km registrados")
            records.filter { it.odometer != null }.forEach { r -> report.row(date(r.date), r.kind.label, "${km(r.odometer!!)} km") }

            report.section("02", "Mantenimiento y reparaciones")
            val maintenance = records.filter { it.kind == RecordKind.MAINTENANCE }
            if (maintenance.isEmpty()) report.empty()
            maintenance.forEach { r ->
                val workshop = snapshot.workshops.find { it.id == r.text("workshopId") }
                val provider = if (r.text("provider") == "self") "Realizado por el propietario" else workshop?.name ?: "Taller no especificado"
                val lines = mutableListOf("${r.odometer?.let { "${km(it)} km · " } ?: ""}$provider")
                if (r.text("axle") in setOf("front", "rear")) lines += "Eje ${if (r.text("axle") == "front") "delantero" else "trasero"}"
                if (options.includePrices) {
                    lines += "Coste: ${price(r.amountCents)}"
                    if (r.details.containsKey("partsCents") || r.details.containsKey("laborCents")) {
                        lines += "Piezas: ${price(r.text("partsCents").toLongOrNull() ?: 0)} · Mano de obra: ${price(r.text("laborCents").toLongOrNull() ?: 0)}"
                    }
                }
                if (r.notes.isNotBlank()) lines += r.notes
                report.row(date(r.date), r.title.ifBlank { "Mantenimiento" }, lines.joinToString("\n"))
            }
            val lifecycles = CarCalculations.partLifecycles(vehicle, all)
            if (lifecycles.isNotEmpty()) {
                report.subheading("Vida útil de las piezas")
                lifecycles.forEach { part ->
                    val axle = when (part.axle) { "front" -> " · Eje delantero"; "rear" -> " · Eje trasero"; else -> "" }
                    report.row(if (part.isActive) "EN USO" else "RETIRADA", part.record.title + axle,
                        "${km(part.distanceKm)} km desde la instalación${part.retiredBy?.let { " · Sustituida el ${date(it.date)}" } ?: " hasta la última lectura"}")
                }
            }
            report.section("03", "Inspecciones técnicas · ITV")
            records.filter { it.kind == RecordKind.ITV }.let { inspections ->
                if (inspections.isEmpty()) report.empty()
                inspections.forEach { r ->
                    val text = listOfNotNull("Resultado: ${r.text("result", "No indicado")}",
                        r.text("station").takeIf { it.isNotBlank() }?.let { "Estación: $it" },
                        r.text("dueDate").toLongOrNull()?.let { "Próxima inspección: ${date(it)}" },
                        r.odometer?.let { "Lectura: ${km(it)} km" },
                        r.text("defects").takeIf { it.isNotBlank() }?.let { "Defectos/observaciones: $it" },
                        if (options.includePrices) "Coste: ${price(r.amountCents)}" else null)
                    report.row(date(r.date), r.title.ifBlank { "Inspección técnica" }, text.joinToString("\n"))
                }
            }
            report.section("04", "Daños y estado de la carrocería")
            records.filter { it.kind == RecordKind.DAMAGE }.let { damages ->
                if (damages.isEmpty()) report.empty("No hay daños registrados. La ausencia de registros no acredita la ausencia de daños.")
                damages.forEach { r -> report.row(date(r.date), r.title.ifBlank { "Daño registrado" },
                    listOfNotNull(r.text("location").takeIf { it.isNotBlank() }?.let { "Zona: $it" },
                        "Estado: ${if (r.flag("repaired")) "Reparado" else "Pendiente de reparar"}",
                        r.text("severity").takeIf { it.isNotBlank() }?.let { "Gravedad: $it" },
                        r.notes.takeIf { it.isNotBlank() }, if (options.includePrices && r.amountCents > 0) "Coste: ${price(r.amountCents)}" else null).joinToString("\n")) }
            }
            report.section("05", "Control de neumáticos")
            records.filter { it.kind == RecordKind.PRESSURE }.let { pressures ->
                if (pressures.isEmpty()) report.empty()
                pressures.forEach { r -> report.row(date(r.date), r.title.ifBlank { "Comprobación de presión" },
                    "Delante: ${r.text("frontBar", "—")} bar · Detrás: ${r.text("rearBar", "—")} bar\nMedición ${if (r.flag("cold")) "en frío" else "sin confirmar en frío"}${r.odometer?.let { " · ${km(it)} km" } ?: ""}") }
            }
            report.section("06", "Repostajes y uso")
            val fuel = CarCalculations.fuelStats(all)
            report.paragraph(fuel.averageLitersPer100Km?.let { "Consumo medio medido: ${String.format(Locale.forLanguageTag("es-ES"), "%.2f", it)} l/100 km · ${fuel.intervals.size} intervalos de lleno a lleno." }
                ?: "Todavía no hay intervalos completos de lleno a lleno para calcular el consumo.", muted = true)
            records.filter { it.kind == RecordKind.FUEL }.let { fills ->
                if (fills.isEmpty()) report.empty()
                fills.forEach { r -> report.row(date(r.date), "${r.odometer?.let { "${km(it)} km" } ?: "Repostaje"} · ${r.text("liters", "—")} litros",
                    "Gasolina ${r.text("fuelType", "95")} · ${if (r.flag("fullTank")) "Depósito lleno" else "Repostaje parcial"}${if (options.includePrices) " · ${price(r.amountCents)}" else ""}") }
            }
            val trips = records.filter { it.kind == RecordKind.TRIP }
            if (trips.isNotEmpty()) {
                report.subheading("Viajes registrados")
                trips.forEach { r ->
                    val start = r.text("startKm").toIntOrNull(); val end = r.text("endKm").toIntOrNull()
                    report.row(date(r.date), if (options.includePersonalData) r.title else "Viaje registrado",
                        if (start != null && end != null) "${km(start)} → ${km(end)} km · ${km((end - start).coerceAtLeast(0))} km recorridos" else "Distancia no indicada")
                }
            }
            if (options.includePrices) {
                report.section("07", "Otros gastos del vehículo")
                records.filter { it.kind == RecordKind.EXPENSE }.let { expenses ->
                    if (expenses.isEmpty()) report.empty()
                    expenses.forEach { r -> report.row(date(r.date), if (options.includePersonalData) r.title else r.text("category", "Gasto"), price(r.amountCents)) }
                }
            }
            if (options.includePhotos) {
                val photoRecords = records.filter { it.kind in setOf(RecordKind.MAINTENANCE, RecordKind.ITV, RecordKind.DAMAGE) }.associateBy { it.id }
                val photos = snapshot.attachments.filter { it.recordId in photoRecords && it.mimeType.startsWith("image/") }
                report.section("A", "Anexo fotográfico")
                report.paragraph("Imágenes originales aportadas por el propietario. Revisa su contenido antes de compartir: pueden mostrar importes y datos personales que no se ocultan automáticamente.", muted = true)
                if (photos.isEmpty()) report.empty("No hay fotografías adjuntas a mantenimientos, ITV o daños.")
                photos.forEach { attachment ->
                    val r = photoRecords.getValue(attachment.recordId)
                    val file = File(attachment.localPath).canonicalFile
                    require(file.path.startsWith(context.filesDir.canonicalPath + File.separator) && file.isFile) { "No se puede leer la foto ${attachment.displayName}." }
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(file.path, bounds)
                    require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Foto no compatible: ${attachment.displayName}." }
                    val decode = BitmapFactory.Options().apply { inSampleSize = 1; while (bounds.outWidth / inSampleSize > 1800 || bounds.outHeight / inSampleSize > 1800) inSampleSize *= 2 }
                    val bitmap = BitmapFactory.decodeFile(file.path, decode) ?: error("No se puede abrir ${attachment.displayName}.")
                    try { report.photo(bitmap, "${date(r.date)} · ${r.title.ifBlank { r.kind.label }}", attachment.displayName) } finally { bitmap.recycle() }
                }
                val pdfCount = snapshot.attachments.count { it.recordId in photoRecords && it.mimeType == "application/pdf" }
                if (pdfCount > 0) report.paragraph("Además hay $pdfCount documentos PDF originales adjuntos a estos registros. Se conservan en la copia de seguridad y no se reproducen en este anexo de fotografías.", muted = true)
            }
            report.finish()
            val output = context.contentResolver.openOutputStream(uri, "wt") ?: error("No se puede guardar el informe PDF.")
            output.use { report.document.writeTo(it) }
        } finally { report.close() }
    }

    private class Report(private val vehicleName: String) {
        val document = PdfDocument()
        private var page: PdfDocument.Page? = null
        private var y = 0f
        private var number = 0
        private val ink = Color.rgb(28, 34, 44)
        private val gray = Color.rgb(102, 112, 128)
        private val red = Color.rgb(204, 40, 59)
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val canvas get() = requireNotNull(page).canvas
        private fun style(size: Float = 10f, color: Int = ink, bold: Boolean = false) { paint.textSize = size; paint.color = color; paint.typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL); paint.style = Paint.Style.FILL }
        private fun newPage() {
            finish()
            page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, ++number).create())
            canvas.drawColor(Color.WHITE)
            style(9f, gray); canvas.drawText("POLO APP  /  HISTORIAL DEL VEHÍCULO", 42f, 31f, paint)
            y = 64f
        }
        private fun need(height: Float) { if (page == null || y + height > 785f) newPage() }
        fun finish() {
            page?.let {
                style(8f, gray); it.canvas.drawText("Polo App · Registro personal · ${LocalDate.now().format(DATE)}", 42f, 816f, paint)
                it.canvas.drawText(number.toString(), 540f, 816f, paint)
                document.finishPage(it); page = null
            }
        }
        fun close() { finish(); document.close() }
        fun cover(vehicle: Vehicle, currentKm: Int, options: PdfOptions) {
            newPage()
            style(10f, red, true); canvas.drawText("CADA KILÓMETRO TIENE SU HISTORIA", 42f, y + 12, paint); y += 50
            text("Historial del\nvehículo", 35f, ink, true, 42f, 490f, 42f)
            y += 14
            text(vehicle.name, 21f, ink, true)
            text("${vehicle.make} ${vehicle.model} · ${vehicle.powerHp} CV", 12f, gray)
            y += 26
            val top = y
            style(color = Color.rgb(246, 247, 249)); canvas.drawRoundRect(42f, top, 553f, top + 164, 16f, 16f, paint)
            y += 29
            text("${km(currentKm)} km", 31f, ink, true, 61f, 470f, 39f)
            text("Última lectura registrada", 10f, gray, x = 61f, width = 470f)
            y += 9
            text("Matriculación  ${date(vehicle.registrationDate)}", 11f, ink, x = 61f, width = 470f)
            text("Compra  ${date(vehicle.purchaseDate)} · ${km(vehicle.purchaseKm)} km", 11f, ink, x = 61f, width = 470f)
            y = top + 184
            if (options.includePersonalData) {
                if (vehicle.owner.isNotBlank()) paragraph("Titular: ${vehicle.owner}")
                if (vehicle.plate.isNotBlank()) paragraph("Matrícula: ${vehicle.plate}")
                if (vehicle.vin.isNotBlank()) paragraph("Bastidor: ${vehicle.vin}")
            }
            paragraph("Un registro organizado del cuidado, las intervenciones y el uso de este vehículo. Generado el ${LocalDate.now().format(DATE)}.", muted = true)
            paragraph("Los datos se han introducido manualmente. Contrasta las lecturas, fechas y trabajos con sus justificantes originales. No constituye una certificación del kilometraje ni del estado mecánico.", muted = true)
            paragraph("Privacidad: este informe excluye deuda, pagos personales, notas, pólizas de seguro y documentos de la guantera.", muted = true)
            newPage()
        }
        fun section(index: String, title: String) {
            need(75f); y += 12
            style(10f, red, true); canvas.drawText(index, 42f, y + 11, paint)
            text(title, 20f, ink, true, x = 73f, width = 475f, lineHeight = 26f)
            y += 6
            style(color = Color.rgb(224, 227, 232)); canvas.drawRect(42f, y, 553f, y + 1, paint); y += 15
        }
        fun subheading(title: String) { need(45f); y += 15; text(title, 14f, ink, true); y += 8 }
        fun paragraph(value: String, muted: Boolean = false) { text(value, 10f, if (muted) gray else ink); y += 10 }
        fun empty(value: String = "No hay registros en esta sección.") = paragraph(value, true)
        fun row(date: String, title: String, body: String) {
            need(62f)
            text(date, 9f, red, true)
            text(title, 12f, ink, true, lineHeight = 17f)
            text(body, 10f, gray)
            y += 9
            style(color = Color.rgb(238, 240, 243)); canvas.drawRect(42f, y, 553f, y + 1, paint); y += 12
        }
        private fun text(value: String, size: Float, color: Int, bold: Boolean = false, x: Float = 42f, width: Float = 511f, lineHeight: Float = maxOf(15f, size * 1.4f)) {
            style(size, color, bold)
            value.replace('\t', ' ').split('\n').forEach { paragraph ->
                if (paragraph.isEmpty()) { need(lineHeight); y += lineHeight; return@forEach }
                var remaining = paragraph
                while (remaining.isNotEmpty()) {
                    style(size, color, bold)
                    var count = paint.breakText(remaining, true, width, null).coerceAtLeast(1)
                    if (count < remaining.length) {
                        val space = remaining.lastIndexOf(' ', count - 1)
                        if (space > count / 2) count = space
                    }
                    val line = remaining.take(count)
                    need(lineHeight)
                    style(size, color, bold)
                    canvas.drawText(line, x, y + size, paint)
                    y += lineHeight
                    remaining = remaining.drop(count).trimStart()
                }
            }
        }
        fun photo(bitmap: Bitmap, title: String, caption: String) {
            newPage(); text(title, 15f, ink, true, lineHeight = 21f); y += 10
            val scale = min(511f / bitmap.width, min(560f, 740f - y) / bitmap.height)
            val width = bitmap.width * scale; val height = bitmap.height * scale
            canvas.drawBitmap(bitmap, null, RectF(42f + (511 - width) / 2, y, 42f + (511 + width) / 2, y + height), Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
            y += height + 14
            paragraph(caption, true)
        }
    }
    companion object {
        private val DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy")
        private val PUBLIC_KINDS = setOf(RecordKind.FUEL, RecordKind.MAINTENANCE, RecordKind.EXPENSE, RecordKind.TRIP, RecordKind.ITV, RecordKind.PRESSURE, RecordKind.DAMAGE, RecordKind.ODOMETER)
        private fun date(day: Long) = LocalDate.ofEpochDay(day).format(DATE)
        private fun km(value: Int) = NumberFormat.getIntegerInstance(Locale.forLanguageTag("es-ES")).format(value)
    }
}
