package com.watchalarm.mobile

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.watchalarm.core.Alarm
import com.watchalarm.core.AlarmStore
import com.watchalarm.core.AlarmSync
import com.watchalarm.core.RuntimeStore
import com.watchalarm.core.SyncContract
import java.util.Calendar

class MainActivity : ComponentActivity() {

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
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
                    AppRoot()
                }
            }
        }
    }
}

@Composable
private fun AppRoot() {
    val context = LocalContext.current
    var alarms by remember { mutableStateOf(AlarmStore.getAlarms(context)) }
    var ringingId by remember { mutableStateOf(RuntimeStore.getRingingAlarmId(context)) }
    var editing by remember { mutableStateOf<Alarm?>(null) }
    var showEditor by remember { mutableStateOf(false) }

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
            ringingId = RuntimeStore.getRingingAlarmId(context)
        }
        runtimePrefs.registerOnSharedPreferenceChangeListener(runtimeListener)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
            runtimePrefs.unregisterOnSharedPreferenceChangeListener(runtimeListener)
        }
    }

    if (showEditor) {
        EditorScreen(
            initial = editing,
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
            onOpenRinging = { id ->
                context.startActivity(
                    Intent(context, AlarmActivity::class.java)
                        .putExtra(SyncContract.EXTRA_ALARM_ID, id)
                )
            },
            onAdd = { editing = null; showEditor = true },
            onEdit = { editing = it; showEditor = true },
            onToggle = { alarm, enabled ->
                AlarmStore.applyLocalChange(context) { list ->
                    list.map { if (it.id == alarm.id) it.copy(enabled = enabled) else it }
                }
            },
        )
    }
}

// ------------------------------------------------------------------- Liste

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ListScreen(
    alarms: List<Alarm>,
    ringingId: String?,
    onOpenRinging: (String) -> Unit,
    onAdd: () -> Unit,
    onEdit: (Alarm) -> Unit,
    onToggle: (Alarm, Boolean) -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("WatchAlarm") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd) {
                Icon(Icons.Filled.Add, contentDescription = "Alarm hinzufügen")
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
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
                        "🔔 Alarm aktiv — tippen zum Ausschalten",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            if (alarms.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Noch keine Alarme.\nTippe auf +, um einen Wecker anzulegen.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(alarms, key = { it.id }) { alarm ->
                        AlarmCard(alarm, onClick = { onEdit(alarm) }, onToggle = { onToggle(alarm, it) })
                    }
                }
            }
        }
    }
}

@Composable
private fun AlarmCard(alarm: Alarm, onClick: () -> Unit, onToggle: (Boolean) -> Unit) {
    val context = LocalContext.current
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
                    val days = repeatDaysLabel(alarm.repeatDays)
                    if (days.isNotBlank()) {
                        if (isNotEmpty()) append(" · ")
                        append(days)
                    }
                }
                if (subtitle.isNotBlank()) {
                    Text(subtitle, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Switch(checked = alarm.enabled, onCheckedChange = onToggle)
        }
    }
}

private val dayOrder = listOf(
    Calendar.MONDAY to "Mo", Calendar.TUESDAY to "Di", Calendar.WEDNESDAY to "Mi",
    Calendar.THURSDAY to "Do", Calendar.FRIDAY to "Fr", Calendar.SATURDAY to "Sa",
    Calendar.SUNDAY to "So",
)

private fun repeatDaysLabel(days: Set<Int>): String = when {
    days.isEmpty() -> ""
    days.size == 7 -> "Täglich"
    else -> dayOrder.filter { it.first in days }.joinToString(" ") { it.second }
}

// ------------------------------------------------------------------ Editor

@OptIn(ExperimentalMaterial3Api::class)
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
    var label by remember { mutableStateOf(initial?.label ?: "") }
    var repeatDays by remember { mutableStateOf(initial?.repeatDays ?: emptySet()) }
    var snoozeMinutes by remember { mutableStateOf(initial?.snoozeMinutes ?: 5) }
    var maxSnoozes by remember { mutableStateOf(initial?.maxSnoozes ?: 3) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (initial == null) "Neuer Wecker" else "Wecker bearbeiten") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
                actions = {
                    if (initial != null) {
                        IconButton(onClick = { onDelete(initial) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Löschen")
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
                label = { Text("Bezeichnung") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Column {
                Text("Wiederholen", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    dayOrder.forEach { (day, name) ->
                        FilterChip(
                            selected = day in repeatDays,
                            onClick = {
                                repeatDays = if (day in repeatDays) repeatDays - day else repeatDays + day
                            },
                            label = { Text(name) },
                        )
                    }
                }
            }

            HorizontalDivider()

            Column {
                Text("Snooze-Dauer", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(3, 5, 10, 15, 30).forEach { min ->
                        FilterChip(
                            selected = snoozeMinutes == min,
                            onClick = { snoozeMinutes = min },
                            label = { Text("$min min") },
                        )
                    }
                }
            }

            Column {
                Text("Snooze-Anzahl (maximal)", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0, 1, 2, 3, 5, 10).forEach { n ->
                        FilterChip(
                            selected = maxSnoozes == n,
                            onClick = { maxSnoozes = n },
                            label = { Text(if (n == 0) "Aus" else "$n×") },
                        )
                    }
                }
            }

            Text(
                "Die Uhr vibriert, das Handy zeigt diesen Stopp-Screen. " +
                    "Ausgeschaltet wird am Handy.",
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
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text("Speichern", fontSize = 16.sp)
            }
        }
    }
}
