package com.watchalarm.core

import android.content.Context
import java.time.ZonedDateTime

/**
 * Was ein Tipp auf einen Vorschlag auslöst — für Handy und Uhr gleich, daher
 * im core-Modul.
 */
object SleepPlannerActions {

    /**
     * Einen Wecker auf die vorgeschlagene Weckzeit stellen — über genau
     * denselben Weg wie der Editor ([AlarmStore.applyLocalChange]): speichern,
     * planen, mit dem anderen Gerät synchronisieren.
     *
     * Gibt es schon einen einmaligen Wecker auf diese Minute, wird er nur
     * eingeschaltet. Sonst entstünden bei zweimaligem Tippen zwei Wecker, die
     * gemeinsam klingeln.
     */
    fun setWakeAlarm(context: Context, wakeTime: ZonedDateTime) {
        val hour = wakeTime.hour
        val minute = wakeTime.minute
        val label = context.getString(R.string.core_planner_alarm_label)
        AlarmStore.applyLocalChange(context) { list ->
            val existing = list.firstOrNull { !it.repeating && it.hour == hour && it.minute == minute }
            if (existing != null) {
                list.map { if (it.id == existing.id) it.copy(enabled = true) else it }
            } else {
                list + Alarm(hour = hour, minute = minute, label = label)
            }
        }
    }
}
