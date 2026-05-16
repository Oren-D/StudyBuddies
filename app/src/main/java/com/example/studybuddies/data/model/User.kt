package com.example.studybuddies.data.model

data class User(
    val uid: String = "",
    val email: String = "",
    val displayName: String = "",
    val friends: List<String> = emptyList(),
    val friendRequests: List<String> = emptyList(),
    val reputationPoints: Int = 100,
    val hasUploaded: Boolean = false,
    val isBanned: Boolean = false,
    val deletedFilesCount: Int = 0,
    val profileImageUri: String = ""
)
