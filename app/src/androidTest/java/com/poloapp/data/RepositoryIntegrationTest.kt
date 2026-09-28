package com.poloapp.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/** Runs against real SQLite/Room in a fresh in-memory database per test. Never opens the user's DB. */
@RunWith(AndroidJUnit4::class)
class RepositoryIntegrationTest {
    private lateinit var database: CarDatabase
    private lateinit var repository: CarRepository
    private val purchaseDate = LocalDate.now().minusDays(90)
    private val vehicle = Vehicle(id = "test-car", name = "Polo de prueba", purchaseKm = 10000,
        purchaseDate = purchaseDate.toEpochDay(), debtStartDate = purchaseDate.toEpochDay())

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, CarDatabase::class.java).build()
        repository = CarRepository(database)
    }
    @After fun tearDown() { database.close() }

    private fun fuel(id: String = "fuel", day: Long = 10, km: Int = 10500) = CarRecord(
        id = id, vehicleId = vehicle.id, kind = RecordKind.FUEL, title = "Depósito lleno",
        date = purchaseDate.plusDays(day).toEpochDay(), odometer = km, amountCents = 4500,
        details = mapOf("liters" to "30", "pricePerLiter" to "1.50", "fullTank" to "true"))
    private fun attachment(id: String = "photo", recordId: String = "fuel") = Attachment(
        id = id, recordId = recordId, localPath = "/data/local/tmp/polo-test-$id.jpg", displayName = "Ticket $id")
    private suspend fun assertRejected(block: suspend () -> Unit) {
        try {
            block()
            fail("La operación inválida debía rechazarse")
        } catch (expected: IllegalArgumentException) {
            assertFalse(expected.message.isNullOrBlank())
        }
    }

    @Test fun createsEditableRulesOnlyOnFirstVehicleSave() = runBlocking {
        repository.saveVehicle(vehicle)
        val first = repository.currentSnapshot()
        assertEquals(listOf(vehicle), first.vehicles)
        assertEquals(CarRepository.defaultRules(vehicle.id).map { it.partKey }.toSet(), first.rules.map { it.partKey }.toSet())
        assertTrue(first.rules.all { it.vehicleId == vehicle.id && it.baselineDate == null && it.baselineKm == null })
        repository.saveVehicle(vehicle.copy(name = "Nombre actualizado"))
        val second = repository.currentSnapshot()
        assertEquals("Nombre actualizado", second.vehicles.single().name)
        assertEquals(first.rules.map { it.id }.toSet(), second.rules.map { it.id }.toSet())
    }

    @Test fun editingParentRowsPreservesRecordsAndAttachments() = runBlocking {
        repository.saveVehicle(vehicle)
        val original = fuel()
        val photo = attachment()
        repository.saveRecordWithAttachments(original, listOf(photo))
        // Room REPLACE would cascade-delete child rows. @Upsert must preserve them.
        repository.saveVehicle(vehicle.copy(color = "Rojo cereza"))
        repository.saveRecord(original.copy(title = "Ticket revisado", notes = "Datos confirmados"))
        val afterEdit = repository.currentSnapshot()
        assertEquals(1, afterEdit.records.size)
        assertEquals("Ticket revisado", afterEdit.records.single().title)
        assertEquals(listOf(photo), afterEdit.attachments)
        repository.saveRecordWithAttachments(afterEdit.records.single(), listOf(photo, attachment("photo-2")))
        assertEquals(setOf("photo", "photo-2"), repository.currentSnapshot().attachments.map { it.id }.toSet())
    }

    @Test fun snapshotFlowContainsUpdatedRecordAndItsAttachmentReferences() = runBlocking {
        repository.saveVehicle(vehicle)
        repository.saveRecordWithAttachments(fuel(), listOf(attachment()))
        val replacement = attachment("replacement")
        repository.saveRecordWithAttachments(fuel().copy(notes = "Nueva factura"), listOf(replacement))
        val observed = repository.snapshot.first()
        assertEquals("Nueva factura", observed.records.single().notes)
        assertEquals(listOf(replacement), observed.attachments)
        assertTrue(observed.attachments.all { a -> observed.records.any { it.id == a.recordId } })
    }

    @Test fun inconsistentHistoricalReadingRollsBackWithoutChangingAnything() = runBlocking {
        repository.saveVehicle(vehicle)
        repository.saveRecordWithAttachments(fuel(day = 20, km = 11000), listOf(attachment()))
        val before = repository.currentSnapshot()
        assertRejected { repository.saveRecord(fuel(id = "bad-history", day = 10, km = 12000)) }
        assertEquals(before, repository.currentSnapshot())
    }

    @Test fun invalidAttachmentReplacementPreservesExistingRecordAndReferences() = runBlocking {
        repository.saveVehicle(vehicle)
        repository.saveRecordWithAttachments(fuel(), listOf(attachment()))
        val before = repository.currentSnapshot()
        assertRejected {
            repository.saveRecordWithAttachments(fuel().copy(title = "No debe guardarse"),
                listOf(attachment("wrong-reference", recordId = "another-record")))
        }
        assertEquals(before, repository.currentSnapshot())
    }

    @Test fun invalidBackupDoesNotEraseExistingGarage() = runBlocking {
        repository.saveVehicle(vehicle)
        repository.saveRecordWithAttachments(fuel(), listOf(attachment()))
        val before = repository.currentSnapshot()
        val brokenBackup = GarageSnapshot(vehicles = listOf(vehicle), records = listOf(fuel().copy(vehicleId = "missing-car")))
        assertRejected { repository.replaceAll(brokenBackup) }
        assertEquals(before, repository.currentSnapshot())
    }

    @Test fun validBackupRestoresAllTablesAndDeletingRecordCascadesItsAttachments() = runBlocking {
        repository.saveVehicle(vehicle)
        val workshop = Workshop(id = "shop", name = "Taller de confianza", rating = 5)
        val repair = CarRecord(id = "repair", vehicleId = vehicle.id, kind = RecordKind.MAINTENANCE,
            title = "Aceite", date = purchaseDate.plusDays(30).toEpochDay(), odometer = 11000,
            details = mapOf("partKey" to "oil", "provider" to "workshop", "workshopId" to workshop.id))
        val backup = GarageSnapshot(vehicles = listOf(vehicle), records = listOf(repair),
            rules = listOf(MaintenanceRule(id = "rule", vehicleId = vehicle.id, title = "Aceite", partKey = "oil", intervalKm = 15000)),
            workshops = listOf(workshop), attachments = listOf(attachment(recordId = repair.id)))
        repository.replaceAll(backup)
        assertEquals(backup, repository.currentSnapshot())
        repository.deleteRecord(repair)
        val afterDelete = repository.currentSnapshot()
        assertTrue(afterDelete.records.isEmpty())
        assertTrue(afterDelete.attachments.isEmpty())
        assertEquals(backup.vehicles, afterDelete.vehicles)
        assertEquals(backup.rules, afterDelete.rules)
        assertEquals(backup.workshops, afterDelete.workshops)
    }
}
