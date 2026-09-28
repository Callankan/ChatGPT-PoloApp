package com.poloapp.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.util.UUID

enum class RecordKind(val label: String) {
    FUEL("Repostaje"), MAINTENANCE("Mantenimiento"), EXPENSE("Gasto"), PAYMENT("Pago"),
    TRIP("Viaje"), NOTE("Nota"), ITV("ITV"), INSURANCE("Seguro"),
    PRESSURE("Presión"), DAMAGE("Daño"), DOCUMENT("Documento"), ODOMETER("Kilometraje")
}

@Entity(tableName = "vehicles")
data class Vehicle(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String = "Mi Polo", val make: String = "Volkswagen", val model: String = "Polo Mk5 1.2 TSI",
    val powerHp: Int = 90, val registrationDate: Long = LocalDate.of(2013, 6, 1).toEpochDay(),
    val plate: String = "", val vin: String = "", val color: String = "Rojo",
    val purchaseDate: Long = LocalDate.now().toEpochDay(), val purchaseKm: Int = 0,
    val initialDebtCents: Long = 0, val debtStartDate: Long = LocalDate.now().toEpochDay(),
    val owner: String = "", val specs: Map<String, String> = emptyMap()
)

@Entity(tableName = "records", indices = [androidx.room.Index("vehicleId"), androidx.room.Index(value = ["vehicleId", "date"])],
    foreignKeys = [androidx.room.ForeignKey(entity = Vehicle::class, parentColumns = ["id"], childColumns = ["vehicleId"], onDelete = androidx.room.ForeignKey.CASCADE)])
data class CarRecord(
    @PrimaryKey val id: String = UUID.randomUUID().toString(), val vehicleId: String,
    val kind: RecordKind, val date: Long = LocalDate.now().toEpochDay(),
    val title: String = "", val notes: String = "", val odometer: Int? = null,
    val amountCents: Long = 0, val details: Map<String, String> = emptyMap(),
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "workshops")
data class Workshop(@PrimaryKey val id: String = UUID.randomUUID().toString(), val name: String,
    val phone: String = "", val address: String = "", val rating: Int = 0)

@Entity(tableName = "maintenance_rules", indices = [androidx.room.Index("vehicleId")],
    foreignKeys = [androidx.room.ForeignKey(entity = Vehicle::class, parentColumns = ["id"], childColumns = ["vehicleId"], onDelete = androidx.room.ForeignKey.CASCADE)])
data class MaintenanceRule(
    @PrimaryKey val id: String = UUID.randomUUID().toString(), val vehicleId: String,
    val title: String, val partKey: String, val intervalKm: Int? = null, val intervalMonths: Int? = null,
    val baselineKm: Int? = null, val baselineDate: Long? = null,
    val warningKm: Int = 1000, val warningDays: Int = 30, val enabled: Boolean = true
)

@Entity(tableName = "attachments", indices = [androidx.room.Index("recordId")],
    foreignKeys = [androidx.room.ForeignKey(entity = CarRecord::class, parentColumns = ["id"], childColumns = ["recordId"], onDelete = androidx.room.ForeignKey.CASCADE)])
data class Attachment(@PrimaryKey val id: String = UUID.randomUUID().toString(), val recordId: String,
    val localPath: String, val mimeType: String = "image/jpeg", val displayName: String = "Foto")

data class GarageSnapshot(val vehicles: List<Vehicle> = emptyList(), val records: List<CarRecord> = emptyList(),
    val rules: List<MaintenanceRule> = emptyList(), val workshops: List<Workshop> = emptyList(), val attachments: List<Attachment> = emptyList()) {
    fun recordsFor(vehicleId: String) = records.filter { it.vehicleId == vehicleId }.sortedWith(compareByDescending<CarRecord> { it.date }.thenByDescending { it.createdAt })
    fun rulesFor(vehicleId: String) = rules.filter { it.vehicleId == vehicleId }
}

fun CarRecord.text(key: String, fallback: String = "") = details[key] ?: fallback
fun CarRecord.number(key: String): Double? = details[key]?.replace(',', '.')?.toDoubleOrNull()?.takeIf { it.isFinite() }
fun CarRecord.flag(key: String) = details[key] == "true"
