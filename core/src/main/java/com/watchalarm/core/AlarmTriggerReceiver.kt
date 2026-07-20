package com.watchalarm.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/** Wird vom AlarmManager ausgelöst und startet den klingelnden Vordergrund-Service. */
class AlarmTriggerReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getStringExtra(SyncContract.EXTRA_ALARM_ID) ?: return
        val isSnooze = intent.getBooleanExtra(AlarmScheduler.EXTRA_IS_SNOOZE, false)

        // Handy auf "aus" und der Alarm ist auch an der Uhr ausschaltbar:
        // Das Handy bleibt komplett stumm; Einmal-Alarme werden über den
        // Sync deaktiviert, sobald die Uhr den Alarm beendet.
        val alarm = AlarmStore.getAlarm(context, alarmId)
        val isWatch = context.packageManager
            .hasSystemFeature(android.content.pm.PackageManager.FEATURE_WATCH)
        if (!isWatch && alarm != null &&
            alarm.phoneMode == Alarm.MODE_OFF && !alarm.phoneOnlyDismiss
        ) {
            AlarmScheduler.rescheduleAll(context)
            return
        }

        val service = Intent(context, AlarmService::class.java)
            .setAction(AlarmService.ACTION_START)
            .putExtra(SyncContract.EXTRA_ALARM_ID, alarmId)
            .putExtra(AlarmScheduler.EXTRA_IS_SNOOZE, isSnooze)
        ContextCompat.startForegroundService(context, service)

        // Wiederholende Alarme direkt für das nächste Vorkommen neu planen.
        AlarmScheduler.rescheduleAll(context)
    }
}
