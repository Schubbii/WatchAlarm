package com.watchalarm.wear

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Colors
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.TimeTextDefaults
import androidx.wear.compose.material.Typography
import com.watchalarm.core.R as CoreR
import java.util.Locale

/**
 * Farben und Schriften der Uhr aus dem Rise-Design (Claude Design, "Watch —
 * Wear OS"). Die Uhr ist dort immer dunkel: fast schwarzes Zifferblatt,
 * Bernstein als einzige Akzentfarbe.
 *
 * Der Entwurf zeichnet die Uhr rechteckig, die echte ist rund — deshalb nur
 * Farben, Schriften und Formen übernehmen, keine Eckpositionen: Inhalte
 * bleiben zentriert und in ScalingLazyColumns, die mit dem Kreis umgehen.
 */
object RiseWear {
    val background = Color(0xFF0C0A09)
    val amber = Color(0xFFFFC78A)
    val sunLight = Color(0xFFFFD9A0)
    val sunDeep = Color(0xFFF0855F)
    val buttonInk = Color(0xFF1A1613)

    /** Fläche der Listeneinträge: rgba(255,255,255,.08) aus dem Entwurf. */
    val item = Color.White.copy(alpha = 0.08f)

    /** Hintergrund der Walze, deckend vorgerechnet (Weiß 5 % auf [background]). */
    val drum = Color(0xFF181615)

    val textDim = Color.White.copy(alpha = 0.45f)
    val textFaint = Color.White.copy(alpha = 0.3f)

    /** Pflaumenschimmer am unteren Rand ("Sleep face"). */
    val plumGlow = Color(0xFFB98CC9).copy(alpha = 0.18f)

    /** Warmes Glühen hinter der Welle ("Silent wake"). */
    val emberGlow = Color(0xFFF0855F).copy(alpha = 0.14f)
}

val DmSans = FontFamily(
    Font(CoreR.font.dm_sans_light, FontWeight.Light),
    Font(CoreR.font.dm_sans_regular, FontWeight.Normal),
    Font(CoreR.font.dm_sans_medium, FontWeight.Medium),
)

val InstrumentSerif = FontFamily(Font(CoreR.font.instrument_serif_regular, FontWeight.Normal))

/** Kleine Versal-Überschrift des Entwurfs ("SLEEP MODE", "EARLIEST WAKE"). */
val EyebrowStyle = TextStyle(
    fontFamily = DmSans,
    fontWeight = FontWeight.Normal,
    fontSize = 11.sp,
    letterSpacing = 0.14.em,
    color = Color.White.copy(alpha = 0.4f),
)

private val RiseWearColors = Colors(
    primary = RiseWear.amber,
    primaryVariant = RiseWear.sunDeep,
    secondary = RiseWear.amber,
    secondaryVariant = RiseWear.sunDeep,
    background = RiseWear.background,
    surface = Color(0xFF1F1D1C),
    onPrimary = RiseWear.buttonInk,
    onSecondary = RiseWear.buttonInk,
    onBackground = Color.White,
    onSurface = Color.White,
    onSurfaceVariant = Color.White.copy(alpha = 0.6f),
)

@Composable
fun RiseWearTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colors = RiseWearColors,
        typography = Typography(defaultFontFamily = DmSans),
        content = content,
    )
}

/**
 * Weiches Glühen als radialer Verlauf, wie die `radial-gradient`-Flächen des
 * Entwurfs. Mittelpunkt und Radius relativ zur Fläche, damit es auf jeder
 * Displaygröße gleich sitzt.
 */
fun Modifier.riseGlow(color: Color, centerY: Float, radius: Float): Modifier = drawBehind {
    drawRect(
        Brush.radialGradient(
            colors = listOf(color, Color.Transparent),
            center = Offset(size.width / 2, size.height * centerY),
            radius = size.minDimension * radius,
        )
    )
}

/** Uhrzeiten und Walzenziffern: Serif mit gleich breiten Ziffern (tabular-nums). */
val SerifNumerals = TextStyle(fontFamily = InstrumentSerif, fontFeatureSettings = "tnum")

/** Listen- und Editorüberschrift im Stil des Entwurfs: klein, gesperrt, Versalien. */
@Composable
fun Eyebrow(text: String) {
    Text(text.uppercase(Locale.getDefault()), style = EyebrowStyle, textAlign = TextAlign.Center)
}

/** Systemzeit am oberen Rand, gedimmt wie die Kopfzeilen des Entwurfs. */
@Composable
fun RiseTimeText() {
    TimeText(timeTextStyle = TimeTextDefaults.timeTextStyle(color = RiseWear.textDim))
}
