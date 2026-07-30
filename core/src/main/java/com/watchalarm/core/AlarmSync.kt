package com.watchalarm.core

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Synchronisation über die Wearable Data Layer API.
 *
 * - Die Alarmliste liegt als DataItem unter [SyncContract.PATH_ALARMS] mit
 *   Lamport-Version. Play Services persistiert und liefert sie auch nach
 *   Verbindungsabbrüchen nach.
 * - Ausschalten/Snooze werden zusätzlich als Messages geschickt, damit das
 *   Klingeln sofort stoppt.
 */
object AlarmSync {

    private const val TAG = "AlarmSync"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Eigenen Alarmbestand (Liste + Version) als DataItem veröffentlichen. */
    fun pushAlarms(context: Context) {
        val appContext = context.applicationContext
        scope.launch {
            try {
                val request = PutDataMapRequest.create(SyncContract.PATH_ALARMS).apply {
                    dataMap.putString(
                        SyncContract.KEY_ALARMS_JSON,
                        Alarm.listToJson(AlarmStore.getAlarms(appContext))
                    )
                    dataMap.putLong(SyncContract.KEY_VERSION, AlarmStore.getVersion(appContext))
                }.asPutDataRequest().setUrgent()
                Wearable.getDataClient(appContext).putDataItem(request).await()
            } catch (e: Exception) {
                Log.w(TAG, "pushAlarms fehlgeschlagen (Gegenseite offline?)", e)
            }
        }
    }

    /** Message (Dismiss/Snooze) an alle verbundenen Nodes senden. */
    fun sendMessageToAll(context: Context, path: String, alarmId: String) {
        val appContext = context.applicationContext
        scope.launch {
            try {
                val nodes = Wearable.getNodeClient(appContext).connectedNodes.await()
                for (node in nodes) {
                    Wearable.getMessageClient(appContext)
                        .sendMessage(node.id, path, alarmId.toByteArray(Charsets.UTF_8))
                        .await()
                }
            } catch (e: Exception) {
                Log.w(TAG, "sendMessageToAll($path) fehlgeschlagen", e)
            }
        }
    }

    /**
     * Vollabgleich beim App-/Gerätestart: höchste Version aus allen
     * vorhandenen DataItems übernehmen bzw. eigenen neueren Stand pushen.
     */
    fun syncNow(context: Context) {
        val appContext = context.applicationContext
        scope.launch {
            try {
                val buffer = Wearable.getDataClient(appContext).dataItems.await()
                var bestVersion = 0L
                var bestJson: String? = null
                try {
                    for (item in buffer) {
                        if (item.uri.path != SyncContract.PATH_ALARMS) continue
                        val map = DataMapItem.fromDataItem(item).dataMap
                        val v = map.getLong(SyncContract.KEY_VERSION)
                        if (v > bestVersion) {
                            bestVersion = v
                            bestJson = map.getString(SyncContract.KEY_ALARMS_JSON)
                        }
                    }
                } finally {
                    buffer.release()
                }
                val json = bestJson
                if (json != null && bestVersion > AlarmStore.getVersion(appContext)) {
                    AlarmStore.applyRemote(appContext, Alarm.listFromJson(json), bestVersion)
                } else if (AlarmStore.getVersion(appContext) > bestVersion) {
                    pushAlarms(appContext)
                }
            } catch (e: Exception) {
                Log.w(TAG, "syncNow fehlgeschlagen", e)
            }
        }
    }
}
