package com.watchalarm.core

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime

/**
 * Lokaler Speicher des Schlafplaners.
 *
 * - **Einstellungen** (Zykluslänge, Einschlafzeit, Schlafziel) gelten pro
 *   Gerät und werden nicht synchronisiert — bewusst einfach gehalten.
 * - **Nächte** liest nur das Handy aus Health Connect; die Uhr bekommt sie
 *   über [AlarmSync.pushSleepNights]. Gespeichert sind pro Nacht nur Datum
 *   und Schlafminuten. Jedes Gerät wertet sie mit seinem eigenen Schlafziel
 *   aus.
 *
 * Eigene Datei statt [AlarmStore]: Deren Inhalt wandert in die
 * Datensicherung (siehe `backup_rules.xml`), Schlafdaten sollen das nicht —
 * und weil die Regeln dort nur [AlarmStore] einschließen, bleibt diese Datei
 * automatisch draußen.
 */
object SleepPlannerStore {

    private const val PREFS = "watchalarm_sleep"
    private const val KEY_CYCLE = "cycle_minutes"
    private const val KEY_FALL_ASLEEP = "fall_asleep_minutes"
    private const val KEY_GOAL = "sleep_goal_minutes"
    private const val KEY_NIGHTS = "nights_json"
    private const val KEY_WAKE_HOUR = "wake_hour"
    private const val KEY_WAKE_MINUTE = "wake_minute"
    private const val KEY_HEALTH_PROMPT_DISMISSED = "health_prompt_dismissed"

    internal const val KEY_REMINDER_AT = "bedtime_reminder_at"
    internal const val KEY_REMINDER_WAKE_AT = "bedtime_reminder_wake_at"

    /** Standard-Weckzeit im Modus „Aufwachen um", solange keine gewählt wurde. */
    private val DEFAULT_WAKE_TIME: LocalTime = LocalTime.of(7, 0)

    fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ------------------------------------------------------------ Einstellungen

    fun getSettings(context: Context): SleepSettings {
        val p = prefs(context)
        return SleepSettings(
            cycleMinutes = p.getInt(KEY_CYCLE, SleepSettings.DEFAULT_CYCLE_MINUTES),
            fallAsleepMinutes = p.getInt(KEY_FALL_ASLEEP, SleepSettings.DEFAULT_FALL_ASLEEP_MINUTES),
            sleepGoalMinutes = p.getInt(KEY_GOAL, SleepSettings.DEFAULT_SLEEP_GOAL_MINUTES),
        )
    }

    fun setSettings(context: Context, settings: SleepSettings) {
        prefs(context).edit()
            .putInt(KEY_CYCLE, settings.cycleMinutes)
            .putInt(KEY_FALL_ASLEEP, settings.fallAsleepMinutes)
            .putInt(KEY_GOAL, settings.sleepGoalMinutes)
            .apply()
    }

    /** Zuletzt gewählte Zielweckzeit — damit der Planer nicht jedes Mal bei 7:00 startet. */
    fun getWakeTime(context: Context): LocalTime {
        val p = prefs(context)
        if (!p.contains(KEY_WAKE_HOUR)) return DEFAULT_WAKE_TIME
        return LocalTime.of(
            p.getInt(KEY_WAKE_HOUR, DEFAULT_WAKE_TIME.hour).coerceIn(0, 23),
            p.getInt(KEY_WAKE_MINUTE, 0).coerceIn(0, 59),
        )
    }

    fun setWakeTime(context: Context, time: LocalTime) {
        prefs(context).edit()
            .putInt(KEY_WAKE_HOUR, time.hour)
            .putInt(KEY_WAKE_MINUTE, time.minute)
            .apply()
    }

    /** Der Nutzer hat den Hinweis „Schlafdaten verbinden" weggeklickt. */
    fun isHealthPromptDismissed(context: Context): Boolean =
        prefs(context).getBoolean(KEY_HEALTH_PROMPT_DISMISSED, false)

    fun setHealthPromptDismissed(context: Context, dismissed: Boolean) {
        prefs(context).edit().putBoolean(KEY_HEALTH_PROMPT_DISMISSED, dismissed).apply()
    }

    // ------------------------------------------------------------------ Nächte

    fun getNights(context: Context): List<SleepNight> =
        nightsFromJson(prefs(context).getString(KEY_NIGHTS, null) ?: "[]") ?: emptyList()

    /** Nächte speichern, ohne sie zu verschicken — das macht der Aufrufer. */
    fun setNights(context: Context, json: String) {
        prefs(context).edit().putString(KEY_NIGHTS, json).apply()
    }

    /** Zusammenfassung mit dem Schlafziel dieses Geräts; `null` ohne Daten. */
    fun summary(context: Context, today: LocalDate = LocalDate.now()): SleepSummary? =
        SleepStats.summarize(getNights(context), today, getSettings(context).sleepGoalMinutes)

    fun nightsToJson(nights: List<SleepNight>): String =
        JSONArray().apply {
            nights.forEach { put(JSONObject().put("date", it.date.toString()).put("minutes", it.asleepMinutes)) }
        }.toString()

    /** `null` bei unlesbarem Text — dann bleibt der bisherige Stand stehen. */
    fun nightsFromJson(json: String): List<SleepNight>? = try {
        val arr = JSONArray(json)
        (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            SleepNight(LocalDate.parse(o.getString("date")), o.getInt("minutes"))
        }
    } catch (e: Exception) {
        null
    }
}
