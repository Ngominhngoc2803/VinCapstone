package com.heartmonitor.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp


@HiltAndroidApp
class HeartMonitorApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize other libraries here if needed
    }
}