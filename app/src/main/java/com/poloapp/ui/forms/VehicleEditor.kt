package com.poloapp.ui.forms

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.poloapp.data.Vehicle
import java.time.LocalDate

@Composable
fun VehicleEditor(existing: Vehicle?, busy: Boolean, externalError: String?, onDismiss: () -> Unit, onSave: (Vehicle) -> Unit) {
    val base = remember { existing ?: Vehicle() }
    var name by rememberSaveable { mutableStateOf(base.name) }
    var make by rememberSaveable { mutableStateOf(base.make) }
    var model by rememberSaveable { mutableStateOf(base.model) }
    var hp by rememberSaveable { mutableStateOf(base.powerHp.toString()) }
    var plate by rememberSaveable { mutableStateOf(base.plate) }
    var vin by rememberSaveable { mutableStateOf(base.vin) }
    var color by rememberSaveable { mutableStateOf(base.color) }
    var owner by rememberSaveable { mutableStateOf(base.owner) }
    var registration by rememberSaveable { mutableLongStateOf(base.registrationDate) }
    var purchase by rememberSaveable { mutableLongStateOf(base.purchaseDate) }
    var km by rememberSaveable { mutableStateOf(existing?.purchaseKm?.toString() ?: "") }
    var error by remember { mutableStateOf<String?>(null) }
    FormPage(if (existing == null) "Tu historia empieza aquí" else "Mi vehículo", "Un lugar para cuidar cada kilómetro. Puedes completar la matrícula y el bastidor más adelante.", busy, error ?: externalError, onDismiss, onSave = {
        try {
            require(name.isNotBlank() && make.isNotBlank() && model.isNotBlank()) { "Añade el nombre, la marca y el modelo." }
            require(registration <= purchase && purchase <= LocalDate.now().toEpochDay()) { "Revisa las fechas: matriculación, compra y hoy deben estar en ese orden." }
            val power = hp.toIntOrNull()?.takeIf { it in 1..3000 } ?: throw IllegalArgumentException("Revisa la potencia en CV.")
            onSave(base.copy(name = name.trim(), make = make.trim(), model = model.trim(), powerHp = power, plate = plate.trim().uppercase(), vin = vin.trim().uppercase(), color = color.trim(), owner = owner.trim(), registrationDate = registration, purchaseDate = purchase, purchaseKm = parseKm(km)))
            error = null
        } catch (e: IllegalArgumentException) { error = e.message }
    }, saveLabel = if (existing == null) "Crear mi garaje" else "Guardar vehículo") {
        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(20.dp)) { Text("POLO APP", style = MaterialTheme.typography.labelLarge); Spacer(Modifier.height(8.dp)); Text("Todo lo que importa.\nSiempre contigo.", style = MaterialTheme.typography.headlineSmall); Spacer(Modifier.height(8.dp)); Text("Funciona sin conexión. Tus datos se quedan en tu móvil.", style = MaterialTheme.typography.bodyMedium) }
        }
        FormField("Nombre del vehículo", name, { name = it })
        FormField("Marca", make, { make = it })
        FormField("Modelo / motor", model, { model = it })
        FormField("Potencia · CV", hp, { hp = it }, numeric = true)
        FormDate("Primera matriculación · confirma el día", registration, { registration = it })
        FormHeading("El comienzo de tu historial")
        FormDate("Fecha de compra", purchase, { purchase = it })
        FormField("Kilómetros en la compra", km, { km = it }, numeric = true, supporting = "Si empiezas el historial hoy, indica aquí la lectura y fecha desde las que quieres llevar el registro.")
        FormField("Matrícula · opcional", plate, { plate = it })
        FormField("Bastidor (VIN) · opcional", vin, { vin = it })
        FormField("Color", color, { color = it })
        FormField("Propietario · opcional", owner, { owner = it }, supporting = "Solo se incluye en el PDF si activas los datos personales.")
    }
}
