package com.watchalarm.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat

/** Wird vom AlarmManager ausgelöst und startet den klingelnden Vordergrund-Service. */
class AlarmTriggerReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getStringExtra(SyncContract.EXTRA_ALARM_ID) ?: return
        val isSnooze = intent.getBooleanExtra(AlarmScheduler.EXTRA_IS_SNOOZE, false)

        val alarm = AlarmStore.getAlarm(context, alarmId)
        if (alarm == null || !alarm.enabled) {
            AlarmScheduler.rescheduleAll(context)
            return
        }

        RuntimeStore.setRingingAlarmId(context, alarmId)
        RuntimeStore.clearSnoozeUntil(context, alarmId)

        try {
            ContextCompat.startForegroundService(
                context,
                Intent(context, AlarmService::class.java)
                    .setAction(AlarmService.ACTION_START)
                    .putExtra(SyncContract.EXTRA_ALARM_ID, alarmId)
                    .putExtra(AlarmScheduler.EXTRA_IS_SNOOZE, isSnooze)
            )
        } catch (e: Exception) {
            // Falls der Hintergrund-Start abgelehnt wird, übernimmt die
            // parallel geplante Klingel-Activity den Service-Start.
            Log.w(TAG, "Service-Start blockiert, Klingel-Activity übernimmt", e)
        }

        // Wiederholende Alarme direkt für das nächste Vorkommen neu planen.
        AlarmScheduler.rescheduleAll(context)
    }

    private companion object {
        const val TAG = "AlarmTriggerReceiver"
    }
}
