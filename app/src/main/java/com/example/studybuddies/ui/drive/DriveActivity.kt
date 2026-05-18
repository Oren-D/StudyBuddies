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
import java.util.UUID

/**
 * This screen shows the list of Subject Drives (like folders).
 * It uses IDriveManager to fetch them from the database.
 */
class DriveActivity : AppCompatActivity() {

    private lateinit var rvFiles: RecyclerView
    private lateinit var tvEmptyState: TextView
    private lateinit var etSearchDrive: EditText
    private lateinit var subjectDriveAdapter: SubjectDriveAdapter

    private lateinit var driveManager: IDriveManager
    private val drivesList = mutableListOf<SubjectDrive>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_drive)

        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        driveManager = FirebaseDriveManager()

        rvFiles = findViewById(R.id.rvFiles)
        tvEmptyState = findViewById(R.id.tvEmptyState)
        etSearchDrive = findViewById(R.id.etSearchDrive)

        etSearchDrive.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterDrives(s.toString())
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

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
        driveManager.listenToSubjectDrives { drives, error ->
            if (error != null) {
                Toast.makeText(this, "Could not get any Subjects due to: ${error.message}", Toast.LENGTH_SHORT).show()
                return@listenToSubjectDrives
            }

            if (drives != null) {
                drivesList.clear()
                drivesList.addAll(drives)
                filterDrives(etSearchDrive.text.toString())
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

    private fun filterDrives(query: String) {
        if (query.isEmpty()) {
            subjectDriveAdapter.updateList(drivesList)
        } else {
            val filteredList = drivesList.filter { 
                it.name.contains(query, ignoreCase = true) 
            }
            subjectDriveAdapter.updateList(filteredList)
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
        driveManager.createSubjectDrive(subjectName) { success, errorMsg ->
            if (success) {
                Toast.makeText(this, "Subject Drive created!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Failed to create drive: $errorMsg", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        driveManager.cleanup()
    }
}
