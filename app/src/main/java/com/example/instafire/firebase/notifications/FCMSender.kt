package com.example.instafire.firebase.notifications

import android.content.Context
import com.google.auth.oauth2.GoogleCredentials
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class FCMSender(private val context: Context) {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun sendNotificationToTopic(
        topic: String = "all",
        title: String,
        body: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val accessToken = getAccessToken()
            val projectId = "insta-fire-23e1b"

            val fcmUrl = "https://fcm.googleapis.com/v1/projects/$projectId/messages:send"

            val jsonPayload = """
        {
            "message": {
                "topic": "$topic",
                "notification": {
                    "title": "$title",
                    "body": "$body"
                },
                "android": {
                    "priority": "high"
                }
            }
        }
        """.trimIndent()

            val request = Request.Builder()
                .url(fcmUrl)
                .addHeader("Authorization", "Bearer $accessToken")
                .post(jsonPayload.toRequestBody("application/json".toMediaType()))
                .build()


            val response = httpClient.newCall(request).execute()
            response.isSuccessful
        } catch (e: Exception) {
            false
        }
    }

    private suspend fun getAccessToken(): String = withContext(Dispatchers.IO) {
        // Open the service account file
        val inputStream = context.assets.open("service-account.json")

        // Create credentials
        val credentials = GoogleCredentials.fromStream(inputStream)
            .createScoped(listOf("https://www.googleapis.com/auth/cloud-platform"))

        // Refresh token if needed
        credentials.refreshIfExpired()

        // Return the token value
        credentials.accessToken.tokenValue
    }
}