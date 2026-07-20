package com.watchalarm.core

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log

/**
 * Plant Alarme über [AlarmManager.setAlarmClock]. Jedes Gerät (Handy und
 * Uhr) plant unabhängig aus der synchronisierten Liste, damit der Alarm
 * auch bei getrennter Bluetooth-Verbindung zuverlässig klingelt.
 */
object AlarmScheduler {

    private const val TAG = "AlarmScheduler"
    const val EXTRA_IS_SNOOZE = "com.watchalarm.extra.IS_SNOOZE"

    fun rescheduleAll(context: Context) {
        AlarmStore.getAlarms(context).forEach { alarm ->
            when {
                !alarm.enabled -> cancel(context, alarm.id)
                // Laufenden Snooze nicht verwerfen (überlebt so auch
                // App-/Geräte-Neustarts und Sync-Updates).
                RuntimeStore.getSnoozeUntil(context, alarm.id) > System.currentTimeMillis() ->
                    scheduleSnooze(context, alarm, RuntimeStore.getSnoozeUntil(context, alarm.id))
                else -> schedule(context, alarm)
            }
        }
    }

    fun schedule(context: Context, alarm: Alarm) {
        setExact(context, alarm.nextTriggerMillis(), triggerPendingIntent(context, alarm.id, isSnooze = false))
    }

    fun scheduleSnooze(context: Context, alarm: Alarm, triggerAtMillis: Long) {
        setExact(context, triggerAtMillis, triggerPendingIntent(context, alarm.id, isSnooze = true))
    }

    fun cancel(context: Context, alarmId: String) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        am.cancel(triggerPendingIntent(context, alarmId, isSnooze = false))
    }

    private fun setExact(context: Context, triggerAtMillis: Long, operation: PendingIntent) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val showIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val showPending = showIntent?.let {
            PendingIntent.getActivity(
                context, 0, it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
        try {
            am.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAtMillis, showPending), operation)
        } catch (e: SecurityException) {
            // Exakte Alarme nicht erlaubt (sollte dank USE_EXACT_ALARM nicht
            // passieren) -> bestmöglicher Fallback.
            Log.w(TAG, "setAlarmClock nicht erlaubt, Fallback auf setAndAllowWhileIdle", e)
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, operation)
        }
    }

    private fun triggerPendingIntent(context: Context, alarmId: String, isSnooze: Boolean): PendingIntent {
        val intent = Intent(context, AlarmTriggerReceiver::class.java)
            .setAction("com.watchalarm.action.ALARM_TRIGGER")
            .setData(Uri.parse("watchalarm://alarm/$alarmId"))
            .putExtra(SyncContract.EXTRA_ALARM_ID, alarmId)
            .putExtra(EXTRA_IS_SNOOZE, isSnooze)
        return PendingIntent.getBroadcast(
            context, alarmId.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
