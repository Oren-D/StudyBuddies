package com.example.studybuddies.ui.home

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.studybuddies.R
import com.example.studybuddies.data.model.User
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

class FriendsActivity : AppCompatActivity() {

    private lateinit var tvMyEmail: TextView
    private lateinit var etSearchEmail: TextInputEditText
    private lateinit var btnAddFriend: Button
    private lateinit var rvFriends: RecyclerView
    private lateinit var tvNoFriends: TextView
    
    private lateinit var friendsAdapter: FriendsAdapter
    private val friendsList = mutableListOf<User>()

    private val db by lazy { FirebaseFirestore.getInstance() }
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val currentUserId by lazy { auth.currentUser?.uid ?: "" }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_friends)

        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        tvMyEmail = findViewById(R.id.tvMyEmail)
        etSearchEmail = findViewById(R.id.etSearchEmail)
        btnAddFriend = findViewById(R.id.btnAddFriend)
        rvFriends = findViewById(R.id.rvFriends)
        tvNoFriends = findViewById(R.id.tvNoFriends)

        tvMyEmail.text = auth.currentUser?.email ?: "Unknown Email"

        friendsAdapter = FriendsAdapter(
            friendsList,
            onMessageClick = { friend ->
                val intent = Intent(this, ChatActivity::class.java).apply {
                    putExtra("TARGET_UID", friend.uid)
                    putExtra("TARGET_NAME", friend.displayName)
                }
                startActivity(intent)
            },
            onRemoveClick = { friend ->
                removeFriend(friend)
            }
        )
        rvFriends.layoutManager = LinearLayoutManager(this)
        rvFriends.adapter = friendsAdapter

        btnAddFriend.setOnClickListener {
            val emailToAdd = etSearchEmail.text.toString().trim()
            if (emailToAdd.isNotEmpty()) {
                addFriend(emailToAdd)
            } else {
                Toast.makeText(this, "Please enter an email", Toast.LENGTH_SHORT).show()
            }
        }

        listenToMyProfile()
    }

    private fun listenToMyProfile() {
        if (currentUserId.isEmpty()) return

        db.collection("users").document(currentUserId).addSnapshotListener { snapshot, error ->
            if (error != null) {
                Toast.makeText(this, "Error fetching profile", Toast.LENGTH_SHORT).show()
                return@addSnapshotListener
            }

            if (snapshot != null && snapshot.exists()) {
                val user = snapshot.toObject(User::class.java)
                user?.let {
                    fetchFriendsData(it.friends)
                }
            }
        }
    }

    private fun fetchFriendsData(friendUids: List<String>) {
        if (friendUids.isEmpty()) {
            friendsList.clear()
            friendsAdapter.notifyDataSetChanged()
            tvNoFriends.visibility = View.VISIBLE
            return
        }

        tvNoFriends.visibility = View.GONE
        
        // Fetch users where UID is in friendUids
        // Note: whereIn is limited to 10 items in Firestore. For a robust app, use pagination or subcollections.
        val chunks = friendUids.chunked(10)
        friendsList.clear()
        
        chunks.forEach { chunk ->
            db.collection("users").whereIn("uid", chunk).get()
                .addOnSuccessListener { result ->
                    for (document in result) {
                        val friendUser = document.toObject(User::class.java)
                        if (!friendsList.any { it.uid == friendUser.uid }) {
                            friendsList.add(friendUser)
                        }
                    }
                    friendsAdapter.notifyDataSetChanged()
                }
        }
    }

    private fun addFriend(email: String) {
        if (email == auth.currentUser?.email) {
            Toast.makeText(this, "You cannot add yourself", Toast.LENGTH_SHORT).show()
            return
        }

        db.collection("users").whereEqualTo("email", email).get()
            .addOnSuccessListener { result ->
                if (result.isEmpty) {
                    Toast.makeText(this, "User not found", Toast.LENGTH_SHORT).show()
                } else {
                    val friendUser = result.documents[0].toObject(User::class.java)
                    friendUser?.let { friend ->
                        // Add friend UID to my list
                        db.collection("users").document(currentUserId)
                            .update("friends", FieldValue.arrayUnion(friend.uid))
                            
                        // Add my UID to their list (mutual)
                        db.collection("users").document(friend.uid)
                            .update("friends", FieldValue.arrayUnion(currentUserId))
                            
                        Toast.makeText(this, "Friend added successfully!", Toast.LENGTH_SHORT).show()
                        etSearchEmail.text?.clear()
                    }
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error finding user", Toast.LENGTH_SHORT).show()
            }
    }

    private fun removeFriend(friend: User) {
        // Remove friend UID from my list
        db.collection("users").document(currentUserId)
            .update("friends", FieldValue.arrayRemove(friend.uid))
            
        // Remove my UID from their list
        db.collection("users").document(friend.uid)
            .update("friends", FieldValue.arrayRemove(currentUserId))
            
        Toast.makeText(this, "Friend removed", Toast.LENGTH_SHORT).show()
    }
}
