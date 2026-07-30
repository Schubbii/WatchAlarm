package com.watchalarm.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Stellt geplante Alarme wieder her, nachdem das System sie verworfen hat:
 * nach Neustart, nach Zeit-/Zeitzonenwechsel und nach einem App-Update
 * (ein Update löscht alle `AlarmManager`-Einträge der App!).
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> {
                AlarmScheduler.rescheduleAll(context)
                AlarmSync.syncNow(context)
            }
        }
    }
}
