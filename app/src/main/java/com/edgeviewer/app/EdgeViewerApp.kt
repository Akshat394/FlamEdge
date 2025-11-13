package com.edgeviewer.app

import android.app.Application
import timber.log.Timber

class EdgeViewerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }
}

