package com.example.instafire.firebase.notifications

import com.google.firebase.Firebase
import com.google.firebase.functions.functions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class FCMSender {

    // Reference to the Firebase Functions instance.
    private val functions = Firebase.functions

    /**
     * Sends a push notification to a topic by calling a secure Cloud Function.
     * The notification logic is executed on the server, not in the app.
     *
     * @param topic The topic to send the notification to (e.g., "all").
     * @param title The title of the notification.
     * @param body The body of the notification.
     * @return True if the function call was successful, false otherwise.
     * */

    suspend fun sendNotificationToTopic(
        topic: String = "all",
        title: String,
        body: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            // Create the data payload to send to the Cloud Function.
            val data = hashMapOf(
                "topic" to topic,
                "payload" to hashMapOf(
                    "title" to title,
                    "body" to body
                )
            )

            // Call the Cloud Function by its name. This call is secure.
            val result = functions
                .getHttpsCallable("sendNotificationToTopic") // This is the name of your Cloud Function
                .call(data)
                .await() // Await the result of the function call

            // The function should return a success status.
//            val resultData = result.data as? Map<*, *>
            val resultData = result.data as? Map<String, Any>
            val isSuccessful = resultData?.get("success") as? Boolean ?: false

            if (isSuccessful) {
                println("Notification sent via Cloud Function successfully.")
            } else {
                println("Cloud Function reported an error.")
            }
            isSuccessful
        } catch (e: Exception) {
            println("Failed to call Cloud Function: ${e.message}")
            false
        }
    }



    //OLD CODE, WORKS PERFECTLY FINE BUT IS UNSAFE TO PUBLISH APP AND UNSAFE TO PUT ON GITHUB
    /*private val httpClient = OkHttpClient.Builder()
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
    }*/
}