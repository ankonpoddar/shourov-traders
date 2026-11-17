package com.example.data.firebase

import android.content.Context
import android.util.Log
import com.example.R
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings

object FirestoreProvider {
    private const val TAG = "FirestoreProvider"

    @Volatile
    private var isConfigured = false

    fun getInstance(context: Context): FirebaseFirestore {
        val appContext = context.applicationContext
        val databaseId = appContext.getString(R.string.firestore_database_id)
        val db = FirebaseFirestore.getInstance(databaseId)
        if (isConfigured) return db
        return synchronized(this) {
            if (!isConfigured) {
                try {
                    val settings = FirebaseFirestoreSettings.Builder()
                        .setLocalCacheSettings(
                            PersistentCacheSettings.newBuilder()
                                .setSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
                                .build()
                        )
                        .build()
                    db.firestoreSettings = settings
                    Log.d(TAG, "Configured Firestore persistent offline cache (unlimited) for $databaseId")
                } catch (e: IllegalStateException) {
                    Log.w(TAG, "Firestore settings already initialized: ${e.message}")
                }
                isConfigured = true
            }
            db
        }
    }
}
