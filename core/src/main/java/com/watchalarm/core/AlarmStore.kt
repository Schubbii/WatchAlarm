package com.watchalarm.core

import android.content.Context
import android.content.SharedPreferences

/**
 * Persistente Alarmliste (SharedPreferences, JSON).
 *
 * Für den Abgleich zwischen Uhr und Handy trägt die Liste einen
 * **Lamport-Versionszähler** statt eines Wanduhr-Zeitstempels: Jede lokale
 * Änderung erhöht ihn um 1; ein empfangener Stand wird übernommen, wenn
 * seine Version höher ist als die eigene. Das ist geräteunabhängig und
 * verhindert das frühere Problem, dass Uhr→Handy-Änderungen wegen
 * abweichender Emulator-Uhren verworfen wurden.
 */
object AlarmStore {

    private const val PREFS = "watchalarm_store"
    private const val KEY_ALARMS = "alarms_json"
    private const val KEY_VERSION = "version"

    fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getAlarms(context: Context): List<Alarm> =
        Alarm.listFromJson(prefs(context).getString(KEY_ALARMS, null) ?: "[]")

    fun getAlarm(context: Context, id: String): Alarm? =
        getAlarms(context).firstOrNull { it.id == id }

    fun getVersion(context: Context): Long = prefs(context).getLong(KEY_VERSION, 0L)

    @Synchronized
    private fun replaceAll(context: Context, alarms: List<Alarm>, version: Long) {
        // apply() statt commit(): der Wert steht sofort im Speicher (alle
        // Leser unten sehen ihn), das Schreiben auf die Platte läuft im
        // Hintergrund. commit() blockierte hier den Compose-Callback, also
        // den Main-Thread — auf Uhren gut sicht- und messbar.
        prefs(context).edit()
            .putString(KEY_ALARMS, Alarm.listToJson(alarms))
            .putLong(KEY_VERSION, version)
            .apply()
    }

    /**
     * Lokale Änderung (UI, Ausschalten eines Einmal-Alarms, …): Version
     * erhöhen, speichern, Alarme neu planen und den neuen Stand pushen.
     */
    fun applyLocalChange(context: Context, mutate: (List<Alarm>) -> List<Alarm>) {
        val updated = mutate(getAlarms(context))
        replaceAll(context, updated, getVersion(context) + 1)
        AlarmScheduler.rescheduleAll(context)
        AlarmSync.pushAlarms(context)
    }

    /**
     * Vom anderen Gerät empfangener Stand. Wird nur übernommen, wenn seine
     * Version höher ist (schützt gegen Echo/veraltete Stände). Kein erneuter
     * Push (sonst Ping-Pong).
     */
    fun applyRemote(context: Context, alarms: List<Alarm>, version: Long): Boolean {
        if (version <= getVersion(context)) return false
        replaceAll(context, alarms, version)
        AlarmScheduler.rescheduleAll(context)
        return true
    }
}
