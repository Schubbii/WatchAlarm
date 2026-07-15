package com.watchalarm.core

import android.content.Context
import android.media.RingtoneManager
import android.net.Uri
import android.provider.Settings
import android.util.Log

/**
 * Löst den gespeicherten Alarmton auf. Ton-URIs sind gerätespezifisch:
 * Zuerst wird die gespeicherte URI probiert, dann ein lokaler Ton mit
 * gleichem Titel gesucht (für den Fall, dass der Alarm auf dem anderen
 * Gerät angelegt wurde), sonst der Standard-Alarmton verwendet.
 */
object ToneResolver {

    private const val TAG = "ToneResolver"

    fun listAlarmTones(context: Context): List<Pair<String, Uri>> {
        val result = mutableListOf<Pair<String, Uri>>()
        try {
            val manager = RingtoneManager(context).apply { setType(RingtoneManager.TYPE_ALARM) }
            val cursor = manager.cursor
            while (cursor.moveToNext()) {
                val title = cursor.getString(RingtoneManager.TITLE_COLUMN_INDEX)
                val uri = manager.getRingtoneUri(cursor.position)
                if (title != null && uri != null) result.add(title to uri)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Alarmtöne konnten nicht geladen werden", e)
        }
        return result
    }

    fun resolve(context: Context, alarm: Alarm): Uri {
        if (alarm.toneUri.isNotBlank()) {
            try {
                val uri = Uri.parse(alarm.toneUri)
                if (RingtoneManager.getRingtone(context, uri) != null) return uri
            } catch (e: Exception) {
                Log.w(TAG, "Gespeicherte Ton-URI unbrauchbar", e)
            }
        }
        if (alarm.toneTitle.isNotBlank()) {
            listAlarmTones(context).firstOrNull { it.first == alarm.toneTitle }?.let { return it.second }
        }
        return RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ?: Settings.System.DEFAULT_ALARM_ALERT_URI
    }
}
