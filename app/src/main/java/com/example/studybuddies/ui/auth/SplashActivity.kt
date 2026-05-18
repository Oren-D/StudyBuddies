package com.example.studybuddies.ui.auth

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.example.studybuddies.R

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)


        Handler(Looper.getMainLooper()).postDelayed({
            // Launch LoginActivity
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
            

            finish()
        }, 2000)
    }
}
