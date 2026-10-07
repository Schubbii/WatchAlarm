package com.watchalarm.core

import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/**
 * Einstellungen des Schlafplaners. Bewusst pro Gerät und nicht
 * synchronisiert — siehe [SleepPlannerStore].
 */
data class SleepSettings(
    /** Länge eines Schlafzyklus (C). */
    val cycleMinutes: Int = DEFAULT_CYCLE_MINUTES,
    /** Zeit bis zum Einschlafen (L). */
    val fallAsleepMinutes: Int = DEFAULT_FALL_ASLEEP_MINUTES,
    /** Schlafziel pro Nacht, Grundlage des Schlafdefizits. */
    val sleepGoalMinutes: Int = DEFAULT_SLEEP_GOAL_MINUTES,
) {
    companion object {
        const val DEFAULT_CYCLE_MINUTES = 90
        const val DEFAULT_FALL_ASLEEP_MINUTES = 15
        const val DEFAULT_SLEEP_GOAL_MINUTES = 8 * 60

        /** Auswahl in den Einstellungen. */
        val CYCLE_CHOICES = listOf(80, 85, 90, 95, 100, 105, 110)
        val FALL_ASLEEP_CHOICES = listOf(0, 5, 10, 15, 20, 25, 30)

        /** 6 bis 10 Stunden in halben Stunden. */
        val SLEEP_GOAL_CHOICES = (12..20).map { it * 30 }
    }
}

/** Die beiden Betriebsarten des Planers. */
enum class PlannerMode {
    /** Zielweckzeit vorgeben, passende Schlafenszeiten bekommen. */
    WAKE_UP_AT,

    /** Jetzt schlafen gehen, passende Weckzeiten bekommen. */
    SLEEP_NOW,
}

/**
 * Ein Vorschlag des Planers: je nach Modus eine Schlafenszeit oder eine
 * Weckzeit.
 *
 * [available] ist nur bei Schlafenszeiten je `false` — wenn sie schon
 * vorbei sind. Die UI zeigt sie dann ausgegraut statt sie wegzulassen, damit
 * die Liste ihre Form behält und man sieht, was man verpasst hat.
 */
data class SleepSuggestion(
    val time: ZonedDateTime,
    val cycles: Int,
    /** Reine Schlafdauer n × C, ohne die Einschlafzeit. */
    val sleepMinutes: Int,
    val available: Boolean = true,
)

/**
 * Schlafzyklus-Rechner nach dem Muster von sleepyti.me: Weck- und
 * Schlafenszeiten so legen, dass man am Ende eines vollen Zyklus aufwacht.
 *
 * Reines `java.time`, kein Android — damit ist alles hier ohne Robolectric
 * testbar. Zeitzone und Sommerzeit kommen über die [ZonedDateTime] des
 * Aufrufers herein; `plusMinutes`/`minusMinutes` rechnen dabei auf der
 * Zeitachse, eine Nacht über die Zeitumstellung bleibt also so lang, wie sie
 * wirklich ist.
 */
object SleepPlanner {

    /** Anzahl der Zyklen, für die Vorschläge berechnet werden. */
    val CYCLE_COUNTS = listOf(3, 4, 5, 6)

    const val DEFAULT_RECOMMENDED_CYCLES = 5
    const val SLEEP_DEBT_RECOMMENDED_CYCLES = 6

    /** Ab diesem Schlafdefizit (strikt größer) werden 6 Zyklen empfohlen. */
    const val SLEEP_DEBT_THRESHOLD_MINUTES = 3 * 60

    /**
     * „Jetzt schlafen": Weckzeiten now + L + n × C für n = 3, 4, 5, 6.
     *
     * [now] wird auf die nächste volle Minute aufgerundet: Wecker klingeln
     * zur vollen Minute, und abgerundet läge die Weckzeit vor dem Ende des
     * Zyklus statt danach.
     */
    fun wakeTimes(now: ZonedDateTime, settings: SleepSettings): List<SleepSuggestion> {
        val start = ceilToMinute(now)
        return CYCLE_COUNTS.map { cycles ->
            val sleepMinutes = cycles * settings.cycleMinutes
            SleepSuggestion(
                time = start.plusMinutes((settings.fallAsleepMinutes + sleepMinutes).toLong()),
                cycles = cycles,
                sleepMinutes = sleepMinutes,
            )
        }
    }

    /**
     * „Aufwachen um T": Schlafenszeiten T − L − n × C für n = 6, 5, 4, 3 —
     * also früheste zuerst, in der Reihenfolge des Abends.
     *
     * T ist das nächste Vorkommen von [wakeAt] nach [now] (wie bei
     * [Alarm.nextTriggerMillis]): um 23:00 ist 06:30 morgen früh gemeint, und
     * die Schlafenszeiten fallen dann teils noch auf heute.
     */
    fun bedtimes(wakeAt: LocalTime, now: ZonedDateTime, settings: SleepSettings): List<SleepSuggestion> {
        val wake = nextOccurrence(wakeAt, now)
        val currentMinute = now.truncatedTo(ChronoUnit.MINUTES)
        return CYCLE_COUNTS.sortedDescending().map { cycles ->
            val sleepMinutes = cycles * settings.cycleMinutes
            val bedtime = wake.minusMinutes((settings.fallAsleepMinutes + sleepMinutes).toLong())
            SleepSuggestion(
                time = bedtime,
                cycles = cycles,
                sleepMinutes = sleepMinutes,
                // Die laufende Minute zählt noch: "jetzt ins Bett" geht.
                available = !bedtime.isBefore(currentMinute),
            )
        }
    }

    /** Nächstes Vorkommen von [time] strikt nach [now], in der Zone von [now]. */
    fun nextOccurrence(time: LocalTime, now: ZonedDateTime): ZonedDateTime {
        val wanted = time.truncatedTo(ChronoUnit.MINUTES)
        val today = ZonedDateTime.of(now.toLocalDate(), wanted, now.zone)
        return if (today.isAfter(now)) {
            today
        } else {
            ZonedDateTime.of(now.toLocalDate().plusDays(1), wanted, now.zone)
        }
    }

    /**
     * Wie viele Zyklen hervorgehoben werden — `null` ohne Schlafdaten, dann
     * zeigt die UI gar keine Empfehlung.
     *
     * Nie weniger als 5: Auch wer gut ausgeschlafen ist, bekommt nicht
     * 4 Zyklen (6 h) als „empfohlen" angezeigt.
     */
    fun recommendedCycles(summary: SleepSummary?): Int? = when {
        summary == null -> null
        summary.debtMinutes > SLEEP_DEBT_THRESHOLD_MINUTES -> SLEEP_DEBT_RECOMMENDED_CYCLES
        else -> DEFAULT_RECOMMENDED_CYCLES
    }

    private fun ceilToMinute(time: ZonedDateTime): ZonedDateTime {
        val truncated = time.truncatedTo(ChronoUnit.MINUTES)
        return if (truncated == time) time else truncated.plusMinutes(1)
    }
}
