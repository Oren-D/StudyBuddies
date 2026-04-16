package com.example.studybuddies.ui.drive

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.studybuddies.R
import com.example.studybuddies.data.model.Comment

class CommentAdapter(
    private val comments: List<Comment>
) : RecyclerView.Adapter<CommentAdapter.CommentViewHolder>() {

    class CommentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvCommenterName: TextView = itemView.findViewById(R.id.tvCommenterName)
        val tvCommentContent: TextView = itemView.findViewById(R.id.tvCommentContent)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CommentViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_comment, parent, false)
        return CommentViewHolder(view)
    }

    override fun onBindViewHolder(holder: CommentViewHolder, position: Int) {
        val comment = comments[position]
        holder.tvCommenterName.text = comment.authorName
        holder.tvCommentContent.text = comment.content
    }

    override fun getItemCount(): Int = comments.size
}
