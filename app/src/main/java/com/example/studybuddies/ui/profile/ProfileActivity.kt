package com.example.studybuddies.ui.profile

import com.example.studybuddies.ui.drive.MyLibraryActivity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.studybuddies.R
import com.example.studybuddies.data.model.User
import com.example.studybuddies.ui.auth.FirebaseAuthenticationManager
import com.example.studybuddies.ui.auth.IAuthenticationManager

/**
 * Shows your profile details and lets you change your picture.
 * It uses IUserManager for profile data and IAuthenticationManager for logging out safely.
 */
class ProfileActivity : AppCompatActivity() {

    private lateinit var tvProfileName: TextView
    private lateinit var tvProfileEmail: TextView
    private lateinit var tvReputation: TextView
    private lateinit var btnMyLibrary: Button
    private lateinit var btnRules: Button
    private lateinit var btnLogout: Button
    private lateinit var switchDarkMode: com.google.android.material.switchmaterial.SwitchMaterial
    private lateinit var btnClearCache: Button
    private lateinit var ivProfilePicture: com.google.android.material.imageview.ShapeableImageView
    private lateinit var fabEditPicture: com.google.android.material.floatingactionbutton.FloatingActionButton

    private lateinit var authManager: IAuthenticationManager
    private lateinit var userManager: IUserManager

    private val pickImageLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.GetContent()) { uri: android.net.Uri? ->
        if (uri != null) {
            try {
                val inputStream = contentResolver.openInputStream(uri)
                val originalBitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
                val scaledBitmap = android.graphics.Bitmap.createScaledBitmap(originalBitmap, 250, 250, true)
                
                val outputStream = java.io.ByteArrayOutputStream()
                scaledBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 70, outputStream)
                val base64String = android.util.Base64.encodeToString(outputStream.toByteArray(), android.util.Base64.DEFAULT)
                
                userManager.updateProfileImage(base64String) { success ->
                    if (success) {
                        Toast.makeText(this, "Profile picture updated!", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        authManager = FirebaseAuthenticationManager()
        userManager = FirebaseUserManager()

        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        tvProfileName = findViewById(R.id.tvProfileName)
        tvProfileEmail = findViewById(R.id.tvProfileEmail)
        tvReputation = findViewById(R.id.tvReputation)
        btnMyLibrary = findViewById(R.id.btnMyLibrary)
        btnRules = findViewById(R.id.btnRules)
        btnLogout = findViewById(R.id.btnLogout)
        switchDarkMode = findViewById(R.id.switchDarkMode)
        btnClearCache = findViewById(R.id.btnClearCache)
        ivProfilePicture = findViewById(R.id.ivProfilePicture)
        fabEditPicture = findViewById(R.id.fabEditPicture)

        loadUserProfile()

        fabEditPicture.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        btnMyLibrary.setOnClickListener { view ->
            view.isEnabled = false
            view.postDelayed({ view.isEnabled = true }, 1000)
            startActivity(Intent(this, MyLibraryActivity::class.java))
        }

        btnRules.setOnClickListener { view ->
            view.isEnabled = false
            view.postDelayed({ view.isEnabled = true }, 1000)
            startActivity(Intent(this, RulesActivity::class.java))
        }

        val prefs = getSharedPreferences("StudyBuddiesPrefs", android.content.Context.MODE_PRIVATE)
        switchDarkMode.isChecked = prefs.getBoolean("dark_mode", false)

        switchDarkMode.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("dark_mode", isChecked).apply()
            val mode = if (isChecked) androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES else androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
            androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(mode)
        }

        btnClearCache.setOnClickListener {
            try {
                cacheDir.deleteRecursively()
                Toast.makeText(this, "Local cache cleared successfully", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Failed to clear cache", Toast.LENGTH_SHORT).show()
            }
        }

        btnLogout.setOnClickListener {
            userManager.cleanup()
            authManager.logout()
            finish() // MainActivity's onResume will catch this!
        }
    }

    private fun loadUserProfile() {
        userManager.listenToUserProfile { userData, error ->
            if (error != null || userData == null) {
                Toast.makeText(this, "Failed to load profile", Toast.LENGTH_SHORT).show()
                return@listenToUserProfile
            }

            tvProfileEmail.text = userData.email
            tvProfileName.text = userData.displayName
            tvReputation.text = userData.reputationPoints.toString()
            
            if (userData.profileImageUri.isNotEmpty()) {
                try {
                    val imageBytes = android.util.Base64.decode(userData.profileImageUri, android.util.Base64.DEFAULT)
                    val decodedImage = android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                    ivProfilePicture.setImageBitmap(decodedImage)
                } catch(e: Exception) {}
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        userManager.cleanup()
    }
}
