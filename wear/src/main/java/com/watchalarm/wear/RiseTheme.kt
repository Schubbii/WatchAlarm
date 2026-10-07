package com.watchalarm.wear

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.wear.compose.material.Colors
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Typography
import com.watchalarm.core.R as CoreR

/*
 * "Rise"-Design auf der Uhr — die dunkle Variante der Produktseite
 * (index.html): Terrakotta-Akzent, warme Grautöne, Instrument Serif für
 * Uhrzeiten, DM Sans für Text. Hintergrund bleibt reines Schwarz: auf
 * OLED-Uhren spart das Akku und ist nachts am ruhigsten.
 */

internal val RiseSans = FontFamily(
    Font(CoreR.font.rise_sans_light, FontWeight.Light),
    Font(CoreR.font.rise_sans_regular, FontWeight.Normal),
    Font(CoreR.font.rise_sans_medium, FontWeight.Medium),
)

internal val RiseSerif = FontFamily(Font(CoreR.font.rise_serif, FontWeight.Normal))

private val RiseWatchColors = Colors(
    primary = Color(0xFFE4875D),
    primaryVariant = Color(0xFFC2643F),
    secondary = Color(0xFFD2A177),
    secondaryVariant = Color(0xFFA6795A),
    background = Color.Black,
    surface = Color(0xFF241E1A),
    onPrimary = Color(0xFF14110F),
    onSecondary = Color(0xFF14110F),
    onBackground = Color(0xFFF2EBE4),
    onSurface = Color(0xFFF2EBE4),
    onSurfaceVariant = Color(0xFFA79D96),
)

private fun TextStyle.serif() = copy(fontFamily = RiseSerif, fontWeight = FontWeight.Normal)

private val RiseWatchTypography = Typography(defaultFontFamily = RiseSans).let { base ->
    base.copy(
        display1 = base.display1.serif(),
        display2 = base.display2.serif(),
        display3 = base.display3.serif(),
        title1 = base.title1.serif(),
    )
}

@Composable
internal fun RiseTheme(content: @Composable () -> Unit) {
    MaterialTheme(colors = RiseWatchColors, typography = RiseWatchTypography, content = content)
}
