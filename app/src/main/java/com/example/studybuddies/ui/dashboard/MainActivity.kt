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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : AppCompatActivity() {

    private var pointsListenerRegistration: com.google.firebase.firestore.ListenerRegistration? = null
    private var queueListenerRegistration: com.google.firebase.firestore.ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val prefs = getSharedPreferences("StudyBuddiesPrefs", Context.MODE_PRIVATE)
        val isDarkMode = prefs.getBoolean("dark_mode", false)
        val mode = if (isDarkMode) androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES else androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(mode)
        
        setContentView(R.layout.activity_main)

        if (FirebaseAuth.getInstance().currentUser == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

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
    
    private fun listenForQueue() {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        queueListenerRegistration = FirebaseFirestore.getInstance().collection("users").document(user.uid)
            .collection("notifications")
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    for (dc in snapshot.documentChanges) {
                        if (dc.type == com.google.firebase.firestore.DocumentChange.Type.ADDED) {
                            val data = dc.document.data
                            val title = data["title"] as? String ?: "StudyBuddies Alert"
                            val msg = data["message"] as? String ?: ""
                            
                            showSystemNotification(title, msg)
                            
                            dc.document.reference.delete()
                        }
                    }
                }
            }
    }
    
    private fun showSystemNotification(title: String, message: String) {
        val channelId = "system_events"
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "StudyBuddies Alerts", NotificationManager.IMPORTANCE_HIGH)
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

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
        val user = FirebaseAuth.getInstance().currentUser ?: return
        var lastPoints = -1
        
        pointsListenerRegistration = FirebaseFirestore.getInstance().collection("users").document(user.uid)
            .addSnapshotListener { document, _ ->
                if (document != null && document.exists()) {
                    val currentPoints = document.getLong("reputationPoints")?.toInt() ?: 100
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
        pointsListenerRegistration?.remove()
        queueListenerRegistration?.remove()
    }

    private fun showNotification(pointsEarned: Int) {
        val channelId = "points_channel"
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Reputation Rewards", NotificationManager.IMPORTANCE_HIGH)
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

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
