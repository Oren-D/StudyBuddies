package com.example.studybuddies.ui.drive

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.studybuddies.BuildConfig
import com.example.studybuddies.R
import com.example.studybuddies.data.model.Comment
import com.example.studybuddies.data.model.DriveFile
import com.example.studybuddies.data.model.User
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class SubjectFilesActivity : AppCompatActivity() {

    private lateinit var rvFiles: RecyclerView
    private lateinit var tvEmptyState: TextView
    private lateinit var tvBlockedText: TextView
    private lateinit var driveAdapter: DriveAdapter

    private lateinit var filesManager: SubjectFilesManager
    private lateinit var geminiManager: GeminiManager

    private var currentDriveId: String = ""
    private var currentDriveName: String = "Subject"
    private var cameraUri: Uri? = null
    
    private var myUserData: User? = null
    private var isListeningToFiles: Boolean = false

    private val selectFileLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            promptForDescription(uri, false)
        }
    }

    private val takePictureLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            cameraUri?.let { promptForDescription(it, true) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_subject_files)

        currentDriveId = intent.getStringExtra("DRIVE_ID") ?: ""
        currentDriveName = intent.getStringExtra("DRIVE_NAME") ?: "Subject Files"

        filesManager = SubjectFilesManager(currentDriveId)
        val apiKey = BuildConfig.GEMINI_API_KEY
        geminiManager = GeminiManager(if (apiKey == "null") "" else apiKey)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = currentDriveName
        toolbar.setNavigationOnClickListener { finish() }

        rvFiles = findViewById(R.id.rvFiles)
        tvEmptyState = findViewById(R.id.tvEmptyState)
        tvBlockedText = findViewById(R.id.tvBlockedText)

        val currentUserId = filesManager.currentUserId ?: ""
        driveAdapter = DriveAdapter(mutableListOf(), currentUserId,
            onDownloadClick = { file -> downloadFile(file) },
            onLikeClick = { file -> 
                filesManager.toggleLike(file, currentUserId) {} 
            },
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
            AlertDialog.Builder(this)
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

    private fun checkUserPermissionsAndListen() {
        filesManager.listenToUserStatus { user, isBanned ->
            if (isBanned) {
                Toast.makeText(this, "Your account is banned.", Toast.LENGTH_LONG).show()
                finish()
                return@listenToUserStatus
            }
            if (user != null) {
                myUserData = user
                if (!user.hasUploaded && user.reputationPoints < 9999L) {
                    tvBlockedText.visibility = View.VISIBLE
                    rvFiles.visibility = View.GONE
                    return@listenToUserStatus
                }
                
                tvBlockedText.visibility = View.GONE
                rvFiles.visibility = View.VISIBLE
                
                if (!isListeningToFiles) {
                    isListeningToFiles = true
                    filesManager.listenToFiles { files ->
                        driveAdapter.updateFiles(files)
                        updateEmptyState(files.isEmpty())
                    }
                }
            } else {
                Toast.makeText(this, "Failed to verify account status.", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun updateEmptyState(isEmpty: Boolean) {
        if (isEmpty) {
            tvEmptyState.visibility = View.VISIBLE
            rvFiles.visibility = View.GONE
        } else {
            tvEmptyState.visibility = View.GONE
            rvFiles.visibility = View.VISIBLE
        }
    }

    private fun launchCamera() {
        val imagesDir = File(externalCacheDir, "images")
        if (!imagesDir.exists()) {
            imagesDir.mkdirs()
        }
        val photoFile = File(imagesDir, "IMG_${System.currentTimeMillis()}.jpg")
        cameraUri = FileProvider.getUriForFile(
            this,
            "${applicationContext.packageName}.provider",
            photoFile
        )
        cameraUri?.let { uri ->
            takePictureLauncher.launch(uri)
        }
    }

    private fun promptForDescription(uri: Uri, isCamera: Boolean) {
        val input = EditText(this)
        input.hint = "e.g., Chapter 4 Math Notes"
        input.maxLines = 3
        
        val container = LinearLayout(this)
        container.orientation = LinearLayout.VERTICAL
        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        params.setMargins(60, 20, 60, 0)
        input.layoutParams = params
        container.addView(input)

        AlertDialog.Builder(this)
            .setTitle("File Description")
            .setMessage("Please provide a description so others know what this file is before downloading.")
            .setView(container)
            .setPositiveButton("Upload") { _, _ ->
                val desc = input.text.toString().trim()
                if (desc.isNotEmpty()) {
                    Toast.makeText(this, "Uploading to Cloud Storage... Please wait.", Toast.LENGTH_LONG).show()
                    val mimeType = applicationContext.contentResolver.getType(uri)
                    filesManager.uploadFile(uri, isCamera, mimeType, desc) { success, errorMsg ->
                        if (success) {
                            Toast.makeText(this, "Upload successful! +10 Reputation Points", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this, "Upload failed: $errorMsg", Toast.LENGTH_LONG).show()
                        }
                    }
                } else {
                    Toast.makeText(this, "Description is required! Upload cancelled.", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .setCancelable(false)
            .show()
    }

    private fun analyzeFileMock(file: DriveFile) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "null") {
            AlertDialog.Builder(this)
                .setTitle("API Key Missing")
                .setMessage("Please ensure your API Key is correctly injected into BuildConfig.")
                .setPositiveButton("OK", null)
                .show()
            return
        }

        val dialogView = layoutInflater.inflate(R.layout.dialog_ai_summary, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .show()

        lifecycleScope.launch {
            try {
                val uId = filesManager.currentUserId
                val isUnlocked = uId != null && (file.uploaderId == uId || file.unlockedBy.contains(uId))
                
                val summaryText = geminiManager.analyzeFile(file, isUnlocked, contentResolver)
                
                withContext(Dispatchers.Main) {
                    dialog.dismiss()
                    AlertDialog.Builder(this@SubjectFilesActivity)
                        .setTitle("✨ AI Smart Summary")
                        .setMessage(summaryText)
                        .setPositiveButton("Awesome!") { _, _ -> }
                        .show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    dialog.dismiss()
                    AlertDialog.Builder(this@SubjectFilesActivity)
                        .setTitle("AI Error")
                        .setMessage("Failed to connect to Gemini AI:\n\n${e.localizedMessage ?: e.toString()}")
                        .setPositiveButton("OK", null)
                        .show()
                }
            }
        }
    }

    private fun downloadFile(file: DriveFile) {
        val uId = filesManager.currentUserId ?: return
        
        if (file.uploaderId == uId || file.unlockedBy.contains(uId)) {
            openFileIntent(file)
            return
        }

        val userPoints = myUserData?.reputationPoints ?: 0
        
        if (userPoints < 5 && userPoints != 9999) {
            AlertDialog.Builder(this)
                .setTitle("Not Enough Points")
                .setMessage("You need at least 5 points to view a file, but you only have $userPoints. Please upload a study material first to earn +10 points!")
                .setPositiveButton("OK", null)
                .show()
            return
        }
        
        if (userPoints < 9000) {
            AlertDialog.Builder(this)
                .setTitle("Unlock Material")
                .setMessage("Viewing this material will cost 5 Reputation Points. Do you want to continue?")
                .setPositiveButton("Unlock (-5 Points)") { _, _ ->
                    filesManager.unlockFile(file, uId) {
                        Toast.makeText(this, "-5 Points to view ${file.name}", Toast.LENGTH_SHORT).show()
                        openFileIntent(file)
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        } else {
            openFileIntent(file)
        }
    }

    private fun openFileIntent(file: DriveFile) {
        try {
            val intent = Intent(Intent.ACTION_VIEW)
            val uri = Uri.parse(file.downloadUrl)
            intent.setDataAndType(uri, if (file.name.endsWith(".jpg")) "image/*" else "application/pdf")
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Unable to open file: ${file.name}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun deleteFile(file: DriveFile) {
        AlertDialog.Builder(this)
            .setTitle("Delete File")
            .setMessage("Are you sure you want to permanently delete this file? This cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                filesManager.deleteFile(file,
                    onSuccess = {
                        Toast.makeText(this, "File deleted successfully.", Toast.LENGTH_SHORT).show()
                    },
                    onFailure = {
                        Toast.makeText(this, "Failed to delete file.", Toast.LENGTH_SHORT).show()
                    }
                )
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun reportFile(file: DriveFile) {
        val currentUserId = filesManager.currentUserId ?: return
        
        if (file.uploaderId == currentUserId) {
            Toast.makeText(this, "You cannot report your own file!", Toast.LENGTH_SHORT).show()
            return
        }
        if (file.reportedBy.contains(currentUserId)) {
            Toast.makeText(this, "You have already reported this file.", Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Report Content")
            .setMessage("Are you sure you want to report this file for violating community guidelines?")
            .setPositiveButton("Report") { _, _ ->
                filesManager.reportFile(file, currentUserId,
                    onReported = {
                        Toast.makeText(this, "File reported.", Toast.LENGTH_SHORT).show()
                    },
                    onDeleted = {
                        Toast.makeText(this, "File removed due to multiple reports.", Toast.LENGTH_SHORT).show()
                    }
                )
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showCommentsDialog(file: DriveFile) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_comments, null)
        val rvComments = dialogView.findViewById<RecyclerView>(R.id.rvComments)
        val tvNoComments = dialogView.findViewById<TextView>(R.id.tvNoComments)
        val etCommentInput = dialogView.findViewById<EditText>(R.id.etCommentInput)
        val btnSendComment = dialogView.findViewById<ImageButton>(R.id.btnSendComment)
        
        val commentsList = mutableListOf<Comment>()
        val commentsAdapter = CommentAdapter(commentsList)
        rvComments.layoutManager = LinearLayoutManager(this)
        rvComments.adapter = commentsAdapter
        
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .show()
            
        filesManager.listenToComments(file.id) { fetched ->
            commentsList.clear()
            commentsList.addAll(fetched)
            commentsAdapter.notifyDataSetChanged()
            
            if (commentsList.isEmpty()) {
                tvNoComments.visibility = View.VISIBLE
                rvComments.visibility = View.GONE
            } else {
                tvNoComments.visibility = View.GONE
                rvComments.visibility = View.VISIBLE
                rvComments.scrollToPosition(commentsList.size - 1)
            }
        }
            
        dialog.setOnDismissListener {
            filesManager.removeCommentsListener()
        }
        
        btnSendComment.setOnClickListener { view ->
            view.isEnabled = false
            view.postDelayed({ view.isEnabled = true }, 500)
            
            val content = etCommentInput.text.toString().trim()
            if (content.isNotEmpty()) {
                val authorNameStr = myUserData?.displayName ?: filesManager.currentUserEmail ?: "Unknown"
                filesManager.postComment(file.id, content, authorNameStr)
                etCommentInput.text.clear()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        filesManager.cleanup()
    }
}
