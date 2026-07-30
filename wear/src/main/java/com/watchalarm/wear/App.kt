package com.watchalarm.wear

import android.app.Application
import com.watchalarm.core.AlarmScheduler
import com.watchalarm.core.AlarmSync
import com.watchalarm.core.AppRegistry

class App : Application() {

    override fun onCreate() {
        super.onCreate()
        AppRegistry.ringActivityClass = WatchRingActivity::class.java
        AlarmScheduler.rescheduleAll(this)
        AlarmSync.syncNow(this)
    }
}
