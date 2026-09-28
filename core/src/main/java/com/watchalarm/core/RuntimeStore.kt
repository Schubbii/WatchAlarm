package com.watchalarm.core

import android.content.Context
import android.content.SharedPreferences

/**
 * Rein lokaler Laufzeitzustand (wird nicht synchronisiert):
 * gerade klingelnder Alarm und verbrauchte Snoozes pro Alarm.
 */
object RuntimeStore {

    private const val PREFS = "watchalarm_runtime"
    private const val KEY_RINGING_IDS = "ringing_alarm_ids"

    /**
     * Vorgänger von [KEY_RINGING_IDS]: ein einzelner String. Wird nur noch
     * aufgeräumt — gelesen wird er nicht mehr, sonst müsste jeder Zugriff mit
     * einer ClassCastException rechnen.
     */
    private const val KEY_RINGING_LEGACY = "ringing_alarm_id"

    private const val KEY_SNOOZE_PREFIX = "snoozes_"
    private const val KEY_SNOOZE_UNTIL_PREFIX = "snooze_until_"

    fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * Alle gerade klingelnden Alarme.
     *
     * Eine Menge und kein einzelner Wert, weil [AlarmService] sie auch so
     * hält: Zwei Alarme auf dieselbe Minute klingeln gemeinsam. Vorher stand
     * hier nur der zuletzt gestartete, und weil [AlarmService.dismiss] und
     * [AlarmService.snooze] an diesem Wert erkennen, ob der gemeinte Alarm
     * überhaupt klingelt, lief ein von der Gegenseite abgeschalteter *erster*
     * Alarm hier einfach weiter — und wurde beim spätereren Stopp ein zweites
     * Mal abgeschlossen.
     */
    fun getRingingAlarmIds(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_RINGING_IDS, emptySet()) ?: emptySet()

    fun addRingingAlarmId(context: Context, id: String) {
        // Die Menge aus getStringSet() darf laut Doku nicht verändert werden;
        // "+" legt ohnehin eine neue an.
        prefs(context).edit()
            .putStringSet(KEY_RINGING_IDS, getRingingAlarmIds(context) + id)
            .apply()
    }

    /** Einzelne Alarme abmelden, ohne die der anderen zu verlieren. */
    fun removeRingingAlarmIds(context: Context, ids: Set<String>) {
        if (ids.isEmpty()) return
        val rest = getRingingAlarmIds(context) - ids
        prefs(context).edit().apply {
            if (rest.isEmpty()) remove(KEY_RINGING_IDS) else putStringSet(KEY_RINGING_IDS, rest)
        }.apply()
    }

    fun clearRingingAlarmIds(context: Context) {
        prefs(context).edit()
            .remove(KEY_RINGING_IDS)
            .remove(KEY_RINGING_LEGACY)
            .apply()
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
