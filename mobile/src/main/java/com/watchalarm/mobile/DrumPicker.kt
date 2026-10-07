package com.watchalarm.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Höhe einer Zeile der Walze; fünf Zeilen ergeben die 212 dp des Entwurfs. */
private val DRUM_ROW_HEIGHT = 42.dp
private const val DRUM_VISIBLE_ROWS = 5
private const val DRUM_CENTER_ROW = DRUM_VISIBLE_ROWS / 2

/**
 * So oft wird eine umlaufende Spalte virtuell hintereinandergehängt. Eine
 * LazyColumn kennt kein echtes Endlos-Scrollen; bei 1000 Runden mit Start in
 * der Mitte kommt beim Drehen niemand ans Ende.
 */
private const val DRUM_LOOPS = 1000

/**
 * Das Gehäuse der Uhrzeit-Walze ("Spin it like a clock radio"): eingelassene
 * Trommel, weißes Auswahlfeld in der Mitte, oben und unten ausgeblendet.
 * Die Spalten kommen als [content] herein.
 */
@Composable
fun DrumFrame(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    val colors = Rise.colors
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(DRUM_ROW_HEIGHT * DRUM_VISIBLE_ROWS)
            .clip(shape)
            .background(colors.drum)
            .border(1.dp, colors.lineA, shape),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 10.dp)
                .fillMaxWidth()
                .height(52.dp)
                .shadow(6.dp, RoundedCornerShape(14.dp), ambientColor = Color(0x2E3C2314), spotColor = Color(0x2E3C2314))
                .background(colors.card, RoundedCornerShape(14.dp)),
        )
        Row(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
        // Ausblenden oben und unten. Reine Zeichnung, nimmt keine Berührung
        // an — gewischt wird trotzdem auf der ganzen Höhe.
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(DRUM_ROW_HEIGHT * DRUM_VISIBLE_ROWS * 0.3f)
                .background(Brush.verticalGradient(0.3f to colors.drum, 1f to Color.Transparent)),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(DRUM_ROW_HEIGHT * DRUM_VISIBLE_ROWS * 0.3f)
                .background(Brush.verticalGradient(0f to Color.Transparent, 0.7f to colors.drum)),
        )
    }
}

/**
 * Eine Spalte der Walze. Rastet immer auf genau einem Wert ein und meldet ihn
 * über [onSelected]; ein Tipp auf einen Nachbarwert dreht ihn in die Mitte.
 *
 * [wrap]: Stunden und Minuten laufen rund (nach 59 kommt 00), AM/PM nicht.
 */
@Composable
fun RowScope.DrumColumn(
    count: Int,
    initial: Int,
    onSelected: (Int) -> Unit,
    contentDescription: String,
    label: (Int) -> String,
    wrap: Boolean = true,
    weight: Float = 1f,
) {
    val colors = Rise.colors
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val rowPx = with(density) { DRUM_ROW_HEIGHT.toPx() }

    // Nicht umlaufend: je zwei leere Zeilen davor und dahinter, damit auch
    // der erste und der letzte Wert in die Mitte rücken können.
    val itemCount = if (wrap) count * DRUM_LOOPS else count + 2 * DRUM_CENTER_ROW
    val firstIndexForValue: (Int) -> Int = { value ->
        if (wrap) (DRUM_LOOPS / 2) * count + value - DRUM_CENTER_ROW else value
    }
    val state = rememberLazyListState(initialFirstVisibleItemIndex = firstIndexForValue(initial))
    val fling = rememberSnapFlingBehavior(lazyListState = state)

    // Die Zeile, die gerade unter dem Auswahlfeld liegt — auch mitten im
    // Wischen, damit die große Ziffer mitläuft und nicht erst beim Einrasten.
    val centerIndex by remember {
        derivedStateOf {
            state.firstVisibleItemIndex + DRUM_CENTER_ROW +
                if (state.firstVisibleItemScrollOffset >= rowPx / 2) 1 else 0
        }
    }
    val valueAt: (Int) -> Int? = { index ->
        if (wrap) index.mod(count)
        else (index - DRUM_CENTER_ROW).takeIf { it in 0 until count }
    }

    val currentOnSelected by rememberUpdatedState(onSelected)
    LaunchedEffect(state) {
        snapshotFlow { centerIndex }.collect { index ->
            valueAt(index)?.let { currentOnSelected(it) }
        }
    }

    // Ziffern in dp statt sp: Die Zeilenhöhe der Walze ist fest, bei großer
    // Systemschrift würden sich die Ziffern sonst überlappen.
    val bigSize = with(density) { 40.dp.toSp() }
    val nearSize = with(density) { 25.dp.toSp() }
    val farSize = with(density) { 21.dp.toSp() }

    LazyColumn(
        state = state,
        flingBehavior = fling,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .weight(weight)
            .height(DRUM_ROW_HEIGHT * DRUM_VISIBLE_ROWS)
            .semantics {
                this.contentDescription = contentDescription
                valueAt(centerIndex)?.let { stateDescription = label(it) }
            },
    ) {
        items(itemCount) { index ->
            val value = valueAt(index)
            val distance = abs(index - centerIndex)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(DRUM_ROW_HEIGHT)
                    .then(
                        if (value != null && distance != 0) {
                            Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) {
                                scope.launch { state.animateScrollToItem(index - DRUM_CENTER_ROW) }
                            }
                        } else {
                            Modifier
                        }
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (value != null) {
                    Text(
                        label(value),
                        style = SerifNumerals,
                        fontSize = when (distance) {
                            0 -> bigSize
                            1 -> nearSize
                            else -> farSize
                        },
                        color = when (distance) {
                            0 -> colors.ink
                            1 -> RiseFixed.drumNear
                            else -> RiseFixed.drumFar
                        },
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
