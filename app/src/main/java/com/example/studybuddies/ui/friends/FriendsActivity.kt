package com.example.studybuddies.ui.friends

import com.example.studybuddies.ui.chat.ChatActivity
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
    private lateinit var rvRequests: RecyclerView
    private lateinit var tvRequestsHeader: TextView
    
    private lateinit var friendsAdapter: FriendsAdapter
    private val friendsList = mutableListOf<User>()
    
    private lateinit var requestsAdapter: RequestsAdapter
    private val requestsList = mutableListOf<User>()

    private val db by lazy { FirebaseFirestore.getInstance() }
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val currentUserId by lazy { auth.currentUser?.uid ?: "" }
    private var profileListener: com.google.firebase.firestore.ListenerRegistration? = null

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
        rvRequests = findViewById(R.id.rvRequests)
        tvRequestsHeader = findViewById(R.id.tvRequestsHeader)

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

        requestsAdapter = RequestsAdapter(
            requestsList,
            onAcceptClick = { friend -> acceptFriend(friend) },
            onDeclineClick = { friend -> declineFriend(friend) }
        )
        rvRequests.layoutManager = LinearLayoutManager(this)
        rvRequests.adapter = requestsAdapter

        btnAddFriend.setOnClickListener { view ->
            view.isEnabled = false
            view.postDelayed({ view.isEnabled = true }, 1000)
            val emailToAdd = etSearchEmail.text.toString().trim().lowercase()
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

        profileListener = db.collection("users").document(currentUserId).addSnapshotListener { snapshot, error ->
            if (error != null) {
                Toast.makeText(this, "Error fetching profile", Toast.LENGTH_SHORT).show()
                return@addSnapshotListener
            }

            if (snapshot != null && snapshot.exists()) {
                val user = snapshot.toObject(User::class.java)
                user?.let {
                    fetchFriendsData(it.friends)
                    fetchRequestsData(it.friendRequests)
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
        val myEmail = auth.currentUser?.email ?: ""
        if (email.lowercase() == myEmail.lowercase()) {
            Toast.makeText(this, "You cannot add yourself", Toast.LENGTH_SHORT).show()
            return
        }

        db.collection("users").get()
            .addOnSuccessListener { result ->
                val matchedDoc = result.documents.find { 
                    it.getString("email")?.lowercase() == email.lowercase() 
                }
                
                if (matchedDoc == null) {
                    Toast.makeText(this, "User not found", Toast.LENGTH_SHORT).show()
                } else {
                    val friendUser = matchedDoc.toObject(User::class.java)
                    friendUser?.let { friend ->
                        if (friend.friends.contains(currentUserId)) {
                            Toast.makeText(this, "Already friends!", Toast.LENGTH_SHORT).show()
                            return@addOnSuccessListener
                        }
                        if (friend.friendRequests.contains(currentUserId)) {
                            Toast.makeText(this, "Request already sent!", Toast.LENGTH_SHORT).show()
                            return@addOnSuccessListener
                        }
                    
                        db.collection("users").document(friend.uid)
                            .set(hashMapOf("friendRequests" to FieldValue.arrayUnion(currentUserId)), com.google.firebase.firestore.SetOptions.merge())
                            
                        val notif = hashMapOf(
                            "title" to "New Friend Request!", 
                            "message" to "$myEmail wants to be your StudyBuddy!"
                        )
                        db.collection("users").document(friend.uid).collection("notifications").add(notif)
                            
                        Toast.makeText(this, "Friend request sent!", Toast.LENGTH_SHORT).show()
                        etSearchEmail.text?.clear()
                    }
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error finding user", Toast.LENGTH_SHORT).show()
            }
    }

    private fun removeFriend(friend: User) {
        db.collection("users").document(currentUserId)
            .set(hashMapOf("friends" to FieldValue.arrayRemove(friend.uid)), com.google.firebase.firestore.SetOptions.merge())
            
        db.collection("users").document(friend.uid)
            .set(hashMapOf("friends" to FieldValue.arrayRemove(currentUserId)), com.google.firebase.firestore.SetOptions.merge())
            
        Toast.makeText(this, "Friend removed", Toast.LENGTH_SHORT).show()
    }

    private fun acceptFriend(friend: User) {
        val myUpdates = hashMapOf<String, Any>(
            "friendRequests" to FieldValue.arrayRemove(friend.uid),
            "friends" to FieldValue.arrayUnion(friend.uid)
        )
        db.collection("users").document(currentUserId).set(myUpdates, com.google.firebase.firestore.SetOptions.merge())
            
        val theirUpdates = hashMapOf<String, Any>(
            "friends" to FieldValue.arrayUnion(currentUserId)
        )
        db.collection("users").document(friend.uid).set(theirUpdates, com.google.firebase.firestore.SetOptions.merge())
            
        val notif = hashMapOf(
            "title" to "Request Accepted!", 
            "message" to "You and ${auth.currentUser?.email} are now StudyBuddies!"
        )
        db.collection("users").document(friend.uid).collection("notifications").add(notif)
            
        Toast.makeText(this, "Request accepted", Toast.LENGTH_SHORT).show()
    }

    private fun declineFriend(friend: User) {
        db.collection("users").document(currentUserId)
            .set(hashMapOf("friendRequests" to FieldValue.arrayRemove(friend.uid)), com.google.firebase.firestore.SetOptions.merge())
        Toast.makeText(this, "Request ignored", Toast.LENGTH_SHORT).show()
    }
    
    private fun fetchRequestsData(requestUids: List<String>) {
        if (requestUids.isEmpty()) {
            requestsList.clear()
            requestsAdapter.notifyDataSetChanged()
            tvRequestsHeader.visibility = View.GONE
            rvRequests.visibility = View.GONE
            return
        }

        tvRequestsHeader.visibility = View.VISIBLE
        rvRequests.visibility = View.VISIBLE
        
        val chunks = requestUids.chunked(10)
        requestsList.clear()
        
        chunks.forEach { chunk ->
            db.collection("users").whereIn("uid", chunk).get()
                .addOnSuccessListener { result ->
                    for (document in result) {
                        val reqUser = document.toObject(User::class.java)
                        if (!requestsList.any { it.uid == reqUser.uid }) {
                            requestsList.add(reqUser)
                        }
                    }
                    requestsAdapter.notifyDataSetChanged()
                }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        profileListener?.remove()
    }
}
