package com.algoarch.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.algoarch.app.data.Track

/** "Night blueprint" palette: deep ink, cyan for algorithms, amber for architecture. */
object AA {
    val Ink = Color(0xFF0B1020)
    val Panel = Color(0xFF141B33)
    val PanelHigh = Color(0xFF1C2545)
    val Line = Color(0xFF2A3560)
    val Text = Color(0xFFE7ECFF)
    val Muted = Color(0xFF8D97C2)
    val Algo = Color(0xFF5EEAD4)
    val Arch = Color(0xFFFBBF24)
    val Good = Color(0xFF4ADE80)
    val Bad = Color(0xFFF87171)
    val Warn = Color(0xFFFBBF24)
    val Xp = Color(0xFFA78BFA)
    val Heart = Color(0xFFFB7185)
    val Fire = Color(0xFFFB923C)
}

val Track.accent: Color get() = if (this == Track.ALGO) AA.Algo else AA.Arch

val MonoStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 19.sp)

private val base = Typography()
private val AppTypography = Typography(
    headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.Black),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.Bold),
)

@Composable
fun AlgoArchTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = AA.Algo,
            onPrimary = AA.Ink,
            secondary = AA.Arch,
            onSecondary = AA.Ink,
            background = AA.Ink,
            onBackground = AA.Text,
            surface = AA.Panel,
            onSurface = AA.Text,
            surfaceVariant = AA.PanelHigh,
            onSurfaceVariant = AA.Muted,
            surfaceContainer = AA.Panel,
            outline = AA.Line,
            outlineVariant = AA.Line,
            error = AA.Bad,
        ),
        typography = AppTypography,
        content = content,
    )
}
