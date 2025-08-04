package com.example.instafire.models

data class Post(
    val id:String = "",
    var current_time_ms: Long = 0,
    var description:String = "",
    var image_url:String = "",
    var user: User?=null
){
    // Required empty constructor for Firestore
    constructor() : this("", 0, "", "", null)
}
