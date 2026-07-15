package com.watchalarm.core

import android.content.Context
import android.content.SharedPreferences

/**
 * Persistente Alarmliste (SharedPreferences, JSON). Die Liste trägt einen
 * lastModified-Zeitstempel; beim Sync gewinnt der neuere Stand
 * (last-write-wins).
 */
object AlarmStore {

    private const val PREFS = "watchalarm_store"
    private const val KEY_ALARMS = "alarms_json"
    private const val KEY_TS = "last_modified"

    fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getAlarms(context: Context): List<Alarm> =
        Alarm.listFromJson(prefs(context).getString(KEY_ALARMS, null) ?: "[]")

    fun getAlarm(context: Context, id: String): Alarm? =
        getAlarms(context).firstOrNull { it.id == id }

    fun getLastModified(context: Context): Long = prefs(context).getLong(KEY_TS, 0L)

    @Synchronized
    private fun replaceAll(context: Context, alarms: List<Alarm>, timestamp: Long) {
        prefs(context).edit()
            .putString(KEY_ALARMS, Alarm.listToJson(alarms))
            .putLong(KEY_TS, timestamp)
            .commit()
    }

    /**
     * Lokale Änderung (UI, Ausschalten eines Einmal-Alarms, …):
     * speichern, Alarme neu planen und den neuen Stand zur Gegenseite pushen.
     */
    fun applyLocalChange(context: Context, mutate: (List<Alarm>) -> List<Alarm>) {
        val updated = mutate(getAlarms(context))
        replaceAll(context, updated, System.currentTimeMillis())
        AlarmScheduler.rescheduleAll(context)
        AlarmSync.pushAlarms(context)
    }

    /**
     * Vom anderen Gerät empfangener Stand. Wird nur übernommen, wenn er
     * neuer ist als der lokale. Kein erneuter Push (sonst Ping-Pong).
     */
    fun applyRemote(context: Context, alarms: List<Alarm>, timestamp: Long): Boolean {
        if (timestamp <= getLastModified(context)) return false
        replaceAll(context, alarms, timestamp)
        AlarmScheduler.rescheduleAll(context)
        return true
    }
}
