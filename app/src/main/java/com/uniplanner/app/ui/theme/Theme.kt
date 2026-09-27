package com.uniplanner.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uniplanner.app.R

// UniPlanner's look: warm paper, dark ink and one coral accent, the same on every phone.
val Paper = Color(0xFFF3F0E8)
val Ink = Color(0xFF17161B)
val Coral = Color(0xFFC94220)
private val DarkPaper = Color(0xFF121114)
private val LightInk = Color(0xFFEDE9DF)

private val Light = lightColorScheme(
    primary = Ink,
    onPrimary = Paper,
    primaryContainer = Color(0xFFE9E4D8),
    onPrimaryContainer = Ink,
    secondary = Coral,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFBE3DA),
    onSecondaryContainer = Color(0xFF5A1C0B),
    tertiary = Color(0xFF2440B8),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFDCE3FC),
    onTertiaryContainer = Color(0xFF0E1C5C),
    background = Paper,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = Color(0xFFE9E4D8),
    onSurfaceVariant = Color(0xFF6B675E),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF7F5EF),
    surfaceContainer = Color(0xFFF0ECE2),
    surfaceContainerHigh = Color(0xFFFBFAF6),
    surfaceContainerHighest = Color(0xFFFBFAF6),
    inverseSurface = Ink,
    inverseOnSurface = Paper,
    inversePrimary = Color(0xFFF07A55),
    outline = Color(0xFFCFC9BB),
    outlineVariant = Color(0xFFE4DFD3),
)

private val Dark = darkColorScheme(
    primary = LightInk,
    onPrimary = DarkPaper,
    primaryContainer = Color(0xFF2C2A31),
    onPrimaryContainer = LightInk,
    secondary = Color(0xFFF07A55),
    onSecondary = Color(0xFF2A0E05),
    secondaryContainer = Color(0xFF4A2217),
    onSecondaryContainer = Color(0xFFFFDBCF),
    tertiary = Color(0xFF7D93FF),
    onTertiary = Color(0xFF0E1C5C),
    tertiaryContainer = Color(0xFF26315E),
    onTertiaryContainer = Color(0xFFDCE3FC),
    background = DarkPaper,
    onBackground = LightInk,
    surface = DarkPaper,
    onSurface = LightInk,
    surfaceVariant = Color(0xFF2C2A31),
    onSurfaceVariant = Color(0xFFA29D92),
    surfaceContainerLowest = Color(0xFF0B0A0D),
    surfaceContainerLow = Color(0xFF17161B),
    surfaceContainer = Color(0xFF1A191E),
    surfaceContainerHigh = Color(0xFF1C1B20),
    surfaceContainerHighest = Color(0xFF232228),
    inverseSurface = LightInk,
    inverseOnSurface = Ink,
    inversePrimary = Coral,
    outline = Color(0xFF4A4750),
    outlineVariant = Color(0xFF2C2A31),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(22.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

val Bricolage = FontFamily(
    Font(R.font.bricolage_regular, FontWeight.Normal),
    Font(R.font.bricolage_medium, FontWeight.Medium),
    Font(R.font.bricolage_bold, FontWeight.SemiBold),
    Font(R.font.bricolage_bold, FontWeight.Bold),
    Font(R.font.bricolage_extrabold, FontWeight.ExtraBold),
)

/** For clocks and numbers that should not jump around as they change. */
val Mono = FontFamily(Font(R.font.geist_mono_medium, FontWeight.Medium))

private fun TextStyle.brand(weight: FontWeight, spacing: Float? = null) =
    copy(fontFamily = Bricolage, fontWeight = weight, letterSpacing = spacing?.sp ?: letterSpacing)

private val base = Typography()
private val AppTypography = Typography(
    displayLarge = base.displayLarge.brand(FontWeight.ExtraBold, -2f),
    displayMedium = base.displayMedium.brand(FontWeight.ExtraBold, -1.5f),
    displaySmall = base.displaySmall.brand(FontWeight.ExtraBold, -1f),
    headlineLarge = base.headlineLarge.brand(FontWeight.ExtraBold, -0.8f),
    headlineMedium = base.headlineMedium.brand(FontWeight.ExtraBold, -0.8f),
    headlineSmall = base.headlineSmall.brand(FontWeight.Bold, -0.4f),
    titleLarge = base.titleLarge.brand(FontWeight.Bold, -0.3f),
    titleMedium = base.titleMedium.brand(FontWeight.Bold),
    titleSmall = base.titleSmall.brand(FontWeight.Bold),
    bodyLarge = base.bodyLarge.brand(FontWeight.Normal),
    bodyMedium = base.bodyMedium.brand(FontWeight.Normal),
    bodySmall = base.bodySmall.brand(FontWeight.Normal),
    labelLarge = base.labelLarge.brand(FontWeight.SemiBold),
    labelMedium = base.labelMedium.brand(FontWeight.Medium),
    labelSmall = base.labelSmall.brand(FontWeight.Medium),
)

/** The soft fill for a course's card, from the course's own colour. */
@Composable
@ReadOnlyComposable
fun courseTint(color: Long): Color {
    val c = Color(color)
    val bg = MaterialTheme.colorScheme.background
    return if (bg.luminance() > 0.5f) lerp(c, Color.White, 0.8f) else lerp(c, bg, 0.72f)
}

/** Text and bars on a course's card: a deep shade in light mode, a light one in dark mode. */
@Composable
@ReadOnlyComposable
fun courseInk(color: Long): Color {
    val c = Color(color)
    return if (MaterialTheme.colorScheme.background.luminance() > 0.5f) lerp(c, Color.Black, 0.35f) else lerp(c, Color.White, 0.45f)
}

@Composable
fun UniPlannerTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) Dark else Light,
        shapes = AppShapes,
        typography = AppTypography,
        content = content,
    )
}
