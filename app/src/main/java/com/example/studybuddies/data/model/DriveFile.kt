package com.example.studybuddies.data.model

data class DriveFile(
    val id: String = "",
    val name: String = "",
    val downloadUrl: String = "",
    val uploaderId: String = "",
    val uploaderName: String = "",
    val driveId: String = "", // The subject folder it belongs to
    val description: String = "", // User provided short description 
    val timestamp: Long = System.currentTimeMillis(),
    val likes: List<String> = emptyList(), // List of User UIDs who liked
    val reportedBy: List<String> = emptyList(), // List of User UIDs who reported this file
    val unlockedBy: List<String> = emptyList(), // List of User UIDs who paid to unlock this file
    val commentCount: Int = 0
)
