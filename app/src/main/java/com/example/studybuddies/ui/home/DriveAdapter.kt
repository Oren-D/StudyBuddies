package com.example.studybuddies.ui.home

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
    private val onReportClick: (DriveFile) -> Unit
) : RecyclerView.Adapter<DriveAdapter.DriveViewHolder>() {

    class DriveViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvFileName: TextView = itemView.findViewById(R.id.tvFileName)
        val tvUploader: TextView = itemView.findViewById(R.id.tvUploader)
        val btnDownload: ImageButton = itemView.findViewById(R.id.btnDownload)
        val btnLike: ImageButton = itemView.findViewById(R.id.btnLike)
        val tvLikeCount: TextView = itemView.findViewById(R.id.tvLikeCount)
        val btnComment: ImageButton = itemView.findViewById(R.id.btnComment)
        val btnReport: ImageButton = itemView.findViewById(R.id.btnReport)
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
        holder.tvLikeCount.text = file.likes.size.toString()
        
        // Highlight if user already liked
        if (file.likes.contains(currentUserId)) {
            holder.btnLike.setImageResource(android.R.drawable.btn_star_big_on)
        } else {
            holder.btnLike.setImageResource(android.R.drawable.btn_star_big_off)
        }

        holder.btnDownload.setOnClickListener { onDownloadClick(file) }
        holder.btnLike.setOnClickListener { onLikeClick(file) }
        holder.btnComment.setOnClickListener { onCommentClick(file) }
        holder.btnReport.setOnClickListener { onReportClick(file) }
    }

    override fun getItemCount() = files.size

    fun updateFiles(newFiles: List<DriveFile>) {
        files.clear()
        files.addAll(newFiles)
        notifyDataSetChanged()
    }
}
