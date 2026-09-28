package com.watchalarm.mobile

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.watchalarm.core.Alarm
import com.watchalarm.core.AlarmStore
import com.watchalarm.core.AlarmSync
import com.watchalarm.core.RuntimeStore
import com.watchalarm.core.SleepDuration
import com.watchalarm.core.SyncContract
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Calendar
import java.util.Locale
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    /**
     * Ab Android 14 ist "Vollbild-Benachrichtigung" eine eigene, vom Nutzer
     * widerrufbare Berechtigung. Fehlt sie, erscheint beim Klingeln nur eine
     * Benachrichtigung statt des Stopp-Screens — darauf weisen wir in der
     * Liste hin. Bei jedem onResume neu prüfen, damit der Hinweis nach dem
     * Erteilen sofort verschwindet.
     */
    private val fullScreenIntentBlocked = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        // Ab targetSdk 35 zeichnet Android immer randlos. Ohne
        // enableEdgeToEdge() erbt die Statusleiste die Icon-Farbe aus dem
        // Plattform-Theme — im hellen Modus also weiß auf hell, unsichtbar.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        AlarmSync.syncNow(this)

        setContent {
            val dark = androidx.compose.foundation.isSystemInDarkTheme()
            MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppRoot(fullScreenIntentBlocked.value)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        fullScreenIntentBlocked.value = Build.VERSION.SDK_INT >= 34 &&
            getSystemService(NotificationManager::class.java)?.canUseFullScreenIntent() == false
    }
}

@Composable
private fun AppRoot(fullScreenIntentBlocked: Boolean) {
    val context = LocalContext.current
    var alarms by remember { mutableStateOf(AlarmStore.getAlarms(context)) }
    // Irgendeiner der klingelnden Alarme genuegt: Das Banner fuehrt auf den
    // gemeinsamen Klingel-Screen, und gestoppt wird ohnehin alles zusammen.
    var ringingId by remember { mutableStateOf(RuntimeStore.getRingingAlarmIds(context).firstOrNull()) }
    // Über die ID statt über das Alarm-Objekt: Alarm ist nicht Parcelable,
    // und so überlebt der geöffnete Editor eine Drehung / Prozess-Neustart.
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var showEditor by rememberSaveable { mutableStateOf(false) }

    // Liste/Klingelstatus aktualisieren, wenn sich der Speicher ändert
    // (auch bei Sync von der Uhr).
    DisposableEffect(Unit) {
        val prefs = AlarmStore.prefs(context)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            alarms = AlarmStore.getAlarms(context)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        val runtimePrefs = RuntimeStore.prefs(context)
        val runtimeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            ringingId = RuntimeStore.getRingingAlarmIds(context).firstOrNull()
        }
        runtimePrefs.registerOnSharedPreferenceChangeListener(runtimeListener)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
            runtimePrefs.unregisterOnSharedPreferenceChangeListener(runtimeListener)
        }
    }

    if (showEditor) {
        EditorScreen(
            initial = editingId?.let { id -> alarms.firstOrNull { it.id == id } },
            onSave = { alarm ->
                AlarmStore.applyLocalChange(context) { list -> list.filter { it.id != alarm.id } + alarm }
                showEditor = false
            },
            onDelete = { alarm ->
                AlarmStore.applyLocalChange(context) { list -> list.filter { it.id != alarm.id } }
                showEditor = false
            },
            onBack = { showEditor = false },
        )
    } else {
        ListScreen(
            alarms = alarms.sortedWith(compareBy({ it.hour }, { it.minute })),
            ringingId = ringingId,
            fullScreenIntentBlocked = fullScreenIntentBlocked,
            onOpenRinging = { id ->
                context.startActivity(
                    Intent(context, AlarmActivity::class.java)
                        .putExtra(SyncContract.EXTRA_ALARM_ID, id)
                )
            },
            onAdd = { editingId = null; showEditor = true },
            onEdit = { editingId = it.id; showEditor = true },
            onToggle = { alarm, enabled ->
                AlarmStore.applyLocalChange(context) { list ->
                    list.map { if (it.id == alarm.id) it.copy(enabled = enabled) else it }
                }
            },
        )
    }
}

// ------------------------------------------------------------- Schlafdauer

/**
 * Aktuelle Zeit, die sich zur vollen Minute selbst aktualisiert — Grundlage
 * der Schlafdauer-Anzeige, damit "noch 7 Std. 30 Min." nicht stehen bleibt,
 * während die App offen ist.
 *
 * Der Takt läuft nur, solange die App im Vordergrund ist (repeatOnLifecycle);
 * im Hintergrund sieht ohnehin niemand die Liste.
 */
@Composable
private fun rememberCurrentMinute(): Long {
    val lifecycleOwner = LocalLifecycleOwner.current
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                val current = System.currentTimeMillis()
                now = current
                // Auf die nächste volle Minute takten, nicht stur 60 Sekunden
                // warten: so springt die Anzeige zusammen mit der Uhrzeit.
                delay(60_000L - current % 60_000L)
            }
        }
    }
    return now
}

// -------------------------------------------------------------- Wochentage

/** Ein Wochentag mit der Calendar-Konstante, die [Alarm.repeatDays] nutzt. */
private data class WeekDay(val calendarDay: Int, val label: String)

private fun DayOfWeek.toCalendarDay(): Int =
    if (this == DayOfWeek.SUNDAY) Calendar.SUNDAY else value + 1

/**
 * Wochentage in der Reihenfolge und Schreibweise der eingestellten Sprache —
 * in Deutschland beginnt die Woche montags, in den USA sonntags, und die
 * Kürzel unterscheiden sich ohnehin. Vorher war beides fest auf Deutsch
 * verdrahtet.
 */
@Composable
private fun rememberWeekDays(): List<WeekDay> {
    // LocalConfiguration als Key: nach einem Sprachwechsel neu berechnen.
    val configuration = LocalConfiguration.current
    return remember(configuration) {
        val locale = Locale.getDefault()
        val firstDay = WeekFields.of(locale).firstDayOfWeek
        (0L until 7L).map { offset ->
            val day = firstDay.plus(offset)
            WeekDay(
                calendarDay = day.toCalendarDay(),
                label = day.getDisplayName(TextStyle.SHORT, locale),
            )
        }
    }
}

@Composable
private fun repeatDaysLabel(days: Set<Int>, weekDays: List<WeekDay>): String = when {
    days.isEmpty() -> ""
    days.size == 7 -> stringResource(R.string.repeat_daily)
    else -> weekDays.filter { it.calendarDay in days }.joinToString(" ") { it.label }
}

// ------------------------------------------------------------------- Liste

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ListScreen(
    alarms: List<Alarm>,
    ringingId: String?,
    fullScreenIntentBlocked: Boolean,
    onOpenRinging: (String) -> Unit,
    onAdd: () -> Unit,
    onEdit: (Alarm) -> Unit,
    onToggle: (Alarm, Boolean) -> Unit,
) {
    val context = LocalContext.current
    val now = rememberCurrentMinute()
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) },
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.add_alarm))
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(modifier = Modifier.fillMaxSize()) {
                if (ringingId != null) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, top = 16.dp)
                            .clickable { onOpenRinging(ringingId) },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                        ),
                    ) {
                        Text(
                            stringResource(R.string.alarm_active_tap),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
                if (fullScreenIntentBlocked) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, top = 16.dp)
                            .clickable {
                                runCatching {
                                    context.startActivity(
                                        Intent(
                                            Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                                            Uri.parse("package:${context.packageName}")
                                        )
                                    )
                                }
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        ),
                    ) {
                        Text(
                            stringResource(R.string.full_screen_intent_warning),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
                if (alarms.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            stringResource(R.string.empty_list),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        // Unten extra Platz, sonst verdeckt der FAB den
                        // letzten Wecker (und dessen Schalter).
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(alarms, key = { it.id }) { alarm ->
                            AlarmCard(
                                alarm = alarm,
                                now = now,
                                onClick = { onEdit(alarm) },
                                onToggle = { onToggle(alarm, it) },
                            )
                        }
                    }
                }
            }
            Text(
                stringResource(R.string.version_label, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.BottomStart).padding(8.dp),
            )
        }
    }
}

@Composable
private fun AlarmCard(alarm: Alarm, now: Long, onClick: () -> Unit, onToggle: (Boolean) -> Unit) {
    val context = LocalContext.current
    val weekDays = rememberWeekDays()
    val daysLabel = repeatDaysLabel(alarm.repeatDays, weekDays)
    // Nur für aktive Wecker: bei ausgeschaltetem Alarm gäbe es keinen
    // Zeitpunkt, bis zu dem man schlafen könnte.
    val sleepDuration = remember(alarm, now) {
        if (alarm.enabled) SleepDuration.formatUntil(context, alarm, now) else null
    }
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    alarm.formattedTime(context),
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Light,
                    color = if (alarm.enabled) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val subtitle = buildString {
                    if (alarm.label.isNotBlank()) append(alarm.label)
                    if (daysLabel.isNotBlank()) {
                        if (isNotEmpty()) append(" · ")
                        append(daysLabel)
                    }
                }
                if (subtitle.isNotBlank()) {
                    Text(subtitle, style = MaterialTheme.typography.bodyMedium)
                }
                if (sleepDuration != null) {
                    Text(
                        stringResource(R.string.sleep_duration, sleepDuration),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Switch(checked = alarm.enabled, onCheckedChange = onToggle)
        }
    }
}

/** Set<Int> ist nicht bundle-fähig, deshalb über eine Liste sichern. */
private val intSetSaver = listSaver<Set<Int>, Int>(
    save = { it.toList() },
    restore = { it.toSet() },
)

// ------------------------------------------------------------------ Editor

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun EditorScreen(
    initial: Alarm?,
    onSave: (Alarm) -> Unit,
    onDelete: (Alarm) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    BackHandler(onBack = onBack)

    val timeState = rememberTimePickerState(
        initialHour = initial?.hour ?: 7,
        initialMinute = initial?.minute ?: 0,
        is24Hour = android.text.format.DateFormat.is24HourFormat(context),
    )
    // rememberSaveable statt remember: sonst sind alle Eingaben nach einer
    // Drehung (oder auf einem Foldable beim Auf-/Zuklappen) wieder weg.
    var label by rememberSaveable { mutableStateOf(initial?.label ?: "") }
    var repeatDays by rememberSaveable(stateSaver = intSetSaver) {
        mutableStateOf(initial?.repeatDays ?: emptySet())
    }
    var snoozeMinutes by rememberSaveable { mutableStateOf(initial?.snoozeMinutes ?: 5) }
    var maxSnoozes by rememberSaveable { mutableStateOf(initial?.maxSnoozes ?: 3) }
    var ringTimeoutMinutes by rememberSaveable {
        mutableStateOf(initial?.ringTimeoutMinutes ?: Alarm.DEFAULT_RING_TIMEOUT_MINUTES)
    }

    val weekDays = rememberWeekDays()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (initial == null) R.string.title_new_alarm else R.string.title_edit_alarm
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
                actions = {
                    if (initial != null) {
                        IconButton(onClick = { onDelete(initial) }) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = stringResource(R.string.delete),
                            )
                        }
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
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TimePicker(state = timeState)
            }

            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                label = { Text(stringResource(R.string.field_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Column {
                Text(stringResource(R.string.section_repeat), style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                // FlowRow statt Row: in einer Row liefen die sieben Chips auf
                // schmalen Geräten (und bei großer Schrift) rechts aus dem
                // Bild — die letzten Tage waren nicht erreichbar.
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    weekDays.forEach { day ->
                        FilterChip(
                            selected = day.calendarDay in repeatDays,
                            onClick = {
                                repeatDays = if (day.calendarDay in repeatDays) {
                                    repeatDays - day.calendarDay
                                } else {
                                    repeatDays + day.calendarDay
                                }
                            },
                            label = { Text(day.label) },
                        )
                    }
                }
            }

            HorizontalDivider()

            Column {
                Text(
                    stringResource(R.string.section_snooze_duration),
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf(3, 5, 10, 15, 30).forEach { min ->
                        FilterChip(
                            selected = snoozeMinutes == min,
                            onClick = { snoozeMinutes = min },
                            label = { Text(stringResource(R.string.snooze_minutes_chip, min)) },
                        )
                    }
                }
            }

            Column {
                Text(
                    stringResource(R.string.section_snooze_count),
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf(0, 1, 2, 3, 5, 10).forEach { n ->
                        FilterChip(
                            selected = maxSnoozes == n,
                            onClick = { maxSnoozes = n },
                            label = {
                                Text(
                                    if (n == 0) stringResource(R.string.snooze_off)
                                    else stringResource(R.string.snooze_times_chip, n)
                                )
                            },
                        )
                    }
                }
            }

            Column {
                Text(
                    stringResource(R.string.section_ring_timeout),
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Alarm.RING_TIMEOUT_CHOICES.forEach { min ->
                        FilterChip(
                            selected = ringTimeoutMinutes == min,
                            onClick = { ringTimeoutMinutes = min },
                            label = { Text(stringResource(R.string.snooze_minutes_chip, min)) },
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.section_ring_timeout_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text(
                stringResource(R.string.editor_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Button(
                onClick = {
                    onSave(
                        (initial ?: Alarm()).copy(
                            hour = timeState.hour,
                            minute = timeState.minute,
                            label = label.trim(),
                            enabled = true,
                            repeatDays = repeatDays,
                            snoozeMinutes = snoozeMinutes,
                            maxSnoozes = maxSnoozes,
                            ringTimeoutMinutes = ringTimeoutMinutes,
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text(stringResource(R.string.save), fontSize = 16.sp)
            }
        }
    }
}
