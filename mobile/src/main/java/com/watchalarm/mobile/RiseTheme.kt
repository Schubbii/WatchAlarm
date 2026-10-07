package com.watchalarm.mobile

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.watchalarm.core.R as CoreR

/**
 * Farben und Schriften aus dem Rise-Design (Claude Design, "UI Screens").
 *
 * Die Namen folgen den CSS-Variablen des Entwurfs (--surface, --ink2 …),
 * damit sich Code und Vorlage direkt nebeneinanderlegen lassen. Material3
 * bekommt daraus ein passendes ColorScheme; was Material nicht kennt (Drum,
 * Chip, Linien), liest der Code über [Rise.colors].
 */
@Immutable
data class RiseColors(
    val surface: Color,
    val card: Color,
    val chip: Color,
    val track: Color,
    val drum: Color,
    val ink: Color,
    val ink2: Color,
    val ink3: Color,
    val lineA: Color,
    val lineB: Color,
    val accent: Color,
    val btnBg: Color,
    val btnFg: Color,
    val onAccent: Color,
)

private val LightRiseColors = RiseColors(
    surface = Color(0xFFF8F2EC),
    card = Color(0xFFFFFFFF),
    chip = Color(0xFFF1E7DD),
    track = Color(0xFFEDE2D8),
    drum = Color(0xFFEFE4D9),
    ink = Color(0xFF1C1917),
    ink2 = Color(0xFF6E655F),
    ink3 = Color(0xFF9A918B),
    lineA = Color(0x141C1917),
    lineB = Color(0x1F1C1917),
    accent = Color(0xFFC2643F),
    btnBg = Color(0xFF1C1917),
    btnFg = Color(0xFFF8F2EC),
    onAccent = Color(0xFFFFFFFF),
)

private val DarkRiseColors = RiseColors(
    surface = Color(0xFF171513),
    card = Color(0xFF221D1A),
    chip = Color(0xFF2A2320),
    track = Color(0xFF332B26),
    drum = Color(0xFF201B18),
    ink = Color(0xFFF2EBE4),
    ink2 = Color(0xFFA79D96),
    ink3 = Color(0xFF8B817A),
    lineA = Color(0x14FFFFFF),
    lineB = Color(0x24FFFFFF),
    accent = Color(0xFFE4875D),
    btnBg = Color(0xFFF2EBE4),
    btnFg = Color(0xFF14110F),
    onAccent = Color(0xFF14110F),
)

/** Farben, die in beiden Modi gleich bleiben (Sonnenaufgang, Drum-Ziffern). */
object RiseFixed {
    val amber = Color(0xFFFFC78A)
    val sunriseTop = Color(0xFF3C2830)
    val sunriseBottom = Color(0xFFE0A98A)
    val wakeButton = Color(0xFF1A1613)
    val wakeButtonSoftText = Color(0xFF241B2C)
    val drumNear = Color(0xFFB8A899)
    val drumFar = Color(0xFFD3C6B9)
}

val DmSans = FontFamily(
    Font(CoreR.font.dm_sans_light, FontWeight.Light),
    Font(CoreR.font.dm_sans_regular, FontWeight.Normal),
    Font(CoreR.font.dm_sans_medium, FontWeight.Medium),
)

val InstrumentSerif = FontFamily(Font(CoreR.font.instrument_serif_regular, FontWeight.Normal))

/** Uhrzeiten und Walzenziffern: Serif mit gleich breiten Ziffern (tabular-nums). */
val SerifNumerals = TextStyle(fontFamily = InstrumentSerif, fontFeatureSettings = "tnum")

/** Kleine Versal-Überschrift des Entwurfs ("TONIGHT", "WAKE WINDOW"). */
val EyebrowStyle = TextStyle(
    fontFamily = DmSans,
    fontWeight = FontWeight.Light,
    fontSize = 11.sp,
    letterSpacing = 0.14.em,
)

private val LocalRiseColors = staticCompositionLocalOf { LightRiseColors }

object Rise {
    val colors: RiseColors
        @Composable get() = LocalRiseColors.current
}

/**
 * Der Entwurf setzt Fließtext in DM Sans 300 und alles Große — Uhrzeiten,
 * Überschriften — in Instrument Serif.
 */
private fun riseTypography(): Typography {
    val base = Typography()
    fun TextStyle.sans(weight: FontWeight = FontWeight.Light) =
        copy(fontFamily = DmSans, fontWeight = weight)
    fun TextStyle.serif() = copy(fontFamily = InstrumentSerif, fontWeight = FontWeight.Normal)
    return base.copy(
        displayLarge = base.displayLarge.serif(),
        displayMedium = base.displayMedium.serif(),
        displaySmall = base.displaySmall.serif(),
        headlineLarge = base.headlineLarge.serif(),
        headlineMedium = base.headlineMedium.serif(),
        headlineSmall = base.headlineSmall.serif(),
        titleLarge = base.titleLarge.serif(),
        titleMedium = base.titleMedium.sans(),
        titleSmall = base.titleSmall.sans(),
        bodyLarge = base.bodyLarge.sans(),
        bodyMedium = base.bodyMedium.sans(),
        bodySmall = base.bodySmall.sans(),
        labelLarge = base.labelLarge.sans(FontWeight.Normal),
        labelMedium = base.labelMedium.sans(FontWeight.Normal),
        labelSmall = base.labelSmall.sans(FontWeight.Normal),
    )
}

@Composable
fun RiseTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val c = if (dark) DarkRiseColors else LightRiseColors
    val base = if (dark) darkColorScheme() else lightColorScheme()
    val scheme = base.copy(
        primary = c.accent,
        onPrimary = c.onAccent,
        background = c.surface,
        onBackground = c.ink,
        surface = c.surface,
        onSurface = c.ink,
        surfaceVariant = c.chip,
        onSurfaceVariant = c.ink2,
        surfaceContainerLowest = c.card,
        surfaceContainerLow = c.card,
        surfaceContainer = c.card,
        surfaceContainerHigh = c.card,
        surfaceContainerHighest = c.card,
        secondaryContainer = c.chip,
        onSecondaryContainer = c.ink2,
        outline = c.ink3,
        outlineVariant = c.lineB,
    )
    CompositionLocalProvider(LocalRiseColors provides c) {
        MaterialTheme(colorScheme = scheme, typography = riseTypography(), content = content)
    }
}
