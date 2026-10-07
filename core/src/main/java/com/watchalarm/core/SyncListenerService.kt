package com.watchalarm.core

import android.util.Log
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService

/**
 * Empfängt Änderungen der Gegenseite: Alarmlisten-Updates (DataItems)
 * und Dismiss-/Snooze-Kommandos (Messages).
 */
class SyncListenerService : WearableListenerService() {

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        for (event in dataEvents) {
            if (event.type != DataEvent.TYPE_CHANGED) continue
            if (event.dataItem.uri.path == SyncContract.PATH_SLEEP) {
                DataMapItem.fromDataItem(event.dataItem).dataMap
                    .getString(SyncContract.KEY_SLEEP_JSON)
                    ?.let { AlarmSync.applySleepNights(this, it) }
                continue
            }
            if (event.dataItem.uri.path != SyncContract.PATH_ALARMS) continue
            val map = DataMapItem.fromDataItem(event.dataItem).dataMap
            val json = map.getString(SyncContract.KEY_ALARMS_JSON) ?: continue
            val version = map.getLong(SyncContract.KEY_VERSION)
            // Beschädigtes Paket verwerfen statt es als „alle Alarme gelöscht"
            // zu übernehmen. Der eigene Stand bleibt damit stehen; die
            // Gegenseite heilt sich mit ihrer nächsten Änderung selbst.
            val alarms = Alarm.listFromJson(json)
            if (alarms == null) {
                Log.w(TAG, "Beschädigte Alarmliste (v$version) verworfen")
                continue
            }
            AlarmStore.applyRemote(this, alarms, version)
        }
    }

    override fun onMessageReceived(event: MessageEvent) {
        val alarmId = String(event.data, Charsets.UTF_8)
        when (event.path) {
            SyncContract.PATH_DISMISS -> AlarmService.dismiss(this, alarmId, fromRemote = true)
            SyncContract.PATH_SNOOZE -> AlarmService.snooze(this, alarmId, fromRemote = true)
        }
    }

    private companion object {
        const val TAG = "SyncListenerService"
    }
}
