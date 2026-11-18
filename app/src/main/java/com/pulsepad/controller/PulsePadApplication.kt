package com.pulsepad.controller

import android.app.Application
import com.pulsepad.controller.util.AppLog
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class PulsePadApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        AppLog.i("Application", "PulsePad starting; accessibility-only injection engine")
    }
}
