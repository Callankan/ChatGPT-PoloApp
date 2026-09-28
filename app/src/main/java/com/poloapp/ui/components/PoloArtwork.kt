package com.poloapp.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/** Original vector illustration, inspired by the five-door red hatchback. */
@Composable
fun PoloArtwork(modifier: Modifier = Modifier) {
    Canvas(modifier.semantics { contentDescription = "Ilustración de un compacto rojo de cinco puertas" }) {
        val unit = minOf(size.width / 640f, size.height / 285f)
        scale(unit, unit, Offset.Zero) {
            fun path(block: Path.() -> Unit) = Path().apply(block)
            drawOval(Brush.radialGradient(listOf(Color.Black.copy(alpha = .5f), Color.Transparent), Offset(327f, 247f), 310f), Offset(27f, 222f), Size(595f, 53f))
            // Wheels, deliberately slightly turned toward the viewer.
            listOf(Offset(147f, 216f), Offset(502f, 201f)).forEachIndexed { i, center ->
                val r = if (i == 0) 48f else 44f
                drawCircle(Color(0xFF090B0D), r, center)
                drawCircle(Color(0xFF282D32), r - 5f, center)
                drawCircle(Brush.linearGradient(listOf(Color(0xFFB5BDC3), Color(0xFF4E5963)), center - Offset(r, r), center + Offset(r, r)), r - 13f, center)
                drawCircle(Color(0xFF191E23), r - 19f, center)
                for (spoke in 0..9) {
                    val a = Math.toRadians(spoke * 36.0 - 8.0)
                    val end = center + Offset(kotlin.math.cos(a).toFloat() * (r - 18f), kotlin.math.sin(a).toFloat() * (r - 18f))
                    drawLine(Color(0xFF9DA6AD), center, end, 5f, StrokeCap.Round)
                }
                drawCircle(Color(0xFFBEC5C9), 8f, center)
                drawCircle(Color(0xFF565F65), 4f, center)
            }
            val body = path {
                moveTo(40f, 164f); cubicTo(55f, 146f, 92f, 132f, 164f, 116f)
                lineTo(248f, 54f); cubicTo(269f, 42f, 392f, 35f, 452f, 46f)
                cubicTo(482f, 52f, 527f, 84f, 561f, 115f)
                lineTo(581f, 139f); lineTo(581f, 198f); lineTo(555f, 219f)
                lineTo(548f, 218f); cubicTo(549f, 147f, 450f, 140f, 452f, 224f)
                lineTo(203f, 247f); cubicTo(199f, 162f, 105f, 163f, 96f, 246f)
                lineTo(45f, 234f); lineTo(24f, 211f); lineTo(24f, 181f); close()
            }
            drawPath(body, Brush.linearGradient(listOf(Color(0xFFFF635E), Color(0xFFD63335), Color(0xFFA91420)), Offset(220f, 48f), Offset(330f, 253f)))
            val roof = path {
                moveTo(163f, 116f); lineTo(248f, 54f); cubicTo(279f, 47f, 359f, 42f, 394f, 46f)
                lineTo(341f, 112f); close()
            }
            drawPath(roof, Color(0xFF311C20))
            val windscreen = path { moveTo(183f, 111f); lineTo(255f, 61f); lineTo(370f, 53f); lineTo(326f, 109f); close() }
            drawPath(windscreen, Brush.linearGradient(listOf(Color(0xFF66808D), Color(0xFF26343E), Color(0xFF111C25)), Offset(221f, 40f), Offset(323f, 125f)))
            drawPath(path { moveTo(205f, 104f); lineTo(263f, 65f); lineTo(277f, 64f); lineTo(224f, 104f) }, Color.White.copy(alpha = .13f))
            val windows = path { moveTo(349f, 110f); lineTo(397f, 52f); lineTo(446f, 53f); cubicTo(475f, 61f, 516f, 87f, 539f, 111f); close() }
            drawPath(windows, Color(0xFF16232C))
            drawPath(path { moveTo(361f, 104f); lineTo(400f, 58f); lineTo(440f, 59f); lineTo(447f, 106f); close() }, Brush.linearGradient(listOf(Color(0xFF647C88), Color(0xFF263943)), Offset(396f, 53f), Offset(432f, 115f)))
            drawPath(path { moveTo(458f, 106f); lineTo(452f, 63f); cubicTo(475f, 71f, 501f, 89f, 519f, 107f); close() }, Color(0xFF314752))
            drawLine(Color(0xFFEF5E59), Offset(349f, 115f), Offset(544f, 117f), 3f)
            // Side creases and shut lines.
            drawPath(path { moveTo(351f, 115f); lineTo(334f, 220f); lineTo(441f, 211f); lineTo(449f, 117f) }, Color(0xFF72181E), style = Stroke(1.6f))
            drawPath(path { moveTo(455f, 115f); lineTo(449f, 211f) }, Color(0xFFEF7169), style = Stroke(1.1f))
            drawLine(Color(0xFFA32129), Offset(209f, 178f), Offset(570f, 146f), 2f)
            drawLine(Color(0xFFFF8E80).copy(alpha = .6f), Offset(207f, 180f), Offset(568f, 149f), 1.2f)
            drawLine(Color(0xFF8B1925), Offset(213f, 229f), Offset(445f, 208f), 5f, StrokeCap.Round)
            drawRoundRect(Color(0xFF771B23), Offset(403f, 127f), Size(27f, 5f), androidx.compose.ui.geometry.CornerRadius(3f))
            drawRoundRect(Color(0xFF771B23), Offset(500f, 121f), Size(23f, 5f), androidx.compose.ui.geometry.CornerRadius(3f))
            drawPath(path { moveTo(331f, 118f); cubicTo(350f, 101f, 365f, 110f, 367f, 125f); lineTo(348f, 133f); close() }, Color(0xFFED4342))
            drawLine(Color(0xFF271A1C), Offset(337f, 128f), Offset(353f, 132f), 5f)
            // Hood and front fascia.
            drawPath(path { moveTo(44f, 161f); lineTo(165f, 121f); lineTo(322f, 117f); lineTo(237f, 168f); close() }, Brush.linearGradient(listOf(Color(0xFFFA5551), Color(0xFFCE2934)), Offset(161f, 125f), Offset(160f, 186f)))
            drawLine(Color(0xFFFF9284).copy(alpha = .55f), Offset(64f, 158f), Offset(163f, 125f), 1.5f)
            drawPath(path { moveTo(29f, 176f); lineTo(89f, 183f); lineTo(89f, 203f); lineTo(30f, 197f); close() }, Color(0xFF24272A))
            drawPath(path { moveTo(186f, 178f); lineTo(242f, 163f); lineTo(232f, 185f); lineTo(190f, 198f); close() }, Color(0xFFDFEDF3))
            drawPath(path { moveTo(31f, 173f); lineTo(78f, 180f); lineTo(69f, 189f); lineTo(29f, 182f); close() }, Color(0xFFD6E3E7))
            drawPath(path { moveTo(83f, 181f); lineTo(182f, 180f); lineTo(184f, 198f); lineTo(84f, 202f); close() }, Color(0xFF15191C))
            drawLine(Color(0xFF808E94), Offset(88f, 185f), Offset(179f, 184f), 2f)
            drawLine(Color(0xFF667176), Offset(88f, 191f), Offset(180f, 190f), 1.2f)
            drawCircle(Color(0xFFCAD4D8), 7f, Offset(131f, 189f))
            drawCircle(Color(0xFF3A474E), 4.5f, Offset(131f, 189f))
            drawPath(path { moveTo(33f, 208f); lineTo(86f, 216f); lineTo(88f, 234f); lineTo(42f, 225f); close() }, Color(0xFF242C31))
            drawRoundRect(Color(0xFFDCE2DF), Offset(49f, 210f), Size(28f, 7f), androidx.compose.ui.geometry.CornerRadius(2f))
            drawPath(path { moveTo(49f, 235f); lineTo(91f, 243f); lineTo(93f, 248f); lineTo(48f, 240f); close() }, Color(0xFF6A1720))
            // A tiny rear light emphasizes the hatchback silhouette.
            drawPath(path { moveTo(555f, 123f); lineTo(576f, 137f); lineTo(575f, 155f); lineTo(561f, 150f); close() }, Color(0xFFEF9990))
        }
    }
}
