package com.poloapp.ui.forms

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.poloapp.data.*
import com.poloapp.domain.CarCalculations
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

val PartOptions = listOf("oil" to "Aceite de motor", "oil_filter" to "Filtro de aceite", "air_filter" to "Filtro de aire", "cabin_filter" to "Filtro de habitáculo", "spark_plugs" to "Bujías", "brake_fluid" to "Líquido de frenos", "brakes" to "Frenos / pastillas", "tires" to "Neumáticos", "timing" to "Distribución", "other" to "Otra pieza / reparación")

@Composable
fun RecordEditor(record: CarRecord, isNew: Boolean, snapshot: GarageSnapshot, busy: Boolean, externalError: String?,
    onDismiss: () -> Unit, onSave: (CarRecord, List<Uri>, List<Attachment>) -> Unit, onDelete: () -> Unit, onOpenAttachment: (Attachment) -> Unit) {
    var title by rememberSaveable(record.id) { mutableStateOf(record.title) }
    var date by rememberSaveable(record.id) { mutableLongStateOf(record.date) }
    var km by rememberSaveable(record.id) { mutableStateOf(record.odometer?.toString() ?: "") }
    var amount by rememberSaveable(record.id) { mutableStateOf(if (record.amountCents > 0) moneyInput(record.amountCents) else "") }
    var notes by rememberSaveable(record.id) { mutableStateOf(record.notes) }
    var details by rememberSaveable(record.id) { mutableStateOf<Map<String, String>>(HashMap(record.details).apply {
        record.details["partsCents"]?.toLongOrNull()?.let { put("partsCost", moneyInput(it)) }
        record.details["laborCents"]?.toLongOrNull()?.let { put("laborCost", moneyInput(it)) }
        record.details["tollsCents"]?.toLongOrNull()?.let { put("tollsCost", moneyInput(it)) }
    }) }
    var newUris by rememberSaveable(record.id) { mutableStateOf<List<String>>(arrayListOf()) }
    var removedIds by rememberSaveable(record.id) { mutableStateOf<List<String>>(arrayListOf()) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val attachments = snapshot.attachments.filter { it.recordId == record.id && it.id !in removedIds }
    val registration = snapshot.vehicles.first { it.id == record.vehicleId }.registrationDate
    val suggestedItv = CarCalculations.suggestedItvDueDate(LocalDate.ofEpochDay(registration), LocalDate.ofEpochDay(date)).toEpochDay()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        newUris = ArrayList((newUris + uris.map(Uri::toString)).distinct().take(12))
    }
    fun set(key: String, value: String) { details = HashMap(details).apply { if (value.isBlank()) remove(key) else put(key, value) } }
    fun get(key: String, fallback: String = "") = details[key] ?: fallback
    fun flag(key: String, default: Boolean = false) = get(key, default.toString()) == "true"
    val hasKm = record.kind in setOf(RecordKind.FUEL, RecordKind.MAINTENANCE, RecordKind.ITV, RecordKind.PRESSURE, RecordKind.DAMAGE, RecordKind.ODOMETER)
    val hasAmount = record.kind in setOf(RecordKind.FUEL, RecordKind.MAINTENANCE, RecordKind.EXPENSE, RecordKind.PAYMENT, RecordKind.ITV, RecordKind.INSURANCE, RecordKind.DAMAGE)
    val save: () -> Unit = {
        try {
            val values = HashMap(details)
            var cents = if (hasAmount) parseCents(amount) else 0L
            var reading = if (hasKm && km.isNotBlank()) parseKm(km) else null
            when (record.kind) {
                RecordKind.FUEL -> {
                    val liters = get("liters").takeIf { it.isNotBlank() }?.let { parseDecimal(it, "los litros") }
                    val price = get("pricePerLiter").takeIf { it.isNotBlank() }?.let { parseDecimal(it, "el precio por litro") }
                    require(listOf(liters != null, price != null, amount.isNotBlank()).count { it } >= 2) { "Introduce dos datos: litros, precio por litro o importe total." }
                    val total = BigDecimal.valueOf(cents, 2)
                    val l = liters ?: total.divide(price!!.also { require(it > BigDecimal.ZERO) { "El precio debe ser mayor que cero." } }, 6, RoundingMode.HALF_UP)
                    require(l > BigDecimal.ZERO) { "Los litros deben ser mayores que cero." }
                    val p = price ?: total.divide(l, 6, RoundingMode.HALF_UP)
                    require(p > BigDecimal.ZERO) { "El precio debe ser mayor que cero." }
                    if (amount.isBlank()) cents = l.multiply(p).multiply(BigDecimal(100)).setScale(0, RoundingMode.HALF_UP).longValueExact()
                    else if (liters != null && price != null) require(l.multiply(p).subtract(total).abs() <= BigDecimal("0.10")) { "Los litros × €/litro no coinciden con el total. Deja vacío el dato que quieras calcular." }
                    values["liters"] = l.toPlainString(); values["pricePerLiter"] = p.toPlainString()
                    values["fullTank"] = flag("fullTank", true).toString(); values["fuelType"] = get("fuelType", "95")
                }
                RecordKind.MAINTENANCE -> {
                    values["partKey"] = get("partKey", "oil"); values["axle"] = get("axle", "all")
                    values["provider"] = get("provider", "self")
                    val parts = parseCents(get("partsCost"), "el coste de piezas"); val labor = parseCents(get("laborCost"), "la mano de obra")
                    values.remove("partsCents"); values.remove("laborCents")
                    if (get("partsCost").isNotBlank() || get("laborCost").isNotBlank()) {
                        values["partsCents"] = parts.toString(); values["laborCents"] = labor.toString()
                        require(amount.isBlank() || cents == parts + labor) { "El total debe coincidir con piezas + mano de obra, o déjalo vacío para calcularlo." }
                        cents = parts + labor
                    }
                    values.remove("partsCost"); values.remove("laborCost")
                }
                RecordKind.TRIP -> {
                    val start = parseKm(get("startKm"), "los km de salida"); val end = parseKm(get("endKm"), "los km de llegada")
                    require(end > start) { "Los km de llegada deben ser mayores que los de salida." }
                    values["startKm"] = start.toString(); values["endKm"] = end.toString(); reading = end
                    values["tollsCents"] = parseCents(get("tollsCost"), "los peajes").toString(); values.remove("tollsCost")
                }
                RecordKind.PRESSURE -> {
                    listOf("frontBar", "rearBar").forEach { key ->
                        val pressure = parseDecimal(get(key), "la presión")
                        require(pressure > BigDecimal.ZERO && pressure <= BigDecimal("6")) { "Revisa la presión en bar (mayor que 0 y hasta 6)." }
                        values[key] = pressure.toPlainString()
                    }
                    values["cold"] = flag("cold", true).toString()
                }
                RecordKind.ITV -> {
                    values["result"] = get("result", "Favorable")
                    require(values["result"] == "Favorable" || get("dueDate").isNotBlank()) { "Introduce expresamente la fecha indicada por la estación para la siguiente inspección." }
                    values["dueDate"] = get("dueDate", suggestedItv.toString())
                }
                RecordKind.INSURANCE -> { values["dueDate"] = get("dueDate", LocalDate.ofEpochDay(date).plusYears(1).toEpochDay().toString()) }
                RecordKind.NOTE -> values["pinned"] = flag("pinned").toString()
                RecordKind.DAMAGE -> { values["repaired"] = flag("repaired").toString(); values["severity"] = get("severity", "Leve") }
                RecordKind.EXPENSE -> values["category"] = get("category", "Otros")
                else -> Unit
            }
            val finalTitle = title.trim().ifEmpty {
                when (record.kind) {
                    RecordKind.MAINTENANCE -> PartOptions.firstOrNull { it.first == values["partKey"] }?.second ?: "Mantenimiento"
                    RecordKind.FUEL -> get("station", "Repostaje")
                    RecordKind.INSURANCE -> get("company", "Seguro")
                    else -> record.kind.label
                }
            }
            error = null
            onSave(record.copy(title = finalTitle, date = date, odometer = reading, amountCents = cents, notes = notes.trim(), details = values), newUris.map(Uri::parse), attachments)
        } catch (e: Exception) { error = e.message ?: "Revisa los datos introducidos." }
    }
    FormPage(if (isNew) "Añadir ${record.kind.label.lowercase()}" else record.kind.label, "Cada detalle cuenta una parte de la historia de tu coche.", busy, error ?: externalError, onDismiss, save) {
        FormField(if (record.kind == RecordKind.TRIP) "Trayecto · por ejemplo, Badalona → Madrid" else "Título · opcional", title, { title = it })
        FormDate("Fecha del registro", date, { date = it })
        if (hasKm) FormField("Cuentakilómetros · km", km, { km = it }, numeric = true)
        when (record.kind) {
            RecordKind.FUEL -> {
                Text("Introduce dos de los tres importes. El tercero se calcula al guardar.", style = MaterialTheme.typography.bodyMedium)
                FormField("Litros", get("liters"), { set("liters", it) }, numeric = true)
                FormField("Precio por litro · €", get("pricePerLiter"), { set("pricePerLiter", it) }, numeric = true)
                FormSwitch("Depósito lleno", flag("fullTank", true), { set("fullTank", it.toString()) }, "Los parciales se acumulan hasta el siguiente depósito lleno.")
                FormField("Gasolinera", get("station"), { set("station", it) })
                FormChoice("Combustible", get("fuelType", "95"), listOf("95" to "Gasolina 95", "98" to "Gasolina 98", "diesel" to "Diésel", "other" to "Otro"), { set("fuelType", it) })
            }
            RecordKind.MAINTENANCE -> {
                FormChoice("Pieza / intervención", get("partKey", "oil"), PartOptions, { set("partKey", it) })
                FormChoice("Eje / posición", get("axle", "all"), listOf("all" to "General / ambos ejes", "front" to "Eje delantero", "rear" to "Eje trasero"), { set("axle", it) })
                FormChoice("Realizado por", get("provider", "self"), listOf("self" to "Por mi cuenta", "workshop" to "Taller"), { set("provider", it) })
                if (get("provider", "self") == "workshop") {
                    if (snapshot.workshops.isEmpty()) Text("Añade primero un taller desde Taller → Agenda de talleres.", color = MaterialTheme.colorScheme.error)
                    else FormChoice("Taller", get("workshopId"), listOf("" to "Seleccionar taller") + snapshot.workshops.map { it.id to it.name }, { set("workshopId", it) })
                }
                FormField("Coste de piezas · € · opcional", get("partsCost"), { set("partsCost", it) }, true)
                FormField("Mano de obra · € · opcional", get("laborCost"), { set("laborCost", it) }, true)
                Text("Registra cada pieza por separado para medir su vida útil. Al reemplazarla, la anterior queda en el historial.", style = MaterialTheme.typography.bodySmall)
            }
            RecordKind.EXPENSE -> FormChoice("Categoría", get("category", "Otros"), listOf("Seguro", "Impuesto", "Parking", "Peajes", "Lavado", "Multas", "Accesorios", "Otros").map { it to it }, { set("category", it) })
            RecordKind.TRIP -> {
                FormField("Kilómetros de salida", get("startKm"), { set("startKm", it) }, true)
                FormField("Kilómetros de llegada", get("endKm"), { set("endKm", it) }, true)
                FormField("Propósito del viaje", get("purpose"), { set("purpose", it) })
                FormField("Peajes · € · opcional", get("tollsCost"), { set("tollsCost", it) }, true)
                Text("El combustible se estima usando tu consumo medido. La estimación no duplica los gastos de repostaje.", style = MaterialTheme.typography.bodySmall)
            }
            RecordKind.NOTE -> {
                FormSwitch("Fijar arriba", flag("pinned"), { set("pinned", it.toString()) })
                FormSwitch("Recordarme en una fecha", get("reminderDate").isNotBlank(), { set("reminderDate", if (it) LocalDate.now().plusDays(1).toEpochDay().toString() else "") })
                if (get("reminderDate").isNotBlank()) FormDate("Recordatorio", get("reminderDate").toLong(), { set("reminderDate", it.toString()) })
            }
            RecordKind.ITV -> {
                FormChoice("Resultado", get("result", "Favorable"), listOf("Favorable", "Desfavorable", "Negativa").map { it to it }, { set("result", it) })
                FormField("Estación ITV", get("station"), { set("station", it) })
                FormField("Defectos / observaciones del informe", get("defects"), { set("defects", it) }, multiline = true)
                FormDate("Próxima ITV · confirma la fecha del informe", get("dueDate").toLongOrNull() ?: suggestedItv, { set("dueDate", it.toString()) })
                Text("Propuesta para turismos particulares en España según antigüedad. La fecha válida es la que consta en la documentación. Para resultado desfavorable o negativo, selecciona expresamente la fecha de revisión.", style = MaterialTheme.typography.bodySmall)
            }
            RecordKind.INSURANCE -> {
                FormField("Compañía", get("company"), { set("company", it) })
                FormField("Número de póliza", get("policy"), { set("policy", it) })
                FormField("Modalidad / cobertura", get("coverage"), { set("coverage", it) })
                FormField("Teléfono de asistencia", get("assistancePhone"), { set("assistancePhone", it) })
                FormDate("Vencimiento de la póliza", get("dueDate").toLongOrNull() ?: LocalDate.ofEpochDay(date).plusYears(1).toEpochDay(), { set("dueDate", it.toString()) })
            }
            RecordKind.PRESSURE -> {
                FormField("Presión delantera · bar", get("frontBar"), { set("frontBar", it) }, true)
                FormField("Presión trasera · bar", get("rearBar"), { set("rearBar", it) }, true)
                FormSwitch("Medida en frío", flag("cold", true), { set("cold", it.toString()) })
                Text("Usa las presiones indicadas en la etiqueta del vehículo para la carga y medida de neumático.", style = MaterialTheme.typography.bodySmall)
            }
            RecordKind.DAMAGE -> {
                FormField("Zona afectada", get("location"), { set("location", it) })
                FormChoice("Gravedad", get("severity", "Leve"), listOf("Leve", "Moderada", "Importante").map { it to it }, { set("severity", it) })
                FormSwitch("Daño reparado", flag("repaired"), { set("repaired", it.toString()) })
            }
            RecordKind.DOCUMENT -> FormChoice("Tipo de documento", get("category", "Otros"), listOf("Permiso de circulación", "Ficha técnica", "Póliza", "Informe ITV", "Factura", "Otros").map { it to it }, { set("category", it) })
            RecordKind.PAYMENT -> Text("Pago del préstamo sin intereses. Puedes registrar cualquier cantidad y periodicidad.", style = MaterialTheme.typography.bodyMedium)
            RecordKind.ODOMETER -> Text("Esta lectura se conservará en la línea temporal. Si no encaja con las anteriores, te avisaremos.", style = MaterialTheme.typography.bodyMedium)
        }
        if (hasAmount) FormField(if (record.kind == RecordKind.DAMAGE) "Coste de reparación · € · opcional" else "Importe total · €", amount, { amount = it }, true)
        FormField(if (record.kind == RecordKind.NOTE) "Escribe tu nota" else "Observaciones", notes, { notes = it }, multiline = true)
        FormHeading("Fotos y documentos")
        Text("Se guardan en este móvil y se incluyen en tu copia de seguridad. Hasta 12 archivos por selección; JPEG, PNG, WebP o PDF.", style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = { picker.launch(arrayOf("image/jpeg", "image/png", "image/webp", "application/pdf")) }, modifier = Modifier.fillMaxWidth(), enabled = !busy) { Icon(Icons.Outlined.AttachFile, null); Spacer(Modifier.width(8.dp)); Text("Adjuntar fotos o factura") }
        attachments.forEach { attachment ->
            Row(Modifier.fillMaxWidth()) { TextButton(onClick = { onOpenAttachment(attachment) }, Modifier.weight(1f)) { Text(attachment.displayName) }; IconButton(onClick = { removedIds = ArrayList(removedIds + attachment.id) }) { Icon(Icons.Outlined.DeleteOutline, "Quitar adjunto") } }
        }
        newUris.forEachIndexed { index, uri -> Row(Modifier.fillMaxWidth()) { Text("Nuevo adjunto ${index + 1}", Modifier.weight(1f)); TextButton(onClick = { newUris = ArrayList(newUris - uri) }) { Text("Quitar") } } }
        if (!isNew) TextButton(onClick = { confirmDelete = true }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Eliminar registro", color = MaterialTheme.colorScheme.error) }
    }
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text("¿Eliminar este registro?") }, text = { Text("Se eliminará también su relación con los adjuntos. Esta acción no se puede deshacer.") }, confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Eliminar") } }, dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } })
}
