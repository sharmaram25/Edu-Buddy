package com.edubuddy.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.edubuddy.app.R
import com.edubuddy.app.data.prefs.ThemeMode

// ---- Brand ---------------------------------------------------------------------------------
// "Ink & Ember". Warm paper and near-black ink carry the interface; a single ember accent marks
// the present moment (the logo's dot, "now", today). Colour is otherwise reserved for meaning:
// subjects, money categories and status.

object Brand {
    val Ink = Color(0xFF131312)
    val Paper = Color(0xFFF6F5F1)
    val Ember = Color(0xFFF2542D)
    val EmberLight = Color(0xFFFF7A55)
}

private val LightColors = lightColorScheme(
    primary = Brand.Ink,
    onPrimary = Brand.Paper,
    primaryContainer = Color(0xFFE9E7E1),
    onPrimaryContainer = Brand.Ink,
    secondary = Color(0xFF4A4945),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE6E4DE),
    onSecondaryContainer = Brand.Ink,
    tertiary = Brand.Ember,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE3DA),
    onTertiaryContainer = Color(0xFF5A1606),
    error = Color(0xFFC4321F),
    onError = Color.White,
    errorContainer = Color(0xFFFCE3DE),
    onErrorContainer = Color(0xFF4A0E05),
    background = Brand.Paper,
    onBackground = Brand.Ink,
    surface = Brand.Paper,
    onSurface = Brand.Ink,
    surfaceVariant = Color(0xFFE9E7E1),
    onSurfaceVariant = Color(0xFF6E6C66),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF1F0EB),
    surfaceContainer = Color(0xFFECEAE5),
    surfaceContainerHigh = Color(0xFFE6E4DF),
    surfaceContainerHighest = Color(0xFFDFDDD7),
    outline = Color(0xFF9A9892),
    outlineVariant = Color(0xFFE2E0DA),
    inverseSurface = Brand.Ink,
    inverseOnSurface = Brand.Paper,
    inversePrimary = Color(0xFFEDEBE6),
    scrim = Color(0xFF000000),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFF1EFEA),
    onPrimary = Brand.Ink,
    primaryContainer = Color(0xFF2A2A28),
    onPrimaryContainer = Color(0xFFF1EFEA),
    secondary = Color(0xFFC9C7C1),
    onSecondary = Brand.Ink,
    secondaryContainer = Color(0xFF2C2C2A),
    onSecondaryContainer = Color(0xFFF1EFEA),
    tertiary = Brand.EmberLight,
    onTertiary = Color(0xFF3A0D02),
    tertiaryContainer = Color(0xFF4A1C10),
    onTertiaryContainer = Color(0xFFFFDBD0),
    error = Color(0xFFFF9C8A),
    onError = Color(0xFF4A0E05),
    errorContainer = Color(0xFF4F1A12),
    onErrorContainer = Color(0xFFFFDAD3),
    background = Color(0xFF0C0C0B),
    onBackground = Color(0xFFEDEBE6),
    surface = Color(0xFF0C0C0B),
    onSurface = Color(0xFFEDEBE6),
    surfaceVariant = Color(0xFF242422),
    onSurfaceVariant = Color(0xFF9F9D97),
    // Cards use surfaceContainerLowest, so in dark mode it sits one step above the background.
    surfaceContainerLowest = Color(0xFF161615),
    surfaceContainerLow = Color(0xFF141413),
    surfaceContainer = Color(0xFF1C1C1B),
    surfaceContainerHigh = Color(0xFF242423),
    surfaceContainerHighest = Color(0xFF2E2E2C),
    outline = Color(0xFF6E6C67),
    outlineVariant = Color(0xFF272725),
    inverseSurface = Color(0xFFEDEBE6),
    inverseOnSurface = Brand.Ink,
    inversePrimary = Brand.Ink,
    scrim = Color(0xFF000000),
)

// ---- Semantic colours not covered by Material ---------------------------------------------

@Immutable
data class ExtraColors(
    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val danger: Color,
    val dangerContainer: Color,
    val income: Color,
    val expense: Color,
    /** The one high-emphasis surface per screen (Today's "next up", the money summary). */
    val hero: Color,
    val onHero: Color,
    val onHeroMuted: Color,
    /** A track/hairline drawn on top of [hero]. */
    val heroTrack: Color,
    /** Ember: "now", today, the logo's dot. Use sparingly. */
    val accent: Color,
    val hairline: Color,
)

private val LightExtra = ExtraColors(
    success = Color(0xFF1F8A5B), onSuccess = Color.White, successContainer = Color(0xFFE0F1E7),
    warning = Color(0xFFB7791F), warningContainer = Color(0xFFF8ECD6),
    danger = Color(0xFFC4321F), dangerContainer = Color(0xFFFCE3DE),
    income = Color(0xFF1F8A5B), expense = Brand.Ink,
    hero = Brand.Ink, onHero = Brand.Paper, onHeroMuted = Color(0xFFA3A19B), heroTrack = Color(0xFF34332F),
    accent = Brand.Ember,
    hairline = Color(0xFFE2E0DA),
)

private val DarkExtra = ExtraColors(
    success = Color(0xFF5CC48F), onSuccess = Color(0xFF052414), successContainer = Color(0xFF13291D),
    warning = Color(0xFFE2B04F), warningContainer = Color(0xFF2E2310),
    danger = Color(0xFFFF8A75), dangerContainer = Color(0xFF3A1610),
    income = Color(0xFF5CC48F), expense = Color(0xFFEDEBE6),
    hero = Color(0xFF1D1D1B), onHero = Color(0xFFF1EFEA), onHeroMuted = Color(0xFF8E8C86), heroTrack = Color(0xFF34332F),
    accent = Brand.EmberLight,
    hairline = Color(0xFF272725),
)

val LocalExtraColors = staticCompositionLocalOf { LightExtra }

object EduTheme {
    val extra: ExtraColors @Composable get() = LocalExtraColors.current
}

/**
 * Eight subject colours, tuned to similar lightness and chroma so a timetable reads as one
 * palette rather than a rainbow. The index is persisted, so order must never change.
 */
object SubjectPalette {
    val colors = listOf(
        Color(0xFF5468D4), // indigo
        Color(0xFFD5653E), // terracotta
        Color(0xFF22957F), // teal
        Color(0xFFC9547A), // rose
        Color(0xFF3B87C9), // sky
        Color(0xFFB98A25), // ochre
        Color(0xFF8460C2), // plum
        Color(0xFF5F8F45), // moss
    )
    val names = listOf("Indigo", "Terracotta", "Teal", "Rose", "Sky", "Ochre", "Plum", "Moss")
    fun color(index: Int): Color = colors[Math.floorMod(index, colors.size)]
    fun colorArgb(index: Int): Int = color(index).toArgb()
}

// ---- Type ----------------------------------------------------------------------------------

private fun jakarta(weight: Int) = Font(
    R.font.jakarta,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val Jakarta = FontFamily(
    jakarta(400), jakarta(500), jakarta(600), jakarta(700),
)

private val base = Typography()

// Tabular figures everywhere: times, amounts and percentages line up in lists and don't jitter
// when a countdown ticks.
private fun TextStyle.j(weight: Int, size: Int, line: Int, tracking: Double) = copy(
    fontFamily = Jakarta,
    fontWeight = FontWeight(weight),
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = tracking.sp,
    fontFeatureSettings = "tnum",
)

val EduTypography = Typography(
    displayLarge = base.displayLarge.j(600, 52, 56, -1.6),
    displayMedium = base.displayMedium.j(600, 42, 48, -1.2),
    displaySmall = base.displaySmall.j(600, 34, 40, -0.9),
    headlineLarge = base.headlineLarge.j(600, 30, 36, -0.7),
    headlineMedium = base.headlineMedium.j(600, 26, 32, -0.5),
    headlineSmall = base.headlineSmall.j(600, 22, 28, -0.3),
    titleLarge = base.titleLarge.j(600, 19, 24, -0.2),
    titleMedium = base.titleMedium.j(600, 16, 22, -0.1),
    titleSmall = base.titleSmall.j(600, 14, 20, 0.0),
    bodyLarge = base.bodyLarge.j(400, 16, 24, 0.0),
    bodyMedium = base.bodyMedium.j(400, 14, 20, 0.0),
    bodySmall = base.bodySmall.j(400, 12, 16, 0.1),
    labelLarge = base.labelLarge.j(500, 14, 20, 0.0),
    labelMedium = base.labelMedium.j(500, 12, 16, 0.1),
    labelSmall = base.labelSmall.j(600, 11, 14, 0.6),
)

val EduShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(26.dp),
)

@Composable
fun EduBuddyTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val dynamic = dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val scheme: ColorScheme = when {
        dynamic -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    val base = if (dark) DarkExtra else LightExtra
    // With wallpaper colours on, the hero follows the system palette instead of ink.
    val extra = if (dynamic) base.copy(
        hero = scheme.primary, onHero = scheme.onPrimary, onHeroMuted = scheme.onPrimary.copy(alpha = 0.7f),
        heroTrack = scheme.onPrimary.copy(alpha = 0.2f), accent = scheme.tertiary, hairline = scheme.outlineVariant,
    ) else base

    CompositionLocalProvider(LocalExtraColors provides extra) {
        MaterialTheme(colorScheme = scheme, typography = EduTypography, shapes = EduShapes, content = content)
    }
}
