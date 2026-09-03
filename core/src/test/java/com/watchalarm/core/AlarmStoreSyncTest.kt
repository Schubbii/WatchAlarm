package com.watchalarm.core

import android.content.Context
import org.robolectric.RuntimeEnvironment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Abgleich zweier Geräte in [AlarmStore].
 *
 * Das Modell ist ein Lamport-Zähler mit Signatur-Stichentscheid. Die
 * Begründung steht ausführlich am Objekt; hier steht, dass es auch tut, was
 * dort behauptet wird. Ein Fehler in diesen Regeln macht keinen Lärm — er
 * lässt zwei Geräte still auseinanderlaufen.
 */
@RunWith(RobolectricTestRunner::class)
class AlarmStoreSyncTest {

    private lateinit var context: Context

    private val fruehA = Alarm(id = "a", hour = 6, minute = 0, label = "A")
    private val spaetA = Alarm(id = "a", hour = 9, minute = 0, label = "A")
    private val alarmB = Alarm(id = "b", hour = 7, minute = 0, label = "B")

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        AlarmStore.prefs(context).edit().clear().commit()
        RuntimeStore.prefs(context).edit().clear().commit()
    }

    /**
     * Lokalen Stand auf [version] bringen. [AlarmStore.applyLocalChange] zählt
     * pro Aufruf um eins hoch; ein direkter Schreibzugriff auf die Prefs würde
     * die Schlüsselnamen dieses Tests an interne Konstanten binden.
     */
    private fun seedLocal(alarms: List<Alarm>, version: Long) {
        repeat(version.toInt()) { runde ->
            AlarmStore.applyLocalChange(context) { if (runde == version.toInt() - 1) alarms else it }
        }
        assertEquals(version, AlarmStore.getVersion(context))
    }

    // ------------------------------------------------------- lokale Änderung

    @Test
    fun `lokale Aenderung zaehlt die Version hoch und speichert`() {
        AlarmStore.applyLocalChange(context) { listOf(fruehA) }
        assertEquals(1L, AlarmStore.getVersion(context))
        assertEquals(listOf(fruehA), AlarmStore.getAlarms(context))
    }

    /**
     * Sonst bleiben Snooze-Zähler und -Zeitpunkt eines gelöschten Alarms für
     * immer in den Prefs liegen — und ein später mit derselben ID angelegter
     * Alarm erbt sie.
     */
    @Test
    fun `geloeschter Alarm verliert seine Snooze-Eintraege`() {
        AlarmStore.applyLocalChange(context) { listOf(fruehA, alarmB) }
        RuntimeStore.setSnoozeCount(context, "a", 2)
        RuntimeStore.setSnoozeUntil(context, "a", System.currentTimeMillis() + 60_000L)

        AlarmStore.applyLocalChange(context) { list -> list.filterNot { it.id == "a" } }

        assertEquals(0, RuntimeStore.getSnoozeCount(context, "a"))
        assertEquals(0L, RuntimeStore.getSnoozeUntil(context, "a"))
    }

    @Test
    fun `per Sync geloeschter Alarm verliert seine Snooze-Eintraege ebenfalls`() {
        seedLocal(listOf(fruehA, alarmB), 3)
        RuntimeStore.setSnoozeCount(context, "a", 1)

        assertTrue(AlarmStore.applyRemote(context, listOf(alarmB), 4))

        assertEquals(0, RuntimeStore.getSnoozeCount(context, "a"))
    }

    // ------------------------------------------------------ Versionsvergleich

    @Test
    fun `hoehere Version wird uebernommen`() {
        seedLocal(listOf(fruehA), 3)
        assertTrue(AlarmStore.applyRemote(context, listOf(spaetA), 4))
        assertEquals(listOf(spaetA), AlarmStore.getAlarms(context))
        assertEquals(4L, AlarmStore.getVersion(context))
    }

    @Test
    fun `aeltere Version wird abgelehnt und der eigene Stand bleibt stehen`() {
        seedLocal(listOf(spaetA), 5)
        assertFalse(AlarmStore.applyRemote(context, listOf(fruehA), 2))
        assertEquals(listOf(spaetA), AlarmStore.getAlarms(context))
        assertEquals(5L, AlarmStore.getVersion(context))
    }

    /**
     * Gleicher Inhalt heißt: nichts tun. Dieser Abbruch ist das, was den
     * Rückpush bei Gleichstand ungefährlich macht — der zurückgesandte Stand
     * trifft drüben auf denselben Inhalt und endet hier, statt sich zwischen
     * den Geräten aufzuschaukeln.
     */
    @Test
    fun `identischer Inhalt aendert nichts`() {
        seedLocal(listOf(fruehA), 3)
        assertFalse(AlarmStore.applyRemote(context, listOf(fruehA), 3))
        assertEquals(3L, AlarmStore.getVersion(context))
    }

    /**
     * Der Fall, an dem das reine „höher gewinnt" scheitert: Beide Seiten sind
     * auf derselben Version und haben nach einem Verbindungsabriss
     * unterschiedlich geändert. Ohne Stichentscheid lehnt jede Seite den Stand
     * der anderen als „nicht neuer" ab und beide bleiben für immer verschieden.
     */
    @Test
    fun `Gleichstand mit unterschiedlichem Inhalt wird aufgeloest`() {
        seedLocal(listOf(fruehA), 4)
        val uebernommen = AlarmStore.applyRemote(context, listOf(spaetA), 4)
        assertEquals(
            if (uebernommen) listOf(spaetA) else listOf(fruehA),
            AlarmStore.getAlarms(context),
        )
    }

    /**
     * Und zwar auf *beiden* Geräten gleich: Dieselben zwei Stände, aus der
     * jeweils anderen Richtung angewandt, müssen denselben Gewinner ergeben.
     * Genau eine Seite übernimmt, und danach stehen beide auf demselben Inhalt.
     */
    @Test
    fun `beide Geraete landen bei Gleichstand auf demselben Stand`() {
        seedLocal(listOf(fruehA), 4)
        val geraetEins = AlarmStore.applyRemote(context, listOf(spaetA), 4)
        val ergebnisEins = AlarmStore.getAlarms(context)

        setUp()
        seedLocal(listOf(spaetA), 4)
        val geraetZwei = AlarmStore.applyRemote(context, listOf(fruehA), 4)
        val ergebnisZwei = AlarmStore.getAlarms(context)

        assertNotEquals("genau eine Seite darf uebernehmen", geraetEins, geraetZwei)
        assertEquals("beide Geraete muessen konvergieren", ergebnisEins, ergebnisZwei)
    }

    // ------------------------------------------------- Vollstaendigkeit der Felder

    /**
     * Regression: Die Klingeldauer ist ein synchronisiertes Feld, taucht aber
     * in [Alarm.listSignature] nicht auf. Der Gleichheits-Abbruch in
     * mergeRemote greift dann auch bei echtem Unterschied, und eine am Handy
     * geänderte Klingeldauer erreicht die Uhr nie — trotz höherer Version.
     */
    @Test
    fun `geaenderte Klingeldauer kommt trotz gleicher uebriger Felder an`() {
        seedLocal(listOf(fruehA.copy(ringTimeoutMinutes = 30)), 3)

        val geaendert = fruehA.copy(ringTimeoutMinutes = 5)
        assertTrue(
            "Aenderung nur an ringTimeoutMinutes wurde als inhaltsgleich verworfen",
            AlarmStore.applyRemote(context, listOf(geaendert), 4),
        )
        assertEquals(5, AlarmStore.getAlarms(context).single().ringTimeoutMinutes)
    }

    // ------------------------------------------------------------ Schnappschuss

    @Test
    fun `Schnappschuss liefert Liste und Version aus einem Zug`() {
        seedLocal(listOf(fruehA, alarmB), 2)
        val snapshot = AlarmStore.snapshot(context)
        assertEquals(2L, snapshot.version)
        assertEquals(listOf(fruehA, alarmB), snapshot.alarms)
        assertEquals(snapshot.alarms, Alarm.listFromJson(snapshot.json))
    }
}
