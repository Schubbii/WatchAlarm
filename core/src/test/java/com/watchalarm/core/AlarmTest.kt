package com.watchalarm.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Calendar

/**
 * [Alarm]: Zeitrechnung, JSON-Runde und Signatur.
 *
 * Robolectric, weil [Alarm.toJson] echtes org.json braucht — im android.jar
 * der Unit-Tests ist das nur ein Stub, der beim Aufruf wirft.
 */
@RunWith(RobolectricTestRunner::class)
class AlarmTest {

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        Calendar.getInstance().apply {
            set(year, month, day, hour, minute, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    /** Jahr, Monat, Tag, Stunde, Minute — Millis vergleichen sich schlecht. */
    private fun fieldsOf(millis: Long): List<Int> =
        Calendar.getInstance().apply { timeInMillis = millis }.let {
            listOf(
                it.get(Calendar.YEAR), it.get(Calendar.MONTH), it.get(Calendar.DAY_OF_MONTH),
                it.get(Calendar.HOUR_OF_DAY), it.get(Calendar.MINUTE),
            )
        }

    // ----------------------------------------------------- nextTriggerMillis

    @Test
    fun `einmaliger Alarm heute, wenn die Uhrzeit noch aussteht`() {
        val alarm = Alarm(hour = 7, minute = 30)
        val now = at(2026, Calendar.MARCH, 10, 6, 0)
        assertEquals(listOf(2026, Calendar.MARCH, 10, 7, 30), fieldsOf(alarm.nextTriggerMillis(now)))
    }

    @Test
    fun `einmaliger Alarm morgen, wenn die Uhrzeit vorbei ist`() {
        val alarm = Alarm(hour = 7, minute = 30)
        val now = at(2026, Calendar.MARCH, 10, 8, 0)
        assertEquals(listOf(2026, Calendar.MARCH, 11, 7, 30), fieldsOf(alarm.nextTriggerMillis(now)))
    }

    /**
     * Die Weckminute selbst zählt als vorbei. Sonst plante ein gerade
     * klingelnder Alarm sich beim rescheduleAll auf denselben Zeitpunkt neu.
     */
    @Test
    fun `exakt die Weckminute zaehlt als vorbei`() {
        val alarm = Alarm(hour = 7, minute = 30)
        val now = at(2026, Calendar.MARCH, 10, 7, 30)
        assertEquals(listOf(2026, Calendar.MARCH, 11, 7, 30), fieldsOf(alarm.nextTriggerMillis(now)))
    }

    @Test
    fun `Wochentagsalarm findet den naechsten passenden Tag`() {
        // Der 10.3.2026 ist ein Dienstag; dieser Alarm gilt nur freitags.
        val alarm = Alarm(hour = 7, minute = 0, repeatDays = setOf(Calendar.FRIDAY))
        val next = alarm.nextTriggerMillis(at(2026, Calendar.MARCH, 10, 8, 0))
        assertEquals(listOf(2026, Calendar.MARCH, 13, 7, 0), fieldsOf(next))
    }

    /**
     * Der Sprung über die Wochengrenze ist der Fall, für den die Schleife in
     * [Alarm.nextTriggerMillis] acht statt sieben Durchläufe hat: von Dienstag
     * zum Montag sind es sechs Tage.
     */
    @Test
    fun `Wochentagsalarm springt ueber die Wochengrenze`() {
        val alarm = Alarm(hour = 7, minute = 0, repeatDays = setOf(Calendar.MONDAY))
        val next = alarm.nextTriggerMillis(at(2026, Calendar.MARCH, 10, 8, 0))
        assertEquals(listOf(2026, Calendar.MARCH, 16, 7, 0), fieldsOf(next))
    }

    @Test
    fun `Wochentagsalarm klingelt heute, wenn die Uhrzeit noch aussteht`() {
        val alarm = Alarm(hour = 9, minute = 0, repeatDays = setOf(Calendar.TUESDAY))
        val next = alarm.nextTriggerMillis(at(2026, Calendar.MARCH, 10, 8, 0))
        assertEquals(listOf(2026, Calendar.MARCH, 10, 9, 0), fieldsOf(next))
    }

    // ------------------------------------------------------------------ JSON

    @Test
    fun `JSON-Runde erhaelt alle Felder`() {
        val alarm = Alarm(
            id = "abc",
            hour = 6,
            minute = 5,
            label = "Früh",
            enabled = false,
            repeatDays = setOf(Calendar.MONDAY, Calendar.THURSDAY),
            snoozeMinutes = 7,
            maxSnoozes = 2,
            ringTimeoutMinutes = 15,
        )
        assertEquals(alarm, Alarm.fromJson(alarm.toJson()))
    }

    /**
     * Stände, die vor der einstellbaren Klingeldauer geschrieben wurden,
     * kennen das Feld nicht. Sie müssen den neuen Standard bekommen — nicht
     * die alten fünf Minuten und vor allem keine 0.
     */
    @Test
    fun `alter Stand ohne ringTimeoutMinutes bekommt den Standard`() {
        val alt = "[{\"id\":\"a\",\"hour\":7,\"minute\":0,\"enabled\":true}]"
        val alarm = Alarm.listFromJson(alt).single()
        assertEquals(Alarm.DEFAULT_RING_TIMEOUT_MINUTES, alarm.ringTimeoutMinutes)
    }

    @Test
    fun `kaputtes JSON ergibt eine leere Liste statt eines Absturzes`() {
        assertEquals(emptyList<Alarm>(), Alarm.listFromJson("kein json"))
    }

    // --------------------------------------------------------------- Signatur

    /**
     * Die Signatur entscheidet in [AlarmStore.applyRemote], ob zwei Stände als
     * inhaltsgleich gelten. Sie muss deshalb unabhängig von der Reihenfolge
     * sein — die UI hängt einen bearbeiteten Alarm hinten an.
     */
    @Test
    fun `Signatur ist unabhaengig von der Reihenfolge der Alarme`() {
        val a = Alarm(id = "a", hour = 6)
        val b = Alarm(id = "b", hour = 7)
        assertEquals(Alarm.listSignature(listOf(a, b)), Alarm.listSignature(listOf(b, a)))
    }

    /** Und unabhängig von der Einfügereihenfolge des repeatDays-Sets. */
    @Test
    fun `Signatur ist unabhaengig von der Reihenfolge der Wochentage`() {
        val vor = Alarm(id = "a", repeatDays = linkedSetOf(Calendar.FRIDAY, Calendar.MONDAY))
        val zurueck = Alarm(id = "a", repeatDays = linkedSetOf(Calendar.MONDAY, Calendar.FRIDAY))
        assertEquals(Alarm.listSignature(listOf(vor)), Alarm.listSignature(listOf(zurueck)))
    }

    /**
     * Jedes synchronisierte Feld muss auf die Signatur durchschlagen. Fehlt
     * eines, gilt eine Änderung daran als inhaltlich identisch, und
     * [AlarmStore.applyRemote] verwirft sie, noch bevor die Version überhaupt
     * betrachtet wird — die Änderung kommt auf dem anderen Gerät nie an.
     */
    @Test
    fun `jedes synchronisierte Feld schlaegt auf die Signatur durch`() {
        val basis = Alarm(
            id = "a", hour = 7, minute = 0, label = "L", enabled = true,
            repeatDays = setOf(Calendar.MONDAY), snoozeMinutes = 5, maxSnoozes = 3,
            ringTimeoutMinutes = 30,
        )
        val abweichungen = mapOf(
            "hour" to basis.copy(hour = 8),
            "minute" to basis.copy(minute = 1),
            "label" to basis.copy(label = "M"),
            "enabled" to basis.copy(enabled = false),
            "repeatDays" to basis.copy(repeatDays = setOf(Calendar.TUESDAY)),
            "snoozeMinutes" to basis.copy(snoozeMinutes = 10),
            "maxSnoozes" to basis.copy(maxSnoozes = 1),
            "ringTimeoutMinutes" to basis.copy(ringTimeoutMinutes = 5),
        )
        val signaturBasis = Alarm.listSignature(listOf(basis))
        abweichungen.forEach { (feld, geaendert) ->
            assertNotEquals(
                "Aenderung an $feld veraendert die Signatur nicht, wird beim Sync also verworfen",
                signaturBasis,
                Alarm.listSignature(listOf(geaendert)),
            )
        }
    }

    /**
     * Der Feldtrenner darf in keinem Feld vorkommen. Sonst ließen sich zwei
     * verschiedene Bestände auf dieselbe Signatur bringen.
     */
    @Test
    fun `Label und Nachbarfeld verwischen die Feldgrenze nicht`() {
        val eins = Alarm(id = "a", label = "7", minute = 0)
        val zwei = Alarm(id = "a", label = "", minute = 7)
        assertNotEquals(Alarm.listSignature(listOf(eins)), Alarm.listSignature(listOf(zwei)))
    }

    @Test
    fun `leere Liste hat eine stabile Signatur`() {
        assertEquals(Alarm.listSignature(emptyList()), Alarm.listSignature(emptyList()))
        assertTrue(Alarm.listSignature(emptyList()).isEmpty())
    }
}
