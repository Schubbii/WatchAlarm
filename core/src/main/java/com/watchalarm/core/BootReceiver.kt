package com.watchalarm.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Stellt geplante Alarme nach Neustart oder Zeitänderung wieder her. */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> {
                AlarmScheduler.rescheduleAll(context)
                AlarmSync.syncNow(context)
            }
        }
    }
}
