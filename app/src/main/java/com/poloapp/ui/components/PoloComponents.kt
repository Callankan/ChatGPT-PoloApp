package com.poloapp.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.poloapp.data.*
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val Spanish = Locale.forLanguageTag("es-ES")
fun money(cents: Long): String = NumberFormat.getCurrencyInstance(Spanish).format(cents / 100.0)
fun decimal(value: Double, places: Int = 1): String = NumberFormat.getNumberInstance(Spanish).apply { minimumFractionDigits = places; maximumFractionDigits = places }.format(value)
fun km(value: Int): String = NumberFormat.getIntegerInstance(Spanish).format(value)
fun dateLabel(day: Long): String = LocalDate.ofEpochDay(day).format(DateTimeFormatter.ofPattern("d MMM yyyy", Spanish))
fun shortDate(day: Long): String = LocalDate.ofEpochDay(day).format(DateTimeFormatter.ofPattern("d MMM", Spanish))

fun kindIcon(kind: RecordKind): ImageVector = when (kind) {
    RecordKind.FUEL -> Icons.Outlined.LocalGasStation
    RecordKind.MAINTENANCE -> Icons.Outlined.Build
    RecordKind.EXPENSE -> Icons.Outlined.ReceiptLong
    RecordKind.PAYMENT -> Icons.Outlined.AccountBalanceWallet
    RecordKind.TRIP -> Icons.Outlined.Route
    RecordKind.NOTE -> Icons.Outlined.StickyNote2
    RecordKind.ITV -> Icons.Outlined.Verified
    RecordKind.INSURANCE -> Icons.Outlined.Shield
    RecordKind.PRESSURE -> Icons.Outlined.TireRepair
    RecordKind.DAMAGE -> Icons.Outlined.CarCrash
    RecordKind.DOCUMENT -> Icons.Outlined.FolderOpen
    RecordKind.ODOMETER -> Icons.Outlined.Speed
}

@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(text.uppercase(Spanish), modifier, style = MaterialTheme.typography.labelSmall, color = color)
}

@Composable
fun PoloCard(modifier: Modifier = Modifier, accent: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier, shape = RoundedCornerShape(24.dp), color = if (accent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .65f))) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
fun SectionHeading(title: String, action: String? = null, onAction: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
        if (action != null) TextButton(onClick = onAction) { Text(action, style = MaterialTheme.typography.labelMedium) }
    }
}

@Composable
fun IconBadge(icon: ImageVector, modifier: Modifier = Modifier, tint: Color = MaterialTheme.colorScheme.primary, size: Int = 44) {
    Box(modifier.size(size.dp).background(tint.copy(alpha = .11f), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size(21.dp), tint = tint)
    }
}

@Composable
fun StatMetric(label: String, value: String, unit: String = "", modifier: Modifier = Modifier, footnote: String? = null) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(value, style = MaterialTheme.typography.headlineMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            if (unit.isNotBlank()) Text(unit, Modifier.padding(bottom = 3.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        footnote?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
fun ActionTile(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick), color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .55f))) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            IconBadge(icon)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Outlined.ChevronRight, null, Modifier.size(19.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun EmptyState(title: String, body: String, icon: ImageVector, action: String? = null, onAction: () -> Unit = {}) {
    PoloCard(Modifier.fillMaxWidth()) {
        IconBadge(icon, size = 52)
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (action != null) FilledTonalButton(onClick = onAction, shape = RoundedCornerShape(14.dp)) {
            Icon(Icons.Outlined.Add, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp)); Text(action)
        }
    }
}

@Composable
fun StatusPill(label: String, positive: Boolean = true) {
    val tint = if (positive) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.tertiary
    Row(Modifier.background(tint.copy(alpha = .12f), CircleShape).padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(5.dp).background(tint, CircleShape))
        Text(label, style = MaterialTheme.typography.labelMedium, color = tint)
    }
}

@Composable
fun RecordRow(record: CarRecord, attachmentCount: Int, subtitle: String? = null, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick).padding(vertical = 13.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(13.dp), verticalAlignment = Alignment.CenterVertically) {
        IconBadge(kindIcon(record.kind), tint = if (record.kind == RecordKind.PAYMENT) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(record.title.ifBlank { record.kind.label }, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(subtitle ?: listOfNotNull(shortDate(record.date), record.odometer?.let { "${km(it)} km" }).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (attachmentCount > 0) Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.AttachFile, null, Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("$attachmentCount ${if (attachmentCount == 1) "adjunto" else "adjuntos"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (record.amountCents > 0) Text(money(record.amountCents), style = MaterialTheme.typography.titleSmall, color = if (record.kind == RecordKind.PAYMENT) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface)
        Icon(Icons.Outlined.ChevronRight, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.outline)
    }
}

@Composable
fun LineChart(values: List<Double>, modifier: Modifier = Modifier, description: String = "Evolución de los registros") {
    val tint = MaterialTheme.colorScheme.primary
    val guide = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier.fillMaxWidth().height(115.dp).semantics { contentDescription = description }) {
        val pad = 10.dp.toPx()
        val w = size.width - pad * 2; val h = size.height - pad * 2
        for (i in 0..2) drawLine(guide.copy(alpha = .5f), Offset(pad, pad + h * i / 2), Offset(size.width - pad, pad + h * i / 2), 1.dp.toPx())
        if (values.isNotEmpty()) {
            val lo = values.minOrNull()!! * .9; val hi = maxOf(values.maxOrNull()!! * 1.1, lo + 1.0)
            val pts = values.mapIndexed { i, v -> Offset(pad + if (values.size == 1) w / 2 else w * i / (values.size - 1), pad + h * (1 - (v - lo) / (hi - lo)).toFloat()) }
            val path = Path().apply { moveTo(pts.first().x, pts.first().y); pts.drop(1).forEach { lineTo(it.x, it.y) } }
            drawPath(path, tint, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
            pts.forEach { drawCircle(tint, 3.5.dp.toPx(), it) }
        }
    }
}

@Composable
fun QuietHint(text: String, icon: ImageVector = Icons.Outlined.Info) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.Top) {
        Icon(icon, null, Modifier.padding(top = 2.dp).size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
