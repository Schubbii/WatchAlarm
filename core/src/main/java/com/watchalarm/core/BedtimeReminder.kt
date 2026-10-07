package com.watchalarm.core

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.time.Instant
import java.time.ZoneId

/**
 * Erinnerung an die Schlafenszeit aus dem Schlafplaner.
 *
 * Bewusst **kein** Wecker: Sie klingelt nicht, vibriert nicht dauerhaft,
 * wird nicht synchronisiert und taucht nicht in der Alarmliste auf. Deshalb
 * läuft sie nicht über [AlarmStore]/[AlarmScheduler], sondern ist nur eine
 * Benachrichtigung zu einem Zeitpunkt.
 *
 * Geplant mit `setWindow()` statt exakt: Ein paar Minuten Spielraum sind für
 * eine Erinnerung egal und schonen den Akku; die Berechtigung für exakte
 * Alarme gehört dem eigentlichen Wecker.
 *
 * Es gibt höchstens eine Erinnerung; eine neue ersetzt die alte.
 */
object BedtimeReminder {

    const val CHANNEL_ID = "watchalarm_bedtime"
    private const val NOTIFICATION_ID = 1002
    private const val REQUEST_CODE = 2002
    private const val WINDOW_MILLIS = 5 * 60_000L

    /**
     * Erinnerung um [atMillis] planen. [wakeAtMillis] ist die Weckzeit, zu
     * der diese Schlafenszeit passt — sie steht später im Text.
     */
    fun schedule(context: Context, atMillis: Long, wakeAtMillis: Long) {
        SleepPlannerStore.prefs(context).edit()
            .putLong(SleepPlannerStore.KEY_REMINDER_AT, atMillis)
            .putLong(SleepPlannerStore.KEY_REMINDER_WAKE_AT, wakeAtMillis)
            .apply()
        setAlarm(context, atMillis)
    }

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java)?.cancel(pendingIntent(context))
        clear(context)
    }

    /** Geplanter Zeitpunkt in Millis, oder `null`, wenn keiner (mehr) aussteht. */
    fun scheduledAt(context: Context, now: Long = System.currentTimeMillis()): Long? =
        SleepPlannerStore.prefs(context).getLong(SleepPlannerStore.KEY_REMINDER_AT, 0L)
            .takeIf { it > now }

    /**
     * Nach Neustart oder App-Update neu eintragen — beides löscht alle
     * AlarmManager-Einträge der App (siehe [BootReceiver]).
     */
    fun restore(context: Context) {
        val at = scheduledAt(context)
        if (at != null) setAlarm(context, at) else clear(context)
    }

    internal fun fire(context: Context) {
        val wakeAt = SleepPlannerStore.prefs(context).getLong(SleepPlannerStore.KEY_REMINDER_WAKE_AT, 0L)
        clear(context)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        createChannel(context)
        val wakeTime = Instant.ofEpochMilli(wakeAt).atZone(ZoneId.systemDefault())
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_core_alarm)
            .setContentTitle(context.getString(R.string.core_bedtime_title))
            .setContentText(
                context.getString(R.string.core_bedtime_text, SleepPlannerFormat.time(context, wakeTime))
            )
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
        context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
            builder.setContentIntent(
                PendingIntent.getActivity(
                    context, REQUEST_CODE, it,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
        }
        context.getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, builder.build())
    }

    private fun setAlarm(context: Context, atMillis: Long) {
        context.getSystemService(AlarmManager::class.java)
            ?.setWindow(AlarmManager.RTC_WAKEUP, atMillis, WINDOW_MILLIS, pendingIntent(context))
    }

    private fun clear(context: Context) {
        SleepPlannerStore.prefs(context).edit()
            .remove(SleepPlannerStore.KEY_REMINDER_AT)
            .remove(SleepPlannerStore.KEY_REMINDER_WAKE_AT)
            .apply()
    }

    private fun pendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context, REQUEST_CODE,
            Intent(context, BedtimeReminderReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.core_bedtime_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.core_bedtime_channel_description)
        }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }
}

/** Vom AlarmManager zur Schlafenszeit ausgelöst. */
class BedtimeReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        BedtimeReminder.fire(context)
    }
}
