package com.watchalarm.core

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Eine Schlafsitzung, wie sie aus Health Connect kommt — schon auf das
 * Nötigste reduziert: Anfang, Ende und die Wachzeit darin.
 */
data class SleepSession(
    val start: Instant,
    val end: Instant,
    /** Wachphasen innerhalb der Sitzung (wach, wach im Bett, aufgestanden). */
    val awakeMinutes: Int = 0,
) {
    val asleepMinutes: Int
        get() = (Duration.between(start, end).toMinutes() - awakeMinutes)
            .coerceAtLeast(0L).toInt()
}

/**
 * Eine Nacht: Datum des Aufwachens und die Schlafzeit darin.
 *
 * Mehr als diese zwei Zahlen pro Nacht verlässt das Handy nie — an die Uhr
 * werden nur sie synchronisiert, keine Zeitpunkte und keine Schlafphasen.
 */
data class SleepNight(val date: LocalDate, val asleepMinutes: Int)

/** Kurzfassung für die Anzeige: „Ø 6 h 40 min … · 4 h 50 min Defizit". */
data class SleepSummary(
    /** Nächte mit Daten im Zeitfenster (1 bis [SleepStats.NIGHTS]). */
    val nights: Int,
    val averageMinutes: Int,
    val debtMinutes: Int,
)

/**
 * Auswertung der Schlafhistorie. Wie [SleepPlanner] reines `java.time`.
 */
object SleepStats {

    /** Betrachtet werden die letzten 7 Nächte, die letzte Nacht eingeschlossen. */
    const val NIGHTS = 7

    /**
     * Ab wann gelesen werden muss, damit auch die älteste Nacht vollständig
     * drin ist: Wer am Morgen vor sieben Tagen aufgewacht ist, ist am Abend
     * davor eingeschlafen — also einen Tag mehr.
     */
    fun readWindowStart(today: LocalDate, zone: ZoneId): Instant =
        today.minusDays(NIGHTS.toLong()).atStartOfDay(zone).toInstant()

    /**
     * Sitzungen zu Nächten zusammenfassen.
     *
     * - Eine Nacht gehört zum Datum ihres **Endes** (des Aufwachens): Wer
     *   um 23:30 einschläft, hat die Nacht „auf heute" geschlafen.
     * - Mehrere Sitzungen mit demselben Aufwachdatum (z. B. nachts kurz
     *   aufgestanden) werden addiert.
     * - **Überlappende** Sitzungen zählen nur einmal, die längere gewinnt.
     *   Das passiert, wenn zwei Apps dieselbe Nacht aufzeichnen (etwa Uhr
     *   und Handy) — addiert ergäbe das 15 Stunden Schlaf.
     */
    fun nights(sessions: List<SleepSession>, zone: ZoneId): List<SleepNight> {
        val kept = mutableListOf<SleepSession>()
        for (session in sessions.filter { it.end.isAfter(it.start) }.sortedBy { it.start }) {
            val last = kept.lastOrNull()
            if (last != null && session.start.isBefore(last.end)) {
                if (session.asleepMinutes > last.asleepMinutes) kept[kept.lastIndex] = session
            } else {
                kept += session
            }
        }
        return kept
            .groupBy { it.end.atZone(zone).toLocalDate() }
            .map { (date, list) -> SleepNight(date, list.sumOf { it.asleepMinutes }) }
            .sortedBy { it.date }
    }

    /**
     * Durchschnitt und Schlafdefizit der letzten [NIGHTS] Nächte bis
     * einschließlich [today]. `null`, wenn es darin keine Nacht mit Schlaf
     * gibt — die UI zeigt dann weder Zusammenfassung noch Empfehlung.
     *
     * Nächte **ohne Daten** zählen nicht als „0 Stunden geschlafen": Meist
     * lag nur die Uhr auf dem Nachttisch. Sie fallen aus Durchschnitt und
     * Defizit heraus.
     *
     * Das Defizit ist **netto**: Σ (Ziel − Schlaf) über die Nächte mit Daten,
     * nie unter 0. Eine lange Nacht baut also einen Teil des Defizits ab —
     * so, wie man es auch erlebt, wenn man am Wochenende nachschläft. Ein
     * Guthaben auf Vorrat gibt es dagegen nicht.
     */
    fun summarize(nights: List<SleepNight>, today: LocalDate, goalMinutes: Int): SleepSummary? {
        val oldest = today.minusDays((NIGHTS - 1).toLong())
        val window = nights.filter {
            !it.date.isBefore(oldest) && !it.date.isAfter(today) && it.asleepMinutes > 0
        }
        if (window.isEmpty()) return null
        val total = window.sumOf { it.asleepMinutes }
        return SleepSummary(
            nights = window.size,
            averageMinutes = Math.round(total.toDouble() / window.size).toInt(),
            debtMinutes = (goalMinutes * window.size - total).coerceAtLeast(0),
        )
    }
}
