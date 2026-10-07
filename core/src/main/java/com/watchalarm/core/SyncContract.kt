package com.watchalarm.core

/** Pfade, Keys und Intent-Konstanten für die Handy↔Uhr-Synchronisation. */
object SyncContract {
    /** DataItem-Pfad, unter dem jede Seite ihre Alarmliste veröffentlicht. */
    const val PATH_ALARMS = "/watchalarm/alarms"

    /** MessageClient-Pfade; Payload ist jeweils die Alarm-ID (UTF-8). */
    const val PATH_DISMISS = "/watchalarm/dismiss"
    const val PATH_SNOOZE = "/watchalarm/snooze"

    const val KEY_ALARMS_JSON = "alarms_json"

    /**
     * Schlafnächte für den Schlafplaner (nur Datum + Minuten, siehe
     * [SleepPlannerStore]). Schreibt nur das Handy, das Health Connect liest.
     */
    const val PATH_SLEEP = "/watchalarm/sleep"
    const val KEY_SLEEP_JSON = "sleep_json"

    /**
     * Lamport-Zähler statt Wanduhr-Zeitstempel: geräteunabhängig und
     * monoton, damit Änderungen von Uhr UND Handy zuverlässig ankommen —
     * auch wenn die Emulator-Uhren voneinander abweichen.
     */
    const val KEY_VERSION = "version"

    const val EXTRA_ALARM_ID = "com.watchalarm.extra.ALARM_ID"

    /** Lokaler Broadcast (nur eigenes Paket): das Klingeln wurde beendet. */
    const val ACTION_RING_STOPPED = "com.watchalarm.action.RING_STOPPED"
}
