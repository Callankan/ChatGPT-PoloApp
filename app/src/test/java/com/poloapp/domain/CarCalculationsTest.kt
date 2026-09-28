package com.poloapp.domain

import com.poloapp.data.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class CarCalculationsTest {
    private val date = LocalDate.of(2025, 1, 1)
    private val vehicle = Vehicle(id = "car", purchaseDate = date.toEpochDay(), purchaseKm = 10000,
        initialDebtCents = 10001, debtStartDate = date.toEpochDay())
    private fun fuel(day: Long, km: Int, liters: Double, cents: Long, full: Boolean, car: String = "car") =
        CarRecord(vehicleId = car, kind = RecordKind.FUEL, date = date.plusDays(day).toEpochDay(), odometer = km,
            amountCents = cents, title = "Gasolina", details = mapOf("liters" to "$liters", "fullTank" to "$full"))
    private fun maintenance(day: Long, km: Int, part: String = "oil", axle: String = "all") = CarRecord(
        vehicleId = "car", kind = RecordKind.MAINTENANCE, date = date.plusDays(day).toEpochDay(), odometer = km,
        title = "Cambio", details = mapOf("partKey" to part, "axle" to axle, "provider" to "self"))

    @Test fun `first full tank does not create fictitious consumption`() {
        val stats = CarCalculations.fuelStats(listOf(fuel(0, 10000, 40.0, 6000, true)))
        assertNull(stats.averageLitersPer100Km)
        assertEquals(1.5, stats.averagePricePerLiter!!, 0.00001)
        assertEquals(6000, stats.totalCents)
    }
    @Test fun `full to full includes every partial refill but excludes the initial fill`() {
        val stats = CarCalculations.fuelStats(listOf(fuel(10, 10800, 20.0, 3000, true),
            fuel(0, 10000, 42.0, 6300, true), fuel(3, 10300, 10.0, 1500, false), fuel(6, 10600, 18.0, 2700, false)))
        assertEquals(1, stats.intervals.size)
        assertEquals(48.0, stats.intervals.single().liters, 0.00001)
        assertEquals(6.0, stats.averageLitersPer100Km!!, 0.00001)
        assertEquals(7200, stats.intervals.single().costCents)
        assertEquals(90.0, stats.totalLiters, 0.00001)
    }
    @Test fun `unclosed partial fills affect purchases not measured consumption`() {
        val stats = CarCalculations.fuelStats(listOf(fuel(0, 10000, 40.0, 6000, true),
            fuel(10, 10500, 30.0, 4800, true), fuel(12, 10600, 10.0, 1800, false)))
        assertEquals(6.0, stats.averageLitersPer100Km!!, 0.00001)
        assertEquals(1.575, stats.averagePricePerLiter!!, 0.00001)
        assertEquals(1, stats.intervals.size)
    }
    @Test fun `mean consumption is weighted by distance`() {
        val stats = CarCalculations.fuelStats(listOf(fuel(0, 10000, 40.0, 6000, true),
            fuel(5, 10200, 20.0, 3000, true), fuel(20, 11000, 40.0, 6000, true)))
        assertEquals(6.0, stats.averageLitersPer100Km!!, 0.00001)
    }
    @Test fun `garage fuel statistics never join different vehicles`() {
        val stats = CarCalculations.fuelStats(listOf(fuel(0, 10000, 40.0, 6000, true),
            fuel(2, 10400, 20.0, 3000, true, "other")))
        assertNull(stats.averageLitersPer100Km)
    }
    @Test fun `partial before baseline cannot establish consumption`() {
        assertNull(CarCalculations.fuelStats(listOf(fuel(0, 10000, 20.0, 3000, false),
            fuel(10, 10500, 30.0, 4500, true))).averageLitersPer100Km)
    }
    @Test fun `debt counts only this vehicle's payments`() {
        val rows = listOf(CarRecord(vehicleId = "car", kind = RecordKind.PAYMENT, amountCents = 1234),
            CarRecord(vehicleId = "car", kind = RecordKind.EXPENSE, amountCents = 2000),
            CarRecord(vehicleId = "other", kind = RecordKind.PAYMENT, amountCents = 9000))
        val summary = CarCalculations.debtSummary(vehicle, rows)
        assertEquals(1234, summary.paidCents)
        assertEquals(8767, summary.remainingCents)
    }
    @Test fun `schedule caps final payment and preserves month end anchor`() {
        val rows = CarCalculations.simulateDebt(10001, 3000, LocalDate.of(2025, 1, 31))
        assertEquals(listOf(3000L, 3000L, 3000L, 1001L), rows.map { it.paymentCents })
        assertEquals(LocalDate.of(2025, 2, 28), rows[1].date)
        assertEquals(LocalDate.of(2025, 3, 31), rows[2].date)
        assertEquals(0L, rows.last().remainingCents)
    }
    @Test fun `extra payments are applied on their actual date and can settle early`() {
        val rows = CarCalculations.simulateDebt(10000, 2000, date, mapOf(date.plusDays(9) to 20000L))
        assertEquals(2, rows.size)
        assertEquals(date.plusDays(9), rows.last().date)
        assertEquals(8000, rows.last().paymentCents)
        assertTrue(rows.last().extra)
    }
    @Test fun `inverse payment rounds upwards to cents and accounts for deadline`() {
        assertEquals(3334, CarCalculations.monthlyPaymentToFinish(10001, date, date.plusMonths(2)))
        assertEquals(5001, CarCalculations.monthlyPaymentToFinish(10001, date.plusDays(30), date.plusMonths(2)))
        assertEquals(2334, CarCalculations.monthlyPaymentToFinish(10001, date, date.plusMonths(2), mapOf(date.plusDays(10) to 3000L)))
    }
    @Test fun `inverse ignores extra payments after deadline`() {
        assertEquals(5000, CarCalculations.monthlyPaymentToFinish(10000, date, date.plusMonths(1), mapOf(date.plusMonths(3) to 9999L)))
    }
    @Test(expected = IllegalArgumentException::class) fun `zero installment cannot loop forever`() {
        CarCalculations.simulateDebt(10000, 0, date)
    }
    @Test fun `paid off debt returns no simulated rows`() {
        assertTrue(CarCalculations.simulateDebt(0, 0, date).isEmpty())
    }
    @Test fun `unknown service history stays unknown instead of showing false health`() {
        val rule = MaintenanceRule(vehicleId = "car", title = "Aceite", partKey = "oil", intervalKm = 15000, intervalMonths = 12)
        val statuses = CarCalculations.maintenanceStatuses(vehicle, emptyList(), listOf(rule), date)
        assertEquals(MaintenanceState.UNKNOWN, statuses.single().state)
        assertNull(CarCalculations.healthScore(statuses))
    }
    @Test fun `maintenance becomes due on whichever limit arrives first`() {
        val rule = MaintenanceRule(vehicleId = "car", title = "Aceite", partKey = "oil", intervalKm = 15000, intervalMonths = 12)
        val record = maintenance(0, 10000)
        val statuses = CarCalculations.maintenanceStatuses(vehicle, listOf(record), listOf(rule), date.plusYears(1))
        assertEquals(MaintenanceState.OVERDUE, statuses.single().state)
        assertEquals(15000, statuses.single().remainingKm)
        assertEquals(0L, statuses.single().remainingDays)
    }
    @Test fun `replacing front tyres leaves rear tyres active with correct lifetime`() {
        val all = maintenance(0, 10000, "tires", "all")
        val front = maintenance(100, 20000, "tires", "front")
        val mileage = CarRecord(vehicleId = "car", kind = RecordKind.ODOMETER, odometer = 25000, date = date.plusDays(150).toEpochDay())
        val life = CarCalculations.partLifecycles(vehicle, listOf(all, front, mileage))
        assertEquals(3, life.size)
        assertEquals(10000, life.single { it.record.id == all.id && it.axle == "front" }.distanceKm)
        assertFalse(life.single { it.record.id == all.id && it.axle == "front" }.isActive)
        assertEquals(15000, life.single { it.record.id == all.id && it.axle == "rear" }.distanceKm)
        assertEquals(5000, life.single { it.record.id == front.id }.distanceKm)
    }
    @Test fun `spending excludes repayments and estimated trip fuel but includes tolls`() {
        val records = listOf(fuel(2, 10500, 30.0, 5000, true),
            CarRecord(vehicleId = "car", kind = RecordKind.PAYMENT, amountCents = 100000),
            CarRecord(vehicleId = "car", kind = RecordKind.TRIP, amountCents = 1000, details = mapOf("tollsCents" to "250")),
            CarRecord(vehicleId = "other", kind = RecordKind.EXPENSE, amountCents = 99000))
        val spending = CarCalculations.spendingStats(vehicle, records)
        assertEquals(5250, spending.totalCents)
        assertEquals(0.105, spending.costPerKm!!, 0.000001)
    }
    @Test fun `trip estimate is converted to integer cents`() {
        val stats = FuelStats(6.0, 1.5, 0.0, 0, emptyList())
        assertEquals(900L, CarCalculations.estimatedTripCostCents(100, stats))
    }
    @Test fun `projected daily distance uses observed history`() {
        val reading = CarRecord(vehicleId = "car", kind = RecordKind.ODOMETER, date = date.plusDays(100).toEpochDay(), odometer = 15000)
        assertEquals(50.0, CarCalculations.dailyDistance(vehicle, listOf(reading))!!, 0.000001)
    }
    @Test fun `extra payments alone can settle debt with no monthly installment`() {
        val rows = CarCalculations.simulateDebt(10000, 0, date, mapOf(date.plusDays(10) to 10000L))
        assertEquals(1, rows.size)
        assertEquals(10000L, rows.last().paymentCents)
    }
    @Test fun `new front axle cannot reset older rear axle reminder`() {
        val rule = MaintenanceRule(vehicleId = "car", title = "Neumáticos", partKey = "tires", intervalKm = 15000)
        val records = listOf(maintenance(0, 10000, "tires", "all"), maintenance(100, 20000, "tires", "front"),
            CarRecord(vehicleId = "car", kind = RecordKind.ODOMETER, odometer = 25000, date = date.plusDays(150).toEpochDay()))
        val status = CarCalculations.maintenanceStatuses(vehicle, records, listOf(rule), date.plusDays(150)).single()
        assertEquals(MaintenanceState.OVERDUE, status.state)
        assertEquals(0, status.remainingKm)
    }
    @Test fun `historical service does not override a more recent manual baseline`() {
        val rule = MaintenanceRule(vehicleId = "car", title = "Aceite", partKey = "oil", intervalKm = 15000,
            baselineDate = date.plusDays(30).toEpochDay(), baselineKm = 11000)
        val records = listOf(maintenance(0, 10000), CarRecord(vehicleId = "car", kind = RecordKind.ODOMETER,
            odometer = 12000, date = date.plusDays(60).toEpochDay()))
        val status = CarCalculations.maintenanceStatuses(vehicle, records, listOf(rule), date.plusDays(60)).single()
        assertEquals(14000, status.remainingKm)
        assertNull(status.lastRecord)
    }

    @Test fun `ITV suggestion changes with passenger vehicle age`() {
        val registered = LocalDate.of(2013, 6, 1)
        assertEquals(LocalDate.of(2017, 6, 1), CarCalculations.suggestedItvDueDate(registered, registered))
        assertEquals(LocalDate.of(2019, 6, 1), CarCalculations.suggestedItvDueDate(registered, registered.plusYears(4)))
        assertEquals(LocalDate.of(2024, 6, 1), CarCalculations.suggestedItvDueDate(registered, registered.plusYears(10)))
    }

}
