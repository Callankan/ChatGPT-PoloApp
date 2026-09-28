package com.poloapp.domain

import com.poloapp.data.*
import java.time.LocalDate

/** Shared validation for direct editing and restoring a backup; kept Android-free for JVM tests. */
object GarageValidation {
    const val MAX_MONEY_CENTS = 100_000_000_000L
    private val earliest = LocalDate.of(1900, 1, 1).toEpochDay()
    private val latest = LocalDate.of(2200, 12, 31).toEpochDay()

    fun validate(snapshot: GarageSnapshot, today: LocalDate = LocalDate.now()) {
        unique(snapshot.vehicles.map { it.id }, "vehículos")
        unique(snapshot.records.map { it.id }, "registros")
        unique(snapshot.rules.map { it.id }, "reglas de mantenimiento")
        unique(snapshot.workshops.map { it.id }, "talleres")
        unique(snapshot.attachments.map { it.id }, "archivos")
        val vehicles = snapshot.vehicles.associateBy { it.id }
        val workshops = snapshot.workshops.map { it.id }.toSet()
        snapshot.vehicles.forEach { validateVehicle(it, today) }
        snapshot.workshops.forEach { workshop ->
            require(workshop.name.isNotBlank()) { "El taller necesita un nombre." }
            require(workshop.rating in 0..5) { "La valoración debe estar entre 0 y 5." }
        }
        snapshot.records.forEach { record ->
            val vehicle = vehicles[record.vehicleId] ?: errorArgument("El registro no pertenece a un vehículo existente.")
            validateRecord(record, today)
            val workshopId = record.text("workshopId")
            if (workshopId.isNotBlank()) require(workshopId in workshops) { "El taller seleccionado ya no existe." }
            if (record.kind == RecordKind.PAYMENT) require(record.date >= vehicle.debtStartDate) { "El pago no puede ser anterior al inicio de la deuda." }
        }
        snapshot.vehicles.forEach { vehicle ->
            val records = snapshot.records.filter { it.vehicleId == vehicle.id }
            validateOdometer(vehicle, records)
            val paid = records.filter { it.kind == RecordKind.PAYMENT }.fold(0L) { sum, record ->
                require(sum <= vehicle.initialDebtCents - record.amountCents) { "Los pagos superan la deuda inicial. Revisa el importe de la deuda o del pago." }
                sum + record.amountCents
            }
            require(paid <= vehicle.initialDebtCents) { "Los pagos superan la deuda inicial." }
        }
        snapshot.rules.forEach { rule ->
            require(rule.vehicleId in vehicles) { "La regla no pertenece a un vehículo existente." }
            require(rule.title.isNotBlank() && rule.partKey.isNotBlank()) { "La regla necesita nombre y pieza." }
            require(rule.intervalKm == null || rule.intervalKm in 1..2_000_000) { "El intervalo de kilómetros debe ser positivo." }
            require(rule.intervalMonths == null || rule.intervalMonths in 1..1200) { "El intervalo debe estar entre 1 y 1200 meses." }
            require(rule.baselineKm == null || rule.baselineKm in 0..9_999_999) { "El kilometraje de referencia no es válido." }
            rule.baselineDate?.let { pastDate(it, "La fecha de referencia", today) }
            require(rule.warningKm in 0..2_000_000 && rule.warningDays in 0..3650) { "Los márgenes de aviso no son válidos." }
            val vehicle = vehicles.getValue(rule.vehicleId)
            if (rule.baselineKm != null) require(rule.baselineKm <= CarCalculations.currentKm(vehicle, snapshot.records)) { "Los kilómetros de referencia no pueden superar el kilometraje actual." }
        }
        val recordsById = snapshot.records.associateBy { it.id }
        snapshot.attachments.forEach { attachment ->
            require(attachment.recordId in recordsById) { "La foto o documento no tiene un registro asociado." }
            require(attachment.localPath.isNotBlank()) { "La ruta del archivo está vacía." }
            require(attachment.mimeType.startsWith("image/") || attachment.mimeType == "application/pdf") { "Solo se admiten imágenes o PDF." }
            require(attachment.displayName.isNotBlank()) { "El archivo necesita un nombre." }
        }
    }

    private fun unique(ids: List<String>, label: String) {
        require(ids.all { it.isNotBlank() }) { "Hay identificadores vacíos en $label." }
        require(ids.distinct().size == ids.size) { "Hay identificadores duplicados en $label." }
    }

    fun validateVehicle(vehicle: Vehicle, today: LocalDate = LocalDate.now()) {
        require(vehicle.name.isNotBlank()) { "Indica un nombre para el vehículo." }
        require(vehicle.make.isNotBlank() && vehicle.model.isNotBlank()) { "Indica marca y modelo." }
        require(vehicle.powerHp in 1..3000) { "La potencia debe estar entre 1 y 3000 CV." }
        pastDate(vehicle.registrationDate, "La matriculación", today)
        pastDate(vehicle.purchaseDate, "La compra", today)
        require(vehicle.purchaseDate >= vehicle.registrationDate) { "La compra no puede ser anterior a la matriculación." }
        pastDate(vehicle.debtStartDate, "El inicio de la deuda", today)
        require(vehicle.purchaseKm in 0..9_999_999) { "Los kilómetros de compra no son válidos." }
        amount(vehicle.initialDebtCents, "La deuda inicial")
    }

    fun validateRecord(record: CarRecord, today: LocalDate = LocalDate.now()) {
        require(record.title.isNotBlank()) { "El registro necesita un título." }
        pastDate(record.date, "La fecha del registro", today)
        require(record.odometer == null || record.odometer in 0..9_999_999) { "El kilometraje no es válido." }
        amount(record.amountCents, "El importe")
        when (record.kind) {
            RecordKind.FUEL -> {
                require(record.odometer != null) { "Introduce el kilometraje del repostaje." }
                val liters = record.number("liters")
                require(liters != null && liters > 0 && liters <= 2000) { "Los litros deben ser mayores que cero y no superar 2.000." }
                require(record.amountCents > 0) { "El precio del repostaje debe ser mayor que cero." }
                record.details["pricePerLiter"]?.takeIf { it.isNotBlank() }?.let {
                    require(record.number("pricePerLiter")?.let { price -> price > 0 && price <= 1000 } == true) { "El precio por litro no es válido." }
                }
                require(record.text("fullTank") in listOf("true", "false")) { "Indica si llenaste el depósito." }
            }
            RecordKind.MAINTENANCE -> {
                require(record.odometer != null) { "Introduce los kilómetros de la intervención." }
                require(record.text("partKey").isNotBlank()) { "Selecciona la pieza u operación." }
                require(record.text("provider") in listOf("workshop", "self")) { "Indica si se hizo en taller o por tu cuenta." }
                if (record.text("provider") == "workshop") require(record.text("workshopId").isNotBlank()) { "Selecciona un taller para la intervención." }
                require(record.text("axle", "all") in listOf("front", "rear", "all")) { "El eje seleccionado no es válido." }
                val parts = optionalCents(record, "partsCents", "El coste de las piezas")
                val labor = optionalCents(record, "laborCents", "El coste de mano de obra")
                require(parts + labor == 0L || parts + labor == record.amountCents) { "Piezas y mano de obra deben sumar el coste total." }
            }
            RecordKind.PAYMENT -> require(record.amountCents > 0) { "El pago debe ser mayor que cero." }
            RecordKind.TRIP -> {
                val start = record.text("startKm").toIntOrNull()
                val end = record.text("endKm").toIntOrNull()
                require(start != null && end != null && start >= 0 && end >= start && end <= 9_999_999) { "Los kilómetros de llegada deben ser iguales o mayores que los de salida." }
                require(record.odometer == end) { "El cuentakilómetros debe coincidir con la llegada del viaje." }
                optionalCents(record, "tollsCents", "Los peajes")
            }
            RecordKind.ITV, RecordKind.INSURANCE -> {
                val dueDate = record.text("dueDate").toLongOrNull()
                require(dueDate != null) { "Indica la fecha de vencimiento." }
                validDate(dueDate, "El vencimiento")
                require(dueDate >= record.date) { "El vencimiento no puede ser anterior a la fecha del registro." }
            }
            RecordKind.NOTE -> record.details["reminderDate"]?.takeIf { it.isNotBlank() }?.let {
                val day = it.toLongOrNull() ?: errorArgument("La fecha del recordatorio no es válida.")
                validDate(day, "El recordatorio")
            }
            RecordKind.PRESSURE -> {
                val front = record.number("frontBar"); val rear = record.number("rearBar")
                require(front != null && rear != null && front in 0.1..10.0 && rear in 0.1..10.0) { "Indica las presiones delantera y trasera en bar (0,1 a 10)." }
            }
            RecordKind.ODOMETER -> require(record.odometer != null) { "Introduce el kilometraje." }
            else -> Unit
        }
    }

    /** Compare day ranges instead of creation order: historical entries may be added later.
     * Same-day readings can vary, but each day's minimum must be at least the prior day's maximum. */
    fun validateOdometer(vehicle: Vehicle, records: List<CarRecord>) {
        val readings = records.filter { it.vehicleId == vehicle.id }.flatMap { record ->
            val end = record.odometer ?: return@flatMap emptyList()
            val start = if (record.kind == RecordKind.TRIP) record.text("startKm").toIntOrNull() else null
            listOfNotNull(record.date to end, start?.let { record.date to it })
        } + (vehicle.purchaseDate to vehicle.purchaseKm)
        var previousMax: Int? = null
        readings.groupBy { it.first }.toSortedMap().forEach { (_, values) ->
            val min = values.minOf { it.second }; val max = values.maxOf { it.second }
            require(previousMax == null || min >= previousMax!!) { "Los kilómetros no encajan con el historial: una lectura posterior no puede ser menor. Revisa también los kilómetros de compra." }
            previousMax = max
        }
    }

    private fun optionalCents(record: CarRecord, key: String, label: String): Long {
        val text = record.text(key)
        if (text.isBlank()) return 0
        val value = text.toLongOrNull() ?: errorArgument("$label no es válido.")
        amount(value, label)
        return value
    }
    private fun amount(value: Long, label: String) {
        require(value in 0..MAX_MONEY_CENTS) { "$label debe estar entre 0 y 1.000.000.000 €." }
    }
    private fun validDate(value: Long, label: String) {
        require(value in earliest..latest) { "$label debe estar entre 1900 y 2200." }
    }
    private fun pastDate(value: Long, label: String, today: LocalDate) {
        validDate(value, label)
        require(value <= today.toEpochDay()) { "$label no puede estar en el futuro." }
    }
    private fun errorArgument(message: String): Nothing = throw IllegalArgumentException(message)
}
