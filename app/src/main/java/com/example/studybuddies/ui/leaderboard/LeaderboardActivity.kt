package com.example.studybuddies.ui.leaderboard

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.studybuddies.R
import com.example.studybuddies.data.model.User
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class LeaderboardActivity : AppCompatActivity() {

    private lateinit var rvLeaderboard: RecyclerView
    private lateinit var tvEmptyState: TextView
    private lateinit var leaderboardAdapter: LeaderboardAdapter
    
    private val db by lazy { FirebaseFirestore.getInstance() }
    private val usersList = mutableListOf<User>()
    private var leaderboardListener: com.google.firebase.firestore.ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_leaderboard)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Top Contributors"
        toolbar.setNavigationOnClickListener { finish() }

        rvLeaderboard = findViewById(R.id.rvLeaderboard)
        tvEmptyState = findViewById(R.id.tvEmptyState)

        leaderboardAdapter = LeaderboardAdapter(usersList)
        rvLeaderboard.layoutManager = LinearLayoutManager(this)
        rvLeaderboard.adapter = leaderboardAdapter

        fetchTopUsers()
    }

    private fun fetchTopUsers() {
        leaderboardListener = db.collection("users")
            .orderBy("reputationPoints", Query.Direction.DESCENDING)
            .limit(10)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Toast.makeText(this, "Failed to load leaderboard.", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val users = snapshot.toObjects(User::class.java).filter { it.reputationPoints > 0 }
                    usersList.clear()
                    usersList.addAll(users)
                    leaderboardAdapter.notifyDataSetChanged()

                    if (usersList.isEmpty()) {
                        tvEmptyState.visibility = View.VISIBLE
                        rvLeaderboard.visibility = View.GONE
                    } else {
                        tvEmptyState.visibility = View.GONE
                        rvLeaderboard.visibility = View.VISIBLE
                    }
                }
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        leaderboardListener?.remove()
    }
}
