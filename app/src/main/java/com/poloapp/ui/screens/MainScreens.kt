package com.poloapp.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.poloapp.data.*
import com.poloapp.domain.*
import com.poloapp.ui.components.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

internal data class ScreenData(val snapshot: GarageSnapshot, val vehicle: Vehicle) {
    val records = snapshot.recordsFor(vehicle.id)
    val currentKm = CarCalculations.currentKm(vehicle, records)
    val fuel = CarCalculations.fuelStats(records)
    val debt = CarCalculations.debtSummary(vehicle, records)
    val statuses = CarCalculations.maintenanceStatuses(vehicle, records, snapshot.rulesFor(vehicle.id))
    val spending = CarCalculations.spendingStats(vehicle, records)
    val attachmentCounts = snapshot.attachments.groupingBy { it.recordId }.eachCount()
    fun ofKind(kind: RecordKind) = records.filter { it.kind == kind }
}

@Composable
fun MainScreens(
    snapshot: GarageSnapshot,
    vehicle: Vehicle,
    section: String,
    onSection: (String) -> Unit,
    onAdd: (RecordKind) -> Unit,
    onEdit: (CarRecord) -> Unit,
    onAction: (String) -> Unit,
    financeLocked: Boolean = false
) {
    val data = remember(snapshot, vehicle, financeLocked) {
        ScreenData(if (financeLocked) snapshot.copy(records = snapshot.records.filter { it.kind != RecordKind.PAYMENT }) else snapshot, vehicle)
    }
    Scaffold(containerColor = MaterialTheme.colorScheme.background, bottomBar = { GarageNavigation(section, onSection) }) { padding ->
        AnimatedContent(targetState = section, modifier = Modifier.fillMaxSize().padding(padding), label = "Cambio de sección",
            transitionSpec = { (fadeIn(tween(210)) + slideInVertically(tween(210)) { it / 28 }).togetherWith(fadeOut(tween(90))) }) { target ->
            when (target) {
                "home" -> HomeScreen(data, onSection, onAdd, onEdit, onAction, financeLocked)
                "fuel" -> FuelScreen(data, onAdd, onEdit)
                "maintenance" -> MaintenanceScreen(data, onAdd, onEdit, onAction)
                "finance" -> if (financeLocked) Page {
                    item { PageHeader("Tu espacio privado.", "La financiación está protegida en este móvil.") }
                    item { EmptyState("Financiación protegida", "Desbloquea para consultar el préstamo y registrar tus pagos.", Icons.Outlined.Lock, "Desbloquear") { onAction("unlockFinance") } }
                } else FinanceScreen(data, onAdd, onEdit, onAction)
                "more" -> MoreScreen(data, onSection, onAction)
                "stats" -> StatisticsScreen(data) { onSection("more") }
                "alerts" -> AlertsScreen(data, onAdd, onEdit, onAction) { onSection("home") }
                else -> RecordsScreen(data, target, onAdd, onEdit) { onSection("more") }
            }
        }
    }
}

private data class Destination(val id: String, val label: String, val icon: ImageVector)

@Composable
private fun GarageNavigation(section: String, onSection: (String) -> Unit) {
    val destinations = listOf(Destination("home", "Inicio", Icons.Outlined.SpaceDashboard), Destination("fuel", "Repostajes", Icons.Outlined.LocalGasStation),
        Destination("maintenance", "Taller", Icons.Outlined.Build), Destination("finance", "Finanzas", Icons.Outlined.AccountBalanceWallet), Destination("more", "Garaje", Icons.Outlined.GridView))
    val selected = section.takeIf { s -> destinations.any { it.id == s } } ?: "more"
    Surface(color = MaterialTheme.colorScheme.background, shadowElevation = 0.dp) {
        Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .65f))
            NavigationBar(containerColor = Color.Transparent, tonalElevation = 0.dp, modifier = Modifier.heightIn(min = 78.dp)) {
                destinations.forEach { d ->
                    NavigationBarItem(selected = selected == d.id, onClick = { onSection(d.id) },
                        icon = { Icon(d.icon, null, Modifier.size(22.dp)) }, label = { Text(d.label, style = MaterialTheme.typography.labelMedium, maxLines = 1) },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = MaterialTheme.colorScheme.primary, selectedTextColor = MaterialTheme.colorScheme.onSurface,
                            indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = .11f), unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant))
                }
            }
        }
    }
}

@Composable
internal fun Page(content: LazyListScope.() -> Unit) {
    LazyColumn(Modifier.fillMaxSize().testTag("polo-page"), contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 18.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(20.dp), content = content)
}

@Composable
internal fun PageHeader(title: String, subtitle: String, icon: ImageVector? = null, onAdd: (() -> Unit)? = null, onBack: (() -> Unit)? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (onBack != null) IconButton(onClick = onBack, modifier = Modifier.size(44.dp)) { Icon(Icons.Outlined.ArrowBack, "Volver") }
            Eyebrow("Polo app / tu garaje", Modifier.weight(1f))
            if (onAdd != null) FilledIconButton(onClick = onAdd, shape = RoundedCornerShape(14.dp), modifier = Modifier.size(46.dp)) { Icon(icon ?: Icons.Outlined.Add, "Añadir registro") }
        }
        Text(title, style = MaterialTheme.typography.headlineLarge)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun HomeScreen(data: ScreenData, onSection: (String) -> Unit, onAdd: (RecordKind) -> Unit, onEdit: (CarRecord) -> Unit, onAction: (String) -> Unit, financeLocked: Boolean) {
    val debtProgress by animateFloatAsState(data.debt.progress, tween(650), label = "Progreso de devolución")
    val today = LocalDate.now()
    val greeting = today.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.forLanguageTag("es-ES"))).replaceFirstChar { it.uppercase() }
    Page {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Eyebrow(greeting)
                    Text("Tu próxima aventura.", style = MaterialTheme.typography.headlineMedium)
                }
                IconButton(onClick = { onSection("alerts") }, Modifier.size(48.dp).background(MaterialTheme.colorScheme.surface, CircleShape)) {
                    Icon(Icons.Outlined.NotificationsNone, "Avisos del coche", tint = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
        item { VehicleHero(data, onAction) }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickAdd("Repostar", Icons.Outlined.LocalGasStation, Modifier.weight(1f)) { onAdd(RecordKind.FUEL) }
                QuickAdd("Gasto", Icons.Outlined.AddCard, Modifier.weight(1f)) { onAdd(RecordKind.EXPENSE) }
                QuickAdd("Nota", Icons.Outlined.NoteAdd, Modifier.weight(1f)) { onAdd(RecordKind.NOTE) }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionHeading("Todo bajo control", "Ver avisos") { onSection("alerts") }
                val statuses = data.statuses
                val health = CarCalculations.healthScore(statuses)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    DeadlineCard("ITV", data.ofKind(RecordKind.ITV).firstOrNull(), Icons.Outlined.Verified, Modifier.weight(1f)) { data.ofKind(RecordKind.ITV).firstOrNull()?.let(onEdit) ?: onAdd(RecordKind.ITV) }
                    DeadlineCard("Seguro", data.ofKind(RecordKind.INSURANCE).firstOrNull(), Icons.Outlined.Shield, Modifier.weight(1f)) { data.ofKind(RecordKind.INSURANCE).firstOrNull()?.let(onEdit) ?: onAdd(RecordKind.INSURANCE) }
                }
                if (health != null) {
                    val allGood = statuses.none { it.state == MaintenanceState.OVERDUE }
                    Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = .45f), RoundedCornerShape(18.dp)).padding(15.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(if (allGood) Icons.Outlined.CheckCircle else Icons.Outlined.Tune, null, tint = MaterialTheme.colorScheme.secondary)
                        Column(Modifier.weight(1f)) {
                            Text("Seguimiento del cuidado · $health/100", style = MaterialTheme.typography.titleSmall)
                            Text("Según tus intervalos de mantenimiento", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                val next = statuses.sortedWith(compareBy<MaintenanceStatus> { when (it.state) { MaintenanceState.OVERDUE -> 0; MaintenanceState.SOON -> 1; MaintenanceState.OK -> 2; else -> 3 } }.thenBy { it.remainingKm ?: Int.MAX_VALUE }).firstOrNull()
                if (next != null) MaintenanceStatusCard(next) { onAction("rules") }
                else ActionTile("Un coche bien cuidado", "Configura los intervalos de mantenimiento", Icons.Outlined.Build, { onAction("rules") })
            }
        }
        item {
            PoloCard(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).clickable { onSection("finance") }) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Eyebrow("A tu ritmo", Modifier.weight(1f))
                    Icon(Icons.Outlined.ArrowOutward, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (financeLocked) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        IconBadge(Icons.Outlined.Lock, tint = MaterialTheme.colorScheme.secondary)
                        Text("Financiación protegida", style = MaterialTheme.typography.titleMedium)
                    }
                    TextButton(onClick = { onAction("unlockFinance") }) { Text("Desbloquear") }
                } else {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1f)) {
                        Text(money(data.debt.remainingCents), style = MaterialTheme.typography.headlineMedium)
                        Text("Pendiente de devolver", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("${(data.debt.progress * 100).toInt()} %", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary)
                }
                LinearProgressIndicator(progress = { debtProgress }, Modifier.fillMaxWidth().height(5.dp).clip(CircleShape), color = MaterialTheme.colorScheme.secondary, trackColor = MaterialTheme.colorScheme.surfaceVariant)
                if (data.vehicle.initialDebtCents == 0L) Text("Añade el importe inicial para empezar a seguir tus pagos.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item { SectionHeading("Últimos movimientos", "Historial") { onSection("history") } }
        if (data.records.isEmpty()) item { EmptyState("Aquí empieza vuestra historia", "Tu primer repostaje, una revisión o ese viaje que recuerdas. Cada registro ayuda a cuidar mejor tu coche.", Icons.Outlined.AutoAwesome, "Añadir mi primer registro") { onAdd(RecordKind.FUEL) } }
        else items(data.records.filter { !financeLocked || it.kind != RecordKind.PAYMENT }.take(4), key = { it.id }) { record -> RecordRow(record, data.attachmentCounts[record.id] ?: 0) { onEdit(record) } }
        item { QuietHint("Tu garaje vive en este móvil. Sin conexión, sin cuentas.", Icons.Outlined.CloudOff) }
    }
}

@Composable
private fun VehicleHero(data: ScreenData, onAction: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Brush.linearGradient(listOf(colors.surfaceVariant.copy(alpha = .6f), colors.surface, colors.surface)))) {
        Row(Modifier.fillMaxWidth().padding(22.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Eyebrow("${data.vehicle.make} · ${data.vehicle.powerHp} CV", color = colors.primary)
                Text(data.vehicle.name, style = MaterialTheme.typography.headlineSmall)
                Text(data.vehicle.model, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
            IconButton(onClick = { onAction("switchVehicle") }) { Icon(Icons.Outlined.SwapHoriz, "Cambiar de vehículo", tint = colors.onSurfaceVariant) }
        }
        PoloArtwork(Modifier.fillMaxWidth().height(160.dp).padding(horizontal = 12.dp))
        Row(Modifier.fillMaxWidth().padding(start = 22.dp, end = 16.dp, bottom = 22.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Eyebrow("Kilómetros compartidos")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                    Text(km(data.currentKm), style = MaterialTheme.typography.displayMedium)
                    Text("km", Modifier.padding(bottom = 7.dp), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                }
            }
            OutlinedIconButton(onClick = { onAction("odometer") }, shape = CircleShape, border = BorderStroke(1.dp, colors.outlineVariant)) { Icon(Icons.Outlined.Edit, "Actualizar kilometraje", Modifier.size(19.dp)) }
        }
    }
}

@Composable
private fun QuickAdd(title: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Surface(modifier.clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick), color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .55f))) {
        Column(Modifier.padding(horizontal = 6.dp, vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Icon(icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
            Text(title, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
internal fun DeadlineCard(title: String, record: CarRecord?, icon: ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val due = record?.text("dueDate")?.toLongOrNull()
    val days = due?.minus(LocalDate.now().toEpochDay())
    val color = when { days == null -> MaterialTheme.colorScheme.onSurfaceVariant; days < 0 -> MaterialTheme.colorScheme.error; days <= 30 -> MaterialTheme.colorScheme.tertiary; else -> MaterialTheme.colorScheme.secondary }
    PoloCard(modifier.clip(RoundedCornerShape(24.dp)).clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { Icon(icon, null, Modifier.size(18.dp), tint = color); Text(title, style = MaterialTheme.typography.labelLarge) }
        Text(when { days == null -> "—"; days < 0 -> "${-days}"; else -> "$days" }, style = MaterialTheme.typography.headlineLarge, color = color)
        Text(when { days == null -> "Añadir fecha"; days < 0 -> "días de retraso"; days == 0L -> "vence hoy"; else -> "días para renovar" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (due != null) Text(shortDate(due), style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
internal fun MaintenanceStatusCard(status: MaintenanceStatus, onClick: () -> Unit) {
    val color = when (status.state) { MaintenanceState.OVERDUE -> MaterialTheme.colorScheme.error; MaintenanceState.SOON -> MaterialTheme.colorScheme.tertiary; MaintenanceState.OK -> MaterialTheme.colorScheme.secondary; else -> MaterialTheme.colorScheme.onSurfaceVariant }
    PoloCard(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
            IconBadge(Icons.Outlined.Build, tint = color)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(status.rule.title, style = MaterialTheme.typography.titleMedium)
                val remaining = listOfNotNull(status.remainingKm?.let { "${km(kotlin.math.abs(it))} km${if (it < 0) " de retraso" else " restantes"}" }, status.remainingDays?.let { "${kotlin.math.abs(it)} días${if (it < 0) " de retraso" else ""}" }).joinToString(" · ")
                Text(remaining.ifBlank { "Añade una revisión de referencia" }, style = MaterialTheme.typography.bodySmall, color = color)
            }
            Icon(Icons.Outlined.ChevronRight, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (status.estimatedDate != null && status.state == MaintenanceState.OK) Text("A tu ritmo actual: hacia el ${dateLabel(status.estimatedDate.toEpochDay())}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

internal fun LazyListScope.recordHistory(data: ScreenData, records: List<CarRecord>, onEdit: (CarRecord) -> Unit) {
    items(records, key = { it.id }) { record -> RecordRow(record, data.attachmentCounts[record.id] ?: 0, recordSubtitle(record, data.snapshot)) { onEdit(record) } }
}

internal fun recordSubtitle(record: CarRecord, snapshot: GarageSnapshot): String {
    val detail = when (record.kind) {
        RecordKind.FUEL -> listOfNotNull(record.number("liters")?.let { "${decimal(it)} l" }, if (record.flag("fullTank")) "Lleno" else "Parcial").joinToString(" · ")
        RecordKind.MAINTENANCE -> if (record.text("provider") == "self") "Por mi cuenta" else snapshot.workshops.firstOrNull { it.id == record.text("workshopId") }?.name ?: "Taller"
        RecordKind.TRIP -> listOfNotNull(record.number("startKm")?.let { start -> record.number("endKm")?.let { "${km((it - start).toInt())} km" } }, record.text("purpose").takeIf { it.isNotBlank() }).joinToString(" · ")
        RecordKind.PRESSURE -> "${record.text("frontBar")} / ${record.text("rearBar")} bar · ${if (record.flag("cold")) "En frío" else "En caliente"}"
        RecordKind.DAMAGE -> if (record.flag("repaired")) "Reparado" else record.text("location", "Pendiente de reparar")
        RecordKind.NOTE -> if (record.flag("pinned")) "Fijada" else record.notes.take(45)
        RecordKind.DOCUMENT, RecordKind.EXPENSE -> record.text("category")
        else -> record.odometer?.let { "${km(it)} km" } ?: ""
    }
    return listOf(shortDate(record.date), detail).filter { it.isNotBlank() }.joinToString(" · ")
}
