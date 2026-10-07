package com.watchalarm.mobile

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
import java.text.DateFormatSymbols
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.launch

/**
 * Schlafplaner am Handy — dieselbe Rechnung ([SleepPlanner]) und dieselben
 * Texte ([SleepPlannerFormat]) wie auf der Uhr.
 *
 * Im Rise-Design wie Editor und Liste: Titel in Instrument Serif,
 * Abschnitte mit [Eyebrow], Chips in Creme bzw. Tinte, die Drehwalze für die
 * Weckzeit, Vorschläge als weiße Karten mit Serif-Uhrzeit, Akzent in
 * Terrakotta. Kein Dialog: Ein gestellter Wecker führt wie „Speichern"
 * zurück in die Liste.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun SleepPlannerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = Rise.colors
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

    // Weckzeit über dieselbe Walze wie im Editor, im Uhrzeitformat des Geräts.
    val is24Hour = remember(context) { android.text.format.DateFormat.is24HourFormat(context) }
    val initialWake = remember { SleepPlannerStore.getWakeTime(context) }
    var wakeHour by rememberSaveable { mutableIntStateOf(initialWake.hour) }
    var wakeMinute by rememberSaveable { mutableIntStateOf(initialWake.minute) }
    val wakeTime = LocalTime.of(wakeHour, wakeMinute)
    LaunchedEffect(wakeTime) { SleepPlannerStore.setWakeTime(context, wakeTime) }

    val nowZoned = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault())
    val suggestions = when (mode) {
        PlannerMode.SLEEP_NOW -> SleepPlanner.wakeTimes(nowZoned, settings)
        PlannerMode.WAKE_UP_AT -> SleepPlanner.bedtimes(wakeTime, nowZoned, settings)
    }
    val recommended = SleepPlanner.recommendedCycles(summary)

    Scaffold(
        containerColor = colors.surface,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.surface,
                    scrolledContainerColor = colors.surface,
                    titleContentColor = colors.ink,
                    navigationIconContentColor = colors.ink,
                ),
                title = {
                    Text(
                        stringResource(CoreR.string.core_planner_title),
                        style = MaterialTheme.typography.headlineMedium,
                        fontSize = 30.sp,
                    )
                },
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
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // Betriebsart als Chips wie die Auswahlen im Editor.
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                listOf(
                    PlannerMode.SLEEP_NOW to CoreR.string.core_planner_mode_now,
                    PlannerMode.WAKE_UP_AT to CoreR.string.core_planner_mode_wake,
                ).forEach { (option, label) ->
                    RiseChip(selected = mode == option, onClick = { mode = option }, label = stringResource(label))
                }
            }

            if (mode == PlannerMode.WAKE_UP_AT) {
                DrumFrame {
                    val amPmLabels = remember { DateFormatSymbols.getInstance().amPmStrings }
                    DrumColumn(
                        count = if (is24Hour) 24 else 12,
                        initial = if (is24Hour) wakeHour else wakeHour % 12,
                        onSelected = { selected ->
                            wakeHour = if (is24Hour) selected else selected + if (wakeHour >= 12) 12 else 0
                        },
                        contentDescription = stringResource(R.string.picker_hour),
                        label = { index ->
                            if (is24Hour) "%02d".format(index)
                            else if (index == 0) "12" else "$index"
                        },
                    )
                    Text(
                        ":",
                        fontFamily = InstrumentSerif,
                        fontSize = with(LocalDensity.current) { 34.dp.toSp() },
                        color = colors.accent,
                    )
                    DrumColumn(
                        count = 60,
                        initial = wakeMinute,
                        onSelected = { wakeMinute = it },
                        contentDescription = stringResource(R.string.picker_minute),
                        label = { "%02d".format(it) },
                    )
                    if (!is24Hour) {
                        DrumColumn(
                            count = 2,
                            initial = if (wakeHour >= 12) 1 else 0,
                            onSelected = { pm -> wakeHour = wakeHour % 12 + if (pm == 1) 12 else 0 },
                            contentDescription = stringResource(R.string.picker_am_pm),
                            label = { amPmLabels.getOrElse(it) { if (it == 0) "AM" else "PM" } },
                            wrap = false,
                            weight = 0.9f,
                        )
                    }
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
                Eyebrow(
                    stringResource(
                        if (mode == PlannerMode.SLEEP_NOW) CoreR.string.core_planner_waketimes_header
                        else CoreR.string.core_planner_bedtimes_header
                    )
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
                color = colors.ink3,
            )

            HorizontalDivider(color = colors.lineB)

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

/** Chip wie im Editor: Creme, gewählt in Tinte, rund, ohne Rahmen. */
@Composable
private fun RiseChip(selected: Boolean, onClick: () -> Unit, label: String) {
    val colors = Rise.colors
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = colors.chip,
            labelColor = colors.ink2,
            selectedContainerColor = colors.btnBg,
            selectedLabelColor = colors.btnFg,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = Color.Transparent,
            selectedBorderColor = Color.Transparent,
        ),
        shape = CircleShape,
    )
}

/** Weiße Karte mit feiner Linie, wie die Wecker-Karten der Liste. */
@Composable
private fun RiseCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val colors = Rise.colors
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = colors.card, contentColor = colors.ink),
        border = BorderStroke(1.dp, colors.lineA),
    ) { content() }
}

/**
 * Zusammenfassung der letzten Nächte in Akzentfarbe, wie die Schlafdauer
 * auf den Wecker-Karten. Solange es keine gibt: der Hinweis zum Verbinden,
 * gestaltet wie der Vollbild-Hinweis der Liste (Creme-Karte, 22dp).
 * Ohne Daten und nach „Nicht jetzt" erscheint nichts.
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
    val colors = Rise.colors
    val needsInstall = status == SleepHealth.Status.NEEDS_INSTALL
    when {
        summary != null -> Column {
            Text(
                stringResource(R.string.planner_summary, SleepPlannerFormat.average(context, summary)),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.accent,
            )
            Text(
                SleepPlannerFormat.debt(context, summary),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.ink2,
            )
        }
        promptDismissed -> Unit
        status == SleepHealth.Status.NOT_GRANTED || needsInstall -> Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = if (needsInstall) onInstall else onConnect),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = colors.chip, contentColor = colors.ink2),
        ) {
            // Die Begründung steht hier, *bevor* der Systemdialog kommt.
            Text(
                stringResource(R.string.health_explanation),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 18.dp),
            )
            Row(modifier = Modifier.fillMaxWidth().padding(end = 8.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismissPrompt) {
                    Text(stringResource(R.string.health_not_now), color = colors.ink2)
                }
                TextButton(onClick = if (needsInstall) onInstall else onConnect) {
                    Text(
                        stringResource(if (needsInstall) R.string.health_install else R.string.health_connect),
                        color = colors.accent,
                    )
                }
            }
        }
        else -> Unit
    }
}

/**
 * Ein Vorschlag wie eine Wecker-Karte: Uhrzeit groß in Serif, Zyklen und
 * Dauer darunter, die Empfehlung in Akzentfarbe. Vergangene Schlafenszeiten
 * sehen aus wie ein ausgeschalteter Wecker und sind nicht antippbar.
 */
@Composable
private fun SuggestionCard(suggestion: SleepSuggestion, recommended: Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
    val colors = Rise.colors
    RiseCard(modifier = Modifier.clickable(enabled = suggestion.available, onClick = onClick)) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            Text(
                SleepPlannerFormat.time(context, suggestion.time),
                style = SerifNumerals,
                fontSize = 40.sp,
                lineHeight = 42.sp,
                color = if (suggestion.available) colors.ink else colors.ink3,
            )
            Text(
                SleepPlannerFormat.details(context, suggestion),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.ink2,
            )
            if (recommended) {
                Text(
                    stringResource(R.string.planner_recommended, stringResource(CoreR.string.core_planner_recommended)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.accent,
                )
            }
        }
    }
}

/** Gestellte Erinnerung — wie ein Wecker mit Schalter; aus = gelöscht. */
@Composable
private fun ReminderCard(time: String, onCancel: () -> Unit) {
    val colors = Rise.colors
    RiseCard {
        Row(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(CoreR.string.core_planner_reminder_active, time),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = true,
                onCheckedChange = { if (!it) onCancel() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = colors.card,
                    checkedTrackColor = colors.accent,
                    checkedBorderColor = colors.accent,
                ),
            )
        }
    }
}

/** Einstellungen wie im Editor: Eyebrow und Chips pro Wert. */
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
    val colors = Rise.colors
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text(
            stringResource(CoreR.string.core_planner_settings),
            style = MaterialTheme.typography.headlineSmall,
            color = colors.ink,
        )

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
            Eyebrow(stringResource(R.string.health_section))
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
                    color = colors.ink2,
                    modifier = Modifier.weight(1f),
                )
                val action: Pair<Int, () -> Unit>? = when (healthStatus) {
                    SleepHealth.Status.GRANTED -> R.string.health_manage to onManage
                    SleepHealth.Status.NOT_GRANTED -> R.string.health_connect to onConnect
                    SleepHealth.Status.NEEDS_INSTALL -> R.string.health_install to onInstall
                    else -> null
                }
                if (action != null) {
                    TextButton(onClick = action.second) {
                        Text(stringResource(action.first), color = colors.accent)
                    }
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
        Eyebrow(title)
        Spacer(Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            choices.forEach { value ->
                RiseChip(selected = selected == value, onClick = { onSelect(value) }, label = label(value))
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
