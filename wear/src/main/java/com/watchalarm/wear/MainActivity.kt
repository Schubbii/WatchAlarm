package com.watchalarm.wear

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.SplitToggleChip
import androidx.wear.compose.material.Switch
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import com.watchalarm.core.Alarm
import com.watchalarm.core.AlarmStore
import com.watchalarm.core.AlarmSync
import com.watchalarm.core.RuntimeStore
import com.watchalarm.core.SleepDuration
import com.watchalarm.core.SyncContract

class MainActivity : ComponentActivity() {

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Ohne diese Berechtigung zeigt das System ab API 33 weder die
        // Alarm-Benachrichtigung noch den Vollbild-Klingelbildschirm!
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        AlarmSync.syncNow(this)
        setContent {
            MaterialTheme {
                WearApp()
            }
        }
    }
}

@Composable
private fun WearApp() {
    val context = LocalContext.current
    var alarms by remember { mutableStateOf(AlarmStore.getAlarms(context)) }
    // Irgendeiner der klingelnden Alarme genuegt: Das Banner fuehrt auf den
    // gemeinsamen Klingel-Screen, und gestoppt wird ohnehin alles zusammen.
    var ringingId by remember { mutableStateOf(RuntimeStore.getRingingAlarmIds(context).firstOrNull()) }
    // ID statt Alarm-Objekt, damit der geöffnete Editor einen
    // Prozess-Neustart übersteht (Alarm ist nicht Parcelable).
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var showEditor by rememberSaveable { mutableStateOf(false) }
    var showPlanner by rememberSaveable { mutableStateOf(false) }

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

    if (showPlanner) {
        SleepPlannerScreen(onBack = { showPlanner = false })
    } else if (showEditor) {
        WatchEditor(
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
        WatchList(
            alarms = alarms.sortedWith(compareBy({ it.hour }, { it.minute })),
            ringingId = ringingId,
            onOpenRinging = { id ->
                context.startActivity(
                    Intent(context, WatchRingActivity::class.java)
                        .putExtra(SyncContract.EXTRA_ALARM_ID, id)
                )
            },
            onAdd = { editingId = null; showEditor = true },
            onOpenPlanner = { showPlanner = true },
            onEdit = { editingId = it.id; showEditor = true },
            onToggle = { alarm, enabled ->
                AlarmStore.applyLocalChange(context) { list ->
                    list.map { if (it.id == alarm.id) it.copy(enabled = enabled) else it }
                }
            },
        )
    }
}

// ------------------------------------------------------------------- Liste

@Composable
private fun WatchList(
    alarms: List<Alarm>,
    ringingId: String?,
    onOpenRinging: (String) -> Unit,
    onAdd: () -> Unit,
    onOpenPlanner: () -> Unit,
    onEdit: (Alarm) -> Unit,
    onToggle: (Alarm, Boolean) -> Unit,
) {
    val context = LocalContext.current
    val now = rememberCurrentMinute()
    val listState = rememberScalingLazyListState()
    Scaffold(timeText = { TimeText() }) {
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().rotaryScroll(listState),
        ) {
            item { ListHeader { Text(stringResource(R.string.title_alarms)) } }
            if (ringingId != null) {
                item {
                    Chip(
                        onClick = { onOpenRinging(ringingId) },
                        label = { Text(stringResource(R.string.alarm_active_open)) },
                        colors = ChipDefaults.primaryChipColors(
                            backgroundColor = MaterialTheme.colors.error,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            items(alarms, key = { it.id }) { alarm ->
                // Zweitzeile: bei aktivem Wecker die Schlafdauer bis zum
                // Klingeln, dahinter die Bezeichnung. Auf der Uhr ist nur
                // eine Zeile Platz und die wird hinten abgeschnitten —
                // deshalb steht die Dauer vorn.
                val secondary = remember(alarm, now) {
                    listOfNotNull(
                        if (alarm.enabled) {
                            context.getString(
                                R.string.sleep_duration,
                                SleepDuration.formatUntil(context, alarm, now),
                            )
                        } else {
                            null
                        },
                        alarm.label.takeIf { it.isNotBlank() },
                    ).joinToString(" · ")
                }
                SplitToggleChip(
                    checked = alarm.enabled,
                    onCheckedChange = { onToggle(alarm, it) },
                    onClick = { onEdit(alarm) },
                    label = { Text(alarm.formattedTime(context)) },
                    secondaryLabel = { if (secondary.isNotBlank()) Text(secondary) },
                    toggleControl = { Switch(checked = alarm.enabled) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Chip(
                    onClick = onAdd,
                    label = { Text(stringResource(R.string.new_alarm)) },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            // Direkt unter "Neuer Wecker": Der Planer endet meist in einem
            // neuen Wecker und gehört damit zur selben Handlung.
            item {
                Chip(
                    onClick = onOpenPlanner,
                    label = { Text(stringResource(R.string.planner_entry)) },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Text(
                    stringResource(R.string.version_label, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.caption3,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                )
            }
        }
    }
}

// ------------------------------------------------------------------ Editor

@Composable
private fun WatchEditor(
    initial: Alarm?,
    onSave: (Alarm) -> Unit,
    onDelete: (Alarm) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)

    val timeState = rememberWatchTimeState(
        initialHour = initial?.hour ?: 7,
        initialMinute = initial?.minute ?: 0,
    )

    val listState = rememberScalingLazyListState()
    Scaffold(timeText = { TimeText() }) {
        ScalingLazyColumn(
            state = listState,
            // Kein rotaryScroll: Auf diesem Screen gehört die Krone den
            // Pickern, gescrollt wird gewischt. Eine Krone, die die Liste
            // verschiebt, statt die Uhrzeit zu stellen, wäre die deutlich
            // schlechtere Hälfte des Tauschs.
            modifier = Modifier.fillMaxSize().rotaryTimePicker(timeState),
        ) {
            item {
                ListHeader {
                    Text(
                        stringResource(
                            if (initial == null) R.string.new_alarm else R.string.title_edit_alarm
                        )
                    )
                }
            }
            item { WatchTimePickerRow(timeState) }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Button(
                        onClick = {
                            onSave(
                                (initial ?: Alarm()).copy(
                                    hour = timeState.hourOfDay,
                                    minute = timeState.minuteOfHour,
                                    enabled = true,
                                )
                            )
                        },
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.save))
                    }
                    if (initial != null) {
                        Spacer(Modifier.width(12.dp))
                        Button(
                            onClick = { onDelete(initial) },
                            colors = ButtonDefaults.secondaryButtonColors(),
                        ) {
                            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.delete))
                        }
                    }
                }
            }
            item {
                Text(
                    stringResource(R.string.editor_hint),
                    style = MaterialTheme.typography.caption3,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                )
            }
        }
    }
}
