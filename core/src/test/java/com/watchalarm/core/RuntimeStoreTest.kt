package com.watchalarm.core

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * [RuntimeStore]: Klingel-Merker und Snooze-Zustand.
 *
 * Der Merker ist eine Menge, weil zwei Alarme auf dieselbe Minute gemeinsam
 * klingeln. An ihm hängt, ob [AlarmService.dismiss] und [AlarmService.snooze]
 * den Service ansprechen oder den Alarm nur still abschließen — steht dort
 * der falsche Alarm, klingelt es weiter.
 */
@RunWith(RobolectricTestRunner::class)
class RuntimeStoreTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        RuntimeStore.prefs(context).edit().clear().commit()
    }

    @Test
    fun `ohne Klingeln ist die Menge leer`() {
        assertEquals(emptySet<String>(), RuntimeStore.getRingingAlarmIds(context))
    }

    /**
     * Der Kern der Sache: Der zweite Alarm darf den ersten nicht verdrängen.
     * Vorher hielt der Merker nur den zuletzt gestarteten, und ein von der
     * Gegenseite abgeschalteter erster Alarm lief deshalb hier weiter.
     */
    @Test
    fun `zweiter klingelnder Alarm verdraengt den ersten nicht`() {
        RuntimeStore.addRingingAlarmId(context, "a")
        RuntimeStore.addRingingAlarmId(context, "b")

        assertEquals(setOf("a", "b"), RuntimeStore.getRingingAlarmIds(context))
        assertTrue("a" in RuntimeStore.getRingingAlarmIds(context))
        assertTrue("b" in RuntimeStore.getRingingAlarmIds(context))
    }

    @Test
    fun `derselbe Alarm zweimal bleibt ein Eintrag`() {
        RuntimeStore.addRingingAlarmId(context, "a")
        RuntimeStore.addRingingAlarmId(context, "a")
        assertEquals(setOf("a"), RuntimeStore.getRingingAlarmIds(context))
    }

    /** Abmelden darf nur den genannten Alarm treffen. */
    @Test
    fun `einzelner Alarm laesst sich abmelden`() {
        RuntimeStore.addRingingAlarmId(context, "a")
        RuntimeStore.addRingingAlarmId(context, "b")

        RuntimeStore.removeRingingAlarmIds(context, setOf("a"))

        assertEquals(setOf("b"), RuntimeStore.getRingingAlarmIds(context))
    }

    @Test
    fun `leere Auswahl meldet nichts ab`() {
        RuntimeStore.addRingingAlarmId(context, "a")
        RuntimeStore.removeRingingAlarmIds(context, emptySet())
        assertEquals(setOf("a"), RuntimeStore.getRingingAlarmIds(context))
    }

    @Test
    fun `Abmelden unbekannter Alarme aendert nichts`() {
        RuntimeStore.addRingingAlarmId(context, "a")
        RuntimeStore.removeRingingAlarmIds(context, setOf("x", "y"))
        assertEquals(setOf("a"), RuntimeStore.getRingingAlarmIds(context))
    }

    @Test
    fun `Klingeln beenden raeumt alles ab`() {
        RuntimeStore.addRingingAlarmId(context, "a")
        RuntimeStore.addRingingAlarmId(context, "b")

        RuntimeStore.clearRingingAlarmIds(context)

        assertEquals(emptySet<String>(), RuntimeStore.getRingingAlarmIds(context))
    }

    /**
     * Ein Stand aus einer Version vor der Menge trägt unter dem alten
     * Schlüssel noch einen einzelnen String. Gelesen wird er nicht mehr —
     * täte man es, käme eine ClassCastException — aber liegen bleiben soll er
     * auch nicht.
     */
    @Test
    fun `alter Einzel-Schluessel stoert nicht und wird aufgeraeumt`() {
        RuntimeStore.prefs(context).edit().putString("ringing_alarm_id", "alt").commit()

        assertEquals(emptySet<String>(), RuntimeStore.getRingingAlarmIds(context))

        RuntimeStore.clearRingingAlarmIds(context)

        assertFalse(RuntimeStore.prefs(context).contains("ringing_alarm_id"))
    }

    // ------------------------------------------------------------ Snooze

    @Test
    fun `Snooze-Zaehler und -Zeitpunkt gehen gemeinsam weg`() {
        RuntimeStore.setSnoozeCount(context, "a", 2)
        RuntimeStore.setSnoozeUntil(context, "a", 12_345L)

        RuntimeStore.clearSnoozeCount(context, "a")

        assertEquals(0, RuntimeStore.getSnoozeCount(context, "a"))
        assertEquals(0L, RuntimeStore.getSnoozeUntil(context, "a"))
    }

    @Test
    fun `Snooze-Zustand ist pro Alarm getrennt`() {
        RuntimeStore.setSnoozeCount(context, "a", 1)
        RuntimeStore.setSnoozeCount(context, "b", 3)

        RuntimeStore.clearSnoozeCount(context, "a")

        assertEquals(0, RuntimeStore.getSnoozeCount(context, "a"))
        assertEquals(3, RuntimeStore.getSnoozeCount(context, "b"))
    }
}
