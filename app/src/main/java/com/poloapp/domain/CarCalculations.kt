package com.poloapp.domain

import com.poloapp.data.*
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlin.math.ceil
import kotlin.math.roundToInt

data class FuelInterval(val startDate: LocalDate, val endDate: LocalDate, val startKm: Int, val endKm: Int,
    val liters: Double, val costCents: Long) {
    val distanceKm: Int get() = endKm - startKm
    val litersPer100Km: Double get() = liters * 100.0 / distanceKm
}
data class FuelStats(val averageLitersPer100Km: Double?, val averagePricePerLiter: Double?,
    val totalLiters: Double, val totalCents: Long, val intervals: List<FuelInterval>)
data class DebtSummary(val initialCents: Long, val paidCents: Long, val remainingCents: Long, val progress: Float)
data class DebtInstallment(val date: LocalDate, val paymentCents: Long, val remainingCents: Long,
    val extra: Boolean = false)
enum class MaintenanceState { UNKNOWN, OK, SOON, OVERDUE }
data class MaintenanceStatus(val rule: MaintenanceRule, val lastRecord: CarRecord?, val remainingKm: Int?,
    val remainingDays: Long?, val dueDate: LocalDate?, val estimatedDate: LocalDate?, val state: MaintenanceState)
data class PartLifecycle(val record: CarRecord, val retiredBy: CarRecord?, val distanceKm: Int,
    val isActive: Boolean, val axle: String = record.text("axle", "all"))
data class SpendingStats(val totalCents: Long, val costPerKm: Double?, val byCategory: Map<String, Long>,
    val byMonth: Map<YearMonth, Long>)

/** Pure calculations. Dates are local calendar dates; all stored money is integer cents. */
object CarCalculations {
    private val chronological = compareBy<CarRecord> { it.date }.thenBy { it.odometer ?: 0 }.thenBy { it.createdAt }.thenBy { it.id }
    fun currentKm(vehicle: Vehicle, records: List<CarRecord>): Int = records.asSequence()
        .filter { it.vehicleId == vehicle.id }.mapNotNull { it.odometer }.maxOrNull()?.coerceAtLeast(vehicle.purchaseKm) ?: vehicle.purchaseKm

    /** A full refill establishes a baseline; only the next full refill closes a consumption interval.
     * Partial refills between the two are included. The baseline's fuel is never counted as consumed. */
    fun fuelStats(records: List<CarRecord>): FuelStats {
        val fills = records.filter { it.kind == RecordKind.FUEL }.sortedWith(chronological)
        val totalLiters = fills.sumOf { it.number("liters") ?: 0.0 }
        val totalCents = fills.sumOf { it.amountCents }
        val intervals = mutableListOf<FuelInterval>()
        // Never combine vehicles, even when passed an entire garage.
        fills.groupBy { it.vehicleId }.values.forEach { vehicleFills ->
            var baseline: CarRecord? = null
            var liters = 0.0
            var cost = 0L
            vehicleFills.forEach fillLoop@ { fill ->
                if (fill.odometer == null) { baseline = null; liters = 0.0; cost = 0L; return@fillLoop }
                if (baseline != null) { liters += fill.number("liters") ?: 0.0; cost += fill.amountCents }
                if (fill.flag("fullTank")) {
                    val start = baseline
                    if (start?.odometer != null && fill.odometer > start.odometer && liters > 0) {
                        intervals += FuelInterval(LocalDate.ofEpochDay(start.date), LocalDate.ofEpochDay(fill.date),
                            start.odometer, fill.odometer, liters, cost)
                    }
                    baseline = fill; liters = 0.0; cost = 0L
                }
            }
        }
        val distance = intervals.sumOf { it.distanceKm.toLong() }
        return FuelStats(if (distance > 0) intervals.sumOf { it.liters } * 100.0 / distance else null,
            if (totalLiters > 0) totalCents / 100.0 / totalLiters else null, totalLiters, totalCents,
            intervals.sortedBy { it.endDate })
    }

    fun debtSummary(vehicle: Vehicle, records: List<CarRecord>): DebtSummary {
        val paid = records.filter { it.vehicleId == vehicle.id && it.kind == RecordKind.PAYMENT }.sumOf { it.amountCents }
        return DebtSummary(vehicle.initialDebtCents, paid, (vehicle.initialDebtCents - paid).coerceAtLeast(0),
            if (vehicle.initialDebtCents > 0) (paid.toDouble() / vehicle.initialDebtCents).toFloat().coerceIn(0f, 1f) else 0f)
    }

    /** Interest-free schedule, including extra payments on their actual dates. The last payment is capped.
     * Month dates are derived from the original day (31 Jan → 28 Feb → 31 Mar). */
    fun simulateDebt(balanceCents: Long, monthlyCents: Long, firstPaymentDate: LocalDate,
        extraPayments: Map<LocalDate, Long> = emptyMap()): List<DebtInstallment> {
        require(balanceCents >= 0) { "La deuda pendiente no puede ser negativa." }
        require(monthlyCents >= 0) { "La cuota mensual no puede ser negativa." }
        val afterExtras = extraPayments.values.fold(balanceCents) { balance, payment ->
            (balance - minOf(balance, payment.coerceAtLeast(0))).coerceAtLeast(0)
        }
        require(monthlyCents > 0 || afterExtras == 0L) { "La cuota debe ser mayor que cero si los pagos extra no saldan toda la deuda." }
        require(extraPayments.all { (date, amount) -> !date.isBefore(firstPaymentDate) && amount >= 0 }) {
            "Los pagos extra deben ser positivos y posteriores al inicio de la simulación." }
        if (balanceCents == 0L) return emptyList()
        if (monthlyCents == 0L) {
            var remaining = balanceCents
            return buildList {
                extraPayments.toSortedMap().forEach { (date, amount) ->
                    if (remaining > 0 && amount > 0) {
                        require(!date.isAfter(firstPaymentDate.plusYears(100))) { "El calendario supera 100 años." }
                        val payment = minOf(remaining, amount)
                        remaining -= payment
                        add(DebtInstallment(date, payment, remaining, true))
                    }
                }
            }
        }
        val rows = mutableListOf<DebtInstallment>()
        var remaining = balanceCents
        var month = 0L
        val extras = extraPayments.toSortedMap().entries.iterator()
        var nextExtra = if (extras.hasNext()) extras.next() else null
        while (remaining > 0) {
            require(month < 1200) { "La cuota es demasiado baja: el calendario supera 100 años." }
            val monthlyDate = firstPaymentDate.plusMonths(month)
            while (nextExtra != null && !nextExtra.key.isAfter(monthlyDate) && remaining > 0) {
                val payment = minOf(remaining, nextExtra.value)
                if (payment > 0) { remaining -= payment; rows += DebtInstallment(nextExtra.key, payment, remaining, true) }
                nextExtra = if (extras.hasNext()) extras.next() else null
            }
            if (remaining > 0) {
                val payment = minOf(remaining, monthlyCents)
                remaining -= payment
                rows += DebtInstallment(monthlyDate, payment, remaining)
            }
            month++
        }
        return rows
    }

    /** Smallest whole-cent monthly payment which clears the debt by the selected final date. */
    fun monthlyPaymentToFinish(balanceCents: Long, firstPaymentDate: LocalDate, finalPaymentDate: LocalDate,
        extraPayments: Map<LocalDate, Long> = emptyMap()): Long {
        require(balanceCents >= 0) { "La deuda pendiente no puede ser negativa." }
        require(!finalPaymentDate.isBefore(firstPaymentDate)) { "La fecha objetivo debe ser posterior al primer pago." }
        require(extraPayments.all { (date, amount) -> amount >= 0 && !date.isBefore(firstPaymentDate) }) {
            "Los pagos extra deben ser positivos y posteriores al inicio de la simulación." }
        var months = ChronoUnit.MONTHS.between(YearMonth.from(firstPaymentDate), YearMonth.from(finalPaymentDate))
        if (firstPaymentDate.plusMonths(months).isAfter(finalPaymentDate)) months--
        val payments = months + 1
        require(payments in 1..1200) { "El plazo debe estar entre 1 mes y 100 años." }
        // Subtract with saturation, so enormous hypothetical extra payments cannot overflow.
        val uncovered = extraPayments.filterKeys { !it.isBefore(firstPaymentDate) && !it.isAfter(finalPaymentDate) }
            .values.fold(balanceCents) { remaining, payment -> (remaining - minOf(remaining, payment)).coerceAtLeast(0) }
        return uncovered / payments + if (uncovered % payments == 0L) 0 else 1
    }

    fun dailyDistance(vehicle: Vehicle, records: List<CarRecord>): Double? {
        val points = records.filter { it.vehicleId == vehicle.id && it.odometer != null }
            .map { it.date to it.odometer!! }.plus(vehicle.purchaseDate to vehicle.purchaseKm)
            .groupBy { it.first }.map { (day, rows) -> day to rows.maxOf { it.second } }.sortedBy { it.first }
        if (points.size < 2) return null
        val latestDay = points.last().first
        val recent = points.filter { latestDay - it.first <= 180 }
        val usable = if (recent.size >= 2 && recent.first().first < latestDay) recent else points
        val first = usable.first(); val last = usable.last()
        val days = last.first - first.first
        return if (days > 0 && last.second > first.second) (last.second - first.second).toDouble() / days else null
    }

    fun maintenanceStatuses(vehicle: Vehicle, records: List<CarRecord>, rules: List<MaintenanceRule>,
        today: LocalDate = LocalDate.now()): List<MaintenanceStatus> {
        val km = currentKm(vehicle, records)
        val daily = dailyDistance(vehicle, records)
        val interventions = records.filter { it.vehicleId == vehicle.id && it.kind == RecordKind.MAINTENANCE }
        return rules.filter { it.vehicleId == vehicle.id && it.enabled }.map { rule ->
            val matching = interventions.filter { it.text("partKey") == rule.partKey }
            // A newer front-axle replacement must not reset the older rear axle's warning.
            val latest = if (rule.partKey in setOf("tires", "brakes"))
                partLifecycles(vehicle, matching).filter { it.isActive }.map { it.record }.minWithOrNull(chronological)
            else matching.maxWithOrNull(chronological)
            val useBaseline = rule.baselineDate != null && (latest == null || rule.baselineDate > latest.date)
            val startKm = if (useBaseline) rule.baselineKm else latest?.odometer ?: rule.baselineKm
            val startDay = if (useBaseline) rule.baselineDate else latest?.date ?: rule.baselineDate
            val remainingKm = if (rule.intervalKm != null && startKm != null) (startKm.toLong() + rule.intervalKm - km).coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt() else null
            val dueDate = if (rule.intervalMonths != null && startDay != null) LocalDate.ofEpochDay(startDay).plusMonths(rule.intervalMonths.toLong()) else null
            val days = dueDate?.let { ChronoUnit.DAYS.between(today, it) }
            val prediction = if (remainingKm != null && daily != null && daily > 0) today.plusDays(ceil(remainingKm.coerceAtLeast(0) / daily).toLong().coerceAtMost(36500)) else null
            val state = when {
                (remainingKm != null && remainingKm <= 0) || (days != null && days <= 0) -> MaintenanceState.OVERDUE
                (remainingKm != null && remainingKm <= rule.warningKm) || (days != null && days <= rule.warningDays) -> MaintenanceState.SOON
                remainingKm == null && days == null -> MaintenanceState.UNKNOWN
                else -> MaintenanceState.OK
            }
            MaintenanceStatus(rule, if (useBaseline) null else latest, remainingKm, days, dueDate,
                listOfNotNull(prediction, dueDate).minOrNull(), state)
        }.sortedWith(compareBy<MaintenanceStatus> { when(it.state) { MaintenanceState.OVERDUE -> 0; MaintenanceState.SOON -> 1; MaintenanceState.UNKNOWN -> 2; MaintenanceState.OK -> 3 } }.thenBy { it.remainingDays ?: Long.MAX_VALUE })
    }

    /** Replacements on both axles are expanded into two lifecycles, so replacing only the front axle
     * doesn't retire the rear tyres/brakes. Every other part has a single lifecycle. */
    fun partLifecycles(vehicle: Vehicle, records: List<CarRecord>): List<PartLifecycle> {
        val km = currentKm(vehicle, records)
        val parts = records.filter { it.vehicleId == vehicle.id && it.kind == RecordKind.MAINTENANCE && it.odometer != null }
            .sortedWith(chronological)
        val expanded = parts.flatMap { record ->
            val axle = record.text("axle", "all")
            if (record.text("partKey") in setOf("tires", "brakes") && axle == "all") listOf(record to "front", record to "rear")
            else listOf(record to axle)
        }
        return expanded.groupBy { it.first.text("partKey") to it.second }.values.flatMap { replacements ->
            replacements.mapIndexed { index, (record, axle) ->
                val next = replacements.getOrNull(index + 1)?.first
                PartLifecycle(record, next, ((next?.odometer ?: km) - record.odometer!!).coerceAtLeast(0), next == null, axle)
            }
        }.sortedWith(compareByDescending<PartLifecycle> { it.isActive }.thenByDescending { it.record.date })
    }

    fun spendingStats(vehicle: Vehicle, records: List<CarRecord>): SpendingStats {
        val costs = records.filter { it.vehicleId == vehicle.id && it.kind in setOf(RecordKind.FUEL, RecordKind.MAINTENANCE,
            RecordKind.EXPENSE, RecordKind.ITV, RecordKind.INSURANCE, RecordKind.DAMAGE) }
            .map { record -> Triple(LocalDate.ofEpochDay(record.date),
                if (record.kind == RecordKind.EXPENSE) record.text("category", "Otros") else record.kind.label, record.amountCents) } +
            records.filter { it.vehicleId == vehicle.id && it.kind == RecordKind.TRIP }
                .map { Triple(LocalDate.ofEpochDay(it.date), "Peajes", it.text("tollsCents").toLongOrNull() ?: 0L) }
        val total = costs.sumOf { it.third }
        val distance = currentKm(vehicle, records) - vehicle.purchaseKm
        return SpendingStats(total, if (distance > 0) total / 100.0 / distance else null,
            costs.groupBy { it.second }.mapValues { (_, rows) -> rows.sumOf { it.third } }.filterValues { it > 0 },
            costs.groupBy { YearMonth.from(it.first) }.mapValues { (_, rows) -> rows.sumOf { it.third } }.toSortedMap())
    }

    /** An indicative completion score for known maintenance rules; never a mechanical diagnosis. */
    fun healthScore(statuses: List<MaintenanceStatus>): Int? {
        val known = statuses.filter { it.state != MaintenanceState.UNKNOWN }
        if (known.isEmpty()) return null
        return (known.sumOf { when(it.state) { MaintenanceState.OK -> 100; MaintenanceState.SOON -> 60; MaintenanceState.OVERDUE -> 0; MaintenanceState.UNKNOWN -> 0 } }.toDouble() / known.size).roundToInt()
    }

    /** Calendar suggestion for a Spanish private passenger car. The expiry printed on the ITV
     * report always takes precedence and the form must let the owner edit this suggestion. */
    fun suggestedItvDueDate(registration: LocalDate, inspection: LocalDate): LocalDate {
        require(!inspection.isBefore(registration)) { "La inspección no puede ser anterior a la matriculación." }
        return when {
            inspection.isBefore(registration.plusYears(4)) -> registration.plusYears(4)
            !inspection.isBefore(registration.plusYears(10)) -> inspection.plusYears(1)
            else -> inspection.plusYears(2)
        }
    }

    fun estimatedTripCostCents(distanceKm: Int, fuel: FuelStats): Long? {
        if (distanceKm < 0) return null
        val consumption = fuel.averageLitersPer100Km ?: return null
        val price = fuel.averagePricePerLiter ?: return null
        return (distanceKm * consumption * price).let { kotlin.math.round(it).toLong() }
    }
}
