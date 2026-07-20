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
 * [watchMode] steuert, WIE die Uhr klingelt (Ton+Vibration, nur Vibration,
 * nur Ton), [phoneMode] das Handy (Ton+Vibration, nur Vibration, aus).
 * Steht [phoneMode] auf [MODE_OFF] und [phoneOnlyDismiss] ist gesetzt,
 * zeigt das Handy eine lautlose Stopp-Ansicht.
 */
data class Alarm(
    val id: String = UUID.randomUUID().toString(),
    val hour: Int = 7,
    val minute: Int = 0,
    val label: String = "",
    val enabled: Boolean = true,
    val repeatDays: Set<Int> = emptySet(),
    val toneTitle: String = "",
    val toneUri: String = "",
    val snoozeMinutes: Int = 5,
    val maxSnoozes: Int = 3,
    val phoneOnlyDismiss: Boolean = false,
    val watchMode: String = MODE_SOUND_VIBRATE,
    val phoneMode: String = MODE_SOUND_VIBRATE,
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
        put("toneTitle", toneTitle)
        put("toneUri", toneUri)
        put("snoozeMinutes", snoozeMinutes)
        put("maxSnoozes", maxSnoozes)
        put("phoneOnlyDismiss", phoneOnlyDismiss)
        put("watchMode", watchMode)
        put("phoneMode", phoneMode)
    }

    companion object {

        /** Ton und Vibration. */
        const val MODE_SOUND_VIBRATE = "sound_vibrate"

        /** Nur Vibration, kein Ton. */
        const val MODE_VIBRATE = "vibrate"

        /** Nur Ton, keine Vibration (nur für die Uhr angeboten). */
        const val MODE_SOUND = "sound"

        /** Gerät klingelt gar nicht (nur für das Handy angeboten). */
        const val MODE_OFF = "off"

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
                toneTitle = o.optString("toneTitle", ""),
                toneUri = o.optString("toneUri", ""),
                snoozeMinutes = o.optInt("snoozeMinutes", 5),
                maxSnoozes = o.optInt("maxSnoozes", 3),
                phoneOnlyDismiss = o.optBoolean("phoneOnlyDismiss", false),
                watchMode = o.optString("watchMode", MODE_SOUND_VIBRATE),
                phoneMode = o.optString("phoneMode", MODE_SOUND_VIBRATE),
            )
        }

        fun listToJson(alarms: List<Alarm>): String =
            JSONArray().apply { alarms.forEach { put(it.toJson()) } }.toString()

        fun listFromJson(json: String): List<Alarm> = try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { fromJson(arr.getJSONObject(it)) }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
