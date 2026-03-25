package com.example.studybuddies.ui.home

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
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

    private val selectFileLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            uploadFileMock(uri)
        }
    }

    private val takePictureLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            cameraUri?.let { uploadFileMock(it) }
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
            onCommentClick = { file -> Toast.makeText(this, "Comments coming soon!", Toast.LENGTH_SHORT).show() },
            onReportClick = { file -> reportFile(file) }
        )
        rvFiles.layoutManager = LinearLayoutManager(this)
        rvFiles.adapter = driveAdapter

        if (currentDriveId.isNotEmpty()) {
            checkUserPermissionsAndListen()
        }

        findViewById<FloatingActionButton>(R.id.fabUpload).setOnClickListener {
            val options = arrayOf("Take Photo", "Choose from Gallery")
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Upload Material")
                .setItems(options) { _, which ->
                    if (which == 0) {
                        launchCamera()
                    } else {
                        selectFileLauncher.launch("image/*")
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
        // We must suppress the nullability warning or use it safely:
        cameraUri?.let { uri ->
            takePictureLauncher.launch(uri)
        }
    }

    private fun downloadFile(file: com.example.studybuddies.data.model.DriveFile) {
        val uId = auth.currentUser?.uid ?: return
        
        // The original uploader views their own file for free
        if (file.uploaderId == uId) {
            openFileIntent(file)
            return
        }

        val userPoints = myUserData?.reputationPoints ?: 0
        
        // Admin or points check
        if (userPoints < 5 && userPoints != 9999) {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Not Enough Points")
                .setMessage("You need at least 5 points to view a file, but you only have $userPoints. Please upload a study material first to earn +10 points!")
                .setPositiveButton("OK", null)
                .show()
            return
        }
        
        // Deduct 5 points with confirmation
        if (userPoints < 9000) {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Unlock Material")
                .setMessage("Viewing this material will cost 5 Reputation Points. Do you want to continue?")
                .setPositiveButton("Unlock (-5 Points)") { _, _ ->
                    db.collection("users").document(uId)
                        .update("reputationPoints", com.google.firebase.firestore.FieldValue.increment(-5))
                    Toast.makeText(this, "-5 Points to view ${file.name}", Toast.LENGTH_SHORT).show()
                    openFileIntent(file)
                }
                .setNegativeButton("Cancel", null)
                .show()
        } else {
            // Admins bypass deduction and prompt
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
        val docRef = db.collection("files").document(file.id)
        if (file.likes.contains(userId)) {
            // Unlike
            docRef.update("likes", com.google.firebase.firestore.FieldValue.arrayRemove(userId))
        } else {
            // Like
            docRef.update("likes", com.google.firebase.firestore.FieldValue.arrayUnion(userId))
            // Reward uploader with 1 rep point
            db.collection("users").document(file.uploaderId)
                .update("reputationPoints", com.google.firebase.firestore.FieldValue.increment(1))
        }
    }

    private fun reportFile(file: com.example.studybuddies.data.model.DriveFile) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Report Content")
            .setMessage("Are you sure you want to report this file for violating community guidelines?")
            .setPositiveButton("Report") { _, _ ->
                val newReportCount = file.reports + 1
                db.collection("files").document(file.id).update("reports", newReportCount)
                Toast.makeText(this, "File reported.", Toast.LENGTH_SHORT).show()

                // If > 3 reports, ban user
                if (newReportCount > 3) {
                    db.collection("users").document(file.uploaderId).update("isBanned", true)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun checkUserPermissionsAndListen() {
        val user = auth.currentUser ?: return
        db.collection("users").document(user.uid).addSnapshotListener { document, error ->
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
                            // Do not fetch files
                            return@addSnapshotListener
                        }
                        
                        // Passed checks!
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
        db.collection("files")
            .whereEqualTo("driveId", currentDriveId)
            // .orderBy requires a composite index if used with whereEqualTo. 
            // For a student project, client-side sorting is easier if no index is manually created.
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

    private fun uploadFileMock(uri: Uri) {
        val user = auth.currentUser ?: return
        
        Toast.makeText(this, "Mocking File Upload...", Toast.LENGTH_SHORT).show()

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

        val docRef = db.collection("files").document()
        val newFile = DriveFile(
            id = docRef.id,
            name = fileName,
            downloadUrl = uri.toString(),
            uploaderId = user.uid,
            uploaderName = user.email ?: "Unknown",
            driveId = currentDriveId,
            timestamp = System.currentTimeMillis()
        )

        docRef.set(newFile)
            .addOnSuccessListener {
                Toast.makeText(this, "Uploaded saved! +10 Reputation Points", Toast.LENGTH_SHORT).show()
                val updates = hashMapOf<String, Any>(
                    "hasUploaded" to true,
                    "reputationPoints" to com.google.firebase.firestore.FieldValue.increment(10)
                )
                db.collection("users").document(user.uid).set(
                    updates,
                    com.google.firebase.firestore.SetOptions.merge()
                )
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Upload failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}
