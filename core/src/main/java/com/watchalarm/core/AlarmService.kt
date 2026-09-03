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
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.core.app.NotificationCompat

/**
 * Vordergrund-Service, solange ein Alarm aktiv ist.
 *
 * - **Uhr:** vibriert (Dauermuster), sonst nichts.
 * - **Handy:** still — zeigt nur die Vollbild-Benachrichtigung bzw. den
 *   Stopp-Screen ([AppRegistry.ringActivityClass]).
 * - Ausgeschaltet wird immer am Handy. Die Uhr bietet einen Notfall-Stopp
 *   nur, wenn das Handy nicht verbunden ist — das regelt die Klingel-
 *   Activity der Uhr.
 * - Sicherheitsnetz: nach [Alarm.ringTimeoutMinutes] automatisch Snooze bzw. Stopp,
 *   damit die Uhr nie endlos vibriert.
 */
class AlarmService : Service() {

    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val handler = Handler(Looper.getMainLooper())

    /**
     * Alle gerade klingelnden Alarme — normalerweise genau einer, aber zwei
     * Alarme auf dieselbe Minute sind möglich. Die teilen sich Service,
     * Benachrichtigung (eine [NOTIFICATION_ID]) und Vibration, ein einzelner
     * Druck auf Stopp beendet also hörbar beide. Vorher merkte sich der
     * Service nur den zuletzt gestarteten: Der erste blieb aktiviert und
     * klingelte als Einmal-Alarm am nächsten Tag erneut.
     */
    private val ringingIds = linkedSetOf<String>()
    private val timeoutRunnable = Runnable { onTimeout() }

    private val isWatch by lazy { packageManager.hasSystemFeature(PackageManager.FEATURE_WATCH) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val id = intent.getStringExtra(SyncContract.EXTRA_ALARM_ID)
                val isSnooze = intent.getBooleanExtra(AlarmScheduler.EXTRA_IS_SNOOZE, false)
                if (id != null) startRinging(id, isSnooze) else stopSelf()
            }
            ACTION_DISMISS -> {
                finalizeRinging(
                    explicitId = intent.getStringExtra(SyncContract.EXTRA_ALARM_ID),
                    fromRemote = intent.getBooleanExtra(EXTRA_FROM_REMOTE, false),
                    snooze = false,
                )
                stopSelf()
            }
            ACTION_SNOOZE -> {
                finalizeRinging(
                    explicitId = intent.getStringExtra(SyncContract.EXTRA_ALARM_ID),
                    fromRemote = intent.getBooleanExtra(EXTRA_FROM_REMOTE, false),
                    snooze = true,
                )
                stopSelf()
            }
            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopVibration()
        releaseWakeLock()
        handler.removeCallbacks(timeoutRunnable)
        // Wird der Service abgeräumt, ohne dass stopRinging() lief — vom System
        // abgeschossen etwa —, bliebe der Klingel-Merker stehen und das rote
        // Banner klebte dauerhaft in beiden Listen. Nur räumen, wenn wir uns
        // noch als klingelnd verstehen und der gespeicherte Alarm auch unserer
        // ist; nach einem regulären stopRinging() ist ringingIds leer und hier
        // nichts zu tun.
        val persisted = RuntimeStore.getRingingAlarmId(this)
        if (persisted != null && persisted in ringingIds) {
            RuntimeStore.setRingingAlarmId(this, null)
        }
        super.onDestroy()
    }

    // ---------------------------------------------------------------- ring

    private fun startRinging(alarmId: String, isSnooze: Boolean) {
        // Läuft schon ein Alarm, sind wir bereits im Vordergrund — dann ist ein
        // fehlgeschlagenes startForeground() unten nur eine nicht aktualisierte
        // Benachrichtigung und kein Grund, den Service abzuräumen.
        val alreadyForeground = ringingIds.isNotEmpty()
        // Receiver UND Klingel-Activity starten den Service — nur einmal starten.
        if (!ringingIds.add(alarmId)) return
        val alarm = AlarmStore.getAlarm(this, alarmId)
        if (alarm == null) {
            ringingIds.remove(alarmId)
            // Nur beenden, wenn dieser Start der einzige war — sonst würde ein
            // ins Leere laufender Start einen anderen laufenden Alarm abwürgen.
            if (ringingIds.isEmpty()) stopSelf()
            return
        }
        if (!isSnooze) RuntimeStore.clearSnoozeCount(this, alarmId)
        RuntimeStore.setRingingAlarmId(this, alarmId)

        // Ohne WakeLock kann die CPU wieder schlafen, sobald der Bildschirm
        // aus ist — dann feuert der Timeout unten nicht und das Vibrations-
        // muster bricht auf manchen Uhren ab.
        val timeoutMs = ringTimeoutMs(alarm)
        acquireWakeLock(timeoutMs)

        createChannel()
        val notification = buildNotification(alarm)
        if (!enterForeground(notification) && !alreadyForeground) {
            ringWithoutService(alarmId, notification)
            return
        }

        if (isWatch) startVibration()

        handler.removeCallbacks(timeoutRunnable)
        handler.postDelayed(timeoutRunnable, timeoutMs)
    }

    /** Klingeldauer dieses Weckers in Millis (mindestens eine Minute). */
    private fun ringTimeoutMs(alarm: Alarm): Long =
        alarm.ringTimeoutMinutes.coerceAtLeast(1) * 60_000L

    /**
     * Vordergrund-Status anfordern.
     *
     * @return false, wenn das System ablehnt — manche Hersteller-ROMs tun das,
     *   und ab API 34 kann auch der Typ selbst verweigert werden.
     */
    private fun enterForeground(notification: android.app.Notification): Boolean = try {
        if (Build.VERSION.SDK_INT >= 34) {
            // specialUse ist der dokumentierte Auffangtyp; die Begründung für
            // Play steht als <property> im Manifest. Vorher stand hier
            // systemExempted — ein Typ, der Apps vorbehalten ist, die von den
            // Hintergrund-Einschränkungen ausgenommen sind. Ein Wecker gehört
            // nicht dazu, das Weiterlaufen war also auf Kulanz des Systems
            // gebaut.
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        true
    } catch (e: Exception) {
        Log.w(TAG, "startForeground abgelehnt", e)
        false
    }

    /**
     * Notklingeln ohne Vordergrund-Service.
     *
     * Der Service **darf** hier nicht weiterlaufen: startForegroundService()
     * startet eine Systemuhr, die die App mit
     * ForegroundServiceDidNotStartInTimeException abschießt, wenn nicht binnen
     * weniger Sekunden ein startForeground() durchkommt. Der frühere Code fing
     * die Ablehnung ab und klingelte einfach weiter — und stürzte deshalb
     * Sekunden später garantiert ab, ausgerechnet auf den Geräten, denen der
     * Fallback helfen sollte. Erst stopSelf() entschärft die Uhr.
     *
     * Übrig bleibt die Benachrichtigung: IMPORTANCE_HIGH mit Full-Screen-
     * Intent, sie holt den Stopp-Screen also weiterhin nach vorne. Die
     * Klingel-Activity startet den Service danach selbst noch einmal — dann
     * aus dem Vordergrund, wo der Start nicht mehr an der Hintergrund-Sperre
     * scheitert. Ist stattdessen der Typ grundsätzlich verboten, scheitert
     * auch das und es bleibt bei der Benachrichtigung: auf der Uhr ohne
     * Vibration und ohne den Auto-Snooze nach [Alarm.ringTimeoutMinutes].
     *
     * Der persistierte Klingel-Merker bleibt bewusst stehen — an ihm hängt die
     * Zustellung von Stopp und Schlummern. [ringingIds] wird dagegen geleert,
     * weil *dieser* Service nichts mehr klingelt; sonst würde onDestroy den
     * Merker gleich mitlöschen.
     */
    private fun ringWithoutService(alarmId: String, notification: android.app.Notification) {
        ringingIds.remove(alarmId)
        Log.w(TAG, "Kein Vordergrund-Service möglich, klingele nur per Benachrichtigung")
        runCatching {
            getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, notification)
        }
        stopSelf()
    }

    /**
     * Klingeln beenden und die betroffenen Alarme abschließen.
     *
     * [explicitId] ist die ID aus dem Intent — von der Benachrichtigungs-
     * Aktion oder von der Gegenseite. Fehlt sie, greifen der Reihe nach die
     * laufenden IDs und der persistierte Klingelzustand: Wurde der Prozess
     * zwischendurch recycelt und stand nur noch die Benachrichtigung, war das
     * In-Memory-Feld leer und der Druck auf Stopp verpuffte folgenlos.
     */
    private fun finalizeRinging(explicitId: String?, fromRemote: Boolean, snooze: Boolean) {
        val ids = LinkedHashSet(ringingIds)
        explicitId?.let { ids.add(it) }
        if (ids.isEmpty()) RuntimeStore.getRingingAlarmId(this)?.let { ids.add(it) }
        stopRinging()
        ids.forEach { id ->
            // Nur den von der Gegenseite benannten Alarm nicht zurückmelden —
            // der ist dort bereits abgeschlossen. Alles Weitere ist eine
            // lokale Entscheidung und muss gemeldet werden, sonst laufen die
            // beiden Geräte auseinander.
            val notifyPeer = !(fromRemote && id == explicitId)
            if (snooze) {
                finalizeSnooze(this, id, notifyPeer)
            } else {
                finalizeDismiss(this, id, notifyPeer)
            }
        }
    }

    private fun onTimeout() {
        val ids = LinkedHashSet(ringingIds)
        if (ids.isEmpty()) return
        stopRinging()
        ids.forEach { id ->
            val alarm = AlarmStore.getAlarm(this, id)
            if (alarm != null && alarm.snoozeMinutes > 0 &&
                RuntimeStore.getSnoozeCount(this, id) < alarm.maxSnoozes
            ) {
                finalizeSnooze(this, id, notifyPeer = false)
            } else {
                finalizeDismiss(this, id, notifyPeer = false)
            }
        }
        stopSelf()
    }

    private fun stopRinging() {
        stopVibration()
        handler.removeCallbacks(timeoutRunnable)
        ringingIds.clear()
        RuntimeStore.setRingingAlarmId(this, null)
        sendBroadcast(Intent(SyncContract.ACTION_RING_STOPPED).setPackage(packageName))
        stopForeground(STOP_FOREGROUND_REMOVE)
        // Falls der Vordergrund-Start oben fehlgeschlagen ist, hängt die
        // Benachrichtigung nicht am Service und muss selbst weg.
        runCatching { getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID) }
        releaseWakeLock()
    }

    private fun startVibration() {
        try {
            val v = getSystemService(Vibrator::class.java) ?: return
            vibrator = v
            // Dauervibration (Muster wiederholt ab Index 0). Die Nutzung
            // "Alarm" ist wichtig: ohne sie unterdrücken "Nicht stören",
            // Kino- und Schlafmodus die Vibration auf vielen Uhren komplett.
            v.vibrate(
                VibrationEffect.createWaveform(longArrayOf(0, 600, 500), 0),
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            Log.d(TAG, "Uhr vibriert")
        } catch (e: Exception) {
            Log.w(TAG, "Vibration nicht möglich", e)
        }
    }

    private fun stopVibration() {
        runCatching { vibrator?.cancel() }
        vibrator = null
    }

    // ------------------------------------------------------------- wakelock

    private fun acquireWakeLock(timeoutMs: Long) {
        if (wakeLock?.isHeld == true) return
        try {
            val pm = getSystemService(PowerManager::class.java) ?: return
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG).apply {
                setReferenceCounted(false)
                // Harte Obergrenze, damit ein hängender Service den Akku
                // nicht leersaugt.
                acquire(timeoutMs + 30_000L)
            }
        } catch (e: Exception) {
            Log.w(TAG, "WakeLock nicht verfügbar", e)
        }
    }

    private fun releaseWakeLock() {
        runCatching { wakeLock?.takeIf { it.isHeld }?.release() }
        wakeLock = null
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
            setSound(null, null)
            enableVibration(false) // Vibration steuern wir selbst (nur Uhr)
        }
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(alarm: Alarm): android.app.Notification {
        val snoozeAvailable = alarm.snoozeMinutes > 0 &&
            RuntimeStore.getSnoozeCount(this, alarm.id) < alarm.maxSnoozes

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_core_alarm)
            .setContentTitle(alarm.label.ifBlank { getString(R.string.core_alarm_title) })
            .setContentText(if (isWatch) getString(R.string.core_dismiss_on_phone) else alarm.formattedTime(this))
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

        // Stopp/Snooze nur am Handy — die Uhr wird am Handy ausgeschaltet.
        if (!isWatch) {
            if (snoozeAvailable) {
                builder.addAction(0, getString(R.string.core_snooze), servicePendingIntent(ACTION_SNOOZE, 2, alarm.id))
            }
            builder.addAction(0, getString(R.string.core_stop), servicePendingIntent(ACTION_DISMISS, 3, alarm.id))
        }
        return builder.build()
    }

    /**
     * FLAG_UPDATE_CURRENT ist hier nicht optional: PendingIntents gelten als
     * gleich, wenn Komponente, Action und Data übereinstimmen — Extras zählen
     * nicht mit. Ein zweiter Alarm bekäme also den PendingIntent des ersten
     * samt dessen [alarmId] wiederverwendet; erst das Flag schreibt die Extras
     * neu.
     */
    private fun servicePendingIntent(action: String, requestCode: Int, alarmId: String): PendingIntent =
        PendingIntent.getService(
            this, requestCode,
            Intent(this, AlarmService::class.java)
                .setAction(action)
                .putExtra(SyncContract.EXTRA_ALARM_ID, alarmId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    companion object {

        private const val TAG = "AlarmService"
        private const val WAKE_LOCK_TAG = "WatchAlarm:ring"

        const val ACTION_START = "com.watchalarm.action.SERVICE_START"
        const val ACTION_DISMISS = "com.watchalarm.action.SERVICE_DISMISS"
        const val ACTION_SNOOZE = "com.watchalarm.action.SERVICE_SNOOZE"
        const val EXTRA_FROM_REMOTE = "com.watchalarm.extra.FROM_REMOTE"

        const val CHANNEL_ID = "watchalarm_ring"
        const val NOTIFICATION_ID = 1001

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
                            .putExtra(SyncContract.EXTRA_ALARM_ID, alarmId)
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
                            .putExtra(SyncContract.EXTRA_ALARM_ID, alarmId)
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
            val triggerAt = System.currentTimeMillis() + alarm.snoozeMinutes * 60_000L
            RuntimeStore.setSnoozeUntil(context, alarmId, triggerAt)
            AlarmScheduler.scheduleSnooze(context, alarm, triggerAt)
            if (notifyPeer) {
                AlarmSync.sendMessageToAll(context, SyncContract.PATH_SNOOZE, alarmId)
            }
        }
    }
}
