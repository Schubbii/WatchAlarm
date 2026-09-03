package com.watchalarm.core

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.UUID

/**
 * Ein einzelner Wecker. Wird als JSON serialisiert und über die
 * Wearable Data Layer zwischen Handy und Uhr synchronisiert.
 *
 * [repeatDays] enthält [java.util.Calendar]-Wochentagskonstanten
 * (SUNDAY=1 .. SATURDAY=7). Leer = einmaliger Alarm.
 *
 * Klingelverhalten ist bewusst fest verdrahtet: Die Uhr vibriert, das Handy
 * zeigt (lautlos) den Stopp-Screen. Ausgeschaltet wird am Handy; die Uhr
 * bietet einen Notfall-Stopp nur, wenn das Handy nicht verbunden ist.
 */
data class Alarm(
    val id: String = UUID.randomUUID().toString(),
    val hour: Int = 7,
    val minute: Int = 0,
    val label: String = "",
    val enabled: Boolean = true,
    val repeatDays: Set<Int> = emptySet(),
    val snoozeMinutes: Int = 5,
    val maxSnoozes: Int = 3,
    /**
     * Klingeldauer ohne Reaktion, danach wird automatisch geschlummert.
     * Sicherheitsnetz, damit die Uhr nicht endlos weitervibriert, wenn sie
     * gar nicht am Handgelenk ist.
     */
    val ringTimeoutMinutes: Int = DEFAULT_RING_TIMEOUT_MINUTES,
) {

    val repeating: Boolean get() = repeatDays.isNotEmpty()

    /** Nächster Auslösezeitpunkt in Millis nach [now]. */
    fun nextTriggerMillis(now: Long = System.currentTimeMillis()): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (repeatDays.isEmpty()) {
            if (cal.timeInMillis <= now) cal.add(Calendar.DAY_OF_YEAR, 1)
            return cal.timeInMillis
        }
        repeat(8) {
            if (cal.timeInMillis > now && repeatDays.contains(cal.get(Calendar.DAY_OF_WEEK))) {
                return cal.timeInMillis
            }
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return cal.timeInMillis
    }

    fun formattedTime(context: Context): String {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
        }
        return android.text.format.DateFormat.getTimeFormat(context).format(cal.time)
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("hour", hour)
        put("minute", minute)
        put("label", label)
        put("enabled", enabled)
        put("repeatDays", JSONArray(repeatDays.toList()))
        put("snoozeMinutes", snoozeMinutes)
        put("maxSnoozes", maxSnoozes)
        put("ringTimeoutMinutes", ringTimeoutMinutes)
    }

    companion object {

        /**
         * 30 Minuten. Vorher waren es 5 — das war als Akkuschutz gedacht,
         * hat den Wecker aber mitten in der Nacht leise gemacht, während
         * man noch schlief.
         */
        const val DEFAULT_RING_TIMEOUT_MINUTES = 30

        /** Auswahl im Editor: 5er-Schritte. */
        val RING_TIMEOUT_CHOICES = listOf(5, 10, 15, 20, 25, 30)

        fun fromJson(o: JSONObject): Alarm {
            val days = mutableSetOf<Int>()
            o.optJSONArray("repeatDays")?.let { arr ->
                for (i in 0 until arr.length()) days.add(arr.getInt(i))
            }
            return Alarm(
                id = o.optString("id", UUID.randomUUID().toString()),
                hour = o.optInt("hour", 7),
                minute = o.optInt("minute", 0),
                label = o.optString("label", ""),
                enabled = o.optBoolean("enabled", true),
                repeatDays = days,
                snoozeMinutes = o.optInt("snoozeMinutes", 5),
                maxSnoozes = o.optInt("maxSnoozes", 3),
                // Ältere Stände kennen das Feld nicht -> neuer Standardwert.
                ringTimeoutMinutes = o.optInt(
                    "ringTimeoutMinutes",
                    DEFAULT_RING_TIMEOUT_MINUTES
                ),
            )
        }

        fun listToJson(alarms: List<Alarm>): String =
            JSONArray().apply { alarms.forEach { put(it.toJson()) } }.toString()

        /**
         * ASCII Unit Separator (0x1F) als Feldtrenner in [listSignature].
         * Bewusst als [Char]-Code statt als Escape-Sequenz im String-Literal,
         * damit kein Steuerzeichen im Quelltext steht.
         */
        private val FIELD_SEPARATOR = Char(31).toString()

        /**
         * Reihenfolge-unabhängige Signatur einer Liste: inhaltlich gleiche
         * Bestände ergeben dieselbe Zeichenkette.
         *
         * Der JSON-Text taugt dafür nicht — die UI hängt einen bearbeiteten
         * Alarm hinten an (`filter { … } + alarm`), und [repeatDays] ist ein
         * Set, dessen Iterationsreihenfolge von der Einfügereihenfolge
         * abhängt. Zwei Geräte könnten also denselben Bestand haben und
         * trotzdem unterschiedliches JSON erzeugen. Deshalb hier alles
         * sortiert und mit einem Trennzeichen, das in Labels nicht vorkommt.
         *
         * **Jedes synchronisierte Feld muss hier auftauchen.** Fehlt eines,
         * halten zwei inhaltlich verschiedene Stände einander für gleich, und
         * [AlarmStore.applyRemote] verwirft die Änderung schon im
         * Gleichheits-Abbruch — noch bevor die Version betrachtet wird. Genau
         * das war [ringTimeoutMinutes] passiert: eingeführt und in [toJson]
         * aufgenommen, hier aber vergessen, sodass eine geänderte Klingeldauer
         * das andere Gerät nie erreichte.
         */
        fun listSignature(alarms: List<Alarm>): String =
            alarms.sortedBy { it.id }.joinToString("\n") { a ->
                listOf(
                    a.id, a.hour, a.minute, a.label, a.enabled,
                    a.repeatDays.sorted().joinToString(","),
                    a.snoozeMinutes, a.maxSnoozes, a.ringTimeoutMinutes,
                ).joinToString(FIELD_SEPARATOR)
            }

        fun listFromJson(json: String): List<Alarm> = try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { fromJson(arr.getJSONObject(it)) }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
