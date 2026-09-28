package com.poloapp.domain

import com.poloapp.data.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class GarageValidationTest {
    private val day = LocalDate.of(2025, 1, 1)
    private val today = LocalDate.of(2025, 6, 1)
    private val vehicle = Vehicle(id = "car", purchaseDate = day.toEpochDay(), purchaseKm = 10000,
        initialDebtCents = 10000, debtStartDate = day.toEpochDay())
    private fun reading(days: Long, km: Int) = CarRecord(vehicleId = "car", kind = RecordKind.ODOMETER,
        title = "Lectura", date = day.plusDays(days).toEpochDay(), odometer = km)
    private fun validate(records: List<CarRecord>) = GarageValidation.validate(GarageSnapshot(vehicles = listOf(vehicle), records = records), today)
    @Test fun `historical entry can be created after a newer reading`() {
        validate(listOf(reading(30, 12000), reading(10, 10500), reading(20, 11000)))
    }
    @Test fun `same day lower readings are allowed because no time is recorded`() {
        validate(listOf(reading(10, 11000), reading(10, 10500), reading(11, 11100)))
    }
    @Test(expected = IllegalArgumentException::class) fun `retroactive reading cannot exceed a later reading`() {
        validate(listOf(reading(20, 11000), reading(10, 12000)))
    }
    @Test(expected = IllegalArgumentException::class) fun `reading cannot fall below an earlier purchase baseline`() {
        validate(listOf(reading(1, 9999)))
    }
    @Test fun `pre purchase history is allowed with lower mileage`() {
        validate(listOf(reading(-1, 9900)))
    }
    @Test(expected = IllegalArgumentException::class) fun `trip start cannot contradict an earlier odometer reading`() {
        validate(listOf(reading(5, 11000), CarRecord(vehicleId = "car", kind = RecordKind.TRIP,
            title = "Madrid", date = day.plusDays(10).toEpochDay(), odometer = 12000,
            details = mapOf("startKm" to "10900", "endKm" to "12000"))))
    }
    @Test(expected = IllegalArgumentException::class) fun `future actual records are rejected`() {
        validate(listOf(reading(1000, 12000)))
    }
    @Test fun `future note reminders are allowed`() {
        validate(listOf(CarRecord(vehicleId = "car", kind = RecordKind.NOTE, title = "Revisión", date = day.toEpochDay(),
            details = mapOf("reminderDate" to day.plusYears(1).toEpochDay().toString()))))
    }
    @Test(expected = IllegalArgumentException::class) fun `overpayment cannot make debt negative`() {
        validate(listOf(CarRecord(vehicleId = "car", kind = RecordKind.PAYMENT, title = "Pago", date = day.toEpochDay(), amountCents = 10001)))
    }
    @Test(expected = IllegalArgumentException::class) fun `sum of payments cannot exceed debt`() {
        validate(listOf(CarRecord(vehicleId = "car", kind = RecordKind.PAYMENT, title = "Pago", date = day.toEpochDay(), amountCents = 6000),
            CarRecord(vehicleId = "car", kind = RecordKind.PAYMENT, title = "Pago", date = day.toEpochDay(), amountCents = 5000)))
    }
    @Test(expected = IllegalArgumentException::class) fun `payment cannot predate the debt`() {
        validate(listOf(CarRecord(vehicleId = "car", kind = RecordKind.PAYMENT, title = "Pago", date = day.minusDays(1).toEpochDay(), amountCents = 1000)))
    }
    @Test(expected = IllegalArgumentException::class) fun `invalid or nonfinite liters are rejected`() {
        validate(listOf(CarRecord(vehicleId = "car", kind = RecordKind.FUEL, title = "Gasolina", date = day.toEpochDay(),
            odometer = 10000, amountCents = 5000, details = mapOf("liters" to "NaN", "fullTank" to "true"))))
    }
    @Test(expected = IllegalArgumentException::class) fun `dangling vehicle reference is rejected during restore`() {
        GarageValidation.validate(GarageSnapshot(records = listOf(reading(1, 11000))), today)
    }
    @Test(expected = IllegalArgumentException::class) fun `duplicate IDs are rejected during restore`() {
        val row = reading(1, 11000)
        validate(listOf(row, row))
    }
    @Test(expected = IllegalArgumentException::class) fun `workshop intervention must preserve a real workshop reference`() {
        validate(listOf(CarRecord(vehicleId = "car", kind = RecordKind.MAINTENANCE, title = "Aceite", date = day.toEpochDay(),
            odometer = 10000, details = mapOf("partKey" to "oil", "provider" to "workshop", "workshopId" to "missing"))))
    }
    @Test(expected = IllegalArgumentException::class) fun `parts and labour cannot disagree with total`() {
        validate(listOf(CarRecord(vehicleId = "car", kind = RecordKind.MAINTENANCE, title = "Aceite", date = day.toEpochDay(),
            odometer = 10000, amountCents = 10000, details = mapOf("partKey" to "oil", "provider" to "self", "partsCents" to "5000", "laborCents" to "6000"))))
    }
    @Test fun `self maintenance with optional omitted breakdown is valid`() {
        validate(listOf(CarRecord(vehicleId = "car", kind = RecordKind.MAINTENANCE, title = "Aceite", date = day.toEpochDay(),
            odometer = 10000, amountCents = 5000, details = mapOf("partKey" to "oil", "provider" to "self"))))
    }
    @Test(expected = IllegalArgumentException::class) fun `negative money rejected`() {
        validate(listOf(CarRecord(vehicleId = "car", kind = RecordKind.EXPENSE, title = "Parking", date = day.toEpochDay(), amountCents = -1)))
    }
}
