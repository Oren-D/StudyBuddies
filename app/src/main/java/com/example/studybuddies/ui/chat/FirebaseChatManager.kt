package com.example.studybuddies.ui.chat

import com.example.studybuddies.data.model.ChatMessage
import com.example.studybuddies.data.model.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions

/**
 * This class handles the actual logic for Chat and Friends.
 * It talks directly to Firebase Firestore to read/write messages and friend requests.
 */
class FirebaseChatManager : IChatManager {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val activeListeners = mutableListOf<ListenerRegistration>()

    private val currentUserId: String
        get() = auth.currentUser?.uid ?: ""

    override fun listenToMessages(chatId: String, onUpdate: (List<ChatMessage>?, Exception?) -> Unit) {
        val listener = db.collection("chats").document(chatId).collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onUpdate(null, error)
                    return@addSnapshotListener
                }//snapshot of real time data from FireBase

                if (snapshot != null) {
                    val msgs = snapshot.toObjects(ChatMessage::class.java)
                    onUpdate(msgs, null)
                }
            }
        activeListeners.add(listener)
    }

    override fun sendMessage(chatId: String, targetUid: String, text: String, onComplete: (Boolean, String?) -> Unit) {
        val user = auth.currentUser
        if (user == null) {
            onComplete(false, "User not logged in")
            return
        }

        val message = ChatMessage(
            id = "",
            text = text,
            senderId = user.uid,
            senderEmail = user.email ?: "Unknown",
            timestamp = System.currentTimeMillis()
        )

        val messageRef = db.collection("chats").document(chatId).collection("messages").document()
        val finalMessage = message.copy(id = messageRef.id)

        messageRef.set(finalMessage)
            .addOnSuccessListener {
                if (targetUid != "GLOBAL_CHAT") {
                    val notif = hashMapOf(
                        "title" to "New message from ${user.email ?: "Unknown"}", 
                        "message" to text
                    )
                    db.collection("users").document(targetUid).collection("notifications").add(notif)
                }
                onComplete(true, null)
            }
            .addOnFailureListener { e ->
                onComplete(false, e.message)
            }
    }

    override fun fetchUsersByUids(uids: List<String>, onComplete: (List<User>) -> Unit) {
        if (uids.isEmpty()) {
            onComplete(emptyList())
            return
        }
        
        val chunks = uids.chunked(10)
        val resultList = mutableListOf<User>()
        var completedChunks = 0
        
        chunks.forEach { chunk ->
            db.collection("users").whereIn("uid", chunk).get()
                .addOnSuccessListener { result ->
                    for (document in result) {
                        val friendUser = document.toObject(User::class.java)
                        if (!resultList.any { it.uid == friendUser.uid }) {
                            resultList.add(friendUser)
                        }
                    }
                    completedChunks++
                    if (completedChunks == chunks.size) {
                        onComplete(resultList)
                    }
                }
                .addOnFailureListener {
                    completedChunks++
                    if (completedChunks == chunks.size) {
                        onComplete(resultList)
                    }
                }
        }
    }

    override fun addFriendByEmail(email: String, onComplete: (Boolean, String) -> Unit) {
        val myEmail = auth.currentUser?.email ?: ""
        if (email.lowercase() == myEmail.lowercase()) {
            onComplete(false, "You cannot add yourself")
            return
        }

        db.collection("users").get()
            .addOnSuccessListener { result ->
                val matchedDoc = result.documents.find { 
                    it.getString("email")?.lowercase() == email.lowercase() 
                }
                
                if (matchedDoc == null) {
                    onComplete(false, "User not found")
                } else {
                    val friend = matchedDoc.toObject(User::class.java)
                    if (friend != null) {
                        if (friend.friends.contains(currentUserId)) {
                            onComplete(false, "Already friends!")
                            return@addOnSuccessListener
                        }
                        if (friend.friendRequests.contains(currentUserId)) {
                            onComplete(false, "Request already sent!")
                            return@addOnSuccessListener
                        }
                    
                        db.collection("users").document(friend.uid)
                            .set(hashMapOf("friendRequests" to FieldValue.arrayUnion(currentUserId)), SetOptions.merge())
                            
                        val notif = hashMapOf(
                            "title" to "New Friend Request!", 
                            "message" to "$myEmail wants to be your StudyBuddy!"
                        )
                        db.collection("users").document(friend.uid).collection("notifications").add(notif)
                            
                        onComplete(true, "Friend request sent!")
                    }
                }
            }
            .addOnFailureListener {
                onComplete(false, "Error finding user")
            }
    }

    override fun removeFriend(friendUid: String, onComplete: (Boolean) -> Unit) {
        db.collection("users").document(currentUserId)
            .set(hashMapOf("friends" to FieldValue.arrayRemove(friendUid)), SetOptions.merge())
            
        db.collection("users").document(friendUid)
            .set(hashMapOf("friends" to FieldValue.arrayRemove(currentUserId)), SetOptions.merge())
            .addOnSuccessListener { onComplete(true) }
            .addOnFailureListener { onComplete(false) }
    }

    override fun acceptFriend(friendUid: String, friendEmail: String, onComplete: (Boolean) -> Unit) {
        val myUpdates = hashMapOf<String, Any>(
            "friendRequests" to FieldValue.arrayRemove(friendUid),
            "friends" to FieldValue.arrayUnion(friendUid)
        )
        db.collection("users").document(currentUserId).set(myUpdates, SetOptions.merge())
            
        val theirUpdates = hashMapOf<String, Any>(
            "friends" to FieldValue.arrayUnion(currentUserId)
        )
        db.collection("users").document(friendUid).set(theirUpdates, SetOptions.merge())
            
        val notif = hashMapOf(
            "title" to "Request Accepted!", 
            "message" to "You and ${auth.currentUser?.email} are now StudyBuddies!"
        )
        db.collection("users").document(friendUid).collection("notifications").add(notif)
            
        onComplete(true)
    }

    override fun declineFriend(friendUid: String, onComplete: (Boolean) -> Unit) {
        db.collection("users").document(currentUserId)
            .set(hashMapOf("friendRequests" to FieldValue.arrayRemove(friendUid)), SetOptions.merge())
            .addOnSuccessListener { onComplete(true) }
            .addOnFailureListener { onComplete(false) }
    }

    override fun cleanup() {
        activeListeners.forEach { it.remove() }
        activeListeners.clear()
    }
}
