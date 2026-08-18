package com.umar.yaraan.chate

import android.app.Application
import com.google.firebase.FirebaseApp

class YaraanApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            FirebaseApp.initializeApp(this)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
