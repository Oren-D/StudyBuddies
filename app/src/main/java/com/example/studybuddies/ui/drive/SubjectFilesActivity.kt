package com.example.studybuddies.ui.drive

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.studybuddies.R
import com.example.studybuddies.data.model.DriveFile
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.util.UUID

class SubjectFilesActivity : AppCompatActivity() {

    private lateinit var rvFiles: RecyclerView
    private lateinit var tvEmptyState: TextView
    private lateinit var driveAdapter: DriveAdapter

    private val db by lazy { FirebaseFirestore.getInstance() }
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val filesList = mutableListOf<DriveFile>()

    private var currentDriveId: String = ""
    private var currentDriveName: String = "Subject"
    private var cameraUri: Uri? = null
    private var isListeningToFiles: Boolean = false
    private var myUserData: com.example.studybuddies.data.model.User? = null
    private var userListener: com.google.firebase.firestore.ListenerRegistration? = null
    private var filesListener: com.google.firebase.firestore.ListenerRegistration? = null

    private val selectFileLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            promptForDescription(uri)
        }
    }

    private val takePictureLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            cameraUri?.let { promptForDescription(it) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_subject_files)

        currentDriveId = intent.getStringExtra("DRIVE_ID") ?: ""
        currentDriveName = intent.getStringExtra("DRIVE_NAME") ?: "Subject Files"

        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = currentDriveName
        toolbar.setNavigationOnClickListener { finish() }

        rvFiles = findViewById(R.id.rvFiles)
        tvEmptyState = findViewById(R.id.tvEmptyState)

        val currentUserId = auth.currentUser?.uid ?: ""
        driveAdapter = DriveAdapter(filesList, currentUserId,
            onDownloadClick = { file -> downloadFile(file) },
            onLikeClick = { file -> toggleLike(file, currentUserId) },
            onCommentClick = { file -> showCommentsDialog(file) },
            onReportClick = { file -> reportFile(file) },
            onDeleteClick = { file -> deleteFile(file) },
            onAnalyzeClick = { file -> analyzeFileMock(file) }
        )
        rvFiles.layoutManager = LinearLayoutManager(this)
        rvFiles.adapter = driveAdapter

        if (currentDriveId.isNotEmpty()) {
            checkUserPermissionsAndListen()
        }

        findViewById<FloatingActionButton>(R.id.fabUpload).setOnClickListener {
            val options = arrayOf("Take Photo", "Upload Photo", "Upload PDF Document")
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Upload Material")
                .setItems(options) { _, which ->
                    when (which) {
                        0 -> launchCamera()
                        1 -> selectFileLauncher.launch("image/*")
                        2 -> selectFileLauncher.launch("application/pdf")
                    }
                }
                .show()
        }
    }

    private fun launchCamera() {
        val imagesDir = java.io.File(externalCacheDir, "images")
        if (!imagesDir.exists()) {
            imagesDir.mkdirs()
        }
        val photoFile = java.io.File(imagesDir, "IMG_${System.currentTimeMillis()}.jpg")
        cameraUri = androidx.core.content.FileProvider.getUriForFile(
            this,
            "${applicationContext.packageName}.provider",
            photoFile
        )
        cameraUri?.let { uri ->
            takePictureLauncher.launch(uri)
        }
    }

    private fun analyzeFileMock(file: com.example.studybuddies.data.model.DriveFile) {
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
                        androidx.appcompat.app.AlertDialog.Builder(this@SubjectFilesActivity)
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
                val uId = auth.currentUser?.uid
                val isUnlocked = uId != null && (file.uploaderId == uId || file.unlockedBy.contains(uId))
                
                val promptText = if (isUnlocked) {
                    "Accurately describe exactly what you see in the provided file in no more than 3 sentences. If it is an academic document or notes, summarize the core concepts. If it is just an everyday object (like a soda can) or unrelated to school, realistically state what it is without inventing any academic meaning or experiments. Additional context: '$safeDesc'."
                } else {
                    "Accurately describe exactly what you see in the provided file as an exciting 'teaser' preview in no more than 3 sentences. If it is an academic document or assignment, summarize the broad topics covered, but CRITICAL RULE: DO NOT REVEAL ANY SPECIFIC ANSWERS, SOLUTIONS, OR EQUATIONS. If it is just an everyday object or unrelated to school, realistically state what it is without inventing any academic meaning. Additional context: '$safeDesc'."
                }
                
                val response = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
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
                
                androidx.appcompat.app.AlertDialog.Builder(this@SubjectFilesActivity)
                    .setTitle("✨ AI Smart Summary")
                    .setMessage(summaryText)
                    .setPositiveButton("Awesome!") { _, _ -> }
                    .show()
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    dialog.dismiss()
                    androidx.appcompat.app.AlertDialog.Builder(this@SubjectFilesActivity)
                        .setTitle("AI Error")
                        .setMessage("Failed to connect to Gemini AI:\n\n${e.localizedMessage ?: e.toString()}")
                        .setPositiveButton("OK", null)
                        .show()
                }
            }
        }
    }

    private fun downloadFile(file: com.example.studybuddies.data.model.DriveFile) {
        val uId = auth.currentUser?.uid ?: return
        
        if (file.uploaderId == uId || file.unlockedBy.contains(uId)) {
            openFileIntent(file)
            return
        }

        val userPoints = myUserData?.reputationPoints ?: 0
        
        if (userPoints < 5 && userPoints != 9999) {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Not Enough Points")
                .setMessage("You need at least 5 points to view a file, but you only have $userPoints. Please upload a study material first to earn +10 points!")
                .setPositiveButton("OK", null)
                .show()
            return
        }
        
        if (userPoints < 9000) {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Unlock Material")
                .setMessage("Viewing this material will cost 5 Reputation Points. Do you want to continue?")
                .setPositiveButton("Unlock (-5 Points)") { _, _ ->
                    db.collection("users").document(uId)
                        .set(hashMapOf("reputationPoints" to com.google.firebase.firestore.FieldValue.increment(-5)), com.google.firebase.firestore.SetOptions.merge())
                        
                    db.collection("files").document(file.id)
                        .update("unlockedBy", com.google.firebase.firestore.FieldValue.arrayUnion(uId))
                        
                    Toast.makeText(this, "-5 Points to view ${file.name}", Toast.LENGTH_SHORT).show()
                    openFileIntent(file)
                }
                .setNegativeButton("Cancel", null)
                .show()
        } else {
            openFileIntent(file)
        }
    }

    private fun openFileIntent(file: com.example.studybuddies.data.model.DriveFile) {
        try {
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW)
            val uri = android.net.Uri.parse(file.downloadUrl)
            intent.setDataAndType(uri, if (file.name.endsWith(".jpg")) "image/*" else "application/pdf")
            intent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Unable to open file: ${file.name}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun toggleLike(file: com.example.studybuddies.data.model.DriveFile, userId: String) {
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

    private fun deleteFile(file: com.example.studybuddies.data.model.DriveFile) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Delete File")
            .setMessage("Are you sure you want to permanently delete this file? This cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                try {
                    com.google.firebase.storage.FirebaseStorage.getInstance().getReferenceFromUrl(file.downloadUrl).delete()
                } catch (e: Exception) {}
                
                db.collection("files").document(file.id).delete()
                    .addOnSuccessListener {
                        Toast.makeText(this, "File deleted successfully.", Toast.LENGTH_SHORT).show()
                        db.collection("subject_drives").document(currentDriveId)
                            .set(hashMapOf("fileCount" to com.google.firebase.firestore.FieldValue.increment(-1)), com.google.firebase.firestore.SetOptions.merge())
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "Failed to delete file.", Toast.LENGTH_SHORT).show()
                    }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun reportFile(file: com.example.studybuddies.data.model.DriveFile) {
        val currentUserId = auth.currentUser?.uid ?: return
        
        if (file.uploaderId == currentUserId) {
            Toast.makeText(this, "You cannot report your own file!", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (file.reportedBy.contains(currentUserId)) {
            Toast.makeText(this, "You have already reported this file.", Toast.LENGTH_SHORT).show()
            return
        }

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Report Content")
            .setMessage("Are you sure you want to report this file for violating community guidelines?")
            .setPositiveButton("Report") { _, _ ->
                val newReportCount = file.reportedBy.size + 1
                
                if (newReportCount > 3) {
                    try {
                        com.google.firebase.storage.FirebaseStorage.getInstance().getReferenceFromUrl(file.downloadUrl).delete()
                    } catch (e: Exception) {}
                    
                    db.collection("files").document(file.id).delete()
                    db.collection("subject_drives").document(currentDriveId)
                        .set(hashMapOf("fileCount" to com.google.firebase.firestore.FieldValue.increment(-1)), com.google.firebase.firestore.SetOptions.merge())
                    Toast.makeText(this, "File removed due to multiple reports.", Toast.LENGTH_SHORT).show()
                    
                    val userRef = db.collection("users").document(file.uploaderId)
                    db.runTransaction { transaction ->
                        val snapshot = transaction.get(userRef)
                        val currentDeletes = snapshot.getLong("deletedFilesCount") ?: 0L
                        val newDeletes = currentDeletes + 1
                        
                        val updates = mutableMapOf<String, Any>("deletedFilesCount" to newDeletes)
                        if (newDeletes > 3) {
                            updates["isBanned"] = true
                        }
                        transaction.set(userRef, updates, com.google.firebase.firestore.SetOptions.merge())
                        null
                    }
                } else {
                    db.collection("files").document(file.id).update("reportedBy", com.google.firebase.firestore.FieldValue.arrayUnion(currentUserId))
                    Toast.makeText(this, "File reported.", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showCommentsDialog(file: com.example.studybuddies.data.model.DriveFile) {
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
                val authorNameStr = myUserData?.displayName ?: auth.currentUser?.email ?: "Unknown"
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

    private fun checkUserPermissionsAndListen() {
        val user = auth.currentUser ?: return
        userListener = db.collection("users").document(user.uid).addSnapshotListener { document, error ->
            if (error != null) {
                Toast.makeText(this, "Failed to verify account status.", Toast.LENGTH_SHORT).show()
                finish()
                return@addSnapshotListener
            }
                if (document != null && document.exists()) {
                    val userData = document.toObject(com.example.studybuddies.data.model.User::class.java)
                    if (userData != null) {
                        myUserData = userData
                        if (userData.isBanned) {
                            Toast.makeText(this, "Your account is banned.", Toast.LENGTH_LONG).show()
                            finish()
                            return@addSnapshotListener
                        }
                        
                        val tvBlockedText = findViewById<android.widget.TextView>(R.id.tvBlockedText)
                        if (!userData.hasUploaded && userData.reputationPoints < 9999) {
                            tvBlockedText.visibility = android.view.View.VISIBLE
                            rvFiles.visibility = android.view.View.GONE
                            return@addSnapshotListener
                        }
                        
                        tvBlockedText.visibility = android.view.View.GONE
                        rvFiles.visibility = android.view.View.VISIBLE
                        
                        if (!isListeningToFiles) {
                            isListeningToFiles = true
                            setupFirestoreListener()
                        }
                    }
                }
            }
    }

    private fun setupFirestoreListener() {
        filesListener = db.collection("files")
            .whereEqualTo("driveId", currentDriveId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Toast.makeText(this, "Listen failed: ${error.message}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val fetchedFiles = snapshot.toObjects(DriveFile::class.java).sortedByDescending { it.timestamp }
                    driveAdapter.updateFiles(fetchedFiles)
                    updateEmptyState()
                }
            }
    }

    private fun updateEmptyState() {
        if (filesList.isEmpty()) {
            tvEmptyState.visibility = View.VISIBLE
            rvFiles.visibility = View.GONE
        } else {
            tvEmptyState.visibility = View.GONE
            rvFiles.visibility = View.VISIBLE
        }
    }

    private fun promptForDescription(uri: Uri) {
        val input = android.widget.EditText(this)
        input.hint = "e.g., Chapter 4 Math Notes"
        input.maxLines = 3
        
        val container = android.widget.LinearLayout(this)
        container.orientation = android.widget.LinearLayout.VERTICAL
        val params = android.widget.LinearLayout.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )
        params.setMargins(60, 20, 60, 0)
        input.layoutParams = params
        container.addView(input)

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("File Description")
            .setMessage("Please provide a description so others know what this file is before downloading.")
            .setView(container)
            .setPositiveButton("Upload") { _, _ ->
                val desc = input.text.toString().trim()
                if (desc.isNotEmpty()) {
                    uploadFileToCloud(uri, desc)
                } else {
                    Toast.makeText(this, "Description is required! Upload cancelled.", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .setCancelable(false)
            .show()
    }

    private fun uploadFileToCloud(uri: Uri, description: String = "") {
        val user = auth.currentUser ?: return
        
        Toast.makeText(this, "Uploading to Cloud Storage... Please wait.", Toast.LENGTH_LONG).show()
        
        val isCamera = uri == cameraUri
        
        var ext = ".pdf"
        var prefix = "Document"
        if (isCamera) {
            ext = ".jpg"
            prefix = "Photo"
        } else {
            val mimeType = applicationContext.contentResolver.getType(uri)
            if (mimeType != null && mimeType.startsWith("image/")) {
                ext = ".jpg"
                prefix = "Photo"
            }
        }
        
        val fileName = "${prefix}_${UUID.randomUUID().toString().take(4)}$ext"

        val storageRef = com.google.firebase.storage.FirebaseStorage.getInstance().reference.child("uploads/$fileName")
        storageRef.putFile(uri)
            .addOnSuccessListener { taskSnapshot ->
                taskSnapshot.storage.downloadUrl.addOnSuccessListener { downloadUri ->
                    
                    val docRef = db.collection("files").document()
                    val newFile = DriveFile(
                        id = docRef.id,
                        name = fileName,
                        downloadUrl = downloadUri.toString(),
                        uploaderId = user.uid,
                        uploaderName = user.email ?: "Unknown",
                        driveId = currentDriveId,
                        description = description,
                        timestamp = System.currentTimeMillis()
                    )

                    docRef.set(newFile)
                        .addOnSuccessListener {
                            Toast.makeText(this, "Upload successful! +10 Reputation Points", Toast.LENGTH_SHORT).show()
                            val updates = hashMapOf<String, Any>(
                                "hasUploaded" to true,
                                "reputationPoints" to com.google.firebase.firestore.FieldValue.increment(10)
                            )
                            db.collection("users").document(user.uid).set(
                                updates,
                                com.google.firebase.firestore.SetOptions.merge()
                            )
                            db.collection("subject_drives").document(currentDriveId)
                                .set(hashMapOf("fileCount" to com.google.firebase.firestore.FieldValue.increment(1)), com.google.firebase.firestore.SetOptions.merge())
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(this, "Database update failed: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Cloud Storage upload failed. Is your Firebase Storage fully set up? ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        userListener?.remove()
        filesListener?.remove()
    }
}
