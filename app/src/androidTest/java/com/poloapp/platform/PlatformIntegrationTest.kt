package com.poloapp.platform

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.poloapp.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.Assume.assumeTrue
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import java.util.zip.ZipInputStream

@RunWith(AndroidJUnit4::class)
class PlatformIntegrationTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun backupRoundTripPreservesAllRecordsAndAttachmentBytes() = runBlocking {
        val original = File(context.filesDir, "test-${UUID.randomUUID()}.png")
        val zip = File(context.cacheDir, "test-${UUID.randomUUID()}.zip")
        val image = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        original.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }; image.recycle()
        val vehicle = Vehicle(purchaseKm = 100_000, initialDebtCents = 500_000, owner = "Propietario de prueba", specs = mapOf("oil" to "Ver manual"))
        val record = CarRecord(vehicleId = vehicle.id, kind = RecordKind.DAMAGE, title = "Roce en puerta", odometer = 100_001, details = mapOf("repaired" to "false", "location" to "Trasera"))
        val attachment = Attachment(recordId = record.id, localPath = original.absolutePath, mimeType = "image/png", displayName = "Puerta.png")
        val workshop = Workshop(name = "Taller de prueba", phone = "600000000", address = "Dirección de prueba", rating = 4)
        val maintenance = CarRecord(vehicleId = vehicle.id, kind = RecordKind.MAINTENANCE, title = "Aceite", odometer = 100_001,
            amountCents = 5000, details = mapOf("partKey" to "oil", "provider" to "workshop", "workshopId" to workshop.id, "partsCents" to "2000", "laborCents" to "3000"))
        val fuel = CarRecord(vehicleId = vehicle.id, kind = RecordKind.FUEL, title = "Gasolina", odometer = 100_001,
            amountCents = 7000, details = mapOf("liters" to "40", "fullTank" to "true", "pricePerLiter" to "1.75"))
        val payment = CarRecord(vehicleId = vehicle.id, kind = RecordKind.PAYMENT, title = "Pago familiar", amountCents = 10_000)
        val note = CarRecord(vehicleId = vehicle.id, kind = RecordKind.NOTE, title = "Nota privada", notes = "Texto con tildes, eñes y saltos.\nSegunda línea.",
            details = mapOf("pinned" to "true", "reminderDate" to LocalDate.now().plusDays(7).toEpochDay().toString()))
        val rule = MaintenanceRule(vehicleId = vehicle.id, title = "Aceite y filtro", partKey = "oil", intervalKm = 15000, intervalMonths = 12,
            baselineKm = 100_000, baselineDate = LocalDate.now().toEpochDay())
        val snapshot = GarageSnapshot(vehicles = listOf(vehicle), records = listOf(record, maintenance, fuel, payment, note),
            rules = listOf(rule), workshops = listOf(workshop), attachments = listOf(attachment))
        val manager = BackupManager(context)
        var restored: GarageSnapshot? = null
        try {
            manager.export(snapshot, Uri.fromFile(zip))
            val imported = manager.import(Uri.fromFile(zip)); restored = imported
            assertEquals(snapshot.vehicles, imported.vehicles)
            assertEquals(snapshot.records, imported.records)
            assertEquals(snapshot.rules, imported.rules)
            assertEquals(snapshot.workshops, imported.workshops)
            assertEquals(attachment.copy(localPath = imported.attachments.single().localPath), imported.attachments.single())
            assertArrayEquals(original.readBytes(), File(imported.attachments.single().localPath).readBytes())
            assertNotEquals(original.absolutePath, imported.attachments.single().localPath)
        } finally { restored?.let(manager::discardImported); original.delete(); zip.delete() }
    }

    @Test fun backupRejectsPathTraversalWithoutCreatingExternalFiles() = runBlocking {
        val zip = File(context.cacheDir, "unsafe-${UUID.randomUUID()}.zip")
        val escaped = File(context.filesDir, "escape-${UUID.randomUUID()}")
        try {
            ZipOutputStream(zip.outputStream()).use { output ->
                output.putNextEntry(ZipEntry("../../../${escaped.name}")); output.write("untrusted".toByteArray()); output.closeEntry()
            }
            var rejected = false
            try { BackupManager(context).import(Uri.fromFile(zip)) }
            catch (_: IllegalArgumentException) { rejected = true }
            catch (_: java.util.zip.ZipException) { rejected = true /* Android 14+ rejects traversal before our own validator. */ }
            assertTrue("Unsafe paths must be rejected", rejected)
            assertFalse(escaped.exists())
        } finally { zip.delete(); escaped.delete() }
    }

    @Test fun corruptedAttachmentIsRejectedAndStagingIsRemoved() = runBlocking {
        val imageFile = File(context.filesDir, "test-${UUID.randomUUID()}.png")
        val original = File(context.cacheDir, "valid-${UUID.randomUUID()}.zip")
        val corrupted = File(context.cacheDir, "corrupt-${UUID.randomUUID()}.zip")
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        imageFile.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
        val vehicle = Vehicle()
        val record = CarRecord(vehicleId = vehicle.id, kind = RecordKind.DAMAGE, title = "Daño")
        val snapshot = GarageSnapshot(vehicles = listOf(vehicle), records = listOf(record), attachments = listOf(
            Attachment(recordId = record.id, localPath = imageFile.absolutePath, mimeType = "image/png")))
        val root = File(context.filesDir, "restores")
        val before = root.list()?.toSet() ?: emptySet()
        try {
            BackupManager(context).export(snapshot, Uri.fromFile(original))
            ZipInputStream(original.inputStream()).use { input ->
                ZipOutputStream(corrupted.outputStream()).use { output ->
                    while (true) {
                        val entry = input.nextEntry ?: break
                        val bytes = input.readBytes()
                        if (entry.name.startsWith("attachments/")) bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
                        output.putNextEntry(ZipEntry(entry.name)); output.write(bytes); output.closeEntry(); input.closeEntry()
                    }
                }
            }
            var rejected = false
            try { BackupManager(context).import(Uri.fromFile(corrupted)) } catch (_: IllegalArgumentException) { rejected = true }
            assertTrue("Checksum changes must be rejected", rejected)
            assertEquals(before, root.list()?.toSet() ?: emptySet<String>())
        } finally { imageFile.delete(); original.delete(); corrupted.delete() }
    }

    @Test fun reportPaginatesLongHistoryAndOpensAsValidPdf() = runBlocking {
        val pdf = File(context.cacheDir, "report-${UUID.randomUUID()}.pdf")
        val day = LocalDate.now().minusDays(100)
        val vehicle = Vehicle(purchaseDate = day.toEpochDay(), purchaseKm = 100_000)
        val records = (1..80).map { index -> CarRecord(vehicleId = vehicle.id, kind = RecordKind.MAINTENANCE,
            date = day.plusDays(index.toLong()).toEpochDay(), title = "Revisión $index", odometer = 100_000 + index * 100,
            details = mapOf("provider" to "self", "partKey" to "other"), notes = "Observaciones del trabajo. ".repeat(30)) }
        try {
            PdfExporter(context).export(GarageSnapshot(vehicles = listOf(vehicle), records = records), vehicle, Uri.fromFile(pdf))
            assertTrue(pdf.length() > 1000)
            assertEquals("%PDF-", pdf.inputStream().use { input -> String(ByteArray(5).apply { input.read(this) }, Charsets.US_ASCII) })
            PdfRenderer(ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY)).use { renderer ->
                assertTrue("Long history must continue across pages", renderer.pageCount > 10)
                renderer.openPage(renderer.pageCount - 1).use { page ->
                    val image = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                    page.render(image, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY); image.recycle()
                }
            }
        } finally { pdf.delete() }
    }

    @androidx.test.filters.SdkSuppress(minSdkVersion = 35)
    @Test fun reportExcludesPrivateRecordsAndHonorsPriceAndIdentityOptions() = runBlocking {
        // Text extraction is a platform PDF API from Android 15. PDF rendering is tested on all supported APIs above.
        assumeTrue(Build.VERSION.SDK_INT >= 35)
        val hidden = File(context.cacheDir, "hidden-${UUID.randomUUID()}.pdf")
        val visible = File(context.cacheDir, "visible-${UUID.randomUUID()}.pdf")
        val vehicle = Vehicle(owner = "PRIVATETESTOWNER", plate = "PRIVATEPLATE", vin = "PRIVATEVIN")
        val record = CarRecord(vehicleId = vehicle.id, kind = RecordKind.DAMAGE, title = "PUBLICDAMAGE", amountCents = 12345)
        val privateRecords = listOf(RecordKind.PAYMENT, RecordKind.NOTE, RecordKind.INSURANCE, RecordKind.DOCUMENT).map { kind ->
            CarRecord(vehicleId = vehicle.id, kind = kind, title = "SECRET${kind.name}", notes = "SECRETCONTENT${kind.name}",
                details = mapOf("policy" to "SECRETPOLICYNUMBER"))
        }
        val snapshot = GarageSnapshot(vehicles = listOf(vehicle), records = listOf(record) + privateRecords)
        try {
            val exporter = PdfExporter(context)
            exporter.export(snapshot, vehicle, Uri.fromFile(hidden), PdfOptions(includePrices = false, includePersonalData = false))
            exporter.export(snapshot, vehicle, Uri.fromFile(visible), PdfOptions(includePrices = true, includePersonalData = true))
            fun text(file: File): String {
                return PdfRenderer(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)).use { renderer ->
                    (0 until renderer.pageCount).joinToString("\n") { index ->
                        renderer.openPage(index).use { page -> page.textContents.joinToString(" ") { it.text } }
                    }
                }
            }
            val hiddenText = text(hidden)
            val visibleText = text(visible)
            assertTrue(hiddenText.contains("PUBLICDAMAGE"))
            assertTrue(visibleText.contains("PUBLICDAMAGE"))
            assertFalse(hiddenText.contains("123,45"))
            assertTrue(visibleText.contains("123,45"))
            listOf("PRIVATETESTOWNER", "PRIVATEPLATE", "PRIVATEVIN").forEach { token ->
                assertFalse(hiddenText.contains(token)); assertTrue(visibleText.contains(token))
            }
            privateRecords.forEach { privateRecord ->
                assertFalse(hiddenText.contains(privateRecord.title)); assertFalse(visibleText.contains(privateRecord.title))
                assertFalse(hiddenText.contains(privateRecord.notes)); assertFalse(visibleText.contains(privateRecord.notes))
            }
            assertFalse(visibleText.contains("SECRETPOLICYNUMBER"))
        } finally { hidden.delete(); visible.delete() }
    }
}
