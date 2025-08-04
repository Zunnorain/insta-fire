package com.example.instafire.workmanager

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.instafire.firebase.notifications.FCMSender

class ScheduledNotificationWorker(
    context:Context,
    workerParams:WorkerParameters
):CoroutineWorker(context,workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val sender = FCMSender(applicationContext)
            val success = sender.sendNotificationToTopic(
                title = "Appointment Alert",
                body = "You have an appointment with Dr.Arslan today at "
            )
            if(success){
                Result.success()
            }else{
                Result.failure()
            }
        }catch (e:Exception){
            Result.failure()
        }
    }

}