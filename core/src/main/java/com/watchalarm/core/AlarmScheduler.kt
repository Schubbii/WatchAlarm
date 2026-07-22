package com.watchalarm.core

import android.app.ActivityOptions
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log

/**
 * Plant Alarme über den [AlarmManager]. Jedes Gerät (Handy und Uhr) plant
 * unabhängig aus der synchronisierten Liste, damit der Alarm auch bei
 * getrennter Bluetooth-Verbindung zuverlässig klingelt.
 *
 * Pro Alarmzeitpunkt werden zwei PendingIntents gesetzt:
 * 1. Ein Broadcast an [AlarmTriggerReceiver] (setAlarmClock) — startet den
 *    Klingel-Service mit Ton/Vibration/Benachrichtigung. Garantiert.
 * 2. Die Klingel-Activity direkt — bringt den Vollbild-Stopp-Screen in
 *    jedem Bildschirmzustand nach vorne (ab API 34 mit explizitem
 *    Background-Start-Opt-in, davor ist das ohnehin erlaubt).
 */
object AlarmScheduler {

    private const val TAG = "AlarmScheduler"
    const val EXTRA_IS_SNOOZE = "com.watchalarm.extra.IS_SNOOZE"

    fun rescheduleAll(context: Context) {
        AlarmStore.getAlarms(context).forEach { alarm ->
            val snoozeUntil = RuntimeStore.getSnoozeUntil(context, alarm.id)
            when {
                !alarm.enabled -> cancel(context, alarm.id)
                // Laufenden Snooze nicht verwerfen (überlebt so auch
                // App-/Geräte-Neustarts und Sync-Updates).
                snoozeUntil > System.currentTimeMillis() ->
                    scheduleSnooze(context, alarm, snoozeUntil)
                else -> schedule(context, alarm)
            }
        }
    }

    fun schedule(context: Context, alarm: Alarm) =
        setAlarm(context, alarm, alarm.nextTriggerMillis(), isSnooze = false)

    fun scheduleSnooze(context: Context, alarm: Alarm, triggerAtMillis: Long) =
        setAlarm(context, alarm, triggerAtMillis, isSnooze = true)

    fun cancel(context: Context, alarmId: String) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        am.cancel(receiverPendingIntent(context, alarmId, isSnooze = false))
        activityPendingIntent(context, alarmId, isSnooze = false)?.let { am.cancel(it) }
    }

    private fun setAlarm(context: Context, alarm: Alarm, triggerAtMillis: Long, isSnooze: Boolean) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val showPending = context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
            PendingIntent.getActivity(
                context, 0, it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
        try {
            am.setAlarmClock(
                AlarmManager.AlarmClockInfo(triggerAtMillis, showPending),
                receiverPendingIntent(context, alarm.id, isSnooze)
            )
            activityPendingIntent(context, alarm.id, isSnooze)?.let {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, it)
            }
        } catch (e: SecurityException) {
            // Exakte Alarme nicht erlaubt (sollte dank USE_EXACT_ALARM nicht
            // passieren) -> bestmöglicher Fallback.
            Log.w(TAG, "Exakter Alarm nicht erlaubt, ungenauer Fallback", e)
            am.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP, triggerAtMillis,
                receiverPendingIntent(context, alarm.id, isSnooze)
            )
        }
    }

    private fun receiverPendingIntent(context: Context, alarmId: String, isSnooze: Boolean): PendingIntent {
        val intent = Intent(context, AlarmTriggerReceiver::class.java)
            .setData(Uri.parse("watchalarm://alarm/$alarmId"))
            .putExtra(SyncContract.EXTRA_ALARM_ID, alarmId)
            .putExtra(EXTRA_IS_SNOOZE, isSnooze)
        return PendingIntent.getBroadcast(
            context, alarmId.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun activityPendingIntent(context: Context, alarmId: String, isSnooze: Boolean): PendingIntent? {
        val cls = AppRegistry.ringActivityClass ?: return null
        val intent = Intent(context, cls)
            .setData(Uri.parse("watchalarm://ring/$alarmId"))
            .putExtra(SyncContract.EXTRA_ALARM_ID, alarmId)
            .putExtra(EXTRA_IS_SNOOZE, isSnooze)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return if (Build.VERSION.SDK_INT >= 34) {
            val options = ActivityOptions.makeBasic()
            options.setPendingIntentCreatorBackgroundActivityStartMode(
                ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
            )
            PendingIntent.getActivity(context, alarmId.hashCode(), intent, flags, options.toBundle())
        } else {
            PendingIntent.getActivity(context, alarmId.hashCode(), intent, flags)
        }
    }
}
