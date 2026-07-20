package com.watchalarm.wear

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.core.content.ContextCompat
import com.watchalarm.core.Alarm
import com.watchalarm.core.AlarmScheduler
import com.watchalarm.core.AlarmService
import com.watchalarm.core.AlarmStore
import com.watchalarm.core.AlarmSync
import com.watchalarm.core.RuntimeStore
import com.watchalarm.core.SyncContract
import kotlinx.coroutines.delay

/**
 * Klingelansicht auf der Uhr.
 *
 * Bei Alarmen mit "Nur am Handy ausschaltbar" gibt es hier keinen
 * Stopp-Button — außer das Handy ist nicht (mehr) verbunden: Dann wird
 * nach kurzer Karenzzeit ein Notfall-Stopp eingeblendet, damit der Alarm
 * niemals unabschaltbar weiterklingelt.
 */
class WatchRingActivity : ComponentActivity() {

    private val stopReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val filter = IntentFilter(SyncContract.ACTION_RING_STOPPED)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(stopReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(stopReceiver, filter)
        }

        val alarmId = intent.getStringExtra(SyncContract.EXTRA_ALARM_ID)
        val alarm = alarmId?.let { AlarmStore.getAlarm(this, it) }
        if (alarm == null) {
            finish()
            return
        }

        // Auch von hier den Service starten: Falls der Receiver-Pfad vom
        // System blockiert wurde, klingelt es trotzdem (doppelter Start ist
        // im Service abgefangen).
        ContextCompat.startForegroundService(
            this,
            Intent(this, AlarmService::class.java)
                .setAction(AlarmService.ACTION_START)
                .putExtra(SyncContract.EXTRA_ALARM_ID, alarmId)
                .putExtra(
                    AlarmScheduler.EXTRA_IS_SNOOZE,
                    intent.getBooleanExtra(AlarmScheduler.EXTRA_IS_SNOOZE, false)
                )
        )

        setContent {
            MaterialTheme {
                RingScreen(alarm)
            }
        }
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(stopReceiver) }
        super.onDestroy()
    }

    private fun sendServiceAction(action: String) {
        startService(Intent(this, AlarmService::class.java).setAction(action))
    }

    @Composable
    private fun RingScreen(alarm: Alarm) {
        val snoozeAvailable = alarm.snoozeMinutes > 0 &&
            RuntimeStore.getSnoozeCount(this, alarm.id) < alarm.maxSnoozes

        // Verbindungs-Überwachung: alle 3 s prüfen, ob das Handy als Node
        // erreichbar ist. Nach DISCONNECT_GRACE_MS ohne Verbindung wird der
        // Notfall-Stopp freigeschaltet.
        var phoneConnected by remember { mutableStateOf(true) }
        var disconnectedSince by remember { mutableLongStateOf(0L) }
        var emergencyDismiss by remember { mutableStateOf(false) }

        if (alarm.phoneOnlyDismiss) {
            LaunchedEffect(Unit) {
                while (true) {
                    val connected = AlarmSync.isPeerConnected(this@WatchRingActivity)
                    val now = System.currentTimeMillis()
                    if (connected) {
                        phoneConnected = true
                        disconnectedSince = 0L
                        emergencyDismiss = false
                    } else {
                        phoneConnected = false
                        if (disconnectedSince == 0L) disconnectedSince = now
                        if (now - disconnectedSince >= DISCONNECT_GRACE_MS) {
                            emergencyDismiss = true
                        }
                    }
                    delay(3_000)
                }
            }
        }

        val canDismissHere = !alarm.phoneOnlyDismiss || emergencyDismiss

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                alarm.formattedTime(this@WatchRingActivity),
                fontSize = 40.sp,
                fontWeight = FontWeight.Light,
            )
            if (alarm.label.isNotBlank()) {
                Text(
                    alarm.label,
                    style = MaterialTheme.typography.body2,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(12.dp))

            if (canDismissHere) {
                if (alarm.phoneOnlyDismiss) {
                    Text(
                        "Handy nicht verbunden",
                        style = MaterialTheme.typography.caption2,
                        color = MaterialTheme.colors.error,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(6.dp))
                }
                Chip(
                    onClick = { sendServiceAction(AlarmService.ACTION_DISMISS) },
                    label = { Text("Stopp") },
                    colors = ChipDefaults.primaryChipColors(
                        backgroundColor = MaterialTheme.colors.error,
                    ),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                )
            } else {
                Text(
                    "📱 Am Handy ausschalten",
                    style = MaterialTheme.typography.title3,
                    textAlign = TextAlign.Center,
                )
                if (!phoneConnected) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Verbindung wird geprüft…",
                        style = MaterialTheme.typography.caption2,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            if (snoozeAvailable) {
                Spacer(Modifier.height(8.dp))
                Chip(
                    onClick = { sendServiceAction(AlarmService.ACTION_SNOOZE) },
                    label = { Text("Schlummern (${alarm.snoozeMinutes} min)") },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                )
            }
        }
    }

    companion object {
        /** Karenzzeit, bevor der Notfall-Stopp auf der Uhr erscheint. */
        const val DISCONNECT_GRACE_MS = 10_000L
    }
}
