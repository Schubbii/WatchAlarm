package com.watchalarm.core

import android.app.AlarmManager
import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/**
 * [AlarmScheduler]: was am Ende wirklich im AlarmManager steht.
 *
 * Hier lag der teuerste Fehler der bisherigen Reviews — ein gelöschter Alarm
 * behielt seinen PendingIntent, das System zeigte weiter „nächster Alarm
 * 07:00", und zur Weckzeit ging der Bildschirm an für einen Alarm, den es
 * nicht mehr gab. Diese Tests halten das fest.
 *
 * Geprüft wird über die Auslösezeit statt über den PendingIntent: Dessen
 * Aufbau ist ein Implementierungsdetail des Schedulers, das ein Test nicht
 * verdoppeln sollte. [AppRegistry.ringActivityClass] ist im core-Modul nicht
 * gesetzt, pro Alarm steht deshalb genau ein Eintrag.
 */
@RunWith(RobolectricTestRunner::class)
class AlarmSchedulerTest {

    private lateinit var context: Context
    private lateinit var alarmManager: AlarmManager

    /** 3:17 — weit genug von jeder üblichen Testlaufzeit entfernt. */
    private val frueh = Alarm(id = "frueh", hour = 3, minute = 17)

    /** 4:23 — unterscheidbare zweite Weckzeit. */
    private val spaet = Alarm(id = "spaet", hour = 4, minute = 23)

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        alarmManager = context.getSystemService(AlarmManager::class.java)
        AlarmStore.prefs(context).edit().clear().commit()
        RuntimeStore.prefs(context).edit().clear().commit()
    }

    // Robolectric hat das Feld zugunsten eines Getters abgekuendigt, den
    // Kotlin hinter der synthetischen Property nicht mehr aufrufen laesst.
    @Suppress("DEPRECATION")
    private fun geplanteZeiten(): List<Long> =
        shadowOf(alarmManager).scheduledAlarms.map { it.triggerAtTime }

    // ------------------------------------------------------------- Planen

    @Test
    fun `eingeschalteter Alarm wird auf seinen naechsten Termin geplant`() {
        AlarmStore.applyLocalChange(context) { listOf(frueh) }

        assertEquals(listOf(frueh.nextTriggerMillis()), geplanteZeiten())
    }

    @Test
    fun `mehrere Alarme werden einzeln geplant`() {
        AlarmStore.applyLocalChange(context) { listOf(frueh, spaet) }

        assertEquals(
            setOf(frueh.nextTriggerMillis(), spaet.nextTriggerMillis()),
            geplanteZeiten().toSet(),
        )
    }

    @Test
    fun `ausgeschalteter Alarm wird nicht geplant`() {
        AlarmStore.applyLocalChange(context) { listOf(frueh.copy(enabled = false)) }

        assertEquals(emptyList<Long>(), geplanteZeiten())
    }

    /**
     * Ausschalten muss den bereits gesetzten Eintrag wieder abräumen, nicht
     * nur das Neusetzen unterlassen.
     */
    @Test
    fun `Ausschalten raeumt den geplanten Eintrag ab`() {
        AlarmStore.applyLocalChange(context) { listOf(frueh) }
        assertTrue(geplanteZeiten().isNotEmpty())

        AlarmStore.applyLocalChange(context) { list -> list.map { it.copy(enabled = false) } }

        assertEquals(emptyList<Long>(), geplanteZeiten())
    }

    // ------------------------------------------------------------- Löschen

    /**
     * Der Befund aus dem Review: cancel() war nur über den !enabled-Zweig von
     * rescheduleAll erreichbar, und der läuft ausschließlich über noch
     * vorhandene Alarme. Ein gelöschter Alarm blieb deshalb geplant.
     */
    @Test
    fun `geloeschter Alarm bleibt nicht geplant`() {
        AlarmStore.applyLocalChange(context) { listOf(frueh, spaet) }

        AlarmStore.applyLocalChange(context) { list -> list.filterNot { it.id == frueh.id } }

        assertEquals(listOf(spaet.nextTriggerMillis()), geplanteZeiten())
    }

    /** Gilt genauso, wenn die Gegenseite gelöscht hat. */
    @Test
    fun `per Sync geloeschter Alarm bleibt nicht geplant`() {
        AlarmStore.applyLocalChange(context) { listOf(frueh, spaet) }
        val version = AlarmStore.getVersion(context)

        AlarmStore.applyRemote(context, listOf(spaet), version + 1)

        assertEquals(listOf(spaet.nextTriggerMillis()), geplanteZeiten())
    }

    @Test
    fun `cancel entfernt den Eintrag`() {
        AlarmStore.applyLocalChange(context) { listOf(frueh) }

        AlarmScheduler.cancel(context, frueh.id)

        assertEquals(emptyList<Long>(), geplanteZeiten())
    }

    // -------------------------------------------------------------- Snooze

    /**
     * Ein laufender Snooze darf beim Neuplanen nicht verloren gehen — sonst
     * stünde nach jedem Sync-Update oder Neustart wieder die reguläre
     * Weckzeit von morgen da und der Schlummertermin fiele aus.
     */
    @Test
    fun `laufender Snooze ueberlebt das Neuplanen`() {
        AlarmStore.applyLocalChange(context) { listOf(frueh) }
        val snoozeUntil = System.currentTimeMillis() + 10 * 60_000L
        RuntimeStore.setSnoozeUntil(context, frueh.id, snoozeUntil)

        AlarmScheduler.rescheduleAll(context)

        assertEquals(listOf(snoozeUntil), geplanteZeiten())
    }

    /**
     * Ein abgelaufener Snooze ist keiner mehr: Sonst bliebe der Alarm auf
     * einem Zeitpunkt in der Vergangenheit stehen und klingelte nie wieder.
     */
    @Test
    fun `abgelaufener Snooze faellt auf die regulaere Weckzeit zurueck`() {
        AlarmStore.applyLocalChange(context) { listOf(frueh) }
        RuntimeStore.setSnoozeUntil(context, frueh.id, System.currentTimeMillis() - 60_000L)

        AlarmScheduler.rescheduleAll(context)

        assertEquals(listOf(frueh.nextTriggerMillis()), geplanteZeiten())
    }

    /**
     * Ein Snooze auf einem ausgeschalteten Alarm darf ihn nicht wiederbeleben
     * — der !enabled-Zweig kommt vor der Snooze-Prüfung.
     */
    @Test
    fun `Snooze auf ausgeschaltetem Alarm plant nichts`() {
        AlarmStore.applyLocalChange(context) { listOf(frueh.copy(enabled = false)) }
        RuntimeStore.setSnoozeUntil(context, frueh.id, System.currentTimeMillis() + 10 * 60_000L)

        AlarmScheduler.rescheduleAll(context)

        assertEquals(emptyList<Long>(), geplanteZeiten())
    }

    // --------------------------------------------------------- Idempotenz

    /**
     * rescheduleAll läuft bei jedem Start, jedem Sync-Update und jedem
     * Klingeln. Es darf sich dabei nichts aufstauen.
     */
    @Test
    fun `mehrfaches Neuplanen haeuft keine Eintraege an`() {
        AlarmStore.applyLocalChange(context) { listOf(frueh, spaet) }

        repeat(3) { AlarmScheduler.rescheduleAll(context) }

        assertEquals(2, geplanteZeiten().size)
    }
}
