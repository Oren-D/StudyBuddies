package com.example.studybuddies.ui.home

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.studybuddies.R
import com.example.studybuddies.data.model.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ProfileActivity : AppCompatActivity() {

    private lateinit var tvProfileName: TextView
    private lateinit var tvProfileEmail: TextView
    private lateinit var tvReputation: TextView
    private lateinit var btnRules: Button
    private lateinit var btnLogout: Button
    private lateinit var ivProfilePicture: com.google.android.material.imageview.ShapeableImageView
    private lateinit var fabEditPicture: com.google.android.material.floatingactionbutton.FloatingActionButton

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance() }

    private val pickImageLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.GetContent()) { uri: android.net.Uri? ->
        if (uri != null) {
            try {
                val inputStream = contentResolver.openInputStream(uri)
                val originalBitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
                // Downscale for Firestore Base64 limits
                val scaledBitmap = android.graphics.Bitmap.createScaledBitmap(originalBitmap, 250, 250, true)
                
                val outputStream = java.io.ByteArrayOutputStream()
                scaledBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 70, outputStream)
                val base64String = android.util.Base64.encodeToString(outputStream.toByteArray(), android.util.Base64.DEFAULT)
                
                val user = auth.currentUser ?: return@registerForActivityResult
                db.collection("users").document(user.uid).update("profileImageUri", base64String)
                    .addOnSuccessListener {
                        Toast.makeText(this, "Profile picture updated!", Toast.LENGTH_SHORT).show()
                    }
            } catch (e: Exception) {
                Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        tvProfileName = findViewById(R.id.tvProfileName)
        tvProfileEmail = findViewById(R.id.tvProfileEmail)
        tvReputation = findViewById(R.id.tvReputation)
        btnRules = findViewById(R.id.btnRules)
        btnLogout = findViewById(R.id.btnLogout)
        ivProfilePicture = findViewById(R.id.ivProfilePicture)
        fabEditPicture = findViewById(R.id.fabEditPicture)

        loadUserProfile()

        fabEditPicture.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        btnRules.setOnClickListener {
            startActivity(Intent(this, RulesActivity::class.java))
        }

        btnLogout.setOnClickListener {
            auth.signOut()
            startActivity(Intent(this, com.example.studybuddies.ui.auth.LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
            finish()
        }
    }

    private fun loadUserProfile() {
        val user = auth.currentUser
        if (user == null) {
            finish()
            return
        }

        tvProfileEmail.text = user.email

        db.collection("users").document(user.uid).addSnapshotListener { snapshot, error ->
            if (error != null) {
                Toast.makeText(this, "Failed to load profile", Toast.LENGTH_SHORT).show()
                return@addSnapshotListener
            }

            if (snapshot != null && snapshot.exists()) {
                val userData = snapshot.toObject(User::class.java)
                userData?.let {
                    tvProfileName.text = it.displayName
                    tvReputation.text = it.reputationPoints.toString()
                    
                    if (it.profileImageUri.isNotEmpty()) {
                        try {
                            val imageBytes = android.util.Base64.decode(it.profileImageUri, android.util.Base64.DEFAULT)
                            val decodedImage = android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                            ivProfilePicture.setImageBitmap(decodedImage)
                        } catch(e: Exception) {}
                    }
                }
            }
        }
    }
}
