package com.example.studybuddies.data.model

data class User(
    val uid: String = "",
    val email: String = "",
    val displayName: String = "",
    val friends: List<String> = emptyList(), // List of UIDs of friends
    val friendRequests: List<String> = emptyList(), // UIDs that sent a request
    val reputationPoints: Int = 100,
    val hasUploaded: Boolean = false,
    val isBanned: Boolean = false,
    val deletedFilesCount: Int = 0,
    val profileImageUri: String = ""
)
