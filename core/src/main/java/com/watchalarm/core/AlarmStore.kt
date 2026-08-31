package com.watchalarm.core

import android.content.Context
import android.content.SharedPreferences

/**
 * Persistente Alarmliste (SharedPreferences, JSON).
 *
 * Für den Abgleich zwischen Uhr und Handy trägt die Liste einen
 * **Lamport-Versionszähler** statt eines Wanduhr-Zeitstempels: Jede lokale
 * Änderung erhöht ihn um 1; ein empfangener Stand wird übernommen, wenn
 * seine Version höher ist als die eigene. Das ist geräteunabhängig und
 * verhindert das frühere Problem, dass Uhr→Handy-Änderungen wegen
 * abweichender Emulator-Uhren verworfen wurden.
 *
 * **Gleichstand.** Ein reines „höher gewinnt" reicht nicht: Sind beide Seiten
 * auf v4 und ändern nach einem Verbindungsabriss unabhängig, stehen beide auf
 * v5 mit verschiedenem Inhalt. Jede lehnte den Stand der anderen als „nicht
 * neuer" ab, und die Geräte blieben dauerhaft verschieden. Bei Gleichstand
 * entscheidet deshalb ein Vergleich von [Alarm.listSignature] — willkürlich,
 * aber auf beiden Geräten identisch, sodass sie garantiert auf demselben Stand
 * landen. Die unterlegene Änderung geht dabei verloren; das ist der Preis
 * dieses Modells, ein echter Merge pro Alarm wäre ein anderer Entwurf.
 *
 * **Nebenläufigkeit.** Lesen und Schreiben gehören zusammen: [applyRemote]
 * läuft auf dem Listener-Thread der Data Layer, [applyLocalChange] auf dem
 * Main-Thread. Beide führen ihren Lese-Ändere-Schreib-Zyklus vollständig unter
 * dem Monitor dieses Objekts aus. Nachgelagertes (Planung, Push) läuft
 * bewusst außerhalb.
 */
object AlarmStore {

    private const val PREFS = "watchalarm_store"
    private const val KEY_ALARMS = "alarms_json"
    private const val KEY_VERSION = "version"

    fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getAlarms(context: Context): List<Alarm> =
        Alarm.listFromJson(prefs(context).getString(KEY_ALARMS, null) ?: "[]")

    fun getAlarm(context: Context, id: String): Alarm? =
        getAlarms(context).firstOrNull { it.id == id }

    fun getVersion(context: Context): Long = prefs(context).getLong(KEY_VERSION, 0L)

    /** Zusammengehörender Stand aus Liste, JSON und Version. */
    data class Snapshot(val alarms: List<Alarm>, val json: String, val version: Long)

    /**
     * Liste und Version in *einem* Zug lesen.
     *
     * Beide getrennt zu lesen war eine Fehlerquelle: Zwischen den zwei
     * Zugriffen konnte ein empfangener Stand dazwischenschreiben, und der Push
     * veröffentlichte dann den Inhalt von v(N+1) unter der Version vN. Die
     * Gegenseite übernahm ihn als vN, und der echte v(N+1)-Push wurde später
     * als „nicht neuer" abgelehnt — beide Geräte standen auseinander.
     */
    @Synchronized
    fun snapshot(context: Context): Snapshot {
        val json = prefs(context).getString(KEY_ALARMS, null) ?: "[]"
        return Snapshot(Alarm.listFromJson(json), json, getVersion(context))
    }

    /** Schreibt den Stand. Aufrufer muss den Monitor halten. */
    private fun writeLocked(context: Context, json: String, version: Long) {
        // apply() statt commit(): der Wert steht sofort im Speicher (alle
        // Leser unten sehen ihn), das Schreiben auf die Platte läuft im
        // Hintergrund. commit() blockierte hier den Compose-Callback, also
        // den Main-Thread — auf Uhren gut sicht- und messbar.
        prefs(context).edit()
            .putString(KEY_ALARMS, json)
            .putLong(KEY_VERSION, version)
            .apply()
    }

    /**
     * Lokale Änderung (UI, Ausschalten eines Einmal-Alarms, …): Version
     * erhöhen, speichern, Alarme neu planen und den neuen Stand pushen.
     */
    fun applyLocalChange(context: Context, mutate: (List<Alarm>) -> List<Alarm>) {
        val commit = commitLocal(context, mutate)
        rescheduleAfterChange(context, commit.before, commit.after)
        // Genau den geschriebenen Stand veröffentlichen, statt ihn im Push
        // erneut aus den Prefs zu lesen — dazwischen kann geschrieben werden.
        AlarmSync.pushAlarms(context, commit.json, commit.version)
    }

    private class LocalCommit(
        val before: List<Alarm>,
        val after: List<Alarm>,
        val json: String,
        val version: Long,
    )

    /**
     * Lesen, Ändern und Schreiben unter einem Lock.
     *
     * Vorher lagen die drei Schritte offen und nur das Schreiben war
     * synchronisiert — das schützte nichts: Der Listener-Thread konnte
     * zwischen dem Lesen und dem Schreiben einen empfangenen Stand anwenden,
     * den die lokale Änderung dann überschrieb. Die Änderung der Gegenseite
     * war weg, und weil die Version trotzdem hochgezählt wurde, verwarf die
     * Gegenseite sie anschließend auch selbst.
     */
    @Synchronized
    private fun commitLocal(context: Context, mutate: (List<Alarm>) -> List<Alarm>): LocalCommit {
        val before = getAlarms(context)
        val after = mutate(before)
        val json = Alarm.listToJson(after)
        val version = getVersion(context) + 1
        writeLocked(context, json, version)
        return LocalCommit(before, after, json, version)
    }

    /**
     * Vom anderen Gerät empfangener Stand.
     *
     * Übernommen bei höherer Version, und bei Gleichstand, wenn die Signatur
     * der Gegenseite gewinnt (siehe Klassen-Kommentar). Ist der empfangene
     * Stand älter oder unterliegt er, wird der eigene zurückgepusht — die
     * Gegenseite weiß sonst nicht, dass sie hinterherhinkt, und die Abweichung
     * heilt nie von selbst.
     *
     * @return true, wenn der lokale Stand ersetzt wurde.
     */
    fun applyRemote(context: Context, alarms: List<Alarm>, version: Long): Boolean {
        val merge = mergeRemote(context, alarms, version)
        if (merge.adopted) rescheduleAfterChange(context, merge.before, alarms)
        merge.push?.let { AlarmSync.pushAlarms(context, it.json, it.version) }
        return merge.adopted
    }

    private class RemoteMerge(
        val adopted: Boolean,
        val before: List<Alarm>,
        /** Stand, der anschließend veröffentlicht werden muss. */
        val push: Snapshot?,
    )

    @Synchronized
    private fun mergeRemote(context: Context, alarms: List<Alarm>, version: Long): RemoteMerge {
        val local = snapshot(context)
        val remoteSignature = Alarm.listSignature(alarms)
        val localSignature = Alarm.listSignature(local.alarms)

        // Inhaltlich identisch: nichts tun — und vor allem nicht pushen. Erst
        // dieser Abbruch macht die Pushes unten gefahrlos: Ein zurückgesandter
        // Stand trifft drüben auf denselben Inhalt und endet genau hier,
        // statt sich zwischen den Geräten endlos aufzuschaukeln.
        if (remoteSignature == localSignature) return RemoteMerge(false, local.alarms, null)

        if (version > local.version) {
            // Normalfall. Kein Push: Die Gegenseite hat diesen Stand ja selbst
            // geschickt, ein Echo wäre reiner Funkverkehr.
            writeLocked(context, Alarm.listToJson(alarms), version)
            return RemoteMerge(true, local.alarms, null)
        }
        if (version < local.version) {
            // Gegenseite hinkt hinterher und weiß es nicht.
            return RemoteMerge(false, local.alarms, local)
        }

        // Gleichstand bei unterschiedlichem Inhalt: Beide Seiten wenden
        // dieselbe Regel auf dieselben zwei Signaturen an, also gewinnt auf
        // beiden Geräten dieselbe. Genau eine übernimmt.
        //
        // Hier pushen *beide* Seiten den Gewinner — sonst blieben zwei
        // DataItems mit gleicher Version, aber verschiedenem Inhalt liegen,
        // und der Vollabgleich beim nächsten Start (der bei Gleichstand
        // willkürlich eins davon greift) würde den Konflikt neu aufrollen.
        if (remoteSignature > localSignature) {
            val json = Alarm.listToJson(alarms)
            writeLocked(context, json, version)
            return RemoteMerge(true, local.alarms, Snapshot(alarms, json, version))
        }
        return RemoteMerge(false, local.alarms, local)
    }

    /**
     * Neu planen und dabei die weggefallenen Alarme abmelden.
     *
     * [AlarmScheduler.rescheduleAll] läuft nur über den *aktuellen* Stand, und
     * das einzige cancel() steckt dort im !enabled-Zweig — den erreichen nur
     * noch vorhandene Alarme. Ein gelöschter Alarm behielt deshalb beide
     * PendingIntents im AlarmManager: Das System zeigte weiter „nächster
     * Alarm 07:00", und zur Weckzeit holte setExactAndAllowWhileIdle() die
     * Klingel-Activity nach vorne, die den Bildschirm einschaltet, bevor sie
     * merkt, dass es den Alarm gar nicht mehr gibt. Gilt genauso für einen
     * per Sync empfangenen Stand, in dem die Gegenseite gelöscht hat.
     */
    private fun rescheduleAfterChange(context: Context, before: List<Alarm>, after: List<Alarm>) {
        val remaining = after.mapTo(HashSet()) { it.id }
        before.forEach { alarm ->
            if (alarm.id !in remaining) {
                AlarmScheduler.cancel(context, alarm.id)
                // Sonst bleiben Snooze-Zähler und -Zeitpunkt des gelöschten
                // Alarms für immer in den Prefs liegen.
                RuntimeStore.clearSnoozeCount(context, alarm.id)
            }
        }
        AlarmScheduler.rescheduleAll(context)
    }
}
