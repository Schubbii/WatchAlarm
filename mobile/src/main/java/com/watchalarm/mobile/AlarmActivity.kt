package com.watchalarm.mobile

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
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
        // Der Sonnenaufgang läuft hinter die Systemleisten; deren Symbole
        // müssen auf dem dunklen oberen Rand hell sein.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)

        applyLockScreenFlags()
        registerStopReceiver()

        if (!bindAlarm(intent)) {
            finish()
            return
        }

        setContent {
            RiseTheme {
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
        val intent = Intent(this, AlarmService::class.java).setAction(action)
        // Gemeinten Alarm mitgeben: Der Service darf sich nicht darauf
        // verlassen, dass sein In-Memory-Zustand noch auf ihn zeigt.
        alarmState.value?.let { intent.putExtra(SyncContract.EXTRA_ALARM_ID, it.id) }
        // Activity ist im Vordergrund, der Service läuft bereits als FGS.
        runCatching { startService(intent) }
    }

    @Composable
    private fun RingScreen(alarm: Alarm) {
        val snoozeAvailable = alarm.snoozeMinutes > 0 &&
            RuntimeStore.getSnoozeCount(this, alarm.id) < alarm.maxSnoozes

        // Sonnenaufgang aus dem Entwurf ("04 — Wake"): Verlauf von Pflaume zu
        // Pfirsich, Welle in der Mitte, die Knöpfe unten.
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to RiseFixed.sunriseTop,
                        0.88f to RiseFixed.sunriseBottom,
                    )
                ),
        ) {
            // Scrollbar und mit Mindest- statt Fixhöhen: im Querformat und auf
            // kleinen Displays lagen Stopp/Schlummern sonst außerhalb des
            // Bildschirms und der Alarm war nicht abstellbar.
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = maxHeight)
                    .fillMaxWidth()
                    .systemBarsPadding()
                    .padding(horizontal = 22.dp, vertical = 26.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Spacer(Modifier.height(0.dp))

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    RiseRipple(
                        ringColor = Color.White.copy(alpha = 0.5f),
                        dotColor = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.size(96.dp),
                    )
                    Spacer(Modifier.height(22.dp))
                    Text(
                        alarm.formattedTime(this@AlarmActivity),
                        fontFamily = InstrumentSerif,
                        fontSize = 66.sp,
                        lineHeight = 68.sp,
                        color = Color.White,
                        maxLines = 1,
                    )
                    if (alarm.label.isNotBlank()) {
                        Spacer(Modifier.height(14.dp))
                        Text(
                            alarm.label,
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White.copy(alpha = 0.78f),
                            textAlign = TextAlign.Center,
                        )
                    }
                }

                Column(modifier = Modifier.padding(top = 32.dp)) {
                    Button(
                        onClick = { sendServiceAction(AlarmService.ACTION_DISMISS) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = RiseFixed.wakeButton,
                            contentColor = Color.White,
                        ),
                    ) {
                        Text(stringResource(R.string.stop), fontSize = 16.sp)
                    }

                    if (snoozeAvailable) {
                        Spacer(Modifier.height(9.dp))
                        Button(
                            onClick = { sendServiceAction(AlarmService.ACTION_SNOOZE) },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White.copy(alpha = 0.3f),
                                contentColor = RiseFixed.wakeButtonSoftText,
                            ),
                        ) {
                            Text(
                                stringResource(R.string.snooze_with_duration, alarm.snoozeMinutes),
                                fontSize = 16.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** CSS `ease-out`, wie im Entwurf. */
private val CssEaseOut = CubicBezierEasing(0f, 0f, 0.58f, 1f)

/**
 * Die "Welle" des Entwurfs: zwei Ringe, die versetzt aus der Mitte wachsen
 * und dabei verblassen (CSS riseRipple: 3,4 s, Skalierung 0,75 → 1,9,
 * Deckkraft 0,32 → 0), darin ein ruhender Punkt.
 */
@Composable
private fun RiseRipple(ringColor: Color, dotColor: Color, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "ripple")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 3400, easing = LinearEasing)),
        label = "rippleProgress",
    )
    Canvas(modifier = modifier) {
        val radius = size.minDimension / 2
        val stroke = Stroke(width = 1.dp.toPx())
        // Zweiter Ring 1,3 s hinter dem ersten.
        listOf(progress, (progress - 1.3f / 3.4f).mod(1f)).forEach { phase ->
            val eased = CssEaseOut.transform(phase)
            drawCircle(
                color = ringColor.copy(alpha = ringColor.alpha * 0.32f * (1f - eased)),
                radius = radius * (0.75f + 1.15f * eased),
                style = stroke,
            )
        }
        // inset 26 px bei 96 px Kantenlänge
        drawCircle(color = dotColor, radius = radius * (1f - 26f / 48f))
    }
}
