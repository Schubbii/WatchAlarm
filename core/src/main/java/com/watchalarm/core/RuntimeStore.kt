package com.watchalarm.core

import android.content.Context
import android.content.SharedPreferences

/**
 * Rein lokaler Laufzeitzustand (wird nicht synchronisiert):
 * gerade klingelnder Alarm und verbrauchte Snoozes pro Alarm.
 */
object RuntimeStore {

    private const val PREFS = "watchalarm_runtime"
    private const val KEY_RINGING = "ringing_alarm_id"
    private const val KEY_SNOOZE_PREFIX = "snoozes_"
    private const val KEY_SNOOZE_UNTIL_PREFIX = "snooze_until_"

    fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getRingingAlarmId(context: Context): String? =
        prefs(context).getString(KEY_RINGING, null)

    fun setRingingAlarmId(context: Context, id: String?) {
        prefs(context).edit().apply {
            if (id == null) remove(KEY_RINGING) else putString(KEY_RINGING, id)
        }.apply()
    }

    fun getSnoozeCount(context: Context, alarmId: String): Int =
        prefs(context).getInt(KEY_SNOOZE_PREFIX + alarmId, 0)

    fun setSnoozeCount(context: Context, alarmId: String, count: Int) {
        prefs(context).edit().putInt(KEY_SNOOZE_PREFIX + alarmId, count).apply()
    }

    fun clearSnoozeCount(context: Context, alarmId: String) {
        prefs(context).edit()
            .remove(KEY_SNOOZE_PREFIX + alarmId)
            .remove(KEY_SNOOZE_UNTIL_PREFIX + alarmId)
            .apply()
    }

    /** Zeitpunkt (Millis), zu dem ein laufender Snooze erneut klingeln soll. */
    fun getSnoozeUntil(context: Context, alarmId: String): Long =
        prefs(context).getLong(KEY_SNOOZE_UNTIL_PREFIX + alarmId, 0L)

    fun setSnoozeUntil(context: Context, alarmId: String, triggerAtMillis: Long) {
        prefs(context).edit().putLong(KEY_SNOOZE_UNTIL_PREFIX + alarmId, triggerAtMillis).apply()
    }

    fun clearSnoozeUntil(context: Context, alarmId: String) {
        prefs(context).edit().remove(KEY_SNOOZE_UNTIL_PREFIX + alarmId).apply()
    }
}
