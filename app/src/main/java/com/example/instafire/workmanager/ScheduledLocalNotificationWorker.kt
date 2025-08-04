package com.example.instafire.workmanager

import android.app.NotificationManager
import android.content.Context
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.extension_functions.showToastShort
import com.example.instafire.R
import com.example.instafire.firebase.notifications.FCMSender
import okhttp3.internal.notify

class ScheduledLocalNotificationWorker(
    val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
//            val sender = FCMSender(applicationContext)
//            val success = sender.sendNotificationToTopic(
//                title = "Appointment Alert",
//                body = "You have an appointment with Dr.Arslan today at "
//            )
            val notifManager =
                applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val notification =
                NotificationCompat.Builder(applicationContext, "channel_id")
                    .setSmallIcon(R.drawable.unsaved_icon)
                    .setContentTitle("Local Appointment")
                    .setContentText("Hello you have an appointment with Dr.Z today!")
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true)
                    .setSound(Settings.System.DEFAULT_NOTIFICATION_URI)
                    .build()

            notifManager.notify(1, notification)
            context.showToastShort("Appointment Successfully set!")
            Result.success()
        } catch (e: Exception) {
            Log.e("NotificationWorker", "Failed to show notification", e)
            Result.failure()
        }
    }

}