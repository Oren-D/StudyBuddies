package com.example.studybuddies.data.model

data class SubjectDrive(
    val id: String = "",
    val name: String = "",
    val creatorUid: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val fileCount: Int = 0
)
