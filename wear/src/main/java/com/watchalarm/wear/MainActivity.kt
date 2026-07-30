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
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Picker
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.SplitToggleChip
import androidx.wear.compose.material.Switch
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.rememberPickerState
import com.watchalarm.core.Alarm
import com.watchalarm.core.AlarmStore
import com.watchalarm.core.AlarmSync
import com.watchalarm.core.AppInfo
import com.watchalarm.core.RuntimeStore
import com.watchalarm.core.SyncContract
import java.text.DateFormatSymbols
import kotlinx.coroutines.launch

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

/**
 * Krone bzw. drehbare Lünette bedienen: Wear liefert Rotary-Events nur an
 * eine fokussierte Komponente. Ohne das hier ließ sich die Liste auf Pixel
 * Watch und Galaxy Watch ausschließlich per Wischen scrollen.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun Modifier.rotaryScroll(state: ScalingLazyListState): Modifier {
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(focusRequester) {
        runCatching { focusRequester.requestFocus() }
    }
    return this
        .onRotaryScrollEvent { event ->
            scope.launch { state.scrollBy(event.verticalScrollPixels) }
            true
        }
        .focusRequester(focusRequester)
        .focusable()
}

@Composable
private fun WearApp() {
    val context = LocalContext.current
    var alarms by remember { mutableStateOf(AlarmStore.getAlarms(context)) }
    var ringingId by remember { mutableStateOf(RuntimeStore.getRingingAlarmId(context)) }
    // ID statt Alarm-Objekt, damit der geöffnete Editor einen
    // Prozess-Neustart übersteht (Alarm ist nicht Parcelable).
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var showEditor by rememberSaveable { mutableStateOf(false) }

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
    onEdit: (Alarm) -> Unit,
    onToggle: (Alarm, Boolean) -> Unit,
) {
    val context = LocalContext.current
    val listState = rememberScalingLazyListState()
    Scaffold(timeText = { TimeText() }) {
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().rotaryScroll(listState),
        ) {
            item { ListHeader { Text("Wecker") } }
            if (ringingId != null) {
                item {
                    Chip(
                        onClick = { onOpenRinging(ringingId) },
                        label = { Text("🔔 Alarm aktiv — öffnen") },
                        colors = ChipDefaults.primaryChipColors(
                            backgroundColor = MaterialTheme.colors.error,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            items(alarms, key = { it.id }) { alarm ->
                SplitToggleChip(
                    checked = alarm.enabled,
                    onCheckedChange = { onToggle(alarm, it) },
                    onClick = { onEdit(alarm) },
                    label = { Text(alarm.formattedTime(context)) },
                    secondaryLabel = { if (alarm.label.isNotBlank()) Text(alarm.label) },
                    toggleControl = { Switch(checked = alarm.enabled) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Chip(
                    onClick = onAdd,
                    label = { Text("Neuer Wecker") },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Text(
                    "v ${AppInfo.VERSION}",
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
    val context = LocalContext.current
    BackHandler(onBack = onBack)

    // Das Uhrzeitformat des Geräts übernehmen: auf 12-Stunden-Geräten stand
    // im Editor "13", in der Liste aber "1:00 PM".
    val is24Hour = remember(context) { android.text.format.DateFormat.is24HourFormat(context) }
    val initialHour = initial?.hour ?: 7

    val hourState = rememberPickerState(
        initialNumberOfOptions = if (is24Hour) 24 else 12,
        initiallySelectedOption = if (is24Hour) initialHour else initialHour % 12,
    )
    val minuteState = rememberPickerState(
        initialNumberOfOptions = 60,
        initiallySelectedOption = initial?.minute ?: 0,
    )
    val amPmState = rememberPickerState(
        initialNumberOfOptions = 2,
        initiallySelectedOption = if (initialHour >= 12) 1 else 0,
    )
    val amPmLabels = remember { DateFormatSymbols.getInstance().amPmStrings }
    val pickerWidth = if (is24Hour) 60.dp else 44.dp

    val listState = rememberScalingLazyListState()
    Scaffold(timeText = { TimeText() }) {
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
        ) {
            item { ListHeader { Text(if (initial == null) "Neuer Wecker" else "Bearbeiten") } }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().height(100.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Picker(
                        state = hourState,
                        contentDescription = "Stunde",
                        modifier = Modifier.width(pickerWidth).fillMaxSize(),
                    ) { index ->
                        Text(
                            if (is24Hour) "%02d".format(index)
                            else if (index == 0) "12" else "$index",
                            fontSize = 28.sp,
                        )
                    }
                    Text(":", fontSize = 28.sp)
                    Picker(
                        state = minuteState,
                        contentDescription = "Minute",
                        modifier = Modifier.width(pickerWidth).fillMaxSize(),
                    ) { index ->
                        Text("%02d".format(index), fontSize = 28.sp)
                    }
                    if (!is24Hour) {
                        Picker(
                            state = amPmState,
                            contentDescription = "Vormittag oder Nachmittag",
                            modifier = Modifier.width(48.dp).fillMaxSize(),
                        ) { index ->
                            Text(amPmLabels.getOrElse(index) { if (index == 0) "AM" else "PM" }, fontSize = 20.sp)
                        }
                    }
                }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Button(
                        onClick = {
                            val hour = if (is24Hour) {
                                hourState.selectedOption
                            } else {
                                (hourState.selectedOption % 12) +
                                    if (amPmState.selectedOption == 1) 12 else 0
                            }
                            onSave(
                                (initial ?: Alarm()).copy(
                                    hour = hour,
                                    minute = minuteState.selectedOption,
                                    enabled = true,
                                )
                            )
                        },
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = "Speichern")
                    }
                    if (initial != null) {
                        Spacer(Modifier.width(12.dp))
                        Button(
                            onClick = { onDelete(initial) },
                            colors = ButtonDefaults.secondaryButtonColors(),
                        ) {
                            Icon(Icons.Filled.Delete, contentDescription = "Löschen")
                        }
                    }
                }
            }
            item {
                Text(
                    "Uhr vibriert · Ausschalten am Handy · Details in der Handy-App",
                    style = MaterialTheme.typography.caption3,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                )
            }
        }
    }
}
