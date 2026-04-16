package com.example.studybuddies.ui.drive

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.studybuddies.R
import com.example.studybuddies.data.model.DriveFile

class DriveAdapter(
    private val files: MutableList<DriveFile>,
    private val currentUserId: String,
    private val onDownloadClick: (DriveFile) -> Unit,
    private val onLikeClick: (DriveFile) -> Unit,
    private val onCommentClick: (DriveFile) -> Unit,
    private val onReportClick: (DriveFile) -> Unit,
    private val onDeleteClick: (DriveFile) -> Unit,
    private val onAnalyzeClick: (DriveFile) -> Unit
) : RecyclerView.Adapter<DriveAdapter.DriveViewHolder>() {

    class DriveViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivFileTypeIcon: android.widget.ImageView = itemView.findViewById(R.id.ivFileTypeIcon)
        val tvFileName: TextView = itemView.findViewById(R.id.tvFileName)
        val tvUploader: TextView = itemView.findViewById(R.id.tvUploader)
        val tvDescription: TextView = itemView.findViewById(R.id.tvDescription)
        val btnAnalyze: com.google.android.material.button.MaterialButton = itemView.findViewById(R.id.btnAnalyze)
        val btnDownload: com.google.android.material.button.MaterialButton = itemView.findViewById(R.id.btnDownload)
        val btnLike: android.widget.ImageView = itemView.findViewById(R.id.btnLike)
        val tvLikeCount: TextView = itemView.findViewById(R.id.tvLikeCount)
        val btnComment: android.widget.ImageView = itemView.findViewById(R.id.btnComment)
        val tvCommentCount: TextView = itemView.findViewById(R.id.tvCommentCount)
        val btnReport: android.widget.ImageView = itemView.findViewById(R.id.btnReport)
        val btnDelete: android.widget.ImageView = itemView.findViewById(R.id.btnDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DriveViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_drive_file, parent, false)
        return DriveViewHolder(view)
    }

    override fun onBindViewHolder(holder: DriveViewHolder, position: Int) {
        val file = files[position]
        holder.tvFileName.text = file.name
        holder.tvUploader.text = "Uploaded by: ${file.uploaderName}"
        
        if (file.name.endsWith(".pdf", true)) {
            holder.ivFileTypeIcon.setImageResource(R.drawable.ic_document)
        } else {
            holder.ivFileTypeIcon.setImageResource(android.R.drawable.ic_menu_gallery)
        }
        if (file.description.isNotEmpty()) {
            holder.tvDescription.text = "\"${file.description}\""
            holder.tvDescription.visibility = View.VISIBLE
        } else {
            holder.tvDescription.visibility = View.GONE
        }
        holder.tvLikeCount.text = file.likes.size.toString()
        holder.tvCommentCount.text = file.commentCount.toString()
        
        if (file.likes.contains(currentUserId)) {
            holder.btnLike.setImageResource(android.R.drawable.btn_star_big_on)
        } else {
            holder.btnLike.setImageResource(android.R.drawable.btn_star_big_off)
        }

        holder.btnAnalyze.setOnClickListener { onAnalyzeClick(file) }
        holder.btnDownload.setOnClickListener { onDownloadClick(file) }
        holder.btnLike.setOnClickListener { onLikeClick(file) }
        holder.btnComment.setOnClickListener { onCommentClick(file) }
        holder.btnReport.setOnClickListener { onReportClick(file) }
        
        if (file.uploaderId == currentUserId) {
            holder.btnDelete.visibility = View.VISIBLE
            holder.btnDelete.setOnClickListener { onDeleteClick(file) }
        } else {
            holder.btnDelete.visibility = View.GONE
        }
    }

    override fun getItemCount() = files.size

    fun updateFiles(newFiles: List<DriveFile>) {
        files.clear()
        files.addAll(newFiles)
        notifyDataSetChanged()
    }
}
