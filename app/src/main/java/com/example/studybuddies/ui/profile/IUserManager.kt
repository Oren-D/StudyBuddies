package com.example.studybuddies.ui.profile

import com.example.studybuddies.data.model.User

/**
 * An interface for User actions.
 * In case Google will go Bankrupt.
 */
interface IUserManager {
    
    fun listenToUserProfile(onUpdate: (User?, Exception?) -> Unit)
    
    fun updateProfileImage(base64String: String, onComplete: (Boolean) -> Unit)
    
    fun listenForNotifications(onNotification: (title: String, message: String) -> Unit)
    
    fun listenToLeaderboard(onUpdate: (List<User>?, Exception?) -> Unit)
    
    fun cleanup()
}
