package com.watchalarm.mobile

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.watchalarm.core.AlarmSync
import com.watchalarm.core.SleepNight
import com.watchalarm.core.SleepPlannerStore
import com.watchalarm.core.SleepSession
import com.watchalarm.core.SleepStats
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Schlafdaten aus Health Connect — nur auf dem Handy: Wear OS hat kein
 * Health Connect. Pixel Watch (Fitbit) und Galaxy Watch (Samsung Health)
 * schreiben ihren Schlaf aber dorthin.
 *
 * Gelesen werden ausschließlich Schlafsitzungen der letzten 7 Nächte. Daraus
 * bleibt pro Nacht nur „Datum + Minuten" übrig ([SleepStats.nights]); genau
 * das wird gespeichert und an die Uhr geschickt — keine Uhrzeiten, keine
 * Schlafphasen.
 */
object SleepHealth {

    private const val TAG = "SleepHealth"

    val PERMISSIONS: Set<String> = setOf(HealthPermission.getReadPermission(SleepSessionRecord::class))

    /** Wachphasen innerhalb einer Sitzung, die nicht als Schlaf zählen. */
    private val AWAKE_STAGES = setOf(
        SleepSessionRecord.STAGE_TYPE_AWAKE,
        SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED,
        SleepSessionRecord.STAGE_TYPE_OUT_OF_BED,
    )

    enum class Status {
        /** Gerät unterstützt Health Connect nicht. */
        UNAVAILABLE,

        /** Unterstützt, aber Health Connect muss erst installiert/aktualisiert werden. */
        NEEDS_INSTALL,

        /** Verfügbar, Berechtigung fehlt (nie gefragt oder abgelehnt). */
        NOT_GRANTED,
        GRANTED,
    }

    suspend fun status(context: Context): Status =
        when (HealthConnectClient.getSdkStatus(context)) {
            HealthConnectClient.SDK_AVAILABLE -> {
                val granted = runCatching {
                    HealthConnectClient.getOrCreate(context).permissionController.getGrantedPermissions()
                }.getOrDefault(emptySet())
                if (granted.containsAll(PERMISSIONS)) Status.GRANTED else Status.NOT_GRANTED
            }
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> Status.NEEDS_INSTALL
            else -> Status.UNAVAILABLE
        }

    /**
     * Nächte neu lesen, speichern und an die Uhr schicken.
     *
     * Ohne Berechtigung werden gespeicherte Nächte gelöscht — auch auf der
     * Uhr: Wer den Zugriff widerruft, soll danach auch keine alten Werte
     * mehr sehen. Schlägt nur das Lesen fehl, bleibt der letzte Stand.
     */
    suspend fun refresh(context: Context): Status {
        val status = status(context)
        val json = if (status == Status.GRANTED) {
            try {
                SleepPlannerStore.nightsToJson(readNights(context))
            } catch (e: Exception) {
                Log.w(TAG, "Schlafdaten nicht lesbar", e)
                return status
            }
        } else {
            SleepPlannerStore.nightsToJson(emptyList())
        }
        val current = SleepPlannerStore.nightsToJson(SleepPlannerStore.getNights(context))
        if (json != current) {
            SleepPlannerStore.setNights(context, json)
            AlarmSync.pushSleepNights(context, json)
        }
        return status
    }

    private suspend fun readNights(context: Context): List<SleepNight> {
        val client = HealthConnectClient.getOrCreate(context)
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val filter = TimeRangeFilter.between(SleepStats.readWindowStart(today, zone), Instant.now())
        val sessions = mutableListOf<SleepSession>()
        var pageToken: String? = null
        do {
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = SleepSessionRecord::class,
                    timeRangeFilter = filter,
                    pageToken = pageToken,
                )
            )
            response.records.mapTo(sessions) { record ->
                val awake = record.stages
                    .filter { it.stage in AWAKE_STAGES }
                    .sumOf { Duration.between(it.startTime, it.endTime).toMinutes() }
                SleepSession(record.startTime, record.endTime, awake.toInt())
            }
            pageToken = response.pageToken
        } while (pageToken != null)
        return SleepStats.nights(sessions, zone)
    }
}
