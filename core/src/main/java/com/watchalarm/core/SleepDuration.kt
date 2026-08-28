package com.watchalarm.core

import android.content.Context

/**
 * Schlafdauer: die Zeit von jetzt bis zum nächsten Klingeln.
 *
 * Liegt im core-Modul, weil Handy und Uhr dieselbe Rechnung und dieselbe
 * Schreibweise brauchen — und weil hier bereits bekannt ist, dass ein
 * laufender Snooze den regulären Weckzeitpunkt vorzieht.
 */
object SleepDuration {

    /**
     * Nächster Klingelzeitpunkt in Millis — inklusive eines laufenden
     * Snooze, genau wie [AlarmScheduler.rescheduleAll] es plant. Sonst
     * stünde nach dem Schlummern die reguläre Weckzeit von morgen da.
     */
    fun nextRingMillis(context: Context, alarm: Alarm, now: Long = System.currentTimeMillis()): Long {
        val snoozeUntil = RuntimeStore.getSnoozeUntil(context, alarm.id)
        return if (snoozeUntil > now) snoozeUntil else alarm.nextTriggerMillis(now)
    }

    /** Verbleibende Millisekunden bis zum nächsten Klingeln, nie negativ. */
    fun millisUntil(context: Context, alarm: Alarm, now: Long = System.currentTimeMillis()): Long =
        (nextRingMillis(context, alarm, now) - now).coerceAtLeast(0L)

    /**
     * "7 Std. 30 Min." — [millis] wird auf volle Minuten **aufgerundet**.
     * Alarme klingeln zur vollen Minute, die laufende Minute ist also
     * angebrochen: um 22:30:15 sind es bis 6:00 Uhr 7 Std. 30 Min., nicht 29.
     */
    fun format(context: Context, millis: Long): String {
        val totalMinutes = ((millis + MINUTE_MILLIS - 1) / MINUTE_MILLIS).toInt()
        val days = totalMinutes / MINUTES_PER_DAY
        val hours = (totalMinutes % MINUTES_PER_DAY) / 60
        val minutes = totalMinutes % 60
        return when {
            // Bei Wiederholungen an einzelnen Wochentagen sind es schnell
            // mehrere Tage — "79 Std." wäre da nicht mehr lesbar.
            days > 0 && hours > 0 ->
                context.getString(R.string.core_duration_days_hours, days, hours)
            days > 0 -> context.getString(R.string.core_duration_days, days)
            hours > 0 && minutes > 0 ->
                context.getString(R.string.core_duration_hours_minutes, hours, minutes)
            hours > 0 -> context.getString(R.string.core_duration_hours, hours)
            else -> context.getString(R.string.core_duration_minutes, minutes)
        }
    }

    /** Kurzform für die Listen: Schlafdauer von jetzt bis zum Klingeln. */
    fun formatUntil(context: Context, alarm: Alarm, now: Long = System.currentTimeMillis()): String =
        format(context, millisUntil(context, alarm, now))

    private const val MINUTE_MILLIS = 60_000L
    private const val MINUTES_PER_DAY = 24 * 60
}
