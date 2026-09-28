package com.poloapp.ui.forms

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

val SpanishDate: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.forLanguageTag("es-ES"))
fun euros(cents: Long): String = java.text.NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-ES")).format(cents / 100.0)
fun moneyInput(cents: Long) = BigDecimal.valueOf(cents, 2).toPlainString()
fun parseDecimal(text: String, label: String): BigDecimal = try {
    require(text.trim().length <= 16)
    BigDecimal(text.trim().replace(',', '.')).also { require(it >= BigDecimal.ZERO) }
} catch (_: Exception) { throw IllegalArgumentException("Revisa $label: introduce un número válido y positivo.") }
fun parseCents(text: String, label: String = "el importe"): Long = if (text.isBlank()) 0 else try {
    parseDecimal(text, label).multiply(BigDecimal(100)).setScale(0, RoundingMode.HALF_UP).longValueExact()
} catch (_: Exception) { throw IllegalArgumentException("Revisa $label.") }
fun parseKm(text: String, label: String = "el kilometraje"): Int = text.trim().toIntOrNull()?.takeIf { it in 0..5_000_000 }
    ?: throw IllegalArgumentException("Revisa $label: usa kilómetros enteros entre 0 y 5.000.000.")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormPage(title: String, subtitle: String = "", busy: Boolean = false, error: String? = null,
    onDismiss: () -> Unit, onSave: (() -> Unit)? = null, saveLabel: String = "Guardar", content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = { if (!busy) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(topBar = {
            TopAppBar(title = { Text(title, style = MaterialTheme.typography.titleLarge) }, navigationIcon = {
                IconButton(onClick = onDismiss, enabled = !busy) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Volver") }
            }, actions = { if (onSave != null) TextButton(onClick = onSave, enabled = !busy, modifier = Modifier.testTag("form-save-top")) { Text(if (busy) "Guardando…" else saveLabel) } })
        }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp).padding(bottom = 36.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (error != null) Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Text(error, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onErrorContainer)
                }
                content()
                if (onSave != null) Button(onClick = onSave, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(if (busy) "Guardando…" else saveLabel) }
            }
        }
    }
}

@Composable
fun FormField(label: String, value: String, onValue: (String) -> Unit, numeric: Boolean = false, supporting: String? = null, multiline: Boolean = false) {
    OutlinedTextField(value = value, onValueChange = onValue, label = { Text(label) },
        modifier = Modifier.fillMaxWidth(), singleLine = !multiline, minLines = if (multiline) 3 else 1,
        shape = MaterialTheme.shapes.medium,
        keyboardOptions = KeyboardOptions(keyboardType = if (numeric) KeyboardType.Decimal else KeyboardType.Text),
        supportingText = if (supporting == null) null else {{ Text(supporting) }})
}

@Composable
fun FormDate(label: String, epoch: Long, onValue: (Long) -> Unit) {
    val context = LocalContext.current
    val date = LocalDate.ofEpochDay(epoch)
    OutlinedButton(onClick = { DatePickerDialog(context, { _, y, m, d -> onValue(LocalDate.of(y, m + 1, d).toEpochDay()) }, date.year, date.monthValue - 1, date.dayOfMonth).show() }, modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp), shape = MaterialTheme.shapes.medium) {
        Icon(Icons.Outlined.CalendarMonth, null)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) { Text(label, style = MaterialTheme.typography.labelMedium); Text(date.format(SpanishDate), style = MaterialTheme.typography.bodyLarge) }
    }
}

@Composable
fun FormSwitch(label: String, checked: Boolean, onValue: (Boolean) -> Unit, supporting: String = "") {
    Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) { Text(label); if (supporting.isNotBlank()) Text(supporting, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Switch(checked, onValue)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormChoice(label: String, value: String, options: List<Pair<String, String>>, onValue: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded, { expanded = !expanded }) {
        OutlinedTextField(options.firstOrNull { it.first == value }?.second ?: value, {}, readOnly = true, label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) }, modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(), shape = MaterialTheme.shapes.medium)
        ExposedDropdownMenu(expanded, { expanded = false }) { options.forEach { (id, name) -> DropdownMenuItem(text = { Text(name) }, onClick = { onValue(id); expanded = false }) } }
    }
}

@Composable
fun FormHeading(text: String) { Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp)) }
