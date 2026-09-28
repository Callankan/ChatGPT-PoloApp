package com.poloapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.poloapp.data.*
import com.poloapp.domain.*
import com.poloapp.ui.components.*
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun RecordsScreen(data: ScreenData, section: String, onAdd: (RecordKind) -> Unit, onEdit: (CarRecord) -> Unit, onBack: () -> Unit) {
    val kind = when (section) { "trips" -> RecordKind.TRIP; "notes" -> RecordKind.NOTE; "expenses" -> RecordKind.EXPENSE; "pressure" -> RecordKind.PRESSURE; "damage" -> RecordKind.DAMAGE; "documents" -> RecordKind.DOCUMENT; else -> RecordKind.ODOMETER }
    val title = when (section) { "trips" -> "Kilómetros que cuentan."; "notes" -> "Que no se te escape."; "expenses" -> "Las cuentas, claras."; "pressure" -> "El contacto con el camino."; "damage" -> "Cada marca, registrada."; "documents" -> "Todo en la guantera."; else -> "La historia completa." }
    val subtitle = when (section) { "trips" -> "Rutas largas y pequeños detalles del viaje."; "notes" -> "Ideas, pendientes y recordatorios."; "expenses" -> "Parking, peajes, lavados y otros gastos."; "pressure" -> "Presiones por eje y fecha de comprobación."; "damage" -> "Golpes, arañazos y su reparación."; "documents" -> "Tus documentos, contigo y sin conexión."; else -> "Cada lectura, cada revisión, cada aventura." }
    var filter by rememberSaveable(section) { mutableStateOf("all") }
    val records = if (section == "history") data.records.filter { filter == "all" || it.kind.name == filter } else data.ofKind(kind).let { list -> if (kind == RecordKind.NOTE) list.sortedByDescending { it.flag("pinned") } else list }
    Page {
        item { PageHeader(title, subtitle, onAdd = if (section == "history") null else ({ onAdd(kind) }), onBack = onBack) }
        if (section == "history") item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = filter == "all", onClick = { filter = "all" }, label = { Text("Todo") })
                RecordKind.entries.forEach { item -> FilterChip(selected = filter == item.name, onClick = { filter = item.name }, label = { Text(item.label) }) }
            }
        }
        when (kind) {
            RecordKind.PRESSURE -> {
                val latest = records.firstOrNull()
                if (latest != null) item {
                    PoloCard(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).clickable { onEdit(latest) }) {
                        Eyebrow("Última comprobación · ${shortDate(latest.date)}")
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                            StatMetric("Eje delantero", latest.number("frontBar")?.let { decimal(it, 2) } ?: "—", "bar", Modifier.weight(1f))
                            StatMetric("Eje trasero", latest.number("rearBar")?.let { decimal(it, 2) } ?: "—", "bar", Modifier.weight(1f))
                        }
                        StatusPill(if (latest.flag("cold")) "Medición en frío" else "Medición en caliente", latest.flag("cold"))
                    }
                }
                item { QuietHint("Usa la presión indicada para tu carga en la etiqueta del vehículo. Puedes guardar esos valores en la ficha rápida.", Icons.Outlined.TireRepair) }
            }
            RecordKind.TRIP -> if (records.isNotEmpty()) item {
                val distance = records.sumOf { ((it.number("endKm") ?: 0.0) - (it.number("startKm") ?: 0.0)).toInt().coerceAtLeast(0) }
                PoloCard(Modifier.fillMaxWidth()) {
                    Eyebrow("Un camino compartido")
                    StatMetric("En ${records.size} viajes registrados", km(distance), "km")
                    CarCalculations.estimatedTripCostCents(distance, data.fuel)?.let { Text("${money(it)} de combustible estimado", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
            RecordKind.EXPENSE -> item {
                PoloCard(Modifier.fillMaxWidth()) {
                    StatMetric("Otros gastos registrados", money(records.sumOf { it.amountCents }))
                    Text("El resumen completo, con combustible y mantenimiento, está en Estadísticas.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            RecordKind.DAMAGE -> if (records.isNotEmpty()) item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PoloCard(Modifier.weight(1f)) { StatMetric("Pendientes", records.count { !it.flag("repaired") }.toString()) }
                    PoloCard(Modifier.weight(1f)) { StatMetric("Reparados", records.count { it.flag("repaired") }.toString()) }
                }
            }
            RecordKind.DOCUMENT -> item { QuietHint("Las fotos y documentos se guardan en el móvil. Inclúyelos en una copia de seguridad para conservarlos.", Icons.Outlined.FolderOpen) }
            else -> Unit
        }
        item { SectionHeading(if (section == "history") "${records.size} registros" else if (kind == RecordKind.NOTE) "Tus notas" else "Historial") }
        if (records.isEmpty()) item {
            EmptyState(
                when (kind) { RecordKind.TRIP -> "¿Cuál será el próximo destino?"; RecordKind.NOTE -> "Un lugar para recordarlo"; RecordKind.PRESSURE -> "Una pequeña revisión, más tranquilidad"; RecordKind.DAMAGE -> "El estado de tu coche, al día"; RecordKind.DOCUMENT -> "Una guantera sin papeles"; else -> "Todavía no hay registros" },
                when (kind) { RecordKind.TRIP -> "Guarda los kilómetros de salida y llegada, el motivo y lo que quieras recordar."; RecordKind.NOTE -> "Guarda una nota, fíjala arriba o añade una fecha de recordatorio."; RecordKind.PRESSURE -> "Apunta la presión de ambos ejes y la fecha. Así sabrás cuándo la comprobaste por última vez."; RecordKind.DAMAGE -> "Si aparece un golpe o un arañazo, guarda su ubicación y fotos. Después podrás marcarlo como reparado."; RecordKind.DOCUMENT -> "Añade permiso de circulación, ficha técnica, póliza o informe de ITV con sus archivos."; else -> "Tu historial aparecerá aquí a medida que uses Polo App." },
                kindIcon(kind), if (section == "history") null else "Añadir ${kind.label.lowercase()}") { onAdd(kind) }
        }
        if (kind == RecordKind.NOTE && section != "history") items(records, key = { it.id }) { note ->
            PoloCard(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).clickable { onEdit(note) }) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(note.title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    if (note.flag("pinned")) Icon(Icons.Outlined.PushPin, "Nota fijada", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                }
                if (note.notes.isNotBlank()) Text(note.notes, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 7)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(dateLabel(note.date), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    note.text("reminderDate").toLongOrNull()?.let { Text("Aviso: ${shortDate(it)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
                }
            }
        } else if (kind == RecordKind.TRIP && section != "history") items(records, key = { it.id }) { trip ->
            PoloCard(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).clickable { onEdit(trip) }) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    IconBadge(Icons.Outlined.Route)
                    Column(Modifier.weight(1f)) { Text(trip.title, style = MaterialTheme.typography.titleMedium); Text(dateLabel(trip.date), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                val distance = ((trip.number("endKm") ?: 0.0) - (trip.number("startKm") ?: 0.0)).toInt().coerceAtLeast(0)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    StatMetric("Recorrido", km(distance), "km", Modifier.weight(1f))
                    CarCalculations.estimatedTripCostCents(distance, data.fuel)?.let { StatMetric("Gasolina estimada", money(it), modifier = Modifier.weight(1f)) }
                }
                if (trip.text("purpose").isNotBlank()) Text(trip.text("purpose"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (trip.notes.isNotBlank()) Text(trip.notes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3)
            }
        } else recordHistory(data, records, onEdit)
        if (kind == RecordKind.TRIP && records.isNotEmpty()) item { QuietHint("La estimación de gasolina usa tu consumo y precio medios registrados. Los peajes se contabilizan por separado.") }
    }
}

@Composable
internal fun AlertsScreen(data: ScreenData, onAdd: (RecordKind) -> Unit, onEdit: (CarRecord) -> Unit, onAction: (String) -> Unit, onBack: () -> Unit) {
    val itvs = data.ofKind(RecordKind.ITV)
    val insurances = data.ofKind(RecordKind.INSURANCE)
    Page {
        item { PageHeader("Un paso por delante.", "ITV, seguro y revisiones en el momento justo.", onBack = onBack) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DeadlineCard("Próxima ITV", itvs.firstOrNull(), Icons.Outlined.Verified, Modifier.weight(1f)) { itvs.firstOrNull()?.let(onEdit) ?: onAdd(RecordKind.ITV) }
                DeadlineCard("Seguro", insurances.firstOrNull(), Icons.Outlined.Shield, Modifier.weight(1f)) { insurances.firstOrNull()?.let(onEdit) ?: onAdd(RecordKind.INSURANCE) }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionTile("Registrar una ITV", "Resultado, estación y próximo vencimiento", Icons.Outlined.Verified, { onAdd(RecordKind.ITV) })
                ActionTile("Añadir o renovar seguro", "Póliza, cobertura y contacto de asistencia", Icons.Outlined.Shield, { onAdd(RecordKind.INSURANCE) })
                val insurance = insurances.firstOrNull()
                if (insurance != null && insurance.text("assistancePhone").isNotBlank()) ActionTile("Asistencia en carretera", "${insurance.text("company")} · ${insurance.text("assistancePhone")}", Icons.Outlined.Phone, { onAction("assistance") })
            }
        }
        item { SectionHeading("Mantenimiento previsto", "Ajustar") { onAction("rules") } }
        if (data.statuses.isEmpty()) item { EmptyState("Cada aviso, a tu medida", "Añade intervalos para recibir avisos por kilómetros o tiempo.", Icons.Outlined.NotificationsNone, "Configurar") { onAction("rules") } }
        items(data.statuses, key = { it.rule.id }) { status -> MaintenanceStatusCard(status) { onAction("rules") } }
        item { ActionTile("Preferencias de avisos", "Notificaciones y recordatorios locales", Icons.Outlined.NotificationsActive, { onAction("settings") }) }
        if (itvs.isNotEmpty() || insurances.isNotEmpty()) item { SectionHeading("Historial de ITV y seguros") }
        recordHistory(data, data.records.filter { it.kind == RecordKind.ITV || it.kind == RecordKind.INSURANCE }, onEdit)
    }
}

@Composable
internal fun StatisticsScreen(data: ScreenData, onBack: () -> Unit) {
    val stats = data.spending
    val months = stats.byMonth.entries.toList().takeLast(12)
    val max = (months.maxOfOrNull { it.value } ?: 0L).coerceAtLeast(1)
    Page {
        item { PageHeader("Tu coche, en perspectiva.", "Cifras reales de los registros que has guardado.", onBack = onBack) }
        item {
            PoloCard(Modifier.fillMaxWidth()) {
                Eyebrow("Coste acumulado")
                Text(money(stats.totalCents), style = MaterialTheme.typography.displayMedium)
                Text("Combustible, mantenimiento, seguro y otros gastos.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    StatMetric("Coste por km", stats.costPerKm?.let { "${decimal(it, 3)} €" } ?: "—", modifier = Modifier.weight(1f))
                    StatMetric("Desde la compra", km((data.currentKm - data.vehicle.purchaseKm).coerceAtLeast(0)), "km", Modifier.weight(1f))
                }
            }
        }
        item { SectionHeading("Gasto mensual") }
        if (months.isEmpty()) item { EmptyState("Las cifras llegarán contigo", "Añade tu primer gasto o repostaje para descubrir cómo evoluciona el coste de tu coche.", Icons.Outlined.BarChart) }
        else item {
            PoloCard(Modifier.fillMaxWidth()) {
                months.forEach { (month, cents) ->
                    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(month.format(DateTimeFormatter.ofPattern("MMM yyyy", Locale.forLanguageTag("es-ES"))), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(money(cents), style = MaterialTheme.typography.labelMedium)
                        }
                        LinearProgressIndicator(progress = { cents.toFloat() / max }, Modifier.fillMaxWidth().height(7.dp).clip(CircleShape), color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.surfaceVariant)
                    }
                }
            }
        }
        if (stats.byCategory.isNotEmpty()) {
            item { SectionHeading("Dónde va cada euro") }
            item {
                PoloCard(Modifier.fillMaxWidth()) {
                    stats.byCategory.entries.sortedByDescending { it.value }.forEach { (category, cents) ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(Modifier.size(7.dp).background(MaterialTheme.colorScheme.secondary, CircleShape))
                            Text(category, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            Column(horizontalAlignment = Alignment.End) {
                                Text(money(cents), style = MaterialTheme.typography.labelLarge)
                                Text("${decimal(cents * 100.0 / stats.totalCents.coerceAtLeast(1), 0)} %", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
        if (data.fuel.intervals.isNotEmpty()) item {
            PoloCard(Modifier.fillMaxWidth()) {
                Eyebrow("Evolución del consumo")
                StatMetric("Media ponderada por distancia", decimal(data.fuel.averageLitersPer100Km ?: 0.0), "l/100 km")
                LineChart(data.fuel.intervals.takeLast(12).map { it.litersPer100Km })
            }
        }
        item { QuietHint("Los pagos del préstamo no se suman al coste de uso. El combustible estimado de viajes tampoco se vuelve a contar.") }
    }
}
