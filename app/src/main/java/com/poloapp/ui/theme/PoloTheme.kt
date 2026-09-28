package com.poloapp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import com.poloapp.R
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val PoloRed = Color(0xFFFF655F)
val PoloMint = Color(0xFF97D9B9)
val PoloAmber = Color(0xFFF2C078)

private val DarkColors = darkColorScheme(
    primary = PoloRed, onPrimary = Color(0xFF371412), primaryContainer = Color(0xFF422320),
    onPrimaryContainer = Color(0xFFFFC5C0), secondary = PoloMint, onSecondary = Color(0xFF102B21),
    secondaryContainer = Color(0xFF20342C), onSecondaryContainer = Color(0xFFBDEFD6),
    tertiary = PoloAmber, background = Color(0xFF111315), onBackground = Color(0xFFF2F0EB),
    surface = Color(0xFF191C1F), onSurface = Color(0xFFF2F0EB),
    surfaceVariant = Color(0xFF272B2F), onSurfaceVariant = Color(0xFFA9AFB5),
    outline = Color(0xFF53585F), outlineVariant = Color(0xFF30353A),
    error = Color(0xFFFFB4AB), errorContainer = Color(0xFF4D2421)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFFB93432), onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE3DE), onPrimaryContainer = Color(0xFF6E1719),
    secondary = Color(0xFF2E7053), onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCEFE3), onSecondaryContainer = Color(0xFF173C29),
    tertiary = Color(0xFF916020), background = Color(0xFFF5F3EE), onBackground = Color(0xFF202427),
    surface = Color(0xFFFFFEFA), onSurface = Color(0xFF202427),
    surfaceVariant = Color(0xFFE9E8E3), onSurfaceVariant = Color(0xFF656A6C),
    outline = Color(0xFF8B908F), outlineVariant = Color(0xFFDEDFD8)
)

private val BodyFont = FontFamily(Font(R.font.manrope))
private val DisplayFont = FontFamily(Font(R.font.space_grotesk))

private val PoloTypography = Typography(
    displayLarge = TextStyle(fontFamily = DisplayFont, fontWeight = FontWeight.Light, fontSize = 56.sp, letterSpacing = (-2.5).sp),
    displayMedium = TextStyle(fontFamily = DisplayFont, fontWeight = FontWeight.Light, fontSize = 44.sp, letterSpacing = (-1.8).sp),
    displaySmall = TextStyle(fontFamily = DisplayFont, fontWeight = FontWeight.Medium, fontSize = 36.sp, letterSpacing = (-1.2).sp),
    headlineLarge = TextStyle(fontFamily = DisplayFont, fontWeight = FontWeight.SemiBold, fontSize = 32.sp, letterSpacing = (-1).sp),
    headlineMedium = TextStyle(fontFamily = DisplayFont, fontWeight = FontWeight.SemiBold, fontSize = 27.sp, letterSpacing = (-0.8).sp),
    headlineSmall = TextStyle(fontFamily = DisplayFont, fontWeight = FontWeight.Medium, fontSize = 23.sp, letterSpacing = (-0.5).sp),
    titleLarge = TextStyle(fontFamily = BodyFont, fontWeight = FontWeight.Medium, fontSize = 20.sp, letterSpacing = (-0.4).sp),
    titleMedium = TextStyle(fontFamily = BodyFont, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 23.sp),
    titleSmall = TextStyle(fontFamily = BodyFont, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = BodyFont, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 25.sp),
    bodyMedium = TextStyle(fontFamily = BodyFont, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontFamily = BodyFont, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = BodyFont, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = BodyFont, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 17.sp),
    labelSmall = TextStyle(fontFamily = BodyFont, fontWeight = FontWeight.SemiBold, fontSize = 10.sp, letterSpacing = 1.4.sp)
)

@Composable
fun PoloTheme(mode: String = "dark", content: @Composable () -> Unit) {
    val dark = when (mode) { "light" -> false; "system" -> isSystemInDarkTheme(); else -> true }
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, typography = PoloTypography, content = content)
}
