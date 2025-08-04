package com.example.instafire

import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.messaging

class MyApp: Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize default app (current project)
        FirebaseApp.initializeApp(this)

        // Initialize secondary app (old project's storage)
        if (FirebaseApp.getApps(this).none { it.name == "oldStorage" }) {
            val config = FirebaseOptions.Builder()
                .setProjectId("instaclone-a2039")
                .setApplicationId("1:1070644387305:android:960e0c4b548b15ce59bf78")
                .setApiKey("AIzaSyCGOWBGio0XEWJUpyuMVHJIrr0Q1GIPyt8")
                .setStorageBucket("instaclone-a2039.appspot.com")
                .build()
            FirebaseApp.initializeApp(this, config, "oldStorage")
        }

        //Firebase Notification
        subscribeToGeneralTopic()

        //Local Notfication
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
            val channel = NotificationChannel(
                "channel_id",
                "Zain's Channel",
                NotificationManager.IMPORTANCE_HIGH
            )
            val notifManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notifManager.createNotificationChannel(channel)

        }
    }

    //Firebase Notif
    // Subscribe once at app start
    private fun subscribeToGeneralTopic() {
        Firebase.messaging.subscribeToTopic("all")
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d("FCM", "Subscribed to 'all' topic")
                } else {
                    Log.e("FCM", "Topic subscription failed", task.exception)
                }
            }
    }
}