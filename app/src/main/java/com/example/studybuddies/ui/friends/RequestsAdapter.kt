package com.example.studybuddies.ui.friends

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.studybuddies.R
import com.example.studybuddies.data.model.User

class RequestsAdapter(
    private val requestsList: List<User>,
    private val onAcceptClick: (User) -> Unit,
    private val onDeclineClick: (User) -> Unit
) : RecyclerView.Adapter<RequestsAdapter.RequestViewHolder>() {

    class RequestViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvRequestName)
        val tvEmail: TextView = view.findViewById(R.id.tvRequestEmail)
        val btnAccept: Button = view.findViewById(R.id.btnAccept)
        val btnDecline: Button = view.findViewById(R.id.btnDecline)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RequestViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_friend_request, parent, false)
        return RequestViewHolder(view)
    }

    override fun onBindViewHolder(holder: RequestViewHolder, position: Int) {
        val user = requestsList[position]
        holder.tvName.text = user.displayName.ifEmpty { "Student" }
        holder.tvEmail.text = user.email

        holder.btnAccept.setOnClickListener { onAcceptClick(user) }
        holder.btnDecline.setOnClickListener { onDeclineClick(user) }
    }

    override fun getItemCount() = requestsList.size
}
