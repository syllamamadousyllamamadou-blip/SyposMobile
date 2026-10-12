package com.sypos.mobile

import android.app.Application
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class SyposApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            val settings = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(
                    PersistentCacheSettings.newBuilder()
                        .setSizeBytes(100 * 1024 * 1024) // 100 MB cache hors-ligne
                        .build()
                )
                .build()
            FirebaseFirestore.getInstance().firestoreSettings = settings
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
