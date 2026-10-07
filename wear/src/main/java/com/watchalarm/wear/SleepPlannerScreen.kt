package com.watchalarm.wear

import android.content.SharedPreferences
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.dialog.Alert
import androidx.wear.compose.material.dialog.Confirmation
import androidx.wear.compose.material.dialog.Dialog
import com.watchalarm.core.BedtimeReminder
import com.watchalarm.core.R as CoreR
import com.watchalarm.core.PlannerMode
import com.watchalarm.core.SleepPlanner
import com.watchalarm.core.SleepPlannerActions
import com.watchalarm.core.SleepPlannerFormat
import com.watchalarm.core.SleepPlannerStore
import com.watchalarm.core.SleepSettings
import com.watchalarm.core.SleepSuggestion
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Schlafplaner auf der Uhr: oben die Betriebsart, im Modus „Aufwachen um"
 * darunter die Uhrzeit (Krone wie im Editor), dann die Vorschläge als große
 * Chips. Die Rechnung liegt komplett in [SleepPlanner]; hier wird nur
 * angezeigt und weitergereicht.
 */
@Composable
internal fun SleepPlannerScreen(onBack: () -> Unit) {
    var showSettings by rememberSaveable { mutableStateOf(false) }
    if (showSettings) {
        PlannerSettingsScreen(onBack = { showSettings = false })
    } else {
        PlannerScreen(onBack = onBack, onOpenSettings = { showSettings = true })
    }
}

/**
 * Hält Einstellungen, Schlafdaten und Erinnerung aktuell — auch wenn die
 * Nächte gerade erst vom Handy hereinkommen, während der Planer offen ist.
 * Der Zähler dient nur als Auslöser fürs Neuberechnen.
 */
@Composable
private fun rememberPlannerStoreVersion(): Int {
    val context = LocalContext.current
    var version by remember { mutableIntStateOf(0) }
    DisposableEffect(Unit) {
        val prefs = SleepPlannerStore.prefs(context)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> version++ }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return version
}

@Composable
private fun PlannerScreen(onBack: () -> Unit, onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    BackHandler(onBack = onBack)

    var mode by rememberSaveable { mutableStateOf(PlannerMode.SLEEP_NOW) }
    val storeVersion = rememberPlannerStoreVersion()
    val now = rememberCurrentMinute()

    val settings = remember(storeVersion) { SleepPlannerStore.getSettings(context) }
    val summary = remember(storeVersion, now) { SleepPlannerStore.summary(context) }
    val reminderAt = remember(storeVersion, now) { BedtimeReminder.scheduledAt(context) }

    val initialWake = remember { SleepPlannerStore.getWakeTime(context) }
    val timeState = rememberWatchTimeState(initialWake.hour, initialWake.minute)
    val wakeTime = LocalTime.of(timeState.hourOfDay, timeState.minuteOfHour)
    LaunchedEffect(wakeTime) { SleepPlannerStore.setWakeTime(context, wakeTime) }

    val nowZoned = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault())
    val suggestions = when (mode) {
        PlannerMode.SLEEP_NOW -> SleepPlanner.wakeTimes(nowZoned, settings)
        PlannerMode.WAKE_UP_AT -> SleepPlanner.bedtimes(wakeTime, nowZoned, settings)
    }
    val recommended = SleepPlanner.recommendedCycles(summary)

    // Bestätigung nach dem Stellen eines Weckers. Der Text bleibt stehen,
    // während der Dialog ausblendet — sonst wäre er im Ausblenden leer.
    var confirmMessage by remember { mutableStateOf("") }
    var showConfirm by remember { mutableStateOf(false) }
    var pendingBedtime by remember { mutableStateOf<SleepSuggestion?>(null) }
    var shownBedtime by remember { mutableStateOf<SleepSuggestion?>(null) }

    val listState = rememberScalingLazyListState()
    Scaffold(timeText = { TimeText() }) {
        ScalingLazyColumn(
            state = listState,
            // Wie im Editor: Mit sichtbarem Picker gehört die Krone der
            // Uhrzeit, sonst scrollt sie die Liste.
            modifier = Modifier.fillMaxSize().let {
                if (mode == PlannerMode.WAKE_UP_AT) it.rotaryTimePicker(timeState) else it.rotaryScroll(listState)
            },
        ) {
            item { ListHeader { Text(stringResource(CoreR.string.core_planner_title)) } }
            item {
                ModeToggle(mode = mode, onModeChange = { mode = it })
            }
            if (mode == PlannerMode.WAKE_UP_AT) {
                item { WatchTimePickerRow(timeState) }
            }
            if (summary != null) {
                item {
                    Text(
                        SleepPlannerFormat.average(context, summary) + "\n" +
                            SleepPlannerFormat.debt(context, summary),
                        style = MaterialTheme.typography.caption2,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    )
                }
            }
            item {
                ListHeader {
                    Text(
                        stringResource(
                            if (mode == PlannerMode.SLEEP_NOW) CoreR.string.core_planner_waketimes_header
                            else CoreR.string.core_planner_bedtimes_header
                        )
                    )
                }
            }
            items(suggestions, key = { "${mode.name}-${it.cycles}" }) { suggestion ->
                SuggestionChip(
                    suggestion = suggestion,
                    recommended = suggestion.cycles == recommended,
                    onClick = {
                        if (mode == PlannerMode.SLEEP_NOW) {
                            SleepPlannerActions.setWakeAlarm(context, suggestion.time)
                            confirmMessage = context.getString(
                                CoreR.string.core_planner_alarm_set,
                                SleepPlannerFormat.time(context, suggestion.time),
                            )
                            showConfirm = true
                        } else {
                            pendingBedtime = suggestion
                            shownBedtime = suggestion
                        }
                    },
                )
            }
            if (reminderAt != null) {
                item {
                    val time = Instant.ofEpochMilli(reminderAt).atZone(ZoneId.systemDefault())
                    Chip(
                        onClick = { BedtimeReminder.cancel(context) },
                        label = {
                            Text(stringResource(CoreR.string.core_planner_reminder_active, SleepPlannerFormat.time(context, time)))
                        },
                        secondaryLabel = { Text(stringResource(CoreR.string.core_planner_reminder_cancel)) },
                        colors = ChipDefaults.secondaryChipColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            item {
                Chip(
                    onClick = onOpenSettings,
                    label = { Text(stringResource(CoreR.string.core_planner_settings)) },
                    icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Text(
                    stringResource(CoreR.string.core_planner_disclaimer),
                    style = MaterialTheme.typography.caption3,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                )
            }
        }
    }

    Dialog(showDialog = showConfirm, onDismissRequest = { showConfirm = false }) {
        Confirmation(
            onTimeout = { showConfirm = false },
            icon = { Icon(Icons.Filled.Check, contentDescription = null) },
        ) {
            Text(confirmMessage, textAlign = TextAlign.Center)
        }
    }

    Dialog(showDialog = pendingBedtime != null, onDismissRequest = { pendingBedtime = null }) {
        val bedtime = shownBedtime
        Alert(
            title = {
                Text(
                    if (bedtime == null) "" else stringResource(
                        CoreR.string.core_planner_reminder_question,
                        SleepPlannerFormat.time(context, bedtime.time),
                    ),
                    textAlign = TextAlign.Center,
                )
            },
            negativeButton = {
                Button(
                    onClick = { pendingBedtime = null },
                    colors = ButtonDefaults.secondaryButtonColors(),
                ) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(CoreR.string.core_planner_cancel))
                }
            },
            positiveButton = {
                Button(
                    onClick = {
                        if (bedtime != null) {
                            scheduleReminder(context, bedtime, settings)
                            confirmMessage = context.getString(
                                CoreR.string.core_planner_reminder_set,
                                SleepPlannerFormat.time(context, bedtime.time),
                            )
                            showConfirm = true
                        }
                        pendingBedtime = null
                    },
                ) {
                    Icon(Icons.Filled.Check, contentDescription = stringResource(CoreR.string.core_planner_reminder_confirm))
                }
            },
        )
    }
}

/** Erinnerung zur Schlafenszeit; die passende Weckzeit steht später im Text. */
private fun scheduleReminder(context: android.content.Context, bedtime: SleepSuggestion, settings: SleepSettings) {
    val wake: ZonedDateTime = bedtime.time.plusMinutes((settings.fallAsleepMinutes + bedtime.sleepMinutes).toLong())
    BedtimeReminder.schedule(context, bedtime.time.toInstant().toEpochMilli(), wake.toInstant().toEpochMilli())
}

/**
 * Umschalter zwischen den Betriebsarten. Zwei kompakte Chips statt eines
 * Schalters: Beide Optionen sind sichtbar, und die gewählte ist gefüllt —
 * wie ein Segmented Control, das Wear Material nicht hat.
 */
@Composable
private fun ModeToggle(mode: PlannerMode, onModeChange: (PlannerMode) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        listOf(
            PlannerMode.SLEEP_NOW to CoreR.string.core_planner_mode_now_short,
            PlannerMode.WAKE_UP_AT to CoreR.string.core_planner_mode_wake_short,
        ).forEach { (option, label) ->
            val isSelected = option == mode
            CompactChip(
                onClick = { onModeChange(option) },
                label = {
                    Text(
                        stringResource(label),
                        maxLines = 1,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
                colors = if (isSelected) ChipDefaults.primaryChipColors() else ChipDefaults.secondaryChipColors(),
                modifier = Modifier.weight(1f).semantics { selected = isSelected },
            )
        }
    }
}

/**
 * Ein Vorschlag als Chip: groß die Uhrzeit, darunter Zyklen und Dauer.
 * Empfohlen = gefüllt und mit Stern; vergangene Schlafenszeiten sind
 * ausgegraut und nicht antippbar.
 */
@Composable
private fun SuggestionChip(suggestion: SleepSuggestion, recommended: Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
    Chip(
        onClick = onClick,
        enabled = suggestion.available,
        label = { Text(SleepPlannerFormat.time(context, suggestion.time)) },
        secondaryLabel = { Text(SleepPlannerFormat.details(context, suggestion)) },
        icon = if (recommended) {
            {
                Icon(
                    Icons.Filled.Star,
                    contentDescription = stringResource(CoreR.string.core_planner_recommended),
                )
            }
        } else {
            null
        },
        colors = if (recommended) ChipDefaults.primaryChipColors() else ChipDefaults.secondaryChipColors(),
        modifier = Modifier.fillMaxWidth(),
    )
}

// ---------------------------------------------------------------- Einstellungen

/**
 * Einstellungen auf der Uhr: Ein Tipp schaltet zum nächsten Wert weiter.
 * Große Chips statt Picker — drei Werte mit wenigen Stufen brauchen keinen.
 */
@Composable
private fun PlannerSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    BackHandler(onBack = onBack)
    val storeVersion = rememberPlannerStoreVersion()
    val settings = remember(storeVersion) { SleepPlannerStore.getSettings(context) }
    val listState = rememberScalingLazyListState()

    fun update(new: SleepSettings) = SleepPlannerStore.setSettings(context, new)

    Scaffold(timeText = { TimeText() }) {
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().rotaryScroll(listState),
        ) {
            item { ListHeader { Text(stringResource(CoreR.string.core_planner_settings)) } }
            item {
                SettingChip(
                    label = stringResource(CoreR.string.core_planner_setting_cycle),
                    value = SleepPlannerFormat.minutes(context, settings.cycleMinutes),
                    onClick = {
                        update(settings.copy(cycleMinutes = SleepSettings.CYCLE_CHOICES.next(settings.cycleMinutes)))
                    },
                )
            }
            item {
                SettingChip(
                    label = stringResource(CoreR.string.core_planner_setting_fall_asleep),
                    value = SleepPlannerFormat.minutes(context, settings.fallAsleepMinutes),
                    onClick = {
                        update(
                            settings.copy(
                                fallAsleepMinutes = SleepSettings.FALL_ASLEEP_CHOICES.next(settings.fallAsleepMinutes)
                            )
                        )
                    },
                )
            }
            item {
                SettingChip(
                    label = stringResource(CoreR.string.core_planner_setting_goal),
                    value = SleepPlannerFormat.duration(context, settings.sleepGoalMinutes),
                    onClick = {
                        update(
                            settings.copy(
                                sleepGoalMinutes = SleepSettings.SLEEP_GOAL_CHOICES.next(settings.sleepGoalMinutes)
                            )
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun SettingChip(label: String, value: String, onClick: () -> Unit) {
    Chip(
        onClick = onClick,
        label = { Text(label) },
        secondaryLabel = { Text(value) },
        colors = ChipDefaults.secondaryChipColors(),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Nächster Wert in der Auswahl, nach dem letzten wieder der erste. */
private fun List<Int>.next(current: Int): Int = this[(indexOf(current) + 1) % size]
