package com.watchalarm.wear

import androidx.compose.foundation.focusable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.material.Picker
import androidx.wear.compose.material.PickerState
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.rememberPickerState
import java.text.DateFormatSymbols
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/*
 * Bedienelemente, die Alarm-Editor und Schlafplaner gemeinsam nutzen:
 * Krone/Lünette, die Uhrzeit-Picker-Reihe und der Minutentakt. Vorher lag
 * alles privat in MainActivity.kt; der Planer hätte es sonst kopieren müssen.
 */

/** Wie oft und wie schnell der Fokus nachgefordert wird, siehe [rotaryFocus]. */
private const val ROTARY_FOCUS_ATTEMPTS = 20
private const val ROTARY_FOCUS_RETRY_MS = 50L

/**
 * Den Fokus holen und behalten — Voraussetzung für alles Rotary.
 *
 * Wear liefert Rotary-Events nur an eine fokussierte Komponente. Ein einzelnes
 * `requestFocus()` beim Aufbau reicht dafür nicht: Ist der Knoten noch nicht
 * platziert, wirft der Aufruf, und weil sich danach nichts mehr ändert, bleibt
 * die Krone dauerhaft tot. Am Emulator ließ sich das gut sehen — im Editor kam
 * kein einziger Event an, und nach dem Zurück aus dem Editor reagierte auch
 * die Alarmliste nicht mehr.
 *
 * Deshalb ist [onFocusChanged] hier das Maß, nicht die Annahme, der Aufruf
 * habe schon gewirkt: Solange der Fokus fehlt, wird nachgefasst, und geht er
 * später verloren, fängt das von vorne an.
 */
@Composable
private fun Modifier.rotaryFocus(): Modifier {
    val focusRequester = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(focused) {
        if (focused) return@LaunchedEffect
        repeat(ROTARY_FOCUS_ATTEMPTS) {
            runCatching { focusRequester.requestFocus() }
            delay(ROTARY_FOCUS_RETRY_MS)
            if (focused) return@LaunchedEffect
        }
    }
    return this
        .onFocusChanged { focused = it.isFocused }
        .focusRequester(focusRequester)
        .focusable()
}

/**
 * Krone bzw. drehbare Lünette bedienen: Ohne das hier ließ sich die Liste auf
 * Pixel Watch und Galaxy Watch ausschließlich per Wischen scrollen.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun Modifier.rotaryScroll(state: ScalingLazyListState): Modifier {
    val scope = rememberCoroutineScope()
    return this
        .onRotaryScrollEvent { event ->
            scope.launch { state.scrollBy(event.verticalScrollPixels) }
            true
        }
        .rotaryFocus()
}

/** Spalte im Picker, die die Krone gerade verstellt. */
private const val COLUMN_HOUR = 0
private const val COLUMN_MINUTE = 1
private const val COLUMN_AM_PM = 2

/** Höhe der Picker-Reihe. Sie zeigt drei Optionen übereinander. */
private val PICKER_ROW_HEIGHT = 100.dp

/**
 * Höhe einer Option. Fest statt aus der Schrift gemessen: So zeigt die Reihe
 * genau die drei Optionen, von denen [rotaryTimePicker] beim Umrechnen
 * ausgeht — auch wenn die gewählte Ziffer größer ist als ihre Nachbarn.
 */
private val PICKER_OPTION_HEIGHT = PICKER_ROW_HEIGHT / 3

/**
 * Eine Ziffer der Walze: die gewählte groß in Bernstein, die Nachbarn klein
 * und gedimmt. Größen in dp, weil die Optionshöhe fest ist — bei großer
 * Systemschrift würden sich die Ziffern sonst überlappen.
 */
@Composable
private fun DrumDigit(
    text: String,
    selected: Boolean,
    selectedSize: Dp = 28.dp,
    otherSize: Dp = 16.dp,
) {
    val size = with(LocalDensity.current) { (if (selected) selectedSize else otherSize).toSp() }
    Box(modifier = Modifier.height(PICKER_OPTION_HEIGHT), contentAlignment = Alignment.Center) {
        Text(
            text,
            style = SerifNumerals,
            fontSize = size,
            color = if (selected) RiseWear.amber else Color.White.copy(alpha = 0.28f),
            maxLines = 1,
        )
    }
}

/** Aufsummierter Rotary-Weg. Bewusst kein State: niemand liest ihn beim Zeichnen. */
private class RotaryAccumulator {
    var pixels = 0f
}

/**
 * Zustand der Uhrzeit-Picker-Reihe: Stunde, Minute, ggf. AM/PM — und welche
 * Spalte die Krone gerade verstellt.
 */
@Stable
internal class WatchTimeState(
    val is24Hour: Boolean,
    val hour: PickerState,
    val minute: PickerState,
    val amPm: PickerState,
) {
    /**
     * Standard ist die Stunde: Das ist der erste Wert, den man einstellt,
     * und ohne Vorbelegung wäre die Krone beim Öffnen wieder wirkungslos.
     */
    var rotaryColumn by mutableStateOf(COLUMN_HOUR)

    /** Gewählte Stunde im 24-Stunden-Format, egal wie angezeigt. */
    val hourOfDay: Int
        get() = if (is24Hour) {
            hour.selectedOption
        } else {
            (hour.selectedOption % 12) + if (amPm.selectedOption == 1) 12 else 0
        }

    val minuteOfHour: Int get() = minute.selectedOption

    fun rotaryTarget(): PickerState = when (rotaryColumn) {
        COLUMN_MINUTE -> minute
        COLUMN_AM_PM -> amPm
        else -> hour
    }
}

/**
 * Das Uhrzeitformat des Geräts übernehmen: auf 12-Stunden-Geräten stand im
 * Editor früher "13", in der Liste aber "1:00 PM".
 */
@Composable
internal fun rememberWatchTimeState(initialHour: Int, initialMinute: Int): WatchTimeState {
    val context = LocalContext.current
    val is24Hour = remember(context) { android.text.format.DateFormat.is24HourFormat(context) }
    val hourState = rememberPickerState(
        initialNumberOfOptions = if (is24Hour) 24 else 12,
        initiallySelectedOption = if (is24Hour) initialHour else initialHour % 12,
    )
    val minuteState = rememberPickerState(
        initialNumberOfOptions = 60,
        initiallySelectedOption = initialMinute,
    )
    val amPmState = rememberPickerState(
        initialNumberOfOptions = 2,
        initiallySelectedOption = if (initialHour >= 12) 1 else 0,
    )
    return remember(is24Hour, hourState, minuteState, amPmState) {
        WatchTimeState(is24Hour, hourState, minuteState, amPmState)
    }
}

/**
 * Krone bzw. drehbare Lünette auf die gerade gewählte Picker-Spalte legen.
 *
 * Im Editor forderte vorher überhaupt nichts den Fokus an, und Wear liefert
 * Rotary-Events nur an eine fokussierte Komponente — die Krone war dort also
 * nicht bloß nicht am Scrollen, sondern schlicht tot.
 *
 * Der Fokus sitzt bewusst am Container und nicht an den Pickern selbst: Die
 * stecken in Lazy-Items, die beim Scrollen entsorgt und neu aufgebaut werden,
 * und mit ihnen wäre auch der Fokus jedes Mal weg. Ein einziger, immer
 * vorhandener Knoten umgeht das — welche Spalte er verstellt, sagt
 * [WatchTimeState.rotaryColumn].
 *
 * Der Picker springt in ganzen Optionen, die Events kommen aber als
 * Pixelbetrag herein. Ein direktes `scrollBy()` ließe ihn zwischen zwei
 * Optionen stehen, weil das Einrasten am Fling hängt und bei einem
 * programmatischen Scroll gar nicht greift. Deshalb wird der Weg aufsummiert
 * und erst bei genug davon eine Option weitergeschaltet.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun Modifier.rotaryTimePicker(state: WatchTimeState): Modifier {
    val scope = rememberCoroutineScope()
    val accumulator = remember { RotaryAccumulator() }
    // Genau die Strecke, die ein Finger ziehen müsste, um eine Option
    // weiterzukommen — Rotary-Pixel und Wischweg sind dieselbe Einheit.
    // Hergeleitet statt geraten: eine feste Pixelzahl wäre auf Uhren mit
    // anderer Dichte mal zäh und mal übersprungen.
    val pixelsPerOption = with(LocalDensity.current) { (PICKER_ROW_HEIGHT / 3f).toPx() }
    return this
        .onRotaryScrollEvent { event ->
            accumulator.pixels += event.verticalScrollPixels
            val steps = (accumulator.pixels / pixelsPerOption).toInt()
            if (steps != 0) {
                accumulator.pixels -= steps * pixelsPerOption
                val picker = state.rotaryTarget()
                val count = picker.numberOfOptions
                val next = ((picker.selectedOption + steps) % count + count) % count
                scope.launch { picker.animateScrollToOption(next) }
            }
            true
        }
        .rotaryFocus()
}

/**
 * Die Krone beim Berühren zu dieser Spalte holen.
 *
 * Über `pointerInput`, weil Pickers eigenes `onSelected` nur an den Semantics
 * hängt und deshalb für Bedienungshilfen feuert, nicht für einen gewöhnlichen
 * Fingertipp.
 */
private fun Modifier.claimRotaryOnTouch(onClaim: () -> Unit): Modifier =
    pointerInput(onClaim) {
        awaitPointerEventScope {
            while (true) {
                // Initial-Pass und nichts konsumieren: Der Picker soll die
                // Berührung danach ganz normal selbst verarbeiten.
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.changes.any { it.pressed }) onClaim()
            }
        }
    }

/**
 * Uhrzeit-Walze aus dem Rise-Design ("Time picker"): eingelassene Fläche,
 * Auswahlfeld mit Bernsteinrand, Ziffern oben und unten ausgeblendet. Reine
 * Kulisse hinter den Pickern — Höhe und Krone bleiben unverändert.
 * Schmaler als die Zeile: Auf dem runden Display lagen die oberen Ecken
 * sonst unter der Lünette. Von Editor und Schlafplaner gemeinsam genutzt.
 */
@Composable
internal fun WatchTimePickerRow(state: WatchTimeState) {
    val amPmLabels = remember { DateFormatSymbols.getInstance().amPmStrings }
    val pickerWidth = if (state.is24Hour) 60.dp else 44.dp
    Box(
        modifier = Modifier
            .fillMaxWidth(0.9f)
            .height(PICKER_ROW_HEIGHT)
            .clip(RoundedCornerShape(20.dp))
            .background(RiseWear.drum),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = 6.dp)
                .fillMaxWidth()
                .height(PICKER_OPTION_HEIGHT + 4.dp)
                .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(11.dp))
                .border(1.dp, RiseWear.amber.copy(alpha = 0.4f), RoundedCornerShape(11.dp)),
        )
        Row(
            modifier = Modifier.fillMaxWidth().height(PICKER_ROW_HEIGHT),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Picker(
                state = state.hour,
                contentDescription = stringResource(R.string.picker_hour),
                onSelected = { state.rotaryColumn = COLUMN_HOUR },
                gradientColor = RiseWear.drum,
                modifier = Modifier.width(pickerWidth).fillMaxSize()
                    .claimRotaryOnTouch { state.rotaryColumn = COLUMN_HOUR },
            ) { index ->
                DrumDigit(
                    if (state.is24Hour) "%02d".format(index)
                    else if (index == 0) "12" else "$index",
                    selected = index == state.hour.selectedOption,
                )
            }
            Text(
                ":",
                fontFamily = InstrumentSerif,
                fontSize = 22.sp,
                color = RiseWear.amber.copy(alpha = 0.7f),
            )
            Picker(
                state = state.minute,
                contentDescription = stringResource(R.string.picker_minute),
                onSelected = { state.rotaryColumn = COLUMN_MINUTE },
                gradientColor = RiseWear.drum,
                modifier = Modifier.width(pickerWidth).fillMaxSize()
                    .claimRotaryOnTouch { state.rotaryColumn = COLUMN_MINUTE },
            ) { index ->
                DrumDigit("%02d".format(index), selected = index == state.minute.selectedOption)
            }
            if (!state.is24Hour) {
                Picker(
                    state = state.amPm,
                    contentDescription = stringResource(R.string.picker_am_pm),
                    onSelected = { state.rotaryColumn = COLUMN_AM_PM },
                    gradientColor = RiseWear.drum,
                    modifier = Modifier.width(48.dp).fillMaxSize()
                        .claimRotaryOnTouch { state.rotaryColumn = COLUMN_AM_PM },
                ) { index ->
                    DrumDigit(
                        amPmLabels.getOrElse(index) { if (index == 0) "AM" else "PM" },
                        selected = index == state.amPm.selectedOption,
                        selectedSize = 18.dp,
                        otherSize = 13.dp,
                    )
                }
            }
        }
    }
}

/**
 * Aktuelle Zeit, die sich zur vollen Minute selbst aktualisiert — sonst
 * bliebe die Schlafdauer in der Liste stehen. Läuft nur im Vordergrund.
 */
@Composable
internal fun rememberCurrentMinute(): Long {
    val lifecycleOwner = LocalLifecycleOwner.current
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                val current = System.currentTimeMillis()
                now = current
                // Auf die nächste volle Minute takten statt stur 60 Sekunden.
                delay(60_000L - current % 60_000L)
            }
        }
    }
    return now
}
