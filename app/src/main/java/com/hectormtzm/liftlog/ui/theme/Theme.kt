package com.hectormtzm.liftlog.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Accent = Color(0xFF3DDC97)
private val AccentDeep = Color(0xFF0E7A52)

private val DarkColors = darkColorScheme(
    primary = Accent,
    onPrimary = Color(0xFF00391F),
    primaryContainer = Color(0xFF16422F),
    onPrimaryContainer = Color(0xFFB4F5D5),
    secondary = Color(0xFFB3CCBF),
    onSecondary = Color(0xFF1E352A),
    secondaryContainer = Color(0xFF26302B),
    onSecondaryContainer = Color(0xFFD6E5DC),
    tertiary = Color(0xFFFFC56B),
    onTertiary = Color(0xFF452B00),
    tertiaryContainer = Color(0xFF4A3812),
    onTertiaryContainer = Color(0xFFFFDEA8),
    background = Color(0xFF101413),
    onBackground = Color(0xFFE2E7E4),
    surface = Color(0xFF101413),
    onSurface = Color(0xFFE2E7E4),
    surfaceVariant = Color(0xFF2A322E),
    onSurfaceVariant = Color(0xFFA9B4AE),
    surfaceContainerLowest = Color(0xFF0B0F0E),
    surfaceContainerLow = Color(0xFF151A18),
    surfaceContainer = Color(0xFF19201D),
    surfaceContainerHigh = Color(0xFF212925),
    surfaceContainerHighest = Color(0xFF2A332F),
    outline = Color(0xFF6F7A74),
    outlineVariant = Color(0xFF333D38),
    error = Color(0xFFFF8A80),
)

private val LightColors = lightColorScheme(
    primary = AccentDeep,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC4F2DB),
    onPrimaryContainer = Color(0xFF002113),
    secondary = Color(0xFF4D6358),
    secondaryContainer = Color(0xFFDDE8E1),
    tertiary = Color(0xFF8A5A00),
    tertiaryContainer = Color(0xFFFFDEA8),
    onTertiaryContainer = Color(0xFF2C1A00),
    background = Color(0xFFF7FAF8),
    surface = Color(0xFFF7FAF8),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF1F5F2),
    surfaceContainer = Color(0xFFEBF0EC),
    surfaceContainerHigh = Color(0xFFE5EBE7),
    surfaceContainerHighest = Color(0xFFDFE5E1),
    outlineVariant = Color(0xFFC9D2CC),
)

private val AppTypography = Typography().let { t ->
    t.copy(
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = t.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

/** Large tabular digits for the rest timer. */
val TimerTextStyle = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp)

@Composable
fun LiftlogTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = AppTypography,
        content = content,
    )
}
