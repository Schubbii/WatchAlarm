package com.watchalarm.mobile

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.watchalarm.core.Alarm
import com.watchalarm.core.AlarmScheduler
import com.watchalarm.core.AlarmService
import com.watchalarm.core.AlarmStore
import com.watchalarm.core.RuntimeStore
import com.watchalarm.core.SyncContract

/** Vollbild-Klingelansicht auf dem Handy. */
class AlarmActivity : ComponentActivity() {

    /**
     * Die Activity läuft als `singleTask`: ein zweiter Alarm (oder ein
     * erneuter Start aus der Benachrichtigung) landet in [onNewIntent],
     * nicht in [onCreate]. Deshalb hängt die Anzeige an einem State — sonst
     * zeigt der Screen weiter den alten Alarm und der Klingel-Service wird
     * nicht erneut angestoßen.
     */
    private val alarmState = mutableStateOf<Alarm?>(null)

    private val stopReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        applyLockScreenFlags()
        registerStopReceiver()

        if (!bindAlarm(intent)) {
            finish()
            return
        }

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val alarm by alarmState
                    alarm?.let { RingScreen(it) }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (!bindAlarm(intent)) finish()
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(stopReceiver) }
        super.onDestroy()
    }

    /**
     * `setShowWhenLocked`/`setTurnScreenOn` gibt es erst ab API 27, die App
     * läuft aber ab API 26 (Android 8.0). Dort tun es die (später
     * abgelösten) Fenster-Flags — vorher gab es auf 8.0 einen
     * `NoSuchMethodError` beim Klingeln.
     */
    private fun applyLockScreenFlags() {
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun registerStopReceiver() {
        val filter = IntentFilter(SyncContract.ACTION_RING_STOPPED)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(stopReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(stopReceiver, filter)
        }
    }

    /** Liefert false, wenn der Intent auf keinen bekannten Alarm zeigt. */
    private fun bindAlarm(intent: Intent): Boolean {
        val alarmId = intent.getStringExtra(SyncContract.EXTRA_ALARM_ID) ?: return false
        val alarm = AlarmStore.getAlarm(this, alarmId) ?: return false
        alarmState.value = alarm

        // Auch von hier den Service starten: Falls der Receiver-Pfad vom
        // System blockiert wurde, klingelt es trotzdem (doppelter Start ist
        // im Service abgefangen).
        runCatching {
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
        }
        return true
    }

    private fun sendServiceAction(action: String) {
        // Activity ist im Vordergrund, der Service läuft bereits als FGS.
        runCatching {
            startService(Intent(this, AlarmService::class.java).setAction(action))
        }
    }

    @androidx.compose.runtime.Composable
    private fun RingScreen(alarm: Alarm) {
        val snoozeAvailable = alarm.snoozeMinutes > 0 &&
            RuntimeStore.getSnoozeCount(this, alarm.id) < alarm.maxSnoozes

        // Scrollbar und mit Mindest- statt Fixhöhen: im Querformat und auf
        // kleinen Displays lagen Stopp/Schlummern sonst außerhalb des
        // Bildschirms und der Alarm war nicht abstellbar.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 32.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                alarm.formattedTime(this@AlarmActivity),
                fontSize = 64.sp,
                fontWeight = FontWeight.Light,
                maxLines = 1,
            )
            if (alarm.label.isNotBlank()) {
                Text(
                    alarm.label,
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(40.dp))

            Button(
                onClick = { sendServiceAction(AlarmService.ACTION_DISMISS) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) {
                Text("Stopp", fontSize = 20.sp)
            }

            if (snoozeAvailable) {
                Spacer(Modifier.height(16.dp))
                OutlinedButton(
                    onClick = { sendServiceAction(AlarmService.ACTION_SNOOZE) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                ) {
                    Text("Schlummern (${alarm.snoozeMinutes} min)", fontSize = 18.sp)
                }
            }
        }
    }
}
