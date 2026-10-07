package com.watchalarm.mobile

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import com.watchalarm.core.BedtimeReminder
import com.watchalarm.core.PlannerMode
import com.watchalarm.core.R as CoreR
import com.watchalarm.core.SleepPlanner
import com.watchalarm.core.SleepPlannerActions
import com.watchalarm.core.SleepPlannerFormat
import com.watchalarm.core.SleepPlannerStore
import com.watchalarm.core.SleepSettings
import com.watchalarm.core.SleepSuggestion
import com.watchalarm.core.SleepSummary
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.launch

/**
 * Schlafplaner am Handy — dieselbe Rechnung ([SleepPlanner]) und dieselben
 * Texte ([SleepPlannerFormat]) wie auf der Uhr.
 *
 * Gestaltet wie der Wecker-Editor und die Alarmliste, mit denselben
 * Bausteinen: Scaffold mit Zurück-Pfeil, scrollende Spalte mit 20dp Abstand,
 * Abschnittstitel in titleSmall, FilterChips zur Auswahl, TimePicker mittig,
 * Vorschläge als Karten wie die Wecker-Karten (große, leichte Uhrzeit,
 * Hervorhebung in Primärfarbe wie die 😴-Schlafdauer). Kein Dialog: Ein
 * gestellter Wecker führt wie „Speichern" zurück in die Liste.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun SleepPlannerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    BackHandler(onBack = onBack)

    var mode by rememberSaveable { mutableStateOf(PlannerMode.SLEEP_NOW) }
    val storeVersion = rememberPlannerStoreVersion()
    val now = rememberCurrentMinute()

    val settings = remember(storeVersion) { SleepPlannerStore.getSettings(context) }
    val summary = remember(storeVersion, now) { SleepPlannerStore.summary(context) }
    val reminderAt = remember(storeVersion, now) { BedtimeReminder.scheduledAt(context) }
    val promptDismissed = remember(storeVersion) { SleepPlannerStore.isHealthPromptDismissed(context) }

    // Health Connect: Status beim Öffnen und nach der Berechtigungsanfrage.
    var healthStatus by remember { mutableStateOf<SleepHealth.Status?>(null) }
    LaunchedEffect(Unit) { healthStatus = SleepHealth.refresh(context) }
    val permissionLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) {
        scope.launch { healthStatus = SleepHealth.refresh(context) }
    }

    // Wie im Editor: TimePicker direkt auf der Seite, Uhrzeitformat vom Gerät.
    val initialWake = remember { SleepPlannerStore.getWakeTime(context) }
    val timeState = rememberTimePickerState(
        initialHour = initialWake.hour,
        initialMinute = initialWake.minute,
        is24Hour = android.text.format.DateFormat.is24HourFormat(context),
    )
    val wakeTime = LocalTime.of(timeState.hour, timeState.minute)
    LaunchedEffect(wakeTime) { SleepPlannerStore.setWakeTime(context, wakeTime) }

    val nowZoned = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault())
    val suggestions = when (mode) {
        PlannerMode.SLEEP_NOW -> SleepPlanner.wakeTimes(nowZoned, settings)
        PlannerMode.WAKE_UP_AT -> SleepPlanner.bedtimes(wakeTime, nowZoned, settings)
    }
    val recommended = SleepPlanner.recommendedCycles(summary)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(CoreR.string.core_planner_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // Betriebsart als FilterChips, wie die Auswahlen im Editor.
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                listOf(
                    PlannerMode.SLEEP_NOW to CoreR.string.core_planner_mode_now,
                    PlannerMode.WAKE_UP_AT to CoreR.string.core_planner_mode_wake,
                ).forEach { (option, label) ->
                    FilterChip(
                        selected = mode == option,
                        onClick = { mode = option },
                        label = { Text(stringResource(label)) },
                    )
                }
            }

            if (mode == PlannerMode.WAKE_UP_AT) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TimePicker(state = timeState)
                }
            }

            SleepDataSection(
                status = healthStatus,
                summary = summary,
                promptDismissed = promptDismissed,
                onConnect = { permissionLauncher.launch(SleepHealth.PERMISSIONS) },
                onDismissPrompt = { SleepPlannerStore.setHealthPromptDismissed(context, true) },
                onInstall = { openHealthConnectInstall(context) },
            )

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(
                        if (mode == PlannerMode.SLEEP_NOW) CoreR.string.core_planner_waketimes_header
                        else CoreR.string.core_planner_bedtimes_header
                    ),
                    style = MaterialTheme.typography.titleSmall,
                )
                suggestions.forEach { suggestion ->
                    SuggestionCard(
                        suggestion = suggestion,
                        recommended = suggestion.cycles == recommended,
                        onClick = {
                            if (mode == PlannerMode.SLEEP_NOW) {
                                SleepPlannerActions.setWakeAlarm(context, suggestion.time)
                                toast(context, CoreR.string.core_planner_alarm_set, suggestion)
                                onBack()
                            } else {
                                val wake = suggestion.time.plusMinutes(
                                    (settings.fallAsleepMinutes + suggestion.sleepMinutes).toLong()
                                )
                                BedtimeReminder.schedule(
                                    context,
                                    suggestion.time.toInstant().toEpochMilli(),
                                    wake.toInstant().toEpochMilli(),
                                )
                                toast(context, CoreR.string.core_planner_reminder_set, suggestion)
                            }
                        },
                    )
                }
                if (reminderAt != null) {
                    ReminderCard(
                        time = SleepPlannerFormat.time(
                            context,
                            Instant.ofEpochMilli(reminderAt).atZone(ZoneId.systemDefault()),
                        ),
                        onCancel = { BedtimeReminder.cancel(context) },
                    )
                }
            }

            Text(
                stringResource(CoreR.string.core_planner_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            HorizontalDivider()

            PlannerSettings(
                settings = settings,
                healthStatus = healthStatus,
                onChange = { SleepPlannerStore.setSettings(context, it) },
                onConnect = { permissionLauncher.launch(SleepHealth.PERMISSIONS) },
                onManage = { openHealthConnectSettings(context) },
                onInstall = { openHealthConnectInstall(context) },
            )
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

/** Auslöser fürs Neuberechnen, wenn sich Einstellungen oder Nächte ändern. */
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

/**
 * Zusammenfassung der letzten Nächte in Primärfarbe, wie die Schlafdauer
 * auf den Wecker-Karten. Solange es keine gibt: der Hinweis zum Verbinden,
 * gestaltet wie der Vollbild-Hinweis der Alarmliste (secondaryContainer,
 * bodyMedium, 16dp). Ohne Daten und nach „Nicht jetzt" erscheint nichts.
 */
@Composable
private fun SleepDataSection(
    status: SleepHealth.Status?,
    summary: SleepSummary?,
    promptDismissed: Boolean,
    onConnect: () -> Unit,
    onDismissPrompt: () -> Unit,
    onInstall: () -> Unit,
) {
    val context = LocalContext.current
    when {
        summary != null -> Column {
            Text(
                stringResource(R.string.planner_summary, SleepPlannerFormat.average(context, summary)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                SleepPlannerFormat.debt(context, summary),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        promptDismissed -> Unit
        status == SleepHealth.Status.NOT_GRANTED || status == SleepHealth.Status.NEEDS_INSTALL -> Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = if (status == SleepHealth.Status.NEEDS_INSTALL) onInstall else onConnect),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        ) {
            // Die Begründung steht hier, *bevor* der Systemdialog kommt.
            Text(
                stringResource(R.string.health_explanation),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp),
            )
            Row(modifier = Modifier.fillMaxWidth().padding(end = 8.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismissPrompt) { Text(stringResource(R.string.health_not_now)) }
                if (status == SleepHealth.Status.NEEDS_INSTALL) {
                    TextButton(onClick = onInstall) { Text(stringResource(R.string.health_install)) }
                } else {
                    TextButton(onClick = onConnect) { Text(stringResource(R.string.health_connect)) }
                }
            }
        }
        else -> Unit
    }
}

/**
 * Ein Vorschlag, aufgebaut wie eine Wecker-Karte: große, leichte Uhrzeit,
 * darunter bodyMedium, Hervorhebung in Primärfarbe. Vergangene
 * Schlafenszeiten sehen aus wie ein ausgeschalteter Wecker und sind nicht
 * antippbar.
 */
@Composable
private fun SuggestionCard(suggestion: SleepSuggestion, recommended: Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth().clickable(enabled = suggestion.available, onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            Text(
                SleepPlannerFormat.time(context, suggestion.time),
                fontSize = 40.sp,
                fontFamily = RiseSerif,
                color = if (suggestion.available) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(SleepPlannerFormat.details(context, suggestion), style = MaterialTheme.typography.bodyMedium)
            if (recommended) {
                Text(
                    stringResource(R.string.planner_recommended, stringResource(CoreR.string.core_planner_recommended)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/** Gestellte Erinnerung — wie ein Wecker mit Schalter; aus = gelöscht. */
@Composable
private fun ReminderCard(time: String, onCancel: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(CoreR.string.core_planner_reminder_active, time),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Switch(checked = true, onCheckedChange = { if (!it) onCancel() })
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlannerSettings(
    settings: SleepSettings,
    healthStatus: SleepHealth.Status?,
    onChange: (SleepSettings) -> Unit,
    onConnect: () -> Unit,
    onManage: () -> Unit,
    onInstall: () -> Unit,
) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(CoreR.string.core_planner_settings), style = MaterialTheme.typography.titleMedium)

        SettingChoices(
            title = stringResource(CoreR.string.core_planner_setting_cycle),
            choices = SleepSettings.CYCLE_CHOICES,
            selected = settings.cycleMinutes,
            label = { SleepPlannerFormat.minutes(context, it) },
            onSelect = { onChange(settings.copy(cycleMinutes = it)) },
        )
        SettingChoices(
            title = stringResource(CoreR.string.core_planner_setting_fall_asleep),
            choices = SleepSettings.FALL_ASLEEP_CHOICES,
            selected = settings.fallAsleepMinutes,
            label = { SleepPlannerFormat.minutes(context, it) },
            onSelect = { onChange(settings.copy(fallAsleepMinutes = it)) },
        )
        SettingChoices(
            title = stringResource(CoreR.string.core_planner_setting_goal),
            choices = SleepSettings.SLEEP_GOAL_CHOICES,
            selected = settings.sleepGoalMinutes,
            label = { SleepPlannerFormat.duration(context, it) },
            onSelect = { onChange(settings.copy(sleepGoalMinutes = it)) },
        )

        Column {
            Text(stringResource(R.string.health_section), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(
                        when (healthStatus) {
                            SleepHealth.Status.GRANTED -> R.string.health_status_connected
                            SleepHealth.Status.NOT_GRANTED -> R.string.health_status_not_connected
                            SleepHealth.Status.NEEDS_INSTALL -> R.string.health_status_not_installed
                            SleepHealth.Status.UNAVAILABLE -> R.string.health_status_unavailable
                            null -> R.string.health_status_checking
                        }
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                when (healthStatus) {
                    SleepHealth.Status.GRANTED ->
                        TextButton(onClick = onManage) { Text(stringResource(R.string.health_manage)) }
                    SleepHealth.Status.NOT_GRANTED ->
                        TextButton(onClick = onConnect) { Text(stringResource(R.string.health_connect)) }
                    SleepHealth.Status.NEEDS_INSTALL ->
                        TextButton(onClick = onInstall) { Text(stringResource(R.string.health_install)) }
                    else -> Unit
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingChoices(
    title: String,
    choices: List<Int>,
    selected: Int,
    label: (Int) -> String,
    onSelect: (Int) -> Unit,
) {
    Column {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            choices.forEach { value ->
                FilterChip(
                    selected = selected == value,
                    onClick = { onSelect(value) },
                    label = { Text(label(value)) },
                )
            }
        }
    }
}

private fun openHealthConnectSettings(context: Context) {
    runCatching { context.startActivity(Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS)) }
}

/** Bis Android 13 ist Health Connect eine eigene App aus dem Play Store. */
private fun openHealthConnectInstall(context: Context) {
    runCatching {
        context.startActivity(
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse("market://details?id=com.google.android.apps.healthdata&url=healthconnect%3A%2F%2Fonboarding"),
            ).setPackage("com.android.vending")
        )
    }
}
