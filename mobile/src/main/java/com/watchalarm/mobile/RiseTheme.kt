package com.watchalarm.mobile

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.watchalarm.core.R as CoreR

/*
 * "Rise"-Design der App — dieselben Farben und Schriften wie die Produktseite
 * (index.html, aus Claude Design): warmes Creme/Tinte im Hellen, fast
 * schwarzes Braun im Dunkeln, Terrakotta als Akzent; Instrument Serif für
 * Uhrzeiten und Überschriften, DM Sans (leicht) für Text. Die Schriften
 * liegen als TTF in core/res/font, damit Handy und Uhr dieselben nutzen.
 */

/** DM Sans — Fließtext, Beschriftungen. */
internal val RiseSans = FontFamily(
    Font(CoreR.font.rise_sans_light, FontWeight.Light),
    Font(CoreR.font.rise_sans_regular, FontWeight.Normal),
    Font(CoreR.font.rise_sans_medium, FontWeight.Medium),
)

/** Instrument Serif — Uhrzeiten und Überschriften. */
internal val RiseSerif = FontFamily(
    Font(CoreR.font.rise_serif, FontWeight.Normal),
    Font(CoreR.font.rise_serif_italic, FontWeight.Normal, FontStyle.Italic),
)

/** Werte aus den CSS-Variablen der Produktseite (:root bzw. Dark-Mode). */
private object RiseColors {
    // Hell
    val Bg = Color(0xFFFAF6F1)
    val Surface = Color(0xFFFFFFFF)
    val Surface2 = Color(0xFFF1E7DD)
    val Ink = Color(0xFF1C1917)
    val Ink2 = Color(0xFF6E655F)
    val Line = Color(0xFFD9D2CC)
    val BarMuted = Color(0xFFE4D9CF)
    val Accent = Color(0xFFC2643F)
    val Accent2 = Color(0xFFA6795A)

    // Dunkel
    val DarkBg = Color(0xFF100E0D)
    val DarkSurface = Color(0xFF1A1715)
    val DarkSurface2 = Color(0xFF241E1A)
    val DarkInk = Color(0xFFF2EBE4)
    val DarkInk2 = Color(0xFFA79D96)
    val DarkLine = Color(0xFF4B433D)
    val DarkBarMuted = Color(0xFF3A332D)
    val DarkAccent = Color(0xFFE4875D)
    val DarkAccent2 = Color(0xFFD2A177)
    val DarkOnAccent = Color(0xFF14110F)
}

private val RiseLight = lightColorScheme(
    primary = RiseColors.Accent,
    onPrimary = Color.White,
    primaryContainer = RiseColors.Surface2,
    onPrimaryContainer = RiseColors.Ink,
    secondary = RiseColors.Accent2,
    onSecondary = Color.White,
    secondaryContainer = RiseColors.Surface2,
    onSecondaryContainer = RiseColors.Ink,
    tertiary = RiseColors.Accent2,
    onTertiary = Color.White,
    background = RiseColors.Bg,
    onBackground = RiseColors.Ink,
    surface = RiseColors.Bg,
    onSurface = RiseColors.Ink,
    surfaceVariant = RiseColors.Surface2,
    onSurfaceVariant = RiseColors.Ink2,
    outline = RiseColors.Line,
    outlineVariant = RiseColors.BarMuted,
    // Karten sind auf der Produktseite weiß auf Creme.
    surfaceContainerLowest = RiseColors.Surface,
    surfaceContainerLow = RiseColors.Surface,
    surfaceContainer = RiseColors.Surface,
    surfaceContainerHigh = RiseColors.Surface,
    surfaceContainerHighest = RiseColors.Surface,
    inverseSurface = RiseColors.Ink,
    inverseOnSurface = RiseColors.Bg,
    inversePrimary = RiseColors.DarkAccent,
)

private val RiseDark = darkColorScheme(
    primary = RiseColors.DarkAccent,
    onPrimary = RiseColors.DarkOnAccent,
    primaryContainer = RiseColors.DarkSurface2,
    onPrimaryContainer = RiseColors.DarkInk,
    secondary = RiseColors.DarkAccent2,
    onSecondary = RiseColors.DarkOnAccent,
    secondaryContainer = RiseColors.DarkSurface2,
    onSecondaryContainer = RiseColors.DarkInk,
    tertiary = RiseColors.DarkAccent2,
    onTertiary = RiseColors.DarkOnAccent,
    background = RiseColors.DarkBg,
    onBackground = RiseColors.DarkInk,
    surface = RiseColors.DarkBg,
    onSurface = RiseColors.DarkInk,
    surfaceVariant = RiseColors.DarkSurface2,
    onSurfaceVariant = RiseColors.DarkInk2,
    outline = RiseColors.DarkLine,
    outlineVariant = RiseColors.DarkBarMuted,
    surfaceContainerLowest = RiseColors.DarkSurface,
    surfaceContainerLow = RiseColors.DarkSurface,
    surfaceContainer = RiseColors.DarkSurface,
    surfaceContainerHigh = RiseColors.DarkSurface,
    surfaceContainerHighest = RiseColors.DarkSurface,
    inverseSurface = RiseColors.DarkInk,
    inverseOnSurface = RiseColors.DarkBg,
    inversePrimary = RiseColors.Accent,
)

private fun TextStyle.sans(weight: FontWeight = FontWeight.Light) = copy(fontFamily = RiseSans, fontWeight = weight)
private fun TextStyle.serif() = copy(fontFamily = RiseSerif, fontWeight = FontWeight.Normal)

/** Überschriften in Instrument Serif, Text in leichter DM Sans — wie auf der Seite. */
private val RiseTypography = Typography().let { base ->
    base.copy(
        displayLarge = base.displayLarge.serif(),
        displayMedium = base.displayMedium.serif(),
        displaySmall = base.displaySmall.serif(),
        headlineLarge = base.headlineLarge.serif(),
        headlineMedium = base.headlineMedium.serif(),
        headlineSmall = base.headlineSmall.serif(),
        titleLarge = base.titleLarge.serif(),
        titleMedium = base.titleMedium.sans(FontWeight.Normal),
        titleSmall = base.titleSmall.sans(FontWeight.Normal),
        bodyLarge = base.bodyLarge.sans(),
        bodyMedium = base.bodyMedium.sans(),
        bodySmall = base.bodySmall.sans(),
        labelLarge = base.labelLarge.sans(FontWeight.Normal),
        labelMedium = base.labelMedium.sans(FontWeight.Normal),
        labelSmall = base.labelSmall.sans(FontWeight.Normal),
    )
}

/** Theme für alle Screens des Handys. [dark] erzwingt Dunkel (Klingel-Screen). */
@Composable
internal fun RiseTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (dark) RiseDark else RiseLight,
        typography = RiseTypography,
        content = content,
    )
}
