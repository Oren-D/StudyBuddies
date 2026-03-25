package com.example.studybuddies.ui.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.studybuddies.R
import com.example.studybuddies.data.model.User

class FriendsAdapter(
    private val friends: List<User>,
    private val onMessageClick: (User) -> Unit,
    private val onRemoveClick: (User) -> Unit
) : RecyclerView.Adapter<FriendsAdapter.FriendViewHolder>() {

    class FriendViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvFriendName: TextView = itemView.findViewById(R.id.tvFriendName)
        val tvFriendEmail: TextView = itemView.findViewById(R.id.tvFriendEmail)
        val btnMessageFriend: ImageButton = itemView.findViewById(R.id.btnMessageFriend)
        val btnRemoveFriend: ImageButton = itemView.findViewById(R.id.btnRemoveFriend)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FriendViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_friend, parent, false)
        return FriendViewHolder(view)
    }

    override fun onBindViewHolder(holder: FriendViewHolder, position: Int) {
        val friend = friends[position]
        holder.tvFriendName.text = friend.displayName
        holder.tvFriendEmail.text = friend.email
        
        holder.btnMessageFriend.setOnClickListener {
            onMessageClick(friend)
        }

        holder.btnRemoveFriend.setOnClickListener {
            onRemoveClick(friend)
        }
    }

    override fun getItemCount(): Int = friends.size
}
