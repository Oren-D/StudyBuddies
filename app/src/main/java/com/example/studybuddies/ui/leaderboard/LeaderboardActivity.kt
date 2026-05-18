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
import com.example.studybuddies.ui.profile.IUserManager
import com.example.studybuddies.ui.profile.FirebaseUserManager

/**
 * Displays the top users with the most reputation points!
 * It uses IUserManager to fetch the top 10 users.
 */
class LeaderboardActivity : AppCompatActivity() {

    private lateinit var rvLeaderboard: RecyclerView
    private lateinit var tvEmptyState: TextView
    private lateinit var leaderboardAdapter: LeaderboardAdapter
    
    private lateinit var userManager: IUserManager
    private val usersList = mutableListOf<User>()

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

        userManager = FirebaseUserManager()
        fetchTopUsers()
    }

    private fun fetchTopUsers() {
        userManager.listenToLeaderboard { users, error ->
            if (error != null) {
                Toast.makeText(this, "Failed to load leaderboard.", Toast.LENGTH_SHORT).show()
                return@listenToLeaderboard
            }

            if (users != null) {
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
        userManager.cleanup()
    }
}
