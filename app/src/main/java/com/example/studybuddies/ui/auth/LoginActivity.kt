package com.example.studybuddies.ui.auth

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.studybuddies.R
import com.example.studybuddies.ui.home.MainActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class LoginActivity : AppCompatActivity() {

    private lateinit var etEmail: TextInputEditText
    private lateinit var etPassword: TextInputEditText
    private lateinit var btnLogin: MaterialButton
    private lateinit var tvRegister: TextView
    private lateinit var btnAdminLogin: MaterialButton
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        auth = FirebaseAuth.getInstance()

        initViews()
        setupListeners()
    }

    private fun initViews() {
        etEmail = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)
        btnLogin = findViewById(R.id.btnLogin)
        tvRegister = findViewById(R.id.tvRegister)
        btnAdminLogin = findViewById(R.id.btnAdminLogin)
    }

    private fun setupListeners() {
        btnLogin.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (email.isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                etEmail.error = getString(R.string.error_invalid_email)
            } else if (password.isEmpty()) {
                etPassword.error = getString(R.string.error_required_field)
            } else {
                auth.signInWithEmailAndPassword(email, password)
                    .addOnCompleteListener(this) { task ->
                        if (task.isSuccessful) {
                            val intent = Intent(this@LoginActivity, MainActivity::class.java)
                            startActivity(intent)
                            finish()
                        } else {
                            Toast.makeText(this@LoginActivity, "Login Failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
            }
        }

        tvRegister.setOnClickListener {
            val intent = Intent(this, RegisterActivity::class.java)
            startActivity(intent)
        }

        btnAdminLogin.setOnClickListener {
            val adminEmail = "admin@studybuddies.com"
            val adminPass = "admin123"
            auth.signInWithEmailAndPassword(adminEmail, adminPass)
                .addOnSuccessListener {
                    Toast.makeText(this, "Admin Logged In", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                }
                .addOnFailureListener {
                    auth.createUserWithEmailAndPassword(adminEmail, adminPass)
                        .addOnSuccessListener { result ->
                            val user = result.user
                            if (user != null) {
                                val newUser = com.example.studybuddies.data.model.User(
                                    uid = user.uid,
                                    email = adminEmail,
                                    displayName = "Administrator",
                                    reputationPoints = 9999,
                                    hasUploaded = true
                                )
                                FirebaseFirestore.getInstance().collection("users").document(user.uid).set(newUser)
                                Toast.makeText(this, "Admin Created & Logged In", Toast.LENGTH_SHORT).show()
                                startActivity(Intent(this, MainActivity::class.java))
                                finish()
                            }
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(this, "Admin Login Failed: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                }
        }
    }
}