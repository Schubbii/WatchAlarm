package com.watchalarm.core

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.core.app.NotificationCompat

/**
 * Vordergrund-Service, der den Alarm klingeln lässt (Ton + Vibration),
 * die Vollbild-Benachrichtigung zeigt und Dismiss/Snooze ausführt.
 *
 * Wichtige Regeln:
 * - "Nur am Handy ausschaltbar": Auf der Uhr wird für solche Alarme keine
 *   Stopp-Aktion angeboten (die Klingel-Activity der Uhr blendet einen
 *   Notfall-Stopp erst ein, wenn das Handy nicht mehr verbunden ist).
 * - Sicherheitsnetz: Nach [RING_TIMEOUT_MS] wird automatisch gesnoozt
 *   bzw. beendet, damit der Alarm nie endlos weiterklingelt — auch wenn
 *   das Handy dauerhaft getrennt bleibt.
 * - Timeout-Aktionen bleiben lokal (kein Sync), damit ein Timeout auf der
 *   Uhr niemals den Alarm auf dem Handy beendet.
 */
class AlarmService : Service() {

    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private val handler = Handler(Looper.getMainLooper())
    private var currentAlarmId: String? = null
    private val timeoutRunnable = Runnable { onTimeout() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val id = intent.getStringExtra(SyncContract.EXTRA_ALARM_ID)
                val isSnooze = intent.getBooleanExtra(AlarmScheduler.EXTRA_IS_SNOOZE, false)
                if (id != null) startRinging(id, isSnooze) else stopSelf()
            }
            ACTION_DISMISS -> {
                val fromRemote = intent.getBooleanExtra(EXTRA_FROM_REMOTE, false)
                val id = currentAlarmId
                stopRinging()
                if (id != null) finalizeDismiss(this, id, notifyPeer = !fromRemote)
                stopSelf()
            }
            ACTION_SNOOZE -> {
                val fromRemote = intent.getBooleanExtra(EXTRA_FROM_REMOTE, false)
                val id = currentAlarmId
                stopRinging()
                if (id != null) finalizeSnooze(this, id, notifyPeer = !fromRemote)
                stopSelf()
            }
            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopPlayback()
        handler.removeCallbacks(timeoutRunnable)
        super.onDestroy()
    }

    // ---------------------------------------------------------------- ring

    private fun startRinging(alarmId: String, isSnooze: Boolean) {
        val alarm = AlarmStore.getAlarm(this, alarmId)
        if (alarm == null) {
            stopSelf()
            return
        }
        stopPlayback()
        currentAlarmId = alarmId
        if (!isSnooze) RuntimeStore.clearSnoozeCount(this, alarmId)
        RuntimeStore.setRingingAlarmId(this, alarmId)

        createChannel()
        val notification = buildNotification(alarm)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        val isWatch = packageManager.hasSystemFeature(PackageManager.FEATURE_WATCH)
        val mode = if (isWatch) alarm.watchMode else alarm.phoneMode
        startPlayback(
            alarm,
            playSound = mode == Alarm.MODE_SOUND_VIBRATE || mode == Alarm.MODE_SOUND,
            vibrate = mode == Alarm.MODE_SOUND_VIBRATE || mode == Alarm.MODE_VIBRATE,
        )

        // Klingel-Activity direkt öffnen (Vollbild), falls von der App registriert.
        AppRegistry.ringActivityClass?.let { cls ->
            try {
                startActivity(
                    Intent(this, cls)
                        .putExtra(SyncContract.EXTRA_ALARM_ID, alarmId)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (e: Exception) {
                Log.w(TAG, "Klingel-Activity konnte nicht gestartet werden", e)
            }
        }

        handler.removeCallbacks(timeoutRunnable)
        handler.postDelayed(timeoutRunnable, RING_TIMEOUT_MS)
    }

    private fun onTimeout() {
        val id = currentAlarmId ?: return
        val alarm = AlarmStore.getAlarm(this, id)
        stopRinging()
        if (alarm != null && alarm.snoozeMinutes > 0 &&
            RuntimeStore.getSnoozeCount(this, id) < alarm.maxSnoozes
        ) {
            finalizeSnooze(this, id, notifyPeer = false)
        } else {
            finalizeDismiss(this, id, notifyPeer = false)
        }
        stopSelf()
    }

    private fun stopRinging() {
        stopPlayback()
        handler.removeCallbacks(timeoutRunnable)
        currentAlarmId = null
        RuntimeStore.setRingingAlarmId(this, null)
        sendBroadcast(Intent(SyncContract.ACTION_RING_STOPPED).setPackage(packageName))
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun startPlayback(alarm: Alarm, playSound: Boolean, vibrate: Boolean) {
        if (playSound) {
            try {
                player = MediaPlayer().apply {
                    setDataSource(this@AlarmService, ToneResolver.resolve(this@AlarmService, alarm))
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    isLooping = true
                    prepare()
                    start()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Alarmton konnte nicht abgespielt werden, nur Vibration", e)
                player = null
            }
        }
        if (vibrate) {
            try {
                vibrator = getSystemService(Vibrator::class.java)
                vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 800, 600), 0))
            } catch (e: Exception) {
                Log.w(TAG, "Vibration nicht möglich", e)
            }
        }
    }

    private fun stopPlayback() {
        try {
            player?.stop()
            player?.release()
        } catch (e: Exception) {
            // ignorieren
        }
        player = null
        vibrator?.cancel()
        vibrator = null
    }

    // -------------------------------------------------------- notification

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.core_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = getString(R.string.core_channel_description)
            setSound(null, null) // Ton kommt vom MediaPlayer
            enableVibration(false)
            setBypassDnd(true)
        }
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(alarm: Alarm): android.app.Notification {
        val isWatch = packageManager.hasSystemFeature(PackageManager.FEATURE_WATCH)
        val allowLocalDismiss = !(isWatch && alarm.phoneOnlyDismiss)
        val snoozeAvailable = alarm.snoozeMinutes > 0 &&
            RuntimeStore.getSnoozeCount(this, alarm.id) < alarm.maxSnoozes

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_core_alarm)
            .setContentTitle(alarm.label.ifBlank { getString(R.string.core_alarm_title) })
            .setContentText(alarm.formattedTime(this))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)

        AppRegistry.ringActivityClass?.let { cls ->
            val fullScreen = PendingIntent.getActivity(
                this, 1,
                Intent(this, cls)
                    .putExtra(SyncContract.EXTRA_ALARM_ID, alarm.id)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.setFullScreenIntent(fullScreen, true)
            builder.setContentIntent(fullScreen)
        }

        if (snoozeAvailable) {
            builder.addAction(0, getString(R.string.core_snooze), servicePendingIntent(ACTION_SNOOZE, 2))
        }
        if (allowLocalDismiss) {
            builder.addAction(0, getString(R.string.core_stop), servicePendingIntent(ACTION_DISMISS, 3))
        } else {
            builder.setContentText(getString(R.string.core_dismiss_on_phone))
        }
        return builder.build()
    }

    private fun servicePendingIntent(action: String, requestCode: Int): PendingIntent =
        PendingIntent.getService(
            this, requestCode,
            Intent(this, AlarmService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    companion object {

        private const val TAG = "AlarmService"

        const val ACTION_START = "com.watchalarm.action.SERVICE_START"
        const val ACTION_DISMISS = "com.watchalarm.action.SERVICE_DISMISS"
        const val ACTION_SNOOZE = "com.watchalarm.action.SERVICE_SNOOZE"
        const val EXTRA_FROM_REMOTE = "com.watchalarm.extra.FROM_REMOTE"

        const val CHANNEL_ID = "watchalarm_ring"
        const val NOTIFICATION_ID = 1001

        /** Maximale Klingeldauer, danach Auto-Snooze bzw. Auto-Stopp. */
        const val RING_TIMEOUT_MS = 5 * 60 * 1000L

        /**
         * Alarm beenden. Läuft der Service gerade, wird er gestoppt;
         * andernfalls werden nur Zustand/Planung aktualisiert.
         */
        fun dismiss(context: Context, alarmId: String, fromRemote: Boolean) {
            if (RuntimeStore.getRingingAlarmId(context) == alarmId) {
                try {
                    context.startService(
                        Intent(context, AlarmService::class.java)
                            .setAction(ACTION_DISMISS)
                            .putExtra(EXTRA_FROM_REMOTE, fromRemote)
                    )
                    return
                } catch (e: Exception) {
                    Log.w(TAG, "Service nicht erreichbar, Dismiss direkt ausführen", e)
                }
            }
            finalizeDismiss(context, alarmId, notifyPeer = !fromRemote)
        }

        /** Alarm snoozen (analog zu [dismiss]). */
        fun snooze(context: Context, alarmId: String, fromRemote: Boolean) {
            if (RuntimeStore.getRingingAlarmId(context) == alarmId) {
                try {
                    context.startService(
                        Intent(context, AlarmService::class.java)
                            .setAction(ACTION_SNOOZE)
                            .putExtra(EXTRA_FROM_REMOTE, fromRemote)
                    )
                    return
                } catch (e: Exception) {
                    Log.w(TAG, "Service nicht erreichbar, Snooze direkt ausführen", e)
                }
            }
            finalizeSnooze(context, alarmId, notifyPeer = !fromRemote)
        }

        /**
         * Zustands-/Planungsfolgen eines Dismiss: Snooze-Zähler zurücksetzen,
         * Einmal-Alarme deaktivieren (und das syncen), Gegenseite informieren.
         */
        fun finalizeDismiss(context: Context, alarmId: String, notifyPeer: Boolean) {
            RuntimeStore.clearSnoozeCount(context, alarmId)
            val alarm = AlarmStore.getAlarm(context, alarmId)
            if (alarm != null && !alarm.repeating && alarm.enabled) {
                AlarmStore.applyLocalChange(context) { list ->
                    list.map { if (it.id == alarmId) it.copy(enabled = false) else it }
                }
            } else {
                AlarmScheduler.rescheduleAll(context)
            }
            if (notifyPeer) {
                AlarmSync.sendMessageToAll(context, SyncContract.PATH_DISMISS, alarmId)
            }
        }

        /** Snooze planen; klingelt danach auf beiden Geräten erneut. */
        fun finalizeSnooze(context: Context, alarmId: String, notifyPeer: Boolean) {
            val alarm = AlarmStore.getAlarm(context, alarmId) ?: return
            val count = RuntimeStore.getSnoozeCount(context, alarmId) + 1
            RuntimeStore.setSnoozeCount(context, alarmId, count)
            AlarmScheduler.scheduleSnooze(
                context, alarm,
                System.currentTimeMillis() + alarm.snoozeMinutes * 60_000L
            )
            if (notifyPeer) {
                AlarmSync.sendMessageToAll(context, SyncContract.PATH_SNOOZE, alarmId)
            }
        }
    }
}
