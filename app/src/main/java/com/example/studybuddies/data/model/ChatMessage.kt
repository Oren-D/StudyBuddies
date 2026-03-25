package com.example.studybuddies.data.model

data class ChatMessage(
    val id: String = "",
    val text: String = "",
    val senderId: String = "",
    val senderEmail: String = "",
    val timestamp: Long = 0L
)
