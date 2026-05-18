package com.example.studybuddies.ui.drive

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.studybuddies.BuildConfig
import com.example.studybuddies.R
import com.example.studybuddies.data.model.DriveFile
import com.example.studybuddies.data.model.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * This screen shows all the study files you have unlocked.
 * It uses IDriveManager to get the data.
 */

class MyLibraryActivity : AppCompatActivity() {

    private lateinit var rvLibraryFiles: RecyclerView
    private lateinit var tvEmptyState: TextView
    private lateinit var driveAdapter: DriveAdapter
    private val filesList = mutableListOf<DriveFile>()
    
    private lateinit var driveManager: IDriveManager
    private lateinit var geminiManager: GeminiManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_my_library)

        val apiKey = BuildConfig.GEMINI_API_KEY
        geminiManager = GeminiManager(if (apiKey == "null") "" else apiKey)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "My Unlocked Vault"
        toolbar.setNavigationOnClickListener { finish() }

        rvLibraryFiles = findViewById(R.id.rvLibraryFiles)
        tvEmptyState = findViewById(R.id.tvEmptyState)

        driveManager = FirebaseDriveManager()

        val uId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: return
        
        driveAdapter = DriveAdapter(filesList, uId, 
            onDownloadClick = { file -> openFileIntent(file) },
            onLikeClick = { file -> toggleLike(file, uId) },
            onCommentClick = { file -> showCommentsDialog(file) },
            onReportClick = { _ -> Toast.makeText(this, "Cannot report from vault.", Toast.LENGTH_SHORT).show() },
            onDeleteClick = { _ -> },
            onAnalyzeClick = { file -> analyzeFile(file) }
        )
        rvLibraryFiles.layoutManager = LinearLayoutManager(this)
        rvLibraryFiles.adapter = driveAdapter

        loadLibrary(uId)
    }

    private fun loadLibrary(uId: String) {
        driveManager.listenToMyLibraryFiles { files, error ->
            if (error != null) {
                Toast.makeText(this, "Failed to load library", Toast.LENGTH_SHORT).show()
                return@listenToMyLibraryFiles
            }
            
            if (files != null) {
                filesList.clear()
                filesList.addAll(files)
                driveAdapter.notifyDataSetChanged()
                
                if (filesList.isEmpty()) {
                    tvEmptyState.visibility = View.VISIBLE
                    rvLibraryFiles.visibility = View.GONE
                } else {
                    tvEmptyState.visibility = View.GONE
                    rvLibraryFiles.visibility = View.VISIBLE
                }
            }
        }
    }
    
    private fun openFileIntent(file: DriveFile) {
        try {
            val intent = Intent(Intent.ACTION_VIEW)
            val uri = android.net.Uri.parse(file.downloadUrl)
            intent.setDataAndType(uri, if (file.name.endsWith(".jpg")) "image/*" else "application/pdf")
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Unable to open file: ${file.name}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun toggleLike(file: DriveFile, userId: String) {
        driveManager.toggleLike(file) { success ->
        }
    }

    private fun showCommentsDialog(file: DriveFile) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_comments, null)
        val rvComments = dialogView.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rvComments)
        val tvNoComments = dialogView.findViewById<android.widget.TextView>(R.id.tvNoComments)
        val etCommentInput = dialogView.findViewById<android.widget.EditText>(R.id.etCommentInput)
        val btnSendComment = dialogView.findViewById<android.widget.ImageButton>(R.id.btnSendComment)
        
        val commentsList = mutableListOf<com.example.studybuddies.data.model.Comment>()
        val commentsAdapter = com.example.studybuddies.ui.drive.CommentAdapter(commentsList)
        rvComments.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        rvComments.adapter = commentsAdapter
        
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .show()
            
        driveManager.listenToComments(file.id) { comments, error ->
            if (comments != null) {
                commentsList.clear()
                commentsList.addAll(comments)
                commentsAdapter.notifyDataSetChanged()
                
                if (commentsList.isEmpty()) {
                    tvNoComments.visibility = android.view.View.VISIBLE
                    rvComments.visibility = android.view.View.GONE
                } else {
                    tvNoComments.visibility = android.view.View.GONE
                    rvComments.visibility = android.view.View.VISIBLE
                    rvComments.scrollToPosition(commentsList.size - 1)
                }
            }
        }
            
        btnSendComment.setOnClickListener { view ->
            view.isEnabled = false
            view.postDelayed({ view.isEnabled = true }, 500)
            
            val content = etCommentInput.text.toString().trim()
            if (content.isNotEmpty()) {
                driveManager.postComment(file.id, content) { success ->
                    if (success) {
                        etCommentInput.text.clear()
                    }
                }
            }
        }
    }
    
    private fun analyzeFile(file: DriveFile) {//Analyze file using Gemini AI NEEDS REAL API KEY!!!
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "null") {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("API Key Missing")
                .setMessage("Please ensure your API Key is correctly injected into BuildConfig.")
                .setPositiveButton("OK", null)
                .show()
            return
        }

        val dialogView = layoutInflater.inflate(R.layout.dialog_ai_summary, null)
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .show()

        lifecycleScope.launch {
            try {
                val summaryText = geminiManager.analyzeFile(file, true, contentResolver)
                
                withContext(Dispatchers.Main) {
                    dialog.dismiss()
                    androidx.appcompat.app.AlertDialog.Builder(this@MyLibraryActivity)
                        .setTitle("✨ AI Smart Summary")
                        .setMessage(summaryText)
                        .setPositiveButton("Awesome!") { _, _ -> }
                        .show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    dialog.dismiss()
                    androidx.appcompat.app.AlertDialog.Builder(this@MyLibraryActivity)
                        .setTitle("AI Error")
                        .setMessage("Failed to connect to Gemini AI:\n\n${e.localizedMessage ?: e.toString()}")
                        .setPositiveButton("OK", null)
                        .show()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        driveManager.cleanup()
    }
}
