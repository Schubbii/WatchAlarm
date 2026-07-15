package com.watchalarm.core

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
            if (event.dataItem.uri.path != SyncContract.PATH_ALARMS) continue
            val map = DataMapItem.fromDataItem(event.dataItem).dataMap
            val json = map.getString(SyncContract.KEY_ALARMS_JSON) ?: continue
            val ts = map.getLong(SyncContract.KEY_TIMESTAMP)
            AlarmStore.applyRemote(this, Alarm.listFromJson(json), ts)
        }
    }

    override fun onMessageReceived(event: MessageEvent) {
        val alarmId = String(event.data, Charsets.UTF_8)
        when (event.path) {
            SyncContract.PATH_DISMISS -> AlarmService.dismiss(this, alarmId, fromRemote = true)
            SyncContract.PATH_SNOOZE -> AlarmService.snooze(this, alarmId, fromRemote = true)
        }
    }
}
