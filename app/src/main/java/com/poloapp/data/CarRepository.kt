package com.poloapp.data

import android.content.Context
import androidx.room.withTransaction
import com.poloapp.domain.GarageValidation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** All mutations validate the complete candidate snapshot inside the same database transaction. */
class CarRepository internal constructor(internal val database: CarDatabase) {
    constructor(context: Context) : this(CarDatabase.get(context))
    private val dao = database.carDao()
    val snapshot: Flow<GarageSnapshot> = combine(dao.observeVehicles(), dao.observeRecords(), dao.observeRules(),
        dao.observeWorkshops(), dao.observeAttachments()) { _, _, _, _, _ -> Unit }
        // Read all tables in one transaction, avoiding partially observed record/attachment updates.
        .map { currentSnapshot() }.distinctUntilChanged()

    suspend fun currentSnapshot(): GarageSnapshot = database.withTransaction { readSnapshot() }
    private suspend fun readSnapshot() = GarageSnapshot(dao.vehicles(), dao.records(), dao.rules(), dao.workshops(), dao.attachments())

    suspend fun saveVehicle(vehicle: Vehicle) = database.withTransaction {
        val old = readSnapshot()
        val initialRules = if (old.vehicles.none { it.id == vehicle.id }) defaultRules(vehicle.id) else emptyList()
        val candidate = old.copy(vehicles = old.vehicles.filterNot { it.id == vehicle.id } + vehicle, rules = old.rules + initialRules)
        GarageValidation.validate(candidate)
        dao.upsert(vehicle)
        if (initialRules.isNotEmpty()) dao.insertRules(initialRules)
    }

    suspend fun saveRecord(record: CarRecord) = database.withTransaction {
        val old = readSnapshot()
        GarageValidation.validate(old.copy(records = old.records.filterNot { it.id == record.id } + record))
        dao.upsert(record)
    }

    suspend fun saveRecordWithAttachments(record: CarRecord, attachments: List<Attachment>) = database.withTransaction {
        require(attachments.all { it.recordId == record.id }) { "Hay archivos asociados a otro registro." }
        val old = readSnapshot()
        val candidate = old.copy(records = old.records.filterNot { it.id == record.id } + record,
            attachments = old.attachments.filterNot { it.recordId == record.id } + attachments)
        GarageValidation.validate(candidate)
        dao.upsert(record)
        dao.deleteAttachmentsFor(record.id)
        dao.insertAttachments(attachments)
    }

    suspend fun deleteRecord(record: CarRecord) = database.withTransaction {
        val old = readSnapshot()
        val candidate = old.copy(records = old.records.filterNot { it.id == record.id },
            attachments = old.attachments.filterNot { it.recordId == record.id })
        GarageValidation.validate(candidate)
        dao.delete(record)
    }

    suspend fun saveWorkshop(workshop: Workshop) = database.withTransaction {
        val old = readSnapshot()
        GarageValidation.validate(old.copy(workshops = old.workshops.filterNot { it.id == workshop.id } + workshop))
        dao.upsert(workshop)
    }

    suspend fun deleteWorkshop(workshop: Workshop) = database.withTransaction {
        val old = readSnapshot()
        require(old.records.none { it.text("workshopId") == workshop.id }) { "Este taller tiene intervenciones. Conserva su ficha para mantener el historial." }
        dao.delete(workshop)
    }

    suspend fun saveRule(rule: MaintenanceRule) = database.withTransaction {
        val old = readSnapshot()
        GarageValidation.validate(old.copy(rules = old.rules.filterNot { it.id == rule.id } + rule))
        dao.upsert(rule)
    }

    suspend fun deleteRule(rule: MaintenanceRule) = database.withTransaction { dao.delete(rule) }

    suspend fun saveAttachment(attachment: Attachment) = database.withTransaction {
        val old = readSnapshot()
        GarageValidation.validate(old.copy(attachments = old.attachments.filterNot { it.id == attachment.id } + attachment))
        dao.upsert(attachment)
    }

    suspend fun replaceAll(snapshot: GarageSnapshot) = database.withTransaction {
        GarageValidation.validate(snapshot)
        dao.clearAttachments()
        dao.clearRecords()
        dao.clearRules()
        dao.clearWorkshops()
        dao.clearVehicles()
        dao.insertVehicles(snapshot.vehicles)
        dao.insertWorkshops(snapshot.workshops)
        dao.insertRules(snapshot.rules)
        dao.insertRecords(snapshot.records)
        dao.insertAttachments(snapshot.attachments)
    }

    companion object {
        /** Editable starting suggestions, never an assertion of the manufacturer's servicing plan.
         * No countdown runs until the owner supplies the last intervention or a baseline. */
        fun defaultRules(vehicleId: String): List<MaintenanceRule> = listOf(
            MaintenanceRule(vehicleId = vehicleId, title = "Aceite de motor", partKey = "oil", intervalKm = 15_000, intervalMonths = 12),
            MaintenanceRule(vehicleId = vehicleId, title = "Filtro de aceite", partKey = "oil_filter", intervalKm = 15_000, intervalMonths = 12),
            MaintenanceRule(vehicleId = vehicleId, title = "Filtro de aire", partKey = "air_filter", intervalKm = 30_000, intervalMonths = 24),
            MaintenanceRule(vehicleId = vehicleId, title = "Filtro de habitáculo", partKey = "cabin_filter", intervalKm = 15_000, intervalMonths = 12),
            MaintenanceRule(vehicleId = vehicleId, title = "Bujías", partKey = "spark_plugs", intervalKm = 60_000, intervalMonths = 48),
            MaintenanceRule(vehicleId = vehicleId, title = "Líquido de frenos", partKey = "brake_fluid", intervalMonths = 24),
            MaintenanceRule(vehicleId = vehicleId, title = "Frenos · revisar intervalo", partKey = "brakes", enabled = false),
            MaintenanceRule(vehicleId = vehicleId, title = "Neumáticos · revisar intervalo", partKey = "tires", enabled = false),
            MaintenanceRule(vehicleId = vehicleId, title = "Distribución · confirmar según motor", partKey = "timing", enabled = false)
        )
    }
}
