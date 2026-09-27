package com.uniplanner.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// UniPlanner's own colours, the same on every phone: indigo, with teal and amber accents.
private val Indigo = Color(0xFF4F46E5)
private val Violet = Color(0xFF7C3AED)
private val Teal = Color(0xFF0D9488)
private val Amber = Color(0xFFF59E0B)

private val Light = lightColorScheme(
    primary = Indigo,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0E7FF),
    onPrimaryContainer = Color(0xFF1E1B4B),
    secondary = Teal,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCCFBF1),
    onSecondaryContainer = Color(0xFF042F2E),
    tertiary = Amber,
    tertiaryContainer = Color(0xFFFEF3C7),
    onTertiaryContainer = Color(0xFF451A03),
    background = Color(0xFFF6F6FB),
    onBackground = Color(0xFF14142B),
    surface = Color(0xFFF6F6FB),
    onSurface = Color(0xFF14142B),
    surfaceVariant = Color(0xFFE8E8F2),
    onSurfaceVariant = Color(0xFF5B5B76),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFBFBFE),
    surfaceContainer = Color(0xFFF0F0F8),
    surfaceContainerHigh = Color.White,
    surfaceContainerHighest = Color.White,
    outline = Color(0xFFC7C7D9),
    outlineVariant = Color(0xFFE3E3EE),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFA5B4FC),
    onPrimary = Color(0xFF1E1B4B),
    primaryContainer = Color(0xFF3730A3),
    onPrimaryContainer = Color(0xFFE0E7FF),
    secondary = Color(0xFF5EEAD4),
    onSecondary = Color(0xFF042F2E),
    secondaryContainer = Color(0xFF115E59),
    onSecondaryContainer = Color(0xFFCCFBF1),
    tertiary = Color(0xFFFCD34D),
    tertiaryContainer = Color(0xFF78350F),
    onTertiaryContainer = Color(0xFFFEF3C7),
    background = Color(0xFF0E0E1A),
    onBackground = Color(0xFFE8E8F5),
    surface = Color(0xFF0E0E1A),
    onSurface = Color(0xFFE8E8F5),
    surfaceVariant = Color(0xFF2A2A3D),
    onSurfaceVariant = Color(0xFFB4B4CC),
    surfaceContainerLowest = Color(0xFF09090F),
    surfaceContainerLow = Color(0xFF14142A),
    surfaceContainer = Color(0xFF181830),
    surfaceContainerHigh = Color(0xFF1E1E38),
    surfaceContainerHighest = Color(0xFF26264A),
    outline = Color(0xFF4A4A66),
    outlineVariant = Color(0xFF2E2E48),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

private val base = Typography()
private val AppTypography = base.copy(
    displayMedium = base.displayMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-1).sp),
    displaySmall = base.displaySmall.copy(fontWeight = FontWeight.Bold),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
)

/** The gradient used on the big cards at the top of the main screens. */
val HeroGradient = Brush.linearGradient(listOf(Indigo, Violet))
val GymGradient = Brush.linearGradient(listOf(Teal, Color(0xFF0EA5E9)))

@Composable
fun UniPlannerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        shapes = AppShapes,
        typography = AppTypography,
        content = content,
    )
}
