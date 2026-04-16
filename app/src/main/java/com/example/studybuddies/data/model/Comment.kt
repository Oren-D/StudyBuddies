package com.example.studybuddies.data.model

data class Comment(
    val id: String = "",
    val fileId: String = "", // The DriveFile this belongs to
    val authorUid: String = "",
    val authorName: String = "",
    val content: String = "",
    val timestamp: Long = System.currentTimeMillis()
)
