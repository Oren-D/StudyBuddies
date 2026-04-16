package com.example.studybuddies.ui.drive

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.studybuddies.R
import com.example.studybuddies.data.model.SubjectDrive
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.util.UUID

class DriveActivity : AppCompatActivity() {

    private lateinit var rvFiles: RecyclerView
    private lateinit var tvEmptyState: TextView
    private lateinit var subjectDriveAdapter: SubjectDriveAdapter

    private val db by lazy { FirebaseFirestore.getInstance() }
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val drivesList = mutableListOf<SubjectDrive>()
    private var driveListener: com.google.firebase.firestore.ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_drive)

        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        rvFiles = findViewById(R.id.rvFiles)
        tvEmptyState = findViewById(R.id.tvEmptyState)

        subjectDriveAdapter = SubjectDriveAdapter(drivesList) { drive ->
            val intent = Intent(this, SubjectFilesActivity::class.java).apply {
                putExtra("DRIVE_ID", drive.id)
                putExtra("DRIVE_NAME", drive.name)
            }
            startActivity(intent)
        }

        rvFiles.layoutManager = LinearLayoutManager(this)
        rvFiles.adapter = subjectDriveAdapter

        setupFirestoreListener()

        findViewById<FloatingActionButton>(R.id.fabCreateDrive).setOnClickListener {
            showCreateDriveDialog()
        }
    }

    private fun setupFirestoreListener() {
        driveListener = db.collection("subject_drives")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Toast.makeText(this, "Listen failed: ${error.message}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val fetchedDrives = snapshot.toObjects(SubjectDrive::class.java)
                    drivesList.clear()
                    drivesList.addAll(fetchedDrives)
                    subjectDriveAdapter.notifyDataSetChanged()
                    updateEmptyState()
                }
            }
    }

    private fun updateEmptyState() {
        if (drivesList.isEmpty()) {
            tvEmptyState.visibility = View.VISIBLE
            rvFiles.visibility = View.GONE
        } else {
            tvEmptyState.visibility = View.GONE
            rvFiles.visibility = View.VISIBLE
        }
    }

    private fun showCreateDriveDialog() {
        val editText = EditText(this)
        editText.hint = "e.g., Mathematics, Computer Science"

        AlertDialog.Builder(this)
            .setTitle("Create Subject Drive")
            .setView(editText)
            .setPositiveButton("Create") { _, _ ->
                val subjectName = editText.text.toString().trim()
                if (subjectName.isNotEmpty()) {
                    createDriveInFirestore(subjectName)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun createDriveInFirestore(subjectName: String) {
        val user = auth.currentUser ?: return
        val docRef = db.collection("subject_drives").document()
        
        val newDrive = SubjectDrive(
            id = docRef.id,
            name = subjectName,
            creatorUid = user.uid,
            timestamp = System.currentTimeMillis()
        )

        docRef.set(newDrive)
            .addOnSuccessListener {
                Toast.makeText(this, "Subject Drive created!", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to create drive: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        driveListener?.remove()
    }
}
