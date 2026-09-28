package com.poloapp.ui.forms

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.poloapp.data.*
import com.poloapp.domain.CarCalculations
import java.time.LocalDate

@Composable
fun DebtEditor(vehicle: Vehicle, busy: Boolean, externalError: String?, onDismiss: () -> Unit, onSave: (Vehicle) -> Unit) {
    var amount by rememberSaveable { mutableStateOf(if (vehicle.initialDebtCents > 0) moneyInput(vehicle.initialDebtCents) else "") }
    var date by rememberSaveable { mutableLongStateOf(vehicle.debtStartDate) }
    var error by remember { mutableStateOf<String?>(null) }
    FormPage("Mi préstamo", "Sin intereses, sin cuotas obligatorias. Registra cada pago cuando lo hagas.", busy, error ?: externalError, onDismiss, onSave = {
        try { onSave(vehicle.copy(initialDebtCents = parseCents(amount), debtStartDate = date)); error = null } catch (e: Exception) { error = e.message }
    }) {
        FormField("Deuda inicial · €", amount, { amount = it }, true)
        FormDate("Fecha de inicio", date, { date = it })
        Text("La financiación nunca aparece en el informe para compradores.", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun DebtSimulator(vehicle: Vehicle, records: List<CarRecord>, onDismiss: () -> Unit) {
    val debt = CarCalculations.debtSummary(vehicle, records)
    var monthly by rememberSaveable { mutableStateOf("150") }
    var inverse by rememberSaveable { mutableStateOf(false) }
    var first by rememberSaveable { mutableLongStateOf(LocalDate.now().plusMonths(1).withDayOfMonth(1).toEpochDay()) }
    var target by rememberSaveable { mutableLongStateOf(LocalDate.now().plusYears(1).toEpochDay()) }
    var extra by rememberSaveable { mutableStateOf("") }
    var extraDate by rememberSaveable { mutableLongStateOf(LocalDate.now().plusMonths(2).withDayOfMonth(1).toEpochDay()) }
    var showAll by rememberSaveable { mutableStateOf(false) }
    val result = remember(monthly, inverse, first, target, extra, extraDate, debt.remainingCents) { runCatching {
        val extras = if (extra.isBlank()) emptyMap() else mapOf(LocalDate.ofEpochDay(extraDate) to parseCents(extra))
        val start = LocalDate.ofEpochDay(first)
        val payment = if (inverse) CarCalculations.monthlyPaymentToFinish(debt.remainingCents, start, LocalDate.ofEpochDay(target), extras) else parseCents(monthly)
        payment to CarCalculations.simulateDebt(debt.remainingCents, payment, start, extras)
    } }
    FormPage("Tu camino a cero", "Pendiente: ${euros(debt.remainingCents)} · simulación sin intereses", onDismiss = onDismiss) {
        FormSwitch("Calcular por fecha objetivo", inverse, { inverse = it }, "Elige cuánto pagar o cuándo quieres terminar.")
        FormDate("Primer pago", first, { first = it })
        if (inverse) FormDate("Quiero terminar antes del", target, { target = it })
        else FormField("Cuota mensual deseada · €", monthly, { monthly = it }, true)
        FormField("Pago extra simulado · € · opcional", extra, { extra = it }, true)
        if (extra.isNotBlank()) FormDate("Fecha del pago extra", extraDate, { extraDate = it })
        result.fold(onSuccess = { (payment, rows) ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (debt.remainingCents == 0L) "No tienes deuda pendiente" else "${euros(payment)} / mes", style = MaterialTheme.typography.headlineMedium)
                    if (rows.isNotEmpty()) Text("Último pago: ${rows.last().date.format(SpanishDate)} · ${rows.size} pagos")
                    Text("Esta simulación no añade pagos al historial.", style = MaterialTheme.typography.bodySmall)
                }
            }
            FormHeading("Calendario de pagos")
            (if (showAll) rows else rows.take(24)).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column { Text(row.date.format(SpanishDate)); Text(if (row.extra) "Pago extra" else "Quedan ${euros(row.remainingCents)}", style = MaterialTheme.typography.bodySmall) }
                    Text(euros(row.paymentCents), style = MaterialTheme.typography.titleMedium)
                }
                HorizontalDivider()
            }
            if (!showAll && rows.size > 24) TextButton(onClick = { showAll = true }) { Text("Ver los ${rows.size} pagos") }
        }, onFailure = { Text(it.message ?: "Revisa la simulación", color = MaterialTheme.colorScheme.error) })
    }
}

@Composable
fun WorkshopEditor(base: Workshop, busy: Boolean, externalError: String?, onDismiss: () -> Unit, onSave: (Workshop) -> Unit) {
    var name by rememberSaveable { mutableStateOf(base.name) }
    var phone by rememberSaveable { mutableStateOf(base.phone) }
    var address by rememberSaveable { mutableStateOf(base.address) }
    var rating by rememberSaveable { mutableStateOf(base.rating.toString()) }
    FormPage("Ficha del taller", "Una agenda reutilizable para todo tu historial.", busy, externalError, onDismiss, onSave = { onSave(base.copy(name = name.trim(), phone = phone.trim(), address = address.trim(), rating = rating.toIntOrNull() ?: 0)) }) {
        FormField("Nombre", name, { name = it }); FormField("Teléfono", phone, { phone = it }); FormField("Dirección", address, { address = it })
        FormChoice("Tu valoración", rating, (0..5).map { it.toString() to if (it == 0) "Sin valorar" else "$it de 5" }, { rating = it })
    }
}

@Composable
fun RuleEditor(base: MaintenanceRule, busy: Boolean, externalError: String?, onDismiss: () -> Unit, onSave: (MaintenanceRule) -> Unit) {
    var title by rememberSaveable { mutableStateOf(base.title) }
    var part by rememberSaveable { mutableStateOf(base.partKey) }
    var km by rememberSaveable { mutableStateOf(base.intervalKm?.toString() ?: "") }
    var months by rememberSaveable { mutableStateOf(base.intervalMonths?.toString() ?: "") }
    var baselineKm by rememberSaveable { mutableStateOf(base.baselineKm?.toString() ?: "") }
    var baselineEnabled by rememberSaveable { mutableStateOf(base.baselineDate != null) }
    var baselineDate by rememberSaveable { mutableLongStateOf(base.baselineDate ?: LocalDate.now().toEpochDay()) }
    var warnKm by rememberSaveable { mutableStateOf(base.warningKm.toString()) }
    var warnDays by rememberSaveable { mutableStateOf(base.warningDays.toString()) }
    var enabled by rememberSaveable { mutableStateOf(base.enabled) }
    var error by remember { mutableStateOf<String?>(null) }
    FormPage("Aviso de mantenimiento", "Se avisa cuando llega primero el límite de km o de tiempo. Confirma los intervalos con el manual de tu motor.", busy, error ?: externalError, onDismiss, onSave = {
        try {
            fun optionalInt(value: String, label: String): Int? = if (value.isBlank()) null else value.toIntOrNull() ?: throw IllegalArgumentException("Revisa $label.")
            onSave(base.copy(title = title.trim(), partKey = part, intervalKm = optionalInt(km, "el intervalo"), intervalMonths = optionalInt(months, "los meses"), baselineKm = optionalInt(baselineKm, "los km de referencia"), baselineDate = if (baselineEnabled) baselineDate else null, warningKm = parseKm(warnKm), warningDays = parseKm(warnDays), enabled = enabled))
            error = null
        } catch (e: Exception) { error = e.message }
    }) {
        FormField("Nombre del aviso", title, { title = it })
        FormChoice("Pieza que reinicia el contador", part, PartOptions, { part = it })
        FormField("Intervalo · km · opcional", km, { km = it }, true)
        FormField("Intervalo · meses · opcional", months, { months = it }, true)
        FormHeading("Si todavía no has registrado el último cambio")
        FormField("Km del último cambio conocido · opcional", baselineKm, { baselineKm = it }, true)
        FormSwitch("Conozco la fecha del último cambio", baselineEnabled, { baselineEnabled = it })
        if (baselineEnabled) FormDate("Último cambio conocido", baselineDate, { baselineDate = it })
        FormHeading("Antelación del aviso")
        FormField("Avisar estos km antes", warnKm, { warnKm = it }, true)
        FormField("Avisar estos días antes", warnDays, { warnDays = it }, true)
        FormSwitch("Aviso activo", enabled, { enabled = it })
    }
}

@Composable
fun SpecsEditor(vehicle: Vehicle, busy: Boolean, error: String?, onDismiss: () -> Unit, onSave: (Vehicle) -> Unit) {
    var values by rememberSaveable { mutableStateOf<Map<String, String>>(HashMap(vehicle.specs)) }
    FormPage("Ficha rápida", "Copia los valores del manual o de la etiqueta del coche. No se presupone la especificación del motor por año.", busy, error, onDismiss, onSave = { onSave(vehicle.copy(specs = values)) }) {
        listOf("tireSize" to "Medida de neumáticos", "frontPressure" to "Presión delantera recomendada · bar", "rearPressure" to "Presión trasera recomendada · bar", "oilSpec" to "Aceite · homologación y viscosidad", "oilQuantity" to "Cantidad de aceite · litros", "engineCode" to "Código de motor", "timingType" to "Distribución · cadena / correa", "bulbs" to "Bombillas", "other" to "Otros datos útiles").forEach { (key, label) ->
            FormField(label, values[key] ?: "", { values = HashMap(values).apply { put(key, it) } })
        }
    }
}
