package com.example.studybuddies.ui.drive

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.studybuddies.R
import com.example.studybuddies.data.model.SubjectDrive

class SubjectDriveAdapter(
    private val drives: List<SubjectDrive>,
    private val onDriveClick: (SubjectDrive) -> Unit
) : RecyclerView.Adapter<SubjectDriveAdapter.DriveViewHolder>() {

    class DriveViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvSubjectName: TextView = itemView.findViewById(R.id.tvSubjectName)
        val tvFileCount: TextView = itemView.findViewById(R.id.tvFileCount)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DriveViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_subject_drive, parent, false)
        return DriveViewHolder(view)
    }

    override fun onBindViewHolder(holder: DriveViewHolder, position: Int) {
        val drive = drives[position]
        holder.tvSubjectName.text = drive.name
        holder.tvFileCount.text = "${drive.fileCount} items"
        
        holder.itemView.setOnClickListener { view ->
            view.isEnabled = false
            view.postDelayed({ view.isEnabled = true }, 1000)
            onDriveClick(drive)
        }
    }

    override fun getItemCount(): Int = drives.size
}
