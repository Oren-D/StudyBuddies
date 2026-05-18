package com.example.studybuddies.ui.dashboard

import com.example.studybuddies.ui.auth.LoginActivity
import com.example.studybuddies.ui.chat.ChatActivity
import com.example.studybuddies.ui.drive.DriveActivity
import com.example.studybuddies.ui.friends.FriendsActivity
import com.example.studybuddies.ui.leaderboard.LeaderboardActivity
import com.example.studybuddies.ui.profile.ProfileActivity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import com.example.studybuddies.R
import com.example.studybuddies.ui.auth.FirebaseAuthenticationManager
import com.example.studybuddies.ui.auth.IAuthenticationManager
import com.example.studybuddies.ui.profile.FirebaseUserManager
import com.example.studybuddies.ui.profile.IUserManager

/**
 * The main dashboard screen of the app.
 * It listens for notifications and point updates using IUserManager.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var userManager: IUserManager
    private lateinit var authManager: IAuthenticationManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val prefs = getSharedPreferences("StudyBuddiesPrefs", Context.MODE_PRIVATE)
        val isDarkMode = prefs.getBoolean("dark_mode", false)
        val mode = if (isDarkMode) androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES else androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(mode)
        
        setContentView(R.layout.activity_main)

        authManager = FirebaseAuthenticationManager()
        userManager = FirebaseUserManager()

        findViewById<View>(R.id.cardDrive).setOnClickListener { view ->
            view.isEnabled = false
            view.postDelayed({ view.isEnabled = true }, 1000)
            startActivity(Intent(this, DriveActivity::class.java))
        }

        findViewById<View>(R.id.cardChat).setOnClickListener { view ->
            view.isEnabled = false
            view.postDelayed({ view.isEnabled = true }, 1000)
            startActivity(Intent(this, FriendsActivity::class.java))
        }
        
        findViewById<View>(R.id.cardGlobalChat).setOnClickListener { view ->
            view.isEnabled = false
            view.postDelayed({ view.isEnabled = true }, 1000)
            val intent = Intent(this, ChatActivity::class.java).apply {
                putExtra("TARGET_UID", "GLOBAL_CHAT")
                putExtra("TARGET_NAME", "Global Study Room")
            }
            startActivity(intent)
        }

        findViewById<View>(R.id.cardProfile).setOnClickListener { view ->
            view.isEnabled = false
            view.postDelayed({ view.isEnabled = true }, 1000)
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        findViewById<View>(R.id.cardLeaderboard).setOnClickListener { view ->
            view.isEnabled = false
            view.postDelayed({ view.isEnabled = true }, 1000)
            startActivity(Intent(this, LeaderboardActivity::class.java))
        }

        listenForPoints()
        listenForQueue()
    }
    
    override fun onResume() {
        super.onResume()
        if (!authManager.isLoggedIn()) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }
    
    private fun listenForQueue() {
        userManager.listenForNotifications { title, message ->
            showSystemNotification(title, message)
        }
    }
    

    private fun showSystemNotification(title: String, message: String) {//Android alerts
        val channelId = "system_events"
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "StudyBuddies Alerts", NotificationManager.IMPORTANCE_HIGH)
            notificationManager.createNotificationChannel(channel)
        }

        val targetClass = if (title.contains("Friend", ignoreCase = true) || title.contains("Request", ignoreCase = true)) {
            FriendsActivity::class.java
        } else {
            MainActivity::class.java
        }

        val intent = Intent(this, targetClass)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }

    private fun listenForPoints() {
        var lastPoints = -1
        
        userManager.listenToUserProfile { user, error ->
            if (user != null) {
                val currentPoints = user.reputationPoints
                if (lastPoints != -1 && currentPoints > lastPoints) {
                    val pointsEarned = currentPoints - lastPoints
                    showNotification(pointsEarned)
                }
                lastPoints = currentPoints
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        userManager.cleanup()
    }

    private fun showNotification(pointsEarned: Int) {
        val channelId = "points_channel"
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Reputation Rewards", NotificationManager.IMPORTANCE_HIGH)
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.btn_star_big_on)
            .setContentTitle("Reputation Increased! ⭐")
            .setContentText("Awesome! You just earned +$pointsEarned Reputation Points from community engagement.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }
}
