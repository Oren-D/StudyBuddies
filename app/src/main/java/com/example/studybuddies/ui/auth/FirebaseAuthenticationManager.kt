package com.example.studybuddies.ui.auth

import com.example.studybuddies.data.model.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class FirebaseAuthenticationManager : IAuthenticationManager {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()

    override fun login(email: String, password: String, onComplete: (Boolean, String?) -> Unit) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    onComplete(true, null)
                } else {
                    onComplete(false, task.exception?.message)
                }
            }
    }

    /**
     * register:
     * It first creates the user in Firebase Auth. If successful, it immediately creates a matching
     * user document in Firestore so the app has a place to store their points and name.
     */
    override fun register(email: String, password: String, onComplete: (Boolean, String?) -> Unit) {
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    if (user != null) {
                        val newUser = User(
                            uid = user.uid,
                            email = email,
                            displayName = email.substringBefore("@"),
                            reputationPoints = 100
                        )
                        db.collection("users").document(user.uid)
                            .set(newUser)
                            .addOnSuccessListener {
                                onComplete(true, null)
                            }
                            .addOnFailureListener { e ->
                                onComplete(false, e.message)
                            }
                    } else {
                        onComplete(false, "User created but is null.")
                    }
                } else {
                    onComplete(false, task.exception?.message)
                }
            }
    }

    override fun isLoggedIn(): Boolean {
        return auth.currentUser != null
    }

    override fun logout() {
        auth.signOut()
    }
}
