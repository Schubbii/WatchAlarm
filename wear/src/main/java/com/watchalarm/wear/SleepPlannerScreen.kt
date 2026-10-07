package com.watchalarm.wear

import android.content.Context
import android.content.SharedPreferences
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipColors
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.RadioButton
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Switch
import androidx.wear.compose.material.SwitchDefaults
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.ToggleChip
import androidx.wear.compose.material.ToggleChipDefaults
import com.watchalarm.core.BedtimeReminder
import com.watchalarm.core.PlannerMode
import com.watchalarm.core.R as CoreR
import com.watchalarm.core.SleepPlanner
import com.watchalarm.core.SleepPlannerActions
import com.watchalarm.core.SleepPlannerFormat
import com.watchalarm.core.SleepPlannerStore
import com.watchalarm.core.SleepSettings
import com.watchalarm.core.SleepSuggestion
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/**
 * Schlafplaner auf der Uhr, im Rise-Design wie Alarmliste und Editor:
 * fast schwarzes Zifferblatt mit Pflaumenschimmer, Eyebrow-Überschriften,
 * Einträge in [RiseWear.item], Uhrzeiten in Serif, Bernstein als Akzent,
 * dieselbe Walze wie im Editor. Kein Dialog: Wie „Speichern" im Editor
 * führt ein gestellter Wecker zurück in die Liste.
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

/** Fläche der Einträge wie "Neuer Wecker" in der Liste. */
@Composable
private fun riseChipColors(secondaryContent: Color = RiseWear.textDim): ChipColors = ChipDefaults.chipColors(
    backgroundColor = RiseWear.item,
    contentColor = Color.White,
    secondaryContentColor = secondaryContent,
    iconColor = RiseWear.amber,
)

/** Auswahl-/Schalter-Einträge in denselben Farben. */
@Composable
private fun riseToggleChipColors() = ToggleChipDefaults.toggleChipColors(
    checkedStartBackgroundColor = RiseWear.item,
    checkedEndBackgroundColor = RiseWear.item,
    checkedContentColor = Color.White,
    checkedSecondaryContentColor = RiseWear.textDim,
    checkedToggleControlColor = RiseWear.amber,
    uncheckedStartBackgroundColor = RiseWear.item,
    uncheckedEndBackgroundColor = RiseWear.item,
    uncheckedContentColor = Color.White,
    uncheckedSecondaryContentColor = RiseWear.textDim,
    uncheckedToggleControlColor = Color.White.copy(alpha = 0.6f),
)

/** Hintergrund wie die Alarmliste ("Sleep face"). */
private fun Modifier.riseSleepFace(): Modifier =
    background(RiseWear.background).riseGlow(RiseWear.plumGlow, centerY = 1.1f, radius = 0.6f)

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

    val listState = rememberScalingLazyListState()
    Scaffold(timeText = { RiseTimeText() }, modifier = Modifier.riseSleepFace()) {
        ScalingLazyColumn(
            state = listState,
            // Wie im Editor: Mit sichtbarer Walze gehört die Krone der
            // Uhrzeit, sonst scrollt sie die Liste.
            modifier = Modifier.fillMaxSize().let {
                if (mode == PlannerMode.WAKE_UP_AT) it.rotaryTimePicker(timeState) else it.rotaryScroll(listState)
            },
        ) {
            item { ListHeader { Eyebrow(stringResource(CoreR.string.core_planner_title)) } }
            items(listOf(PlannerMode.SLEEP_NOW, PlannerMode.WAKE_UP_AT)) { option ->
                ToggleChip(
                    checked = mode == option,
                    onCheckedChange = { if (it) mode = option },
                    label = {
                        Text(
                            stringResource(
                                if (option == PlannerMode.SLEEP_NOW) CoreR.string.core_planner_mode_now
                                else CoreR.string.core_planner_mode_wake
                            )
                        )
                    },
                    toggleControl = { RadioButton(selected = mode == option) },
                    colors = riseToggleChipColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
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
                        color = RiseWear.amber,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    )
                }
            }
            item {
                ListHeader {
                    Eyebrow(
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
                            toast(context, CoreR.string.core_planner_alarm_set, suggestion)
                            onBack()
                        } else {
                            scheduleReminder(context, suggestion, settings)
                            toast(context, CoreR.string.core_planner_reminder_set, suggestion)
                        }
                    },
                )
            }
            if (reminderAt != null) {
                item {
                    val time = Instant.ofEpochMilli(reminderAt).atZone(ZoneId.systemDefault())
                    // Wie ein Wecker in der Liste: Schalter aus = Erinnerung weg.
                    ToggleChip(
                        checked = true,
                        onCheckedChange = { if (!it) BedtimeReminder.cancel(context) },
                        label = {
                            Text(
                                stringResource(
                                    CoreR.string.core_planner_reminder_active,
                                    SleepPlannerFormat.time(context, time),
                                )
                            )
                        },
                        toggleControl = {
                            Switch(
                                checked = true,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = RiseWear.amber,
                                    checkedTrackColor = RiseWear.amber.copy(alpha = 0.5f),
                                ),
                            )
                        },
                        colors = riseToggleChipColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            item {
                Chip(
                    onClick = onOpenSettings,
                    label = { Text(stringResource(CoreR.string.core_planner_settings)) },
                    icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                    colors = riseChipColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Text(
                    stringResource(CoreR.string.core_planner_disclaimer),
                    style = MaterialTheme.typography.caption3,
                    color = RiseWear.textFaint,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                )
            }
        }
    }
}

/** Kurze Bestätigung mit der Uhrzeit des Vorschlags. */
private fun toast(context: Context, message: Int, suggestion: SleepSuggestion) {
    Toast.makeText(
        context,
        context.getString(message, SleepPlannerFormat.time(context, suggestion.time)),
        Toast.LENGTH_SHORT,
    ).show()
}

/** Erinnerung zur Schlafenszeit; die passende Weckzeit steht später im Text. */
private fun scheduleReminder(context: Context, bedtime: SleepSuggestion, settings: SleepSettings) {
    val wake = bedtime.time.plusMinutes((settings.fallAsleepMinutes + bedtime.sleepMinutes).toLong())
    BedtimeReminder.schedule(context, bedtime.time.toInstant().toEpochMilli(), wake.toInstant().toEpochMilli())
}

/**
 * Ein Vorschlag wie ein Wecker in der Liste: Uhrzeit in Serif, darunter
 * Zyklen und Dauer gedimmt. Die Empfehlung steht mit ⭐ in Bernstein in der
 * Zweitzeile. Vergangene Schlafenszeiten sind ausgegraut und nicht antippbar.
 */
@Composable
private fun SuggestionChip(suggestion: SleepSuggestion, recommended: Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
    val details = SleepPlannerFormat.details(context, suggestion)
    Chip(
        onClick = onClick,
        enabled = suggestion.available,
        label = {
            Text(
                SleepPlannerFormat.time(context, suggestion.time),
                style = SerifNumerals,
                fontSize = 22.sp,
                lineHeight = 24.sp,
            )
        },
        secondaryLabel = {
            Text(if (recommended) stringResource(R.string.planner_recommended_prefix, details) else details)
        },
        colors = riseChipColors(secondaryContent = if (recommended) RiseWear.amber else RiseWear.textDim),
        modifier = Modifier.fillMaxWidth(),
    )
}

// ---------------------------------------------------------------- Einstellungen

/**
 * Einstellungen auf der Uhr: Ein Tipp schaltet zum nächsten Wert weiter.
 * Große Einträge statt Picker — drei Werte mit wenigen Stufen brauchen keinen.
 */
@Composable
private fun PlannerSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    BackHandler(onBack = onBack)
    val storeVersion = rememberPlannerStoreVersion()
    val settings = remember(storeVersion) { SleepPlannerStore.getSettings(context) }
    val listState = rememberScalingLazyListState()

    fun update(new: SleepSettings) = SleepPlannerStore.setSettings(context, new)

    Scaffold(timeText = { RiseTimeText() }, modifier = Modifier.riseSleepFace()) {
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().rotaryScroll(listState),
        ) {
            item { ListHeader { Eyebrow(stringResource(CoreR.string.core_planner_settings)) } }
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
        colors = riseChipColors(secondaryContent = RiseWear.amber),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Nächster Wert in der Auswahl, nach dem letzten wieder der erste. */
private fun List<Int>.next(current: Int): Int = this[(indexOf(current) + 1) % size]
