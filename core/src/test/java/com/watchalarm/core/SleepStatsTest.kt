package com.watchalarm.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** [SleepStats]: Nächte bilden, Durchschnitt und Schlafdefizit. */
class SleepStatsTest {

    private val berlin = ZoneId.of("Europe/Berlin")
    private val today = LocalDate.of(2026, 10, 7)
    private val goal = 8 * 60

    private fun instant(month: Int, day: Int, hour: Int, minute: Int = 0): Instant =
        LocalDateTime.of(2026, month, day, hour, minute).atZone(berlin).toInstant()

    private fun night(daysAgo: Long, minutes: Int) = SleepNight(today.minusDays(daysAgo), minutes)

    // ------------------------------------------------------------ Leere Daten

    @Test
    fun `keine Naechte ergeben keine Zusammenfassung`() {
        assertNull(SleepStats.summarize(emptyList(), today, goal))
    }

    @Test
    fun `keine Sitzungen ergeben keine Naechte`() {
        assertEquals(emptyList<SleepNight>(), SleepStats.nights(emptyList(), berlin))
    }

    @Test
    fun `nur alte Naechte ausserhalb des Fensters ergeben keine Zusammenfassung`() {
        assertNull(SleepStats.summarize(listOf(night(7, 420), night(10, 400)), today, goal))
    }

    @Test
    fun `Naechte mit null Minuten zaehlen nicht`() {
        assertNull(SleepStats.summarize(listOf(night(1, 0)), today, goal))
    }

    // ------------------------------------------------------- Zusammenfassung

    @Test
    fun `Durchschnitt und Defizit ueber sieben Naechte`() {
        // 6 h 40 min an allen sieben Nächten: 80 min unter Ziel pro Nacht.
        val nights = (0L..6L).map { night(it, 400) }
        val summary = SleepStats.summarize(nights, today, goal)!!
        assertEquals(7, summary.nights)
        assertEquals(400, summary.averageMinutes)
        assertEquals(7 * 80, summary.debtMinutes)
    }

    @Test
    fun `fehlende Naechte zaehlen nicht als null Stunden`() {
        val summary = SleepStats.summarize(listOf(night(0, 420), night(2, 480)), today, goal)!!
        assertEquals(2, summary.nights)
        assertEquals(450, summary.averageMinutes)
        assertEquals(60, summary.debtMinutes)
    }

    @Test
    fun `lange Naechte bauen Defizit ab, aber nie unter null`() {
        val reduced = SleepStats.summarize(listOf(night(0, 600), night(1, 360)), today, goal)!!
        assertEquals(0, reduced.debtMinutes) // −120 + 120

        val surplus = SleepStats.summarize(listOf(night(0, 600), night(1, 480)), today, goal)!!
        assertEquals(0, surplus.debtMinutes)
    }

    @Test
    fun `Fenster umfasst heute und die sechs Tage davor`() {
        val nights = listOf(night(0, 420), night(6, 420), night(7, 100), SleepNight(today.plusDays(1), 100))
        val summary = SleepStats.summarize(nights, today, goal)!!
        assertEquals(2, summary.nights)
        assertEquals(420, summary.averageMinutes)
    }

    @Test
    fun `eigenes Schlafziel wird verwendet`() {
        val summary = SleepStats.summarize(listOf(night(0, 420)), today, 7 * 60)!!
        assertEquals(0, summary.debtMinutes)
    }

    // ---------------------------------------------------------- Nächte bilden

    @Test
    fun `Nacht ueber Mitternacht gehoert zum Aufwachdatum`() {
        val nights = SleepStats.nights(
            listOf(SleepSession(instant(10, 6, 23, 30), instant(10, 7, 7, 0))),
            berlin,
        )
        assertEquals(listOf(SleepNight(LocalDate.of(2026, 10, 7), 450)), nights)
    }

    @Test
    fun `Wachphasen werden abgezogen`() {
        val session = SleepSession(instant(10, 6, 23, 0), instant(10, 7, 7, 0), awakeMinutes = 35)
        assertEquals(445, SleepStats.nights(listOf(session), berlin).single().asleepMinutes)
    }

    @Test
    fun `zwei Sitzungen derselben Nacht werden addiert`() {
        val nights = SleepStats.nights(
            listOf(
                SleepSession(instant(10, 6, 23, 0), instant(10, 7, 3, 0)),
                SleepSession(instant(10, 7, 3, 30), instant(10, 7, 7, 0)),
            ),
            berlin,
        )
        assertEquals(listOf(SleepNight(LocalDate.of(2026, 10, 7), 240 + 210)), nights)
    }

    @Test
    fun `ueberlappende Sitzungen zaehlen einmal, die laengere gewinnt`() {
        // Uhr und Handy zeichnen dieselbe Nacht auf.
        val nights = SleepStats.nights(
            listOf(
                SleepSession(instant(10, 6, 23, 0), instant(10, 7, 6, 0)),
                SleepSession(instant(10, 6, 23, 10), instant(10, 7, 6, 30)),
            ),
            berlin,
        )
        assertEquals(listOf(SleepNight(LocalDate.of(2026, 10, 7), 440)), nights)
    }

    @Test
    fun `Sitzungen ohne Dauer werden ignoriert`() {
        val t = instant(10, 7, 6, 0)
        assertEquals(emptyList<SleepNight>(), SleepStats.nights(listOf(SleepSession(t, t)), berlin))
    }

    @Test
    fun `Lesefenster beginnt einen Tag vor der aeltesten Nacht`() {
        assertEquals(instant(9, 30, 0), SleepStats.readWindowStart(today, berlin))
    }
}
