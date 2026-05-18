package com.example.studybuddies.ui.profile

import com.example.studybuddies.data.model.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query

/**
 * This class handles fetching and updating User data in Firestore.
 * It handles the profile pictures, leaderboard, and reputation points.
 */
class FirebaseUserManager : IUserManager {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    
    private val activeListeners = mutableListOf<ListenerRegistration>()

    override fun listenToUserProfile(onUpdate: (User?, Exception?) -> Unit) {
        val user = auth.currentUser
        if (user == null) {
            onUpdate(null, Exception("User not logged in"))
            return
        }

        val listener = db.collection("users").document(user.uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onUpdate(null, error)
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    val userData = snapshot.toObject(User::class.java)
                    onUpdate(userData, null)
                } else {
                    onUpdate(null, Exception("User document does not exist"))
                }
            }
        activeListeners.add(listener)
    }

    override fun updateProfileImage(base64String: String, onComplete: (Boolean) -> Unit) {
        val user = auth.currentUser
        if (user == null) {
            onComplete(false)
            return
        }

        db.collection("users").document(user.uid).update("profileImageUri", base64String)
            .addOnSuccessListener { onComplete(true) }
            .addOnFailureListener { onComplete(false) }
    }

    override fun listenForNotifications(onNotification: (title: String, message: String) -> Unit) {
        val user = auth.currentUser ?: return
        val listener = db.collection("users").document(user.uid)
            .collection("notifications")
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    for (dc in snapshot.documentChanges) {
                        if (dc.type == DocumentChange.Type.ADDED) {
                            val data = dc.document.data
                            val title = data["title"] as? String ?: "StudyBuddies Alert"
                            val msg = data["message"] as? String ?: ""
                            
                            onNotification(title, msg)
                            
                            dc.document.reference.delete()
                        }
                    }
                }
            }
        activeListeners.add(listener)
    }

    override fun listenToLeaderboard(onUpdate: (List<User>?, Exception?) -> Unit) {
        val listener = db.collection("users")
            .orderBy("reputationPoints", Query.Direction.DESCENDING)
            .limit(10)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onUpdate(null, error)
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val users = snapshot.toObjects(User::class.java).filter { it.reputationPoints > 0 }
                    onUpdate(users, null)
                }
            }
        activeListeners.add(listener)
    }

    override fun cleanup() {
        for (listener in activeListeners) {
            listener.remove()
        }
        activeListeners.clear()
    }
}
