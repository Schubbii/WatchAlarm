package com.watchalarm.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * [SleepPlanner]: reine Rechnung, deshalb ohne Robolectric. Die Zeitzone ist
 * in jedem Test fest vorgegeben, damit nichts von der Maschine abhängt, auf
 * der die Tests laufen.
 */
class SleepPlannerTest {

    private val berlin = ZoneId.of("Europe/Berlin")
    private val defaults = SleepSettings()

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int = 0): ZonedDateTime =
        ZonedDateTime.of(LocalDateTime.of(year, month, day, hour, minute, second), berlin)

    private fun ZonedDateTime.local(): LocalDateTime = toLocalDateTime()

    // ------------------------------------------------------------ Jetzt schlafen

    @Test
    fun `jetzt schlafen liefert now + L + n mal C fuer 3 bis 6 Zyklen`() {
        val result = SleepPlanner.wakeTimes(at(2026, 10, 7, 22, 0), defaults)

        assertEquals(listOf(3, 4, 5, 6), result.map { it.cycles })
        assertEquals(
            listOf(
                LocalDateTime.of(2026, 10, 8, 2, 45),
                LocalDateTime.of(2026, 10, 8, 4, 15),
                LocalDateTime.of(2026, 10, 8, 5, 45),
                LocalDateTime.of(2026, 10, 8, 7, 15),
            ),
            result.map { it.time.local() },
        )
        assertEquals(listOf(270, 360, 450, 540), result.map { it.sleepMinutes })
        assertTrue(result.all { it.available })
    }

    @Test
    fun `jetzt schlafen ueber Mitternacht landet auf dem naechsten Tag`() {
        val result = SleepPlanner.wakeTimes(at(2026, 10, 7, 23, 50), defaults)
        // 23:50 + 15 + 3 × 90 = 04:35 am Folgetag
        assertEquals(LocalDateTime.of(2026, 10, 8, 4, 35), result.first().time.local())
    }

    @Test
    fun `angebrochene Minute wird aufgerundet`() {
        val result = SleepPlanner.wakeTimes(at(2026, 10, 7, 22, 0, 30), defaults)
        assertEquals(LocalDateTime.of(2026, 10, 8, 2, 46), result.first().time.local())
    }

    @Test
    fun `eigene Zykluslaenge und Einschlafzeit werden verwendet`() {
        val settings = SleepSettings(cycleMinutes = 100, fallAsleepMinutes = 5)
        val result = SleepPlanner.wakeTimes(at(2026, 10, 7, 22, 0), settings)
        // 22:00 + 5 + 5 × 100 = 06:25
        assertEquals(LocalDateTime.of(2026, 10, 8, 6, 25), result.first { it.cycles == 5 }.time.local())
        assertEquals(500, result.first { it.cycles == 5 }.sleepMinutes)
    }

    // ---------------------------------------------------------- Aufwachen um T

    @Test
    fun `aufwachen um T liefert Schlafenszeiten fuer 6 bis 3 Zyklen`() {
        val result = SleepPlanner.bedtimes(LocalTime.of(6, 30), at(2026, 10, 7, 18, 0), defaults)

        assertEquals(listOf(6, 5, 4, 3), result.map { it.cycles })
        assertEquals(
            listOf(
                LocalDateTime.of(2026, 10, 7, 21, 15),
                LocalDateTime.of(2026, 10, 7, 22, 45),
                LocalDateTime.of(2026, 10, 8, 0, 15),
                LocalDateTime.of(2026, 10, 8, 1, 45),
            ),
            result.map { it.time.local() },
        )
        assertTrue(result.all { it.available })
    }

    @Test
    fun `Schlafenszeiten vor Mitternacht fallen auf den Vortag der Weckzeit`() {
        // Kurz nach Mitternacht: 06:30 ist heute, die 6-Zyklen-Schlafenszeit
        // lag gestern Abend und ist damit vorbei.
        val result = SleepPlanner.bedtimes(LocalTime.of(6, 30), at(2026, 10, 8, 0, 5), defaults)

        val six = result.first { it.cycles == 6 }
        assertEquals(LocalDateTime.of(2026, 10, 7, 21, 15), six.time.local())
        assertFalse(six.available)
        val three = result.first { it.cycles == 3 }
        assertEquals(LocalDateTime.of(2026, 10, 8, 1, 45), three.time.local())
        assertTrue(three.available)
    }

    @Test
    fun `vergangene Schlafenszeiten sind nicht verfuegbar`() {
        val result = SleepPlanner.bedtimes(LocalTime.of(6, 0), at(2026, 10, 7, 23, 30), defaults)
        // 06:00 − 15 − n × 90: 20:45, 22:15, 23:45, 01:15
        assertEquals(listOf(false, false, true, true), result.map { it.available })
    }

    @Test
    fun `Schlafenszeit in der laufenden Minute gilt noch`() {
        // 07:00 − 15 − 6 × 90 = 21:45; jetzt ist 21:45:40.
        val result = SleepPlanner.bedtimes(LocalTime.of(7, 0), at(2026, 10, 7, 21, 45, 40), defaults)
        assertTrue(result.first { it.cycles == 6 }.available)
    }

    @Test
    fun `Weckzeit gleich jetzt meint morgen`() {
        val next = SleepPlanner.nextOccurrence(LocalTime.of(7, 0), at(2026, 10, 7, 7, 0))
        assertEquals(LocalDateTime.of(2026, 10, 8, 7, 0), next.local())
    }

    @Test
    fun `Weckzeit spaeter am Tag meint heute`() {
        val next = SleepPlanner.nextOccurrence(LocalTime.of(14, 30), at(2026, 10, 7, 13, 0))
        assertEquals(LocalDateTime.of(2026, 10, 7, 14, 30), next.local())
    }

    // ----------------------------------------------------------- Zeitumstellung

    @Test
    fun `Nacht der Zeitumstellung rechnet echte Minuten`() {
        // 25.10.2026, 03:00 MESZ -> 02:00 MEZ: die Nacht hat eine Stunde mehr.
        // 22:00 + 15 + 5 × 90 Minuten echter Zeit = 05:45 Wanduhr minus 1 h.
        val result = SleepPlanner.wakeTimes(at(2026, 10, 24, 22, 0), defaults)
        val five = result.first { it.cycles == 5 }
        assertEquals(LocalDateTime.of(2026, 10, 25, 4, 45), five.time.local())
        assertEquals(
            Duration.ofMinutes(15L + 450L),
            Duration.between(at(2026, 10, 24, 22, 0), five.time),
        )
    }

    @Test
    fun `Zeitzone des Aufrufers bestimmt das Datum`() {
        val tokyo = ZonedDateTime.of(LocalDateTime.of(2026, 10, 7, 23, 0), ZoneId.of("Asia/Tokyo"))
        val result = SleepPlanner.wakeTimes(tokyo, defaults)
        assertEquals(ZoneId.of("Asia/Tokyo"), result.first().time.zone)
        assertEquals(LocalDate.of(2026, 10, 8), result.first().time.toLocalDate())
    }

    // -------------------------------------------------------------- Empfehlung

    @Test
    fun `ohne Schlafdaten keine Empfehlung`() {
        assertNull(SleepPlanner.recommendedCycles(null))
    }

    @Test
    fun `Standardempfehlung sind 5 Zyklen`() {
        val summary = SleepSummary(nights = 7, averageMinutes = 470, debtMinutes = 70)
        assertEquals(5, SleepPlanner.recommendedCycles(summary))
    }

    @Test
    fun `genau 3 Stunden Defizit bleiben bei 5 Zyklen`() {
        val summary = SleepSummary(nights = 7, averageMinutes = 454, debtMinutes = 180)
        assertEquals(5, SleepPlanner.recommendedCycles(summary))
    }

    @Test
    fun `mehr als 3 Stunden Defizit ergeben 6 Zyklen`() {
        val summary = SleepSummary(nights = 7, averageMinutes = 400, debtMinutes = 181)
        assertEquals(6, SleepPlanner.recommendedCycles(summary))
    }

    @Test
    fun `ohne Defizit nie weniger als 5 Zyklen`() {
        val summary = SleepSummary(nights = 7, averageMinutes = 600, debtMinutes = 0)
        assertEquals(5, SleepPlanner.recommendedCycles(summary))
    }
}
