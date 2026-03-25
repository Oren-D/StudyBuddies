package com.example.studybuddies.data.model

data class DriveFile(
    val id: String = "",
    val name: String = "",
    val downloadUrl: String = "",
    val uploaderId: String = "",
    val uploaderName: String = "",
    val driveId: String = "", // The subject folder it belongs to
    val timestamp: Long = System.currentTimeMillis(),
    val likes: List<String> = emptyList(), // List of User UIDs who liked
    val reports: Int = 0 // Number of times this file was reported
)
