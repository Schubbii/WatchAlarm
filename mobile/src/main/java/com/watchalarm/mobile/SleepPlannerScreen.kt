package com.watchalarm.mobile

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
 * Texte ([SleepPlannerFormat]) wie auf der Uhr, nur mit mehr Platz: Hier
 * sitzen zusätzlich die Verbindung zu Health Connect und die Einstellungen.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun SleepPlannerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    BackHandler(onBack = onBack)

    var mode by rememberSaveable { mutableStateOf(PlannerMode.SLEEP_NOW) }
    val storeVersion = rememberPlannerStoreVersion()
    val now = rememberCurrentMinute()

    val settings = remember(storeVersion) { SleepPlannerStore.getSettings(context) }
    val wakeTime = remember(storeVersion) { SleepPlannerStore.getWakeTime(context) }
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

    val nowZoned = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault())
    val suggestions = when (mode) {
        PlannerMode.SLEEP_NOW -> SleepPlanner.wakeTimes(nowZoned, settings)
        PlannerMode.WAKE_UP_AT -> SleepPlanner.bedtimes(wakeTime, nowZoned, settings)
    }
    val recommended = SleepPlanner.recommendedCycles(summary)

    var showTimePicker by rememberSaveable { mutableStateOf(false) }
    var pendingBedtime by remember { mutableStateOf<SleepSuggestion?>(null) }

    fun showMessage(text: String) {
        scope.launch { snackbar.showSnackbar(text) }
    }

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
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    listOf(
                        PlannerMode.SLEEP_NOW to CoreR.string.core_planner_mode_now,
                        PlannerMode.WAKE_UP_AT to CoreR.string.core_planner_mode_wake,
                    ).forEachIndexed { index, (option, label) ->
                        SegmentedButton(
                            selected = mode == option,
                            onClick = { mode = option },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = 2),
                        ) {
                            Text(stringResource(label))
                        }
                    }
                }
            }
            if (mode == PlannerMode.WAKE_UP_AT) {
                item {
                    OutlinedButton(
                        onClick = { showTimePicker = true },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                    ) {
                        Text(
                            stringResource(
                                R.string.planner_wake_time_button,
                                SleepPlannerFormat.time(context, SleepPlanner.nextOccurrence(wakeTime, nowZoned)),
                            ),
                            fontSize = 18.sp,
                        )
                    }
                }
            }
            item {
                SleepDataCard(
                    status = healthStatus,
                    summary = summary,
                    promptDismissed = promptDismissed,
                    onConnect = { permissionLauncher.launch(SleepHealth.PERMISSIONS) },
                    onDismissPrompt = { SleepPlannerStore.setHealthPromptDismissed(context, true) },
                    onInstall = { openHealthConnectInstall(context) },
                )
            }
            item {
                Text(
                    stringResource(
                        if (mode == PlannerMode.SLEEP_NOW) CoreR.string.core_planner_waketimes_header
                        else CoreR.string.core_planner_bedtimes_header
                    ),
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            items(suggestions, key = { "${mode.name}-${it.cycles}" }) { suggestion ->
                SuggestionCard(
                    suggestion = suggestion,
                    recommended = suggestion.cycles == recommended,
                    onClick = {
                        if (mode == PlannerMode.SLEEP_NOW) {
                            SleepPlannerActions.setWakeAlarm(context, suggestion.time)
                            showMessage(
                                context.getString(
                                    CoreR.string.core_planner_alarm_set,
                                    SleepPlannerFormat.time(context, suggestion.time),
                                )
                            )
                        } else {
                            pendingBedtime = suggestion
                        }
                    },
                )
            }
            if (reminderAt != null) {
                item {
                    val time = Instant.ofEpochMilli(reminderAt).atZone(ZoneId.systemDefault())
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                stringResource(
                                    CoreR.string.core_planner_reminder_active,
                                    SleepPlannerFormat.time(context, time),
                                ),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = { BedtimeReminder.cancel(context) }) {
                                Text(stringResource(CoreR.string.core_planner_reminder_cancel))
                            }
                        }
                    }
                }
            }
            item {
                Text(
                    stringResource(CoreR.string.core_planner_disclaimer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item { HorizontalDivider() }
            item {
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

    if (showTimePicker) {
        val timeState = rememberTimePickerState(
            initialHour = wakeTime.hour,
            initialMinute = wakeTime.minute,
            is24Hour = android.text.format.DateFormat.is24HourFormat(context),
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        SleepPlannerStore.setWakeTime(context, LocalTime.of(timeState.hour, timeState.minute))
                        showTimePicker = false
                    },
                ) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text(stringResource(CoreR.string.core_planner_cancel))
                }
            },
            text = { TimePicker(state = timeState) },
        )
    }

    pendingBedtime?.let { bedtime ->
        val timeText = SleepPlannerFormat.time(context, bedtime.time)
        AlertDialog(
            onDismissRequest = { pendingBedtime = null },
            title = { Text(stringResource(CoreR.string.core_planner_reminder_question, timeText)) },
            text = { Text(SleepPlannerFormat.line(context, bedtime)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val wake = bedtime.time.plusMinutes((settings.fallAsleepMinutes + bedtime.sleepMinutes).toLong())
                        BedtimeReminder.schedule(
                            context,
                            bedtime.time.toInstant().toEpochMilli(),
                            wake.toInstant().toEpochMilli(),
                        )
                        pendingBedtime = null
                        showMessage(context.getString(CoreR.string.core_planner_reminder_set, timeText))
                    },
                ) { Text(stringResource(CoreR.string.core_planner_reminder_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingBedtime = null }) {
                    Text(stringResource(CoreR.string.core_planner_cancel))
                }
            },
        )
    }
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
 * Zusammenfassung der letzten Nächte — oder, solange es keine gibt, der
 * Hinweis, warum sich das Verbinden lohnt. Ohne Daten und nach „Nicht jetzt"
 * verschwindet die Karte; der Rechner funktioniert dann einfach ohne
 * Empfehlung weiter.
 */
@Composable
private fun SleepDataCard(
    status: SleepHealth.Status?,
    summary: SleepSummary?,
    promptDismissed: Boolean,
    onConnect: () -> Unit,
    onDismissPrompt: () -> Unit,
    onInstall: () -> Unit,
) {
    val context = LocalContext.current
    when {
        summary != null -> Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    SleepPlannerFormat.average(context, summary),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                    SleepPlannerFormat.debt(context, summary),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
        promptDismissed -> Unit
        status == SleepHealth.Status.NOT_GRANTED || status == SleepHealth.Status.NEEDS_INSTALL -> Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    stringResource(R.string.health_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Spacer(Modifier.height(4.dp))
                // Die Begründung steht hier, *bevor* der Systemdialog kommt.
                Text(
                    stringResource(R.string.health_explanation),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismissPrompt) { Text(stringResource(R.string.health_not_now)) }
                    if (status == SleepHealth.Status.NEEDS_INSTALL) {
                        TextButton(onClick = onInstall) { Text(stringResource(R.string.health_install)) }
                    } else {
                        TextButton(onClick = onConnect) { Text(stringResource(R.string.health_connect)) }
                    }
                }
            }
        }
        else -> Unit
    }
}

@Composable
private fun SuggestionCard(suggestion: SleepSuggestion, recommended: Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            // Vergangene Schlafenszeiten: sichtbar, aber ausgegraut und
            // nicht antippbar.
            .alpha(if (suggestion.available) 1f else 0.38f)
            .clickable(enabled = suggestion.available, onClick = onClick),
        colors = if (recommended) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        } else {
            CardDefaults.cardColors()
        },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                // Wie die Uhrzeit in der Alarmliste: groß und leicht.
                Text(
                    SleepPlannerFormat.time(context, suggestion.time),
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Light,
                )
                Text(SleepPlannerFormat.details(context, suggestion), style = MaterialTheme.typography.bodyMedium)
            }
            if (recommended) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Star,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        stringResource(CoreR.string.core_planner_recommended),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }
        }
    }
}

/** Einstellungen wie im Wecker-Editor: Chips pro Wert. */
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
