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
import com.example.studybuddies.R
import com.example.studybuddies.data.model.DriveFile
import com.example.studybuddies.data.model.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MyLibraryActivity : AppCompatActivity() {

    private lateinit var rvLibraryFiles: RecyclerView
    private lateinit var tvEmptyState: TextView
    private lateinit var driveAdapter: DriveAdapter
    private val filesList = mutableListOf<DriveFile>()
    
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance() }
    private var libraryListener: com.google.firebase.firestore.ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_my_library)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "My Unlocked Vault"
        toolbar.setNavigationOnClickListener { finish() }

        rvLibraryFiles = findViewById(R.id.rvLibraryFiles)
        tvEmptyState = findViewById(R.id.tvEmptyState)

        val uId = auth.currentUser?.uid ?: return
        
        driveAdapter = DriveAdapter(filesList, uId, 
            onDownloadClick = { file -> openFileIntent(file) },
            onLikeClick = { file -> toggleLike(file, uId) },
            onCommentClick = { file -> showCommentsDialog(file) },
            onReportClick = { _ -> Toast.makeText(this, "Cannot report from vault.", Toast.LENGTH_SHORT).show() },
            onDeleteClick = { _ -> },
            onAnalyzeClick = { file -> analyzeFileMock(file) }
        )
        rvLibraryFiles.layoutManager = LinearLayoutManager(this)
        rvLibraryFiles.adapter = driveAdapter

        loadLibrary(uId)
    }

    private fun loadLibrary(uId: String) {
        libraryListener = db.collection("files").whereArrayContains("unlockedBy", uId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Toast.makeText(this, "Failed to load library", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }
                
                if (snapshot != null) {
                    filesList.clear()
                    filesList.addAll(snapshot.toObjects(DriveFile::class.java))
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
        if (file.uploaderId == userId) {
            Toast.makeText(this, "You cannot like your own file!", Toast.LENGTH_SHORT).show()
            return
        }

        val docRef = db.collection("files").document(file.id)
        if (file.likes.contains(userId)) {
            docRef.update("likes", com.google.firebase.firestore.FieldValue.arrayRemove(userId))
        } else {
            docRef.update("likes", com.google.firebase.firestore.FieldValue.arrayUnion(userId))
            db.collection("users").document(file.uploaderId)
                .set(hashMapOf("reputationPoints" to com.google.firebase.firestore.FieldValue.increment(1)), com.google.firebase.firestore.SetOptions.merge())
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
            
        val commentsRef = db.collection("files").document(file.id).collection("comments")
        val listener = commentsRef.orderBy("timestamp", com.google.firebase.firestore.Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (snapshot != null) {
                    val fetched = snapshot.toObjects(com.example.studybuddies.data.model.Comment::class.java)
                    commentsList.clear()
                    commentsList.addAll(fetched)
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
            
        dialog.setOnDismissListener {
            listener.remove()
        }
        
        btnSendComment.setOnClickListener { view ->
            view.isEnabled = false
            view.postDelayed({ view.isEnabled = true }, 500)
            
            val content = etCommentInput.text.toString().trim()
            if (content.isNotEmpty()) {
                val newRef = commentsRef.document()
                db.collection("users").document(auth.currentUser?.uid ?: "").get()
                    .addOnSuccessListener { doc ->
                        val authorNameStr = doc.getString("displayName") ?: auth.currentUser?.email ?: "Unknown"
                        val comment = com.example.studybuddies.data.model.Comment(
                            id = newRef.id,
                            fileId = file.id,
                            authorUid = auth.currentUser?.uid ?: "",
                            authorName = authorNameStr,
                            content = content
                        )
                        newRef.set(comment).addOnSuccessListener {
                            etCommentInput.text.clear()
                            db.collection("files").document(file.id)
                                .set(hashMapOf("commentCount" to com.google.firebase.firestore.FieldValue.increment(1)), com.google.firebase.firestore.SetOptions.merge())
                        }
                    }
            }
        }
    }
    
    private fun analyzeFileMock(file: DriveFile) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_ai_summary, null)
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .show()

        lifecycleScope.launch {
            try {
                val apiKey = com.example.studybuddies.BuildConfig.GEMINI_API_KEY
                if (apiKey.isEmpty() || apiKey == "null") {
                    withContext(Dispatchers.Main) {
                        dialog.dismiss()
                        androidx.appcompat.app.AlertDialog.Builder(this@MyLibraryActivity)
                            .setTitle("API Key Missing")
                            .setMessage("Please ensure your API Key is correctly injected into BuildConfig.")
                            .setPositiveButton("OK", null)
                            .show()
                    }
                    return@launch
                }
                
                val generativeModel = com.google.ai.client.generativeai.GenerativeModel(
                    modelName = "gemini-flash-latest",
                    apiKey = apiKey
                )
                
                val safeDesc = if (file.description.isBlank()) "No description provided." else file.description
                val promptText = "Accurately describe exactly what you see in the provided file in no more than 3 sentences. If it is an academic document or notes, summarize the core concepts and answers. If it is just an everyday object (like a soda can) or unrelated to school, realistically state what it is without inventing any academic meaning or experiments. Additional context: '$safeDesc'."
                
                val response = withContext(Dispatchers.IO) {
                    val isImage = file.name.endsWith(".jpg", true) || file.name.endsWith(".png", true) || file.name.endsWith(".jpeg", true)
                    
                    if (isImage) {
                        try {
                            val bitmap = if (file.downloadUrl.startsWith("http")) {
                                val inputStream = java.net.URL(file.downloadUrl).openStream()
                                android.graphics.BitmapFactory.decodeStream(inputStream)
                            } else {
                                val uri = android.net.Uri.parse(file.downloadUrl)
                                contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it) }
                            }
                            
                            if (bitmap != null) {
                                val inputContent = com.google.ai.client.generativeai.type.content {
                                    image(bitmap)
                                    text(promptText)
                                }
                                generativeModel.generateContent(inputContent)
                            } else {
                                generativeModel.generateContent(promptText)
                            }
                        } catch (e: Exception) {
                            generativeModel.generateContent(promptText)
                        }
                    } else {
                        generativeModel.generateContent(promptText)
                    }
                }

                dialog.dismiss()
                val summaryText = response.text ?: "Could not generate summary."
                
                androidx.appcompat.app.AlertDialog.Builder(this@MyLibraryActivity)
                    .setTitle("✨ AI Smart Summary")
                    .setMessage(summaryText)
                    .setPositiveButton("Awesome!") { _, _ -> }
                    .show()
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
        libraryListener?.remove()
    }
}
