package com.uniplanner.app.ui.theme

import android.content.Context
import android.os.Build
import androidx.annotation.StringRes
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uniplanner.app.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** The looks a student can pick in Settings. Each one changes colours, letters and a few signature details. */
enum class AppStyle(@StringRes val label: Int, @StringRes val about: Int) {
    MARCA(R.string.style_marca, R.string.style_marca_about),
    FITAS(R.string.style_fitas, R.string.style_fitas_about),
    METRO(R.string.style_metro, R.string.style_metro_about),
    RISO(R.string.style_riso, R.string.style_riso_about),
    MARCADOR(R.string.style_marcador, R.string.style_marcador_about),
    CLASSICO(R.string.style_classico, R.string.style_classico_about),
    PAPEL(R.string.style_papel, R.string.style_papel_about),
}

/** The course palette's colours, in the order [com.uniplanner.app.ui.screens.CourseColors] lists them. */
internal val CourseInkBase = listOf(
    0xFF3949AB, 0xFF00897B, 0xFFE53935, 0xFFFB8C00,
    0xFF8E24AA, 0xFF43A047, 0xFF6D4C41, 0xFF1E88E5,
)

private val PaperInks = CourseInkBase.map { Color(it) }

/** How a course's progress is drawn: the signature detail of each look. */
enum class ProgressLook { STROKE, RIBBON, LINE, HALFTONE, HIGHLIGHT, MATERIAL, ROUNDED }

/** Everything about a look that Material's colour scheme does not carry. */
data class UniLook(
    val style: AppStyle,
    val dark: Boolean,
    /** Huge numbers: the hours studied, the clock. */
    val numbers: FontFamily,
    val numbersWeight: FontWeight,
    /** One colour per entry of the course palette, in this look's own inks. */
    val inks: List<Color>,
    val progress: ProgressLook,
    /** The selected tab and other small highlights. */
    val accent: Color,
    val onAccent: Color,
    /** Course cards look like album covers, with the music controls while studying. */
    val music: Boolean = false,
    /** The colour of the gym and health tabs. */
    val sport: Color,
)

val LocalUniLook = staticCompositionLocalOf { lookFor(AppStyle.MARCA, dark = false) }

/** The chosen look, kept on the phone. */
object StylePrefs {
    private const val PREFS = "settings"
    private const val KEY = "style"
    private val flow = MutableStateFlow<AppStyle?>(null)

    fun flow(ctx: Context): StateFlow<AppStyle?> {
        if (flow.value == null) {
            flow.value = runCatching { AppStyle.valueOf(ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)!!) }
                .getOrDefault(AppStyle.MARCA)
        }
        return flow
    }

    fun set(ctx: Context, style: AppStyle) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, style.name).apply()
        flow.value = style
    }
}

// ---------- letters ----------

val Anybody = FontFamily(
    Font(R.font.anybody_bold, FontWeight.Bold),
    Font(R.font.anybody_bold, FontWeight.ExtraBold),
    Font(R.font.anybody_display, FontWeight.Black),
)
/** Condensed italic, like the lettering on sportswear. */
val AnybodySport = FontFamily(Font(R.font.anybody_sport, FontWeight.Black, FontStyle.Italic))
val Onest = FontFamily(
    Font(R.font.onest_regular, FontWeight.Normal),
    Font(R.font.onest_medium, FontWeight.Medium),
    Font(R.font.onest_semibold, FontWeight.SemiBold),
    Font(R.font.onest_bold, FontWeight.Bold),
    Font(R.font.onest_bold, FontWeight.ExtraBold),
)
val Figtree = FontFamily(
    Font(R.font.figtree_regular, FontWeight.Normal),
    Font(R.font.figtree_regular, FontWeight.Medium),
    Font(R.font.figtree_semibold, FontWeight.SemiBold),
    Font(R.font.figtree_bold, FontWeight.Bold),
    Font(R.font.figtree_bold, FontWeight.ExtraBold),
)
val BigShoulders = FontFamily(Font(R.font.bigshoulders_black, FontWeight.Black))
val Overpass = FontFamily(
    Font(R.font.overpass_regular, FontWeight.Normal),
    Font(R.font.overpass_regular, FontWeight.Medium),
    Font(R.font.overpass_semibold, FontWeight.SemiBold),
    Font(R.font.overpass_extrabold, FontWeight.Bold),
    Font(R.font.overpass_extrabold, FontWeight.ExtraBold),
)
val Familjen = FontFamily(
    Font(R.font.familjen_regular, FontWeight.Normal),
    Font(R.font.familjen_regular, FontWeight.Medium),
    Font(R.font.familjen_semibold, FontWeight.SemiBold),
    Font(R.font.familjen_bold, FontWeight.Bold),
    Font(R.font.familjen_bold, FontWeight.ExtraBold),
)
val Schibsted = FontFamily(
    Font(R.font.schibsted_regular, FontWeight.Normal),
    Font(R.font.schibsted_medium, FontWeight.Medium),
    Font(R.font.schibsted_bold, FontWeight.SemiBold),
    Font(R.font.schibsted_bold, FontWeight.Bold),
    Font(R.font.schibsted_extrabold, FontWeight.ExtraBold),
)
/** Handwriting, for the little notes of the highlighter look. */
val Caveat = FontFamily(Font(R.font.caveat_bold, FontWeight.Bold))

private fun TextStyle.with(family: FontFamily, weight: FontWeight, spacing: Float? = null) =
    copy(fontFamily = family, fontWeight = weight, letterSpacing = spacing?.sp ?: letterSpacing)

/** A type scale in one family, with heavier headings; [display] sets the biggest titles. */
fun typographyOf(body: FontFamily, display: FontFamily = body, displayWeight: FontWeight = FontWeight.ExtraBold): Typography {
    val b = Typography()
    return Typography(
        displayLarge = b.displayLarge.with(display, displayWeight, -2f),
        displayMedium = b.displayMedium.with(display, displayWeight, -1.5f),
        displaySmall = b.displaySmall.with(display, displayWeight, -1f),
        headlineLarge = b.headlineLarge.with(body, FontWeight.ExtraBold, -0.8f),
        headlineMedium = b.headlineMedium.with(body, FontWeight.ExtraBold, -0.8f),
        headlineSmall = b.headlineSmall.with(body, FontWeight.Bold, -0.4f),
        titleLarge = b.titleLarge.with(body, FontWeight.Bold, -0.3f),
        titleMedium = b.titleMedium.with(body, FontWeight.Bold),
        titleSmall = b.titleSmall.with(body, FontWeight.Bold),
        bodyLarge = b.bodyLarge.with(body, FontWeight.Normal),
        bodyMedium = b.bodyMedium.with(body, FontWeight.Normal),
        bodySmall = b.bodySmall.with(body, FontWeight.Normal),
        labelLarge = b.labelLarge.with(body, FontWeight.SemiBold),
        labelMedium = b.labelMedium.with(body, FontWeight.Medium),
        labelSmall = b.labelSmall.with(body, FontWeight.Medium),
    )
}

/** The gym's letters: condensed italic headings and wide numbers on top of the look's own body face. */
fun Typography.sporty(): Typography {
    fun TextStyle.sport(size: Float) = copy(fontFamily = AnybodySport, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic, fontSize = size.sp, lineHeight = (size * 0.95f).sp, letterSpacing = 0.sp)
    return copy(
        displayLarge = displayLarge.sport(64f),
        displayMedium = displayMedium.sport(52f),
        displaySmall = displaySmall.sport(44f),
        headlineLarge = headlineLarge.sport(40f),
        headlineMedium = headlineMedium.sport(36f),
        headlineSmall = headlineSmall.sport(28f),
        titleLarge = titleLarge.copy(fontFamily = Anybody, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.3).sp),
    )
}

fun shapesOf(small: Int, medium: Int, large: Int) = Shapes(
    extraSmall = RoundedCornerShape((small * 0.7f).dp),
    small = RoundedCornerShape(small.dp),
    medium = RoundedCornerShape(medium.dp),
    large = RoundedCornerShape(large.dp),
    extraLarge = RoundedCornerShape((large + 6).dp),
)

// ---------- colours ----------

/** A whole Material scheme from the few colours a look decides; the rest is mixed from them. */
private fun scheme(
    dark: Boolean,
    ground: Color,
    card: Color,
    raised: Color,
    ink: Color,
    muted: Color,
    line: Color,
    primary: Color,
    onPrimary: Color,
    second: Color,
    onSecond: Color,
    mark: Color,
    onMark: Color,
    third: Color,
    inverse: Color = ink,
    onInverse: Color = ground,
    inverseAccent: Color = mark,
): ColorScheme {
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = raised,
        onPrimaryContainer = ink,
        secondary = second,
        onSecondary = onSecond,
        secondaryContainer = mark,
        onSecondaryContainer = onMark,
        tertiary = third,
        onTertiary = if (dark) Color(0xFF111111) else Color.White,
        tertiaryContainer = lerp(third, ground, 0.78f),
        onTertiaryContainer = lerp(third, ink, 0.6f),
        background = ground,
        onBackground = ink,
        surface = ground,
        onSurface = ink,
        surfaceVariant = raised,
        onSurfaceVariant = muted,
        surfaceContainerLowest = if (dark) lerp(ground, Color.Black, 0.4f) else Color.White,
        surfaceContainerLow = lerp(ground, card, 0.5f),
        surfaceContainer = card,
        surfaceContainerHigh = card,
        surfaceContainerHighest = raised,
        inverseSurface = inverse,
        inverseOnSurface = onInverse,
        inversePrimary = inverseAccent,
        outline = lerp(line, muted, 0.35f),
        outlineVariant = line,
        surfaceTint = Color.Transparent,
    )
}

private fun c(hex: Long) = Color(hex)

/** The colour scheme of a look; [ctx] lets the classic look follow the phone's wallpaper. */
fun schemeFor(style: AppStyle, dark: Boolean, ctx: Context?): ColorScheme = when (style) {
    AppStyle.MARCA -> if (dark) scheme(
        true, c(0xFF0B0B10), c(0xFF15151C), c(0xFF1E1E27), c(0xFFF4F4F7), c(0xFF9C9CAB), c(0xFF2A2A35),
        primary = c(0xFFF4F4F7), onPrimary = c(0xFF0B0B10), second = c(0xFFFF5FB0), onSecond = c(0xFF1A0010),
        mark = c(0xFFF5FF3D), onMark = c(0xFF111111), third = c(0xFF5CC8FF),
    ) else scheme(
        false, c(0xFFF7F7F9), c(0xFFFFFFFF), c(0xFFEFEFF4), c(0xFF111117), c(0xFF5E5E6C), c(0xFFE3E3EA),
        primary = c(0xFF111117), onPrimary = Color.White, second = c(0xFFE0207F), onSecond = Color.White,
        mark = c(0xFFEFFA3A), onMark = c(0xFF111111), third = c(0xFF0A84D6),
    )
    AppStyle.FITAS -> if (dark) scheme(
        true, c(0xFF0B0D22), c(0xFF151937), c(0xFF1E2347), c(0xFFEEF0FF), c(0xFFA3A8CC), c(0xFF2A2F55),
        primary = c(0xFF9EA8FF), onPrimary = c(0xFF0B0D22), second = c(0xFFFF5470), onSecond = c(0xFF2A000A),
        mark = c(0xFF4A1020), onMark = c(0xFFFFD9DF), third = c(0xFFFFC94D),
        inverse = c(0xFF2A3290), onInverse = Color.White, inverseAccent = c(0xFFFFC94D),
    ) else scheme(
        false, c(0xFFF3F4FA), c(0xFFFFFFFF), c(0xFFE6E8F4), c(0xFF121433), c(0xFF5B5F80), c(0xFFDDE0EE),
        primary = c(0xFF1C2470), onPrimary = Color.White, second = c(0xFFC8102E), onSecond = Color.White,
        mark = c(0xFFF6D3D9), onMark = c(0xFF5A0614), third = c(0xFFE0A100),
        inverse = c(0xFF1C2470), onInverse = Color.White, inverseAccent = c(0xFFFFC94D),
    )
    AppStyle.METRO -> if (dark) scheme(
        true, c(0xFF0C0C0E), c(0xFF17171A), c(0xFF222226), c(0xFFF5F5F2), c(0xFFA0A0A6), c(0xFF2C2C31),
        primary = c(0xFFFFD100), onPrimary = c(0xFF111111), second = c(0xFFFF3B4E), onSecond = Color.White,
        mark = c(0xFFFFD100), onMark = c(0xFF111111), third = c(0xFF3DA5FF),
        inverse = c(0xFFF5F5F2), onInverse = c(0xFF0C0C0E), inverseAccent = c(0xFFFFD100),
    ) else scheme(
        false, c(0xFFFFFFFF), c(0xFFF4F4F4), c(0xFFEAEAEA), c(0xFF0E0E10), c(0xFF5F6168), c(0xFFDDDDDD),
        primary = c(0xFF0E0E10), onPrimary = Color.White, second = c(0xFFE4002B), onSecond = Color.White,
        mark = c(0xFFFFD100), onMark = c(0xFF111111), third = c(0xFF0072CE),
        inverse = c(0xFF0E0E10), onInverse = Color.White, inverseAccent = c(0xFFFFD100),
    )
    AppStyle.RISO -> if (dark) scheme(
        true, c(0xFF14121C), c(0xFF1E1B28), c(0xFF282435), c(0xFFF3EFE6), c(0xFFADA8B8), c(0xFF332F40),
        primary = c(0xFFFF6FC0), onPrimary = c(0xFF1A0010), second = c(0xFF4FB4FF), onSecond = c(0xFF001A2E),
        mark = c(0xFFFFE800), onMark = c(0xFF1A1530), third = c(0xFF2FD283),
        inverse = c(0xFFFF6FC0), onInverse = c(0xFF14121C), inverseAccent = c(0xFFFFE800),
    ) else scheme(
        false, c(0xFFFCFBF8), c(0xFFFFFFFF), c(0xFFF1EFEA), c(0xFF1A1530), c(0xFF5D5770), c(0xFFE6E2DA),
        primary = c(0xFF0078BF), onPrimary = Color.White, second = c(0xFFE8338A), onSecond = Color.White,
        mark = c(0xFFFFE800), onMark = c(0xFF1A1530), third = c(0xFF00A95C),
        inverse = c(0xFFFF48B0), onInverse = c(0xFF1A1530), inverseAccent = c(0xFFFFE800),
    )
    AppStyle.MARCADOR -> if (dark) scheme(
        true, c(0xFF0F1324), c(0xFF171C33), c(0xFF20263F), c(0xFFF1F3FA), c(0xFFA2A8C2), c(0xFF2A3150),
        primary = c(0xFFFFF04D), onPrimary = c(0xFF111111), second = c(0xFFFF8AD0), onSecond = c(0xFF2A0019),
        mark = c(0xFFFFF04D), onMark = c(0xFF111111), third = c(0xFF7DF29A),
        inverse = c(0xFFF1F3FA), onInverse = c(0xFF0F1324), inverseAccent = c(0xFFFFF04D),
    ) else scheme(
        false, c(0xFFFFFFFF), c(0xFFF6F7FA), c(0xFFECEEF4), c(0xFF1B1C22), c(0xFF5E616C), c(0xFFE4E7EE),
        primary = c(0xFF1B1C22), onPrimary = Color.White, second = c(0xFFD6246E), onSecond = Color.White,
        mark = c(0xFFFFF04D), onMark = c(0xFF1B1C22), third = c(0xFF14A44D),
        inverse = c(0xFF1B1C22), onInverse = Color.White, inverseAccent = c(0xFFFFF04D),
    )
    AppStyle.CLASSICO -> when {
        ctx != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> darkColorScheme(primary = c(0xFFBAC3FF), secondary = c(0xFFC3C5DD), tertiary = c(0xFFE4BAD9))
        else -> lightColorScheme(primary = c(0xFF4F5BA8), secondary = c(0xFF5B5D72), tertiary = c(0xFF77536D))
    }
    AppStyle.PAPEL -> if (dark) PaperDark else PaperLight
}


/** The look's details for [style]. */
fun lookFor(style: AppStyle, dark: Boolean, scheme: ColorScheme? = null): UniLook = when (style) {
    AppStyle.MARCA -> UniLook(
        style, dark, Anybody, FontWeight.Black,
        inks = if (dark) listOf(0xFFB48CFF, 0xFF4DFFA6, 0xFFFF5FB0, 0xFFFF9A3D, 0xFFD77BFF, 0xFFA6FF4D, 0xFFFFC85C, 0xFF5CC8FF).map(::c)
        else listOf(0xFF9B6BFF, 0xFF00D47A, 0xFFFF3D9E, 0xFFFF8A1F, 0xFFC04DFF, 0xFF6CCB12, 0xFFF5B800, 0xFF2BB3FF).map(::c),
        progress = ProgressLook.STROKE,
        accent = if (dark) c(0xFFF5FF3D) else c(0xFF111117),
        onAccent = if (dark) c(0xFF111111) else Color.White,
        music = true,
        sport = if (dark) c(0xFFD8FF2E) else c(0xFFC6F000),
    )
    AppStyle.FITAS -> UniLook(
        style, dark, BigShoulders, FontWeight.Black,
        inks = listOf(0xFF3B4BC8, 0xFF0F8A5F, 0xFFC8102E, 0xFFE0A100, 0xFF6B3FA0, 0xFF2E9E44, 0xFF9A5B2A, 0xFF1E88E5).map(::c),
        progress = ProgressLook.RIBBON,
        accent = c(0xFFC8102E),
        onAccent = Color.White,
        sport = c(0xFFFF2E4C),
    )
    AppStyle.METRO -> UniLook(
        style, dark, Overpass, FontWeight.ExtraBold,
        inks = listOf(0xFF0039A6, 0xFF00A19A, 0xFFE4002B, 0xFFF5A800, 0xFF7A3FB0, 0xFF0A8F4E, 0xFF996633, 0xFF0072CE).map(::c),
        progress = ProgressLook.LINE,
        accent = c(0xFFFFD100),
        onAccent = c(0xFF111111),
        sport = c(0xFFFFD100),
    )
    AppStyle.RISO -> UniLook(
        style, dark, Anybody, FontWeight.Black,
        inks = listOf(0xFF0078BF, 0xFF00A95C, 0xFFFF48B0, 0xFFFF6C2F, 0xFF765BA7, 0xFF237E74, 0xFFB8A15A, 0xFF3255A4).map(::c),
        progress = ProgressLook.HALFTONE,
        accent = c(0xFFFFE800),
        onAccent = c(0xFF1A1530),
        sport = c(0xFFFF48B0),
    )
    AppStyle.MARCADOR -> UniLook(
        style, dark, Schibsted, FontWeight.ExtraBold,
        inks = listOf(0xFFA9A6FF, 0xFF7DF29A, 0xFFFF8AD0, 0xFFFFB15C, 0xFFD9A6FF, 0xFFC5F27D, 0xFFFFE36E, 0xFF8FD3FF).map(::c),
        progress = ProgressLook.HIGHLIGHT,
        accent = c(0xFFFFF04D),
        onAccent = c(0xFF111111),
        sport = c(0xFF7DF29A),
    )
    AppStyle.CLASSICO -> UniLook(
        style, dark, FontFamily.Default, FontWeight.Medium,
        inks = PaperInks,
        progress = ProgressLook.MATERIAL,
        accent = scheme?.secondaryContainer ?: c(0xFFDEE0FF),
        onAccent = scheme?.onSecondaryContainer ?: c(0xFF111111),
        sport = scheme?.primary ?: c(0xFF4F5BA8),
    )
    AppStyle.PAPEL -> UniLook(
        style, dark, Bricolage, FontWeight.ExtraBold,
        inks = PaperInks,
        progress = ProgressLook.ROUNDED,
        accent = Coral,
        onAccent = Color.White,
        sport = Coral,
    )
}

fun typographyFor(style: AppStyle): Typography = when (style) {
    AppStyle.MARCA -> typographyOf(Onest, Anybody, FontWeight.Black)
    AppStyle.FITAS -> typographyOf(Figtree, BigShoulders, FontWeight.Black)
    AppStyle.METRO -> typographyOf(Overpass)
    AppStyle.RISO -> typographyOf(Familjen, Anybody, FontWeight.Black)
    AppStyle.MARCADOR -> typographyOf(Schibsted)
    AppStyle.CLASSICO -> Typography()
    AppStyle.PAPEL -> PaperTypography
}

fun shapesFor(style: AppStyle): Shapes = when (style) {
    AppStyle.MARCA -> shapesOf(14, 20, 26)
    AppStyle.FITAS -> shapesOf(8, 12, 16)
    AppStyle.METRO -> shapesOf(6, 10, 14)
    AppStyle.RISO -> shapesOf(4, 6, 8)
    AppStyle.MARCADOR -> shapesOf(12, 16, 20)
    AppStyle.CLASSICO -> Shapes()
    AppStyle.PAPEL -> PaperShapes
}

/** The gym and health colours: deeper grounds and the look's sport colour in the lead. */
fun ColorScheme.sporty(look: UniLook): ColorScheme {
    val accent = look.sport
    val onAccent = if (accent.luminanceSafe() > 0.45f) Color(0xFF0B0B0A) else Color.White
    return if (look.dark) copy(
        background = lerp(background, Color.Black, 0.55f),
        surface = lerp(surface, Color.Black, 0.55f),
        surfaceContainerHigh = lerp(surfaceContainerHigh, Color.Black, 0.25f),
        surfaceContainer = lerp(surfaceContainer, Color.Black, 0.25f),
        inverseSurface = accent,
        inverseOnSurface = onAccent,
        inversePrimary = onAccent,
        secondary = accent,
        onSecondary = onAccent,
        secondaryContainer = lerp(accent, Color.Black, 0.7f),
        onSecondaryContainer = accent,
    ) else copy(
        background = lerp(background, Color(0xFFE4E4DC), 0.6f),
        surface = lerp(surface, Color(0xFFE4E4DC), 0.6f),
        inverseSurface = Color(0xFF0B0B0A),
        inverseOnSurface = Color.White,
        inversePrimary = accent,
        secondary = lerp(accent, Color.Black, 0.25f),
        onSecondary = onAccent,
        secondaryContainer = accent,
        onSecondaryContainer = onAccent,
    )
}

private fun Color.luminanceSafe(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue


/** A course's colour in the current look: the palette entry it came from, or the nearest one by hue. */
fun UniLook.ink(color: Long): Color {
    val i = CourseInkBase.indexOf(color)
    if (i >= 0) return inks[i % inks.size]
    if (inks === PaperInks) return Color(color)
    val target = Color(color)
    return inks[CourseInkBase.indices.minBy { hueDistance(Color(CourseInkBase[it]), target) } % inks.size]
}

private fun hueDistance(a: Color, b: Color): Float {
    fun hue(c: Color): Float {
        val max = maxOf(c.red, c.green, c.blue)
        val min = minOf(c.red, c.green, c.blue)
        val d = max - min
        if (d < 0.0001f) return 0f
        val h = when (max) {
            c.red -> ((c.green - c.blue) / d) % 6f
            c.green -> (c.blue - c.red) / d + 2f
            else -> (c.red - c.green) / d + 4f
        }
        return (h * 60f + 360f) % 360f
    }
    val d = kotlin.math.abs(hue(a) - hue(b))
    return minOf(d, 360f - d)
}
