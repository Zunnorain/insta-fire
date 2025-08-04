package com.example.instafire.utils

import android.content.Context
import android.view.View
import com.example.instafire.R
import com.example.instafire.models.User
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseUser
import java.util.concurrent.TimeUnit

fun View.showSnackMessage(
    message: String?,
    anchorView: View? = null,
    length: Int = Snackbar.LENGTH_SHORT
) {
    message?.let {
        try {
            val snack = Snackbar.make(this, it, length)
            snack.anchorView = anchorView
            snack.show()
        } catch (ex: Exception) {
            ex.message
        }
    }
}
fun FirebaseUser.toUser(age:Long): User {
    return User(
        name = displayName ?: "Anonymous",
        user_id = uid,
        age = age // Set dynamically or from user profile
    )
}

fun Long.toTimeAgo(context: Context): String {
    val diffMs = System.currentTimeMillis() - this

    return when {
        TimeUnit.MILLISECONDS.toDays(diffMs) > 0 ->
            context.getString(R.string.posted_days_ago, TimeUnit.MILLISECONDS.toDays(diffMs))
        TimeUnit.MILLISECONDS.toHours(diffMs) > 0 ->
            context.getString(R.string.posted_hours_ago, TimeUnit.MILLISECONDS.toHours(diffMs))
        TimeUnit.MILLISECONDS.toMinutes(diffMs) > 0 ->
            context.getString(R.string.posted_minutes_ago, TimeUnit.MILLISECONDS.toMinutes(diffMs))
        else -> context.getString(R.string.posted_just_now)
    }
}