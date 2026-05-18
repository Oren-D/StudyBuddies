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
import com.example.studybuddies.ui.chat.FirebaseChatManager
import com.example.studybuddies.ui.chat.IChatManager
import com.example.studybuddies.ui.profile.FirebaseUserManager
import com.example.studybuddies.ui.profile.IUserManager
import com.google.android.material.textfield.TextInputEditText

/**
 * This screen shows your friends and friend requests.
 * It uses IChatManager to fetch the lists.
 */
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

    private lateinit var chatManager: IChatManager
    private lateinit var userManager: IUserManager

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

        chatManager = FirebaseChatManager()
        userManager = FirebaseUserManager()

        tvMyEmail.text = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.email ?: "Unknown Email"

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
        userManager.listenToUserProfile { user, error ->
            if (error != null) {
                Toast.makeText(this, "Error fetching profile", Toast.LENGTH_SHORT).show()
                return@listenToUserProfile
            }

            if (user != null) {
                fetchFriendsData(user.friends)
                fetchRequestsData(user.friendRequests)
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
        
        chatManager.fetchUsersByUids(friendUids) { users ->
            friendsList.clear()
            friendsList.addAll(users)
            friendsAdapter.notifyDataSetChanged()
        }
    }

    private fun addFriend(email: String) {
        chatManager.addFriendByEmail(email) { success, msg ->
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            if (success) {
                etSearchEmail.text?.clear()
            }
        }
    }

    private fun removeFriend(friend: User) {
        chatManager.removeFriend(friend.uid) { success ->
            if (success) {
                Toast.makeText(this, "Friend removed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun acceptFriend(friend: User) {
        chatManager.acceptFriend(friend.uid, friend.email) { success ->
            if (success) {
                Toast.makeText(this, "Request accepted", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun declineFriend(friend: User) {
        chatManager.declineFriend(friend.uid) { success ->
            if (success) {
                Toast.makeText(this, "Request ignored", Toast.LENGTH_SHORT).show()
            }
        }
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
        
        chatManager.fetchUsersByUids(requestUids) { users ->
            requestsList.clear()
            requestsList.addAll(users)
            requestsAdapter.notifyDataSetChanged()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        userManager.cleanup()
        chatManager.cleanup()
    }
}
