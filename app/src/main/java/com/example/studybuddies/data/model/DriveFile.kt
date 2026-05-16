package com.example.studybuddies.data.model

data class DriveFile(
    val id: String = "",
    val name: String = "",
    val downloadUrl: String = "",
    val uploaderId: String = "",
    val uploaderName: String = "",
    val driveId: String = "",
    val description: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val likes: List<String> = emptyList(),
    val reportedBy: List<String> = emptyList(),
    val unlockedBy: List<String> = emptyList(),
    val commentCount: Int = 0
)
