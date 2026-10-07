package com.watchalarm.core

import android.content.Context
import java.time.ZonedDateTime
import java.util.Date

/**
 * Schreibweisen des Schlafplaners — gemeinsam für Handy und Uhr, damit beide
 * dieselben Texte zeigen. Dauer über [SleepDuration.format], die Uhrzeit wie
 * überall in der App aus der 12-/24-Stunden-Einstellung des Geräts.
 */
object SleepPlannerFormat {

    /**
     * Uhrzeit in der 12-/24-Stunden-Schreibweise des Geräts.
     *
     * `DateFormat.getTimeFormat` rechnet in der Standard-Zeitzone; die
     * Vorschläge entstehen in `ZoneId.systemDefault()`, also derselben.
     */
    fun time(context: Context, time: ZonedDateTime): String =
        android.text.format.DateFormat.getTimeFormat(context).format(Date.from(time.toInstant()))

    fun cycles(context: Context, cycles: Int): String =
        context.resources.getQuantityString(R.plurals.core_planner_cycles, cycles, cycles)

    fun minutes(context: Context, minutes: Int): String =
        context.getString(R.string.core_duration_minutes, minutes)

    fun duration(context: Context, minutes: Int): String =
        SleepDuration.format(context, minutes * MINUTE_MILLIS)

    /** "5 cycles · 7 h 30 min" — die Zweitzeile unter der Uhrzeit. */
    fun details(context: Context, suggestion: SleepSuggestion): String =
        cycles(context, suggestion.cycles) + SEPARATOR + duration(context, suggestion.sleepMinutes)

    /** "06:45 · 5 cycles · 7 h 30 min" — für Bedienungshilfen in einem Stück. */
    fun line(context: Context, suggestion: SleepSuggestion): String =
        time(context, suggestion.time) + SEPARATOR + details(context, suggestion)

    /** "Ø 6 h 40 min last 7 nights" */
    fun average(context: Context, summary: SleepSummary): String =
        context.resources.getQuantityString(
            R.plurals.core_sleep_average,
            summary.nights,
            duration(context, summary.averageMinutes),
            summary.nights,
        )

    /** "4 h 50 min sleep debt" bzw. "No sleep debt". */
    fun debt(context: Context, summary: SleepSummary): String =
        if (summary.debtMinutes > 0) {
            context.getString(R.string.core_sleep_debt, duration(context, summary.debtMinutes))
        } else {
            context.getString(R.string.core_sleep_no_debt)
        }

    /** "Ø 6 h 40 min last 7 nights · 4 h 50 min sleep debt" */
    fun summary(context: Context, summary: SleepSummary): String =
        average(context, summary) + SEPARATOR + debt(context, summary)

    private const val SEPARATOR = " · "
    private const val MINUTE_MILLIS = 60_000L
}
