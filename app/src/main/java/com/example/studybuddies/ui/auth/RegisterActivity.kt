package com.example.studybuddies.ui.auth

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.studybuddies.R
import com.example.studybuddies.data.model.User
import com.example.studybuddies.ui.dashboard.MainActivity

/**
 *  This screen handles creating a new account.
 * It uses IAuthenticationManager to register the user in Firebase.
 */
class RegisterActivity : AppCompatActivity() {

    private lateinit var authManager: IAuthenticationManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        authManager = FirebaseAuthenticationManager()

        val etEmail = findViewById<EditText>(R.id.etEmail)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val btnRegister = findViewById<Button>(R.id.btnRegister)
        val tvLogin = findViewById<TextView>(R.id.tvLogin)

        btnRegister.setOnClickListener {
            val email = etEmail.text.toString().trim().lowercase()
            val password = etPassword.text.toString().trim()

            if (email.isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                etEmail.error = getString(R.string.error_invalid_email)
                return@setOnClickListener
            }

            if (password.length < 6) {
                etPassword.error = getString(R.string.error_short_password)
                return@setOnClickListener
            }

            authManager.register(email, password) { isSuccess, errorMsg ->
                if (isSuccess) {
                    Toast.makeText(this, "Registration Successful", Toast.LENGTH_SHORT).show()
                    val intent = Intent(this, MainActivity::class.java)
                    startActivity(intent)
                    finish()
                } else {
                    Toast.makeText(this@RegisterActivity, "Registration Failed: $errorMsg", Toast.LENGTH_SHORT).show()
                }
            }
        }

        tvLogin.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()//THE MOST ***** INTENT EVER
        }
    }
}
