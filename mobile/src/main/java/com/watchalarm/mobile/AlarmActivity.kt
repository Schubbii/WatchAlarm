package com.watchalarm.mobile

import android.app.KeyguardManager
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.watchalarm.core.Alarm
import com.watchalarm.core.AlarmService
import com.watchalarm.core.AlarmStore
import com.watchalarm.core.RuntimeStore
import com.watchalarm.core.SyncContract

/** Vollbild-Klingelansicht auf dem Handy. */
class AlarmActivity : ComponentActivity() {

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
        getSystemService(KeyguardManager::class.java)?.requestDismissKeyguard(this, null)

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

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    RingScreen(alarm)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Alarm inzwischen anderweitig beendet (z.B. von der Uhr)?
        val ringing = RuntimeStore.getRingingAlarmId(this)
        val shownId = intent.getStringExtra(SyncContract.EXTRA_ALARM_ID)
        if (ringing == null || ringing != shownId) finish()
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(stopReceiver) }
        super.onDestroy()
    }

    private fun sendServiceAction(action: String) {
        // Activity ist im Vordergrund, der Service läuft bereits als FGS.
        startService(Intent(this, AlarmService::class.java).setAction(action))
    }

    @androidx.compose.runtime.Composable
    private fun RingScreen(alarm: Alarm) {
        val snoozeAvailable = alarm.snoozeMinutes > 0 &&
            RuntimeStore.getSnoozeCount(this, alarm.id) < alarm.maxSnoozes

        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                alarm.formattedTime(this@AlarmActivity),
                fontSize = 72.sp,
                fontWeight = FontWeight.Light,
            )
            if (alarm.label.isNotBlank()) {
                Text(alarm.label, style = MaterialTheme.typography.headlineSmall)
            }
            if (alarm.phoneOnlyDismiss) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Dieser Alarm kann nur hier am Handy gestoppt werden",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (alarm.phoneMode == Alarm.MODE_OFF) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "🔕 Handy stumm — der Alarm klingelt auf der Uhr",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(48.dp))

            Button(
                onClick = { sendServiceAction(AlarmService.ACTION_DISMISS) },
                modifier = Modifier.fillMaxWidth().height(64.dp),
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
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) {
                    Text("Schlummern (${alarm.snoozeMinutes} min)", fontSize = 18.sp)
                }
            }
        }
    }
}
