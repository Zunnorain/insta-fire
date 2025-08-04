package com.example.instafire.models

data class Comment(
    val id: String = "",
    val postId: String = "",
    var content: String = "",
    var current_time_ms: Long = 0,
    val user: User? = null
){
    // Required empty constructor for Firestore
    constructor() : this("", "", "", 0, null)
}
