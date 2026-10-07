package com.watchalarm.wear

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.watchalarm.core.Alarm
import com.watchalarm.core.AlarmScheduler
import com.watchalarm.core.AlarmService
import com.watchalarm.core.AlarmStore
import com.watchalarm.core.SyncContract

/**
 * Klingelansicht auf der Uhr (die Uhr vibriert dabei).
 *
 * Der Alarm lässt sich hier direkt per Stopp-Button beenden; das stoppt
 * über eine Message auch das Handy. Zusätzlich der Hinweis, dass man ihn
 * auch am Handy ausschalten kann.
 */
class WatchRingActivity : ComponentActivity() {

    /**
     * `singleTask`: ein erneuter Start (zweiter Alarm, Tipp auf die
     * Benachrichtigung) kommt über [onNewIntent] herein. Ohne State bliebe
     * der alte Alarm stehen.
     */
    private val alarmState = mutableStateOf<Alarm?>(null)

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

        if (!bindAlarm(intent)) {
            finish()
            return
        }

        setContent {
            RiseWearTheme {
                val alarm by alarmState
                alarm?.let { RingScreen(it) }
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

    /** Liefert false, wenn der Intent auf keinen bekannten Alarm zeigt. */
    private fun bindAlarm(intent: Intent): Boolean {
        val alarmId = intent.getStringExtra(SyncContract.EXTRA_ALARM_ID) ?: return false
        val alarm = AlarmStore.getAlarm(this, alarmId) ?: return false
        alarmState.value = alarm

        // Auch von hier den Service starten: Falls der Receiver-Pfad vom
        // System blockiert wurde, vibriert es trotzdem (doppelter Start ist
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
        val intent = Intent(this, AlarmService::class.java).setAction(action)
        // Gemeinten Alarm mitgeben: Der Service darf sich nicht darauf
        // verlassen, dass sein In-Memory-Zustand noch auf ihn zeigt.
        alarmState.value?.let { intent.putExtra(SyncContract.EXTRA_ALARM_ID, it.id) }
        runCatching { startService(intent) }
    }

    /**
     * "Silent wake" aus dem Entwurf: schwarzes Zifferblatt mit warmem
     * Glühen, die Welle über der Uhrzeit. Der Entwurf zeichnet die Uhr
     * eckig; hier bleibt alles mittig gestapelt, damit auf dem runden
     * Display nichts unter die Lünette rutscht.
     */
    @Composable
    private fun RingScreen(alarm: Alarm) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(RiseWear.background)
                .riseGlow(RiseWear.emberGlow, centerY = 0.5f, radius = 0.68f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            RiseRipple(modifier = Modifier.size(36.dp))
            Spacer(Modifier.height(6.dp))
            Text(
                alarm.formattedTime(this@WatchRingActivity),
                style = SerifNumerals,
                fontSize = 32.sp,
                color = Color.White,
                maxLines = 1,
            )
            if (alarm.label.isNotBlank()) {
                Text(
                    alarm.label,
                    style = MaterialTheme.typography.body2,
                    color = Color.White.copy(alpha = 0.5f),
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(12.dp))

            Chip(
                onClick = { sendServiceAction(AlarmService.ACTION_DISMISS) },
                label = {
                    Text(
                        stringResource(R.string.stop),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
                colors = ChipDefaults.gradientBackgroundChipColors(
                    startBackgroundColor = RiseWear.sunLight,
                    endBackgroundColor = RiseWear.sunDeep,
                    contentColor = RiseWear.buttonInk,
                ),
                // Auf runden Displays würden die Ecken eines randbreiten
                // Chips unter der Lünette verschwinden.
                modifier = Modifier.fillMaxWidth(0.92f).heightIn(min = 52.dp),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.ring_hint),
                style = MaterialTheme.typography.caption2,
                color = Color.White.copy(alpha = 0.5f),
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** CSS `ease-out`, wie im Entwurf. */
private val CssEaseOut = CubicBezierEasing(0f, 0f, 0.58f, 1f)

/**
 * Die "Welle" des Entwurfs: zwei Bernsteinringe, die versetzt aus der Mitte
 * wachsen und verblassen (CSS riseRipple: 3,4 s, Skalierung 0,75 → 1,9,
 * Deckkraft 0,32 → 0), darin der Sonnenpunkt mit Verlauf.
 */
@Composable
private fun RiseRipple(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "ripple")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 3400, easing = LinearEasing)),
        label = "rippleProgress",
    )
    val ring = RiseWear.amber.copy(alpha = 0.6f)
    Canvas(modifier = modifier) {
        val radius = size.minDimension / 2
        val stroke = Stroke(width = 1.dp.toPx())
        // Zweiter Ring 1,2 s hinter dem ersten.
        listOf(progress, (progress - 1.2f / 3.4f).mod(1f)).forEach { phase ->
            val eased = CssEaseOut.transform(phase)
            drawCircle(
                color = ring.copy(alpha = ring.alpha * 0.32f * (1f - eased)),
                radius = radius * (0.75f + 1.15f * eased),
                style = stroke,
            )
        }
        // inset 28 px bei 92 px Kantenlänge, Verlauf 140° wie im Entwurf
        val dot = radius * (1f - 28f / 46f)
        drawCircle(
            brush = Brush.linearGradient(
                colors = listOf(RiseWear.sunLight, RiseWear.sunDeep),
                start = Offset(center.x - dot, center.y - dot),
                end = Offset(center.x + dot, center.y + dot),
            ),
            radius = dot,
        )
    }
}
