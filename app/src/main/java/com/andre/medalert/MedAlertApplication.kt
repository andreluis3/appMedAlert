package com.andre.medalert

import android.app.Application
import com.andre.medalert.data.remote.SessionManager

/** Registrada no AndroidManifest (android:name=".MedAlertApplication"). */
class MedAlertApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        SessionManager.init(this)
    }
}
