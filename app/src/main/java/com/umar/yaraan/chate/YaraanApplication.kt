package com.umar.yaraan.chate

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions

class YaraanApplication : Application() {

    companion object {
        private const val TAG = "YaraanApplication"
    }

    override fun onCreate() {
        super.onCreate()
        try {
            if (FirebaseApp.getApps(this).isEmpty()) {
                val options = FirebaseOptions.Builder()
                    .setApplicationId("1:740464208491:android:256ca7d21147f04ca09f2e")
                    .setApiKey("AIzaSyA_06bFtGC54BqB1OoGBU4nAyPABIWsGow")
                    .setDatabaseUrl("https://yaraan-voice-chat-f7b85-default-rtdb.firebaseio.com")
                    .setProjectId("yaraan-voice-chat-f7b85")
                    .setStorageBucket("yaraan-voice-chat-f7b85.firebasestorage.app")
                    .build()
                FirebaseApp.initializeApp(this, options)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing FirebaseApp", e)
            try {
                FirebaseApp.initializeApp(this)
            } catch (ex: Exception) {
                Log.e(TAG, "Fallback FirebaseApp.initializeApp failed", ex)
            }
        }
    }
}
