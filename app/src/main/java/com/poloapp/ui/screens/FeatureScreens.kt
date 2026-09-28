package com.poloapp.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.poloapp.data.*
import com.poloapp.domain.*
import com.poloapp.ui.components.*
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun FuelScreen(data: ScreenData, onAdd: (RecordKind) -> Unit, onEdit: (CarRecord) -> Unit) {
    val fills = data.ofKind(RecordKind.FUEL)
    Page {
        item { PageHeader("Cada litro cuenta.", "Tu consumo real, de lleno a lleno.", onAdd = { onAdd(RecordKind.FUEL) }) }
        item {
            PoloCard(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Eyebrow("Consumo medio", Modifier.weight(1f))
                    IconBadge(Icons.Outlined.LocalGasStation, size = 38)
                }
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(data.fuel.averageLitersPer100Km?.let { decimal(it) } ?: "—", style = MaterialTheme.typography.displayLarge)
                    Text("l/100 km", Modifier.padding(bottom = 9.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (data.fuel.intervals.isNotEmpty()) {
                    LineChart(data.fuel.intervals.takeLast(12).map { it.litersPer100Km }, description = "Evolución del consumo: " + data.fuel.intervals.takeLast(12).joinToString { "${decimal(it.litersPer100Km)} litros cada 100 kilómetros" })
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(shortDate(data.fuel.intervals.takeLast(12).first().endDate.toEpochDay()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(shortDate(data.fuel.intervals.last().endDate.toEpochDay()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else QuietHint("El consumo aparecerá después de dos depósitos completos. Los repostajes parciales también cuentan.")
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PoloCard(Modifier.weight(1f)) { StatMetric("Precio medio", data.fuel.averagePricePerLiter?.let { decimal(it, 3) } ?: "—", "€/l") }
                PoloCard(Modifier.weight(1f)) { StatMetric("Litros repostados", decimal(data.fuel.totalLiters), "l") }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("En combustible", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(money(data.fuel.totalCents), style = MaterialTheme.typography.headlineSmall) }
                StatusPill("${fills.size} repostajes")
            }
        }
        item { SectionHeading("Historial de repostajes") }
        if (fills.isEmpty()) item { EmptyState("Tu primera parada", "Guarda los litros, el importe y los kilómetros. El resto lo calcula Polo App.", Icons.Outlined.LocalGasStation, "Añadir repostaje") { onAdd(RecordKind.FUEL) } }
        recordHistory(data, fills, onEdit)
    }
}

@Composable
internal fun MaintenanceScreen(data: ScreenData, onAdd: (RecordKind) -> Unit, onEdit: (CarRecord) -> Unit, onAction: (String) -> Unit) {
    var selected by rememberSaveable { mutableStateOf("plan") }
    val interventions = data.ofKind(RecordKind.MAINTENANCE)
    val lifecycle = remember(data) { CarCalculations.partLifecycles(data.vehicle, data.records) }
    Page {
        item { PageHeader("Cuidarlo es avanzar.", "Revisiones, piezas y kilómetros de vida.", onAdd = { onAdd(RecordKind.MAINTENANCE) }) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PoloCard(Modifier.weight(1f)) { StatMetric("Intervenciones", interventions.size.toString(), footnote = "Todo el historial") }
                PoloCard(Modifier.weight(1f)) { StatMetric("Piezas activas", lifecycle.count { it.isActive }.toString(), footnote = "Con seguimiento") }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("plan" to "Próximas revisiones", "parts" to "Mis piezas", "history" to "Historial").forEach { (key, title) -> FilterChip(selected = selected == key, onClick = { selected = key }, label = { Text(title) }) }
            }
        }
        when (selected) {
            "plan" -> {
                item { SectionHeading("Plan de mantenimiento", "Ajustar") { onAction("rules") } }
                if (data.statuses.isEmpty()) item { EmptyState("Un plan a tu medida", "Configura los intervalos de kilómetros y tiempo de cada pieza.", Icons.Outlined.Tune, "Configurar plan") { onAction("rules") } }
                items(data.statuses, key = { it.rule.id }) { status -> MaintenanceStatusCard(status) { onAction("rules") } }
                item { QuietHint("Los intervalos son editables. Confírmalos con el manual o tu taller. La fecha estimada usa tu ritmo de conducción.") }
            }
            "parts" -> {
                if (lifecycle.isEmpty()) item { EmptyState("Cada pieza tiene su historia", "Al registrar una sustitución verás cuánto lleva recorrida. Cuando la cambies, conservaremos su vida útil.", Icons.Outlined.Settings, "Registrar intervención") { onAdd(RecordKind.MAINTENANCE) } }
                items(lifecycle, key = { it.record.id + it.axle }) { part ->
                    PoloCard(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).clickable { onEdit(part.record) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(part.record.title, style = MaterialTheme.typography.titleMedium)
                                Text(when (part.axle) { "front" -> "Eje delantero"; "rear" -> "Eje trasero"; else -> "Desde ${dateLabel(part.record.date)}" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            StatusPill(if (part.isActive) "En uso" else "Retirada", part.isActive)
                        }
                        StatMetric(if (part.isActive) "Recorridos desde su instalación" else "Vida útil registrada", km(part.distanceKm), "km")
                        if (part.retiredBy != null) Text("Sustituida el ${dateLabel(part.retiredBy.date)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            else -> {
                if (interventions.isEmpty()) item { EmptyState("El cuidado queda por escrito", "Registra trabajos de taller y los que haces tú. Adjunta facturas y guarda cada detalle.", Icons.Outlined.Build, "Añadir intervención") { onAdd(RecordKind.MAINTENANCE) } }
                recordHistory(data, interventions, onEdit)
            }
        }
        item { ActionTile("Tus talleres de confianza", "Agenda, contacto y valoraciones", Icons.Outlined.Storefront, { onAction("workshops") }) }
    }
}

@Composable
internal fun FinanceScreen(data: ScreenData, onAdd: (RecordKind) -> Unit, onEdit: (CarRecord) -> Unit, onAction: (String) -> Unit) {
    val payments = data.ofKind(RecordKind.PAYMENT)
    val debtProgress by animateFloatAsState(data.debt.progress, tween(650), label = "Deuda devuelta")
    Page {
        item { PageHeader("Poco a poco, tuyo.", "Un préstamo familiar. Cero intereses.", onAdd = { onAdd(RecordKind.PAYMENT) }) }
        item {
            PoloCard(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Eyebrow("Pendiente de devolver", Modifier.weight(1f))
                    IconButton(onClick = { onAction("debt") }) { Icon(Icons.Outlined.Edit, "Configurar deuda", Modifier.size(20.dp)) }
                }
                Text(money(data.debt.remainingCents), style = MaterialTheme.typography.displayMedium)
                LinearProgressIndicator(progress = { debtProgress }, Modifier.fillMaxWidth().height(7.dp).clip(CircleShape), color = MaterialTheme.colorScheme.secondary, trackColor = MaterialTheme.colorScheme.surfaceVariant)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${(data.debt.progress * 100).toInt()} % devuelto", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                    Text("de ${money(data.debt.initialCents)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                HorizontalDivider(Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    StatMetric("Ya has devuelto", money(data.debt.paidCents), modifier = Modifier.weight(1f))
                    StatMetric("Pagos registrados", payments.size.toString(), modifier = Modifier.weight(1f))
                }
                if (data.debt.initialCents == 0L) FilledTonalButton(onClick = { onAction("debt") }, shape = RoundedCornerShape(14.dp)) { Text("Configurar mi préstamo") }
                else Button(onClick = { onAdd(RecordKind.PAYMENT) }, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Registrar un pago") }
            }
        }
        item {
            PoloCard(Modifier.fillMaxWidth(), accent = true) {
                IconBadge(Icons.Outlined.Calculate)
                Text("Ponle fecha a tu objetivo.", style = MaterialTheme.typography.headlineSmall)
                Text("Prueba una cuota, añade pagos extra o elige cuándo quieres terminar. Tú decides el ritmo.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                TextButton(onClick = { onAction("simulator") }, contentPadding = PaddingValues(0.dp)) { Text("Abrir simulador"); Spacer(Modifier.width(10.dp)); Icon(Icons.Outlined.ArrowForward, null, Modifier.size(18.dp)) }
            }
        }
        item { SectionHeading("Cada pago, un paso") }
        if (payments.isEmpty()) item { EmptyState("Sin cuotas obligatorias", "Añade un pago cuando lo hagas, sea este mes o dentro de unos meses. Aquí tendrás siempre las cuentas claras.", Icons.Outlined.AccountBalanceWallet, "Registrar pago") { onAdd(RecordKind.PAYMENT) } }
        recordHistory(data, payments, onEdit)
        item { QuietHint("Tu financiación es privada y queda fuera del informe de venta.", Icons.Outlined.Lock) }
    }
}

@Composable
internal fun MoreScreen(data: ScreenData, onSection: (String) -> Unit, onAction: (String) -> Unit) {
    Page {
        item { PageHeader("Tu garaje, al detalle.", "Todo lo que acompaña a tu coche.") }
        item {
            PoloCard(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).clickable { onAction("vehicle") }) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    IconBadge(Icons.Outlined.DirectionsCar, size = 52)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(data.vehicle.name, style = MaterialTheme.typography.titleLarge)
                        Text(listOf(data.vehicle.plate.ifBlank { "Sin matrícula" }, "${data.vehicle.powerHp} CV").joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.Outlined.ChevronRight, null)
                }
            }
        }
        item { Eyebrow("En el día a día") }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionTile("Viajes", "Rutas, recuerdos y coste estimado", Icons.Outlined.Route, { onSection("trips") })
                ActionTile("Notas y recordatorios", "Que no se quede nada en el aire", Icons.Outlined.StickyNote2, { onSection("notes") })
                ActionTile("Gastos", "El coste de cada kilómetro", Icons.Outlined.ReceiptLong, { onSection("expenses") })
                ActionTile("Presión de neumáticos", "Valores y fecha de cada revisión", Icons.Outlined.TireRepair, { onSection("pressure") })
                ActionTile("Daños y reparaciones", "Fotos y seguimiento de cada marca", Icons.Outlined.CarCrash, { onSection("damage") })
            }
        }
        item { Eyebrow("Información y documentos") }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionTile("Estadísticas", "Tu coche, en perspectiva", Icons.Outlined.BarChart, { onSection("stats") })
                ActionTile("ITV y seguro", "Vencimientos y asistencia", Icons.Outlined.Verified, { onSection("alerts") })
                ActionTile("Guantera digital", "Documentos siempre a mano", Icons.Outlined.FolderOpen, { onSection("documents") })
                ActionTile("Ficha rápida", "Aceite, neumáticos y datos útiles", Icons.Outlined.FactCheck, { onAction("specs") })
            }
        }
        item {
            PoloCard(Modifier.fillMaxWidth(), accent = true) {
                Eyebrow("Una historia que da confianza")
                Text("El cuidado se puede demostrar.", style = MaterialTheme.typography.headlineSmall)
                Text("Crea un informe con revisiones, ITV y lecturas de kilometraje para el próximo dueño.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Button(onClick = { onAction("pdf") }, shape = RoundedCornerShape(14.dp)) { Icon(Icons.Outlined.PictureAsPdf, null, Modifier.size(19.dp)); Spacer(Modifier.width(8.dp)); Text("Preparar informe PDF") }
            }
        }
        item { Eyebrow("Tu espacio") }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionTile("Guardar una copia", "Historial y fotos en un archivo", Icons.Outlined.SaveAlt, { onAction("backup") })
                ActionTile("Restaurar una copia", "Recuperar un garaje exportado", Icons.Outlined.Restore, { onAction("restore") })
                ActionTile("Añadir otro vehículo", "Cada coche con su propia historia", Icons.Outlined.AddCircleOutline, { onAction("addVehicle") })
                ActionTile("Ajustes", "Apariencia, privacidad y avisos", Icons.Outlined.Tune, { onAction("settings") })
            }
        }
        item {
            Column(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("POLO APP", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Hecha para el camino.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
