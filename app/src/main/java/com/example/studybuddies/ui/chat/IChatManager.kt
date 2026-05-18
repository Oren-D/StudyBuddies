package com.example.studybuddies.ui.chat

import com.example.studybuddies.data.model.ChatMessage
import com.example.studybuddies.data.model.User

/**
 *  This is just a interface for Chat activities. In case Google will go Bankrupt and i will have to use SQL
 */
interface IChatManager {
    
    fun listenToMessages(chatId: String, onUpdate: (List<ChatMessage>?, Exception?) -> Unit)
    fun sendMessage(chatId: String, targetUid: String, text: String, onComplete: (Boolean, String?) -> Unit)
    fun fetchUsersByUids(uids: List<String>, onComplete: (List<User>) -> Unit)
    fun addFriendByEmail(email: String, onComplete: (Boolean, String) -> Unit)
    fun removeFriend(friendUid: String, onComplete: (Boolean) -> Unit)
    fun acceptFriend(friendUid: String, friendEmail: String, onComplete: (Boolean) -> Unit)
    fun declineFriend(friendUid: String, onComplete: (Boolean) -> Unit)
    
    fun cleanup()
}
