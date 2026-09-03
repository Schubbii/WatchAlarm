package com.watchalarm.core

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.util.Calendar

/**
 * [SleepDuration]: Restzeit bis zum Klingeln und deren Schreibweise.
 *
 * Geprüft wird gegen die englischen Standard-Ressourcen (values/), die
 * Robolectric ohne gesetzte Locale verwendet.
 */
@RunWith(RobolectricTestRunner::class)
class SleepDurationTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        RuntimeStore.prefs(context).edit().clear().commit()
    }

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int = 0): Long =
        Calendar.getInstance().apply {
            set(year, month, day, hour, minute, second)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    // -------------------------------------------------------------- Formatierung

    @Test
    fun `Stunden und Minuten`() {
        assertEquals("7 h 30 min", SleepDuration.format(context, (7 * 60 + 30) * 60_000L))
    }

    @Test
    fun `volle Stunden ohne Minutenanteil`() {
        assertEquals("8 h", SleepDuration.format(context, 8 * 60 * 60_000L))
    }

    @Test
    fun `unter einer Stunde nur Minuten`() {
        assertEquals("45 min", SleepDuration.format(context, 45 * 60_000L))
    }

    /**
     * Ab einem Tag Abstand — Wiederholung an einzelnen Wochentagen — wäre
     * "79 h" nicht mehr lesbar.
     */
    @Test
    fun `ab einem Tag in Tagen und Stunden`() {
        assertEquals("3 d 7 h", SleepDuration.format(context, (3 * 24 + 7) * 60 * 60_000L))
    }

    @Test
    fun `voller Tag ohne Stundenanteil`() {
        assertEquals("2 d", SleepDuration.format(context, 2 * 24 * 60 * 60_000L))
    }

    /**
     * Aufgerundet wird auf volle Minuten: Alarme klingeln zur vollen Minute,
     * die laufende ist also angebrochen. Um 22:30:15 sind es bis 6:00 Uhr
     * 7 h 30 min, nicht 7 h 29 min.
     */
    @Test
    fun `angebrochene Minute zaehlt voll`() {
        val millis = (7 * 60 + 29) * 60_000L + 45_000L
        assertEquals("7 h 30 min", SleepDuration.format(context, millis))
    }

    @Test
    fun `null Millisekunden ergibt null Minuten`() {
        assertEquals("0 min", SleepDuration.format(context, 0L))
    }

    // ------------------------------------------------------------ Restzeit

    @Test
    fun `Restzeit bis zum naechsten regulaeren Klingeln`() {
        val alarm = Alarm(id = "a", hour = 6, minute = 0)
        val now = at(2026, Calendar.MARCH, 10, 22, 30)
        assertEquals(7 * 60 + 30, (SleepDuration.millisUntil(context, alarm, now) / 60_000L).toInt())
    }

    /**
     * Ein laufender Snooze zieht den nächsten Termin vor — genau so plant
     * [AlarmScheduler.rescheduleAll] ihn auch. Sonst stünde nach dem
     * Schlummern die reguläre Weckzeit von morgen da.
     */
    @Test
    fun `laufender Snooze zieht den naechsten Termin vor`() {
        val alarm = Alarm(id = "a", hour = 6, minute = 0)
        val now = at(2026, Calendar.MARCH, 10, 22, 30)
        RuntimeStore.setSnoozeUntil(context, "a", now + 9 * 60_000L)

        assertEquals(9L, SleepDuration.millisUntil(context, alarm, now) / 60_000L)
        assertEquals("9 min", SleepDuration.formatUntil(context, alarm, now))
    }

    /**
     * Ein abgelaufener Snooze ist keiner mehr — sonst zeigte die Liste nach
     * einem verpassten Schlummertermin dauerhaft "0 min".
     */
    @Test
    fun `abgelaufener Snooze wird ignoriert`() {
        val alarm = Alarm(id = "a", hour = 6, minute = 0)
        val now = at(2026, Calendar.MARCH, 10, 22, 30)
        RuntimeStore.setSnoozeUntil(context, "a", now - 60_000L)

        assertEquals(7 * 60 + 30, (SleepDuration.millisUntil(context, alarm, now) / 60_000L).toInt())
    }

    @Test
    fun `Restzeit wird nie negativ`() {
        val alarm = Alarm(id = "a", hour = 6, minute = 0)
        val now = at(2026, Calendar.MARCH, 10, 22, 30)
        RuntimeStore.setSnoozeUntil(context, "a", now - 5 * 60_000L)
        assert(SleepDuration.millisUntil(context, alarm, now) >= 0L)
    }
}
