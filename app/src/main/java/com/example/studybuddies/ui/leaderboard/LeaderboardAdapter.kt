package com.example.studybuddies.ui.leaderboard

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.studybuddies.R
import com.example.studybuddies.data.model.User

class LeaderboardAdapter(private val users: List<User>) : RecyclerView.Adapter<LeaderboardAdapter.LeaderboardViewHolder>() {

    class LeaderboardViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvRank: TextView = itemView.findViewById(R.id.tvRank)
        val tvName: TextView = itemView.findViewById(R.id.tvName)
        val tvPoints: TextView = itemView.findViewById(R.id.tvPoints)
        val tvBadge: TextView = itemView.findViewById(R.id.tvBadge)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LeaderboardViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_leaderboard, parent, false)
        return LeaderboardViewHolder(view)
    }

    override fun onBindViewHolder(holder: LeaderboardViewHolder, position: Int) {
        val user = users[position]
        val rank = position + 1
        
        holder.tvRank.text = rank.toString()
        holder.tvName.text = user.displayName.ifEmpty { user.email.substringBefore("@") }
        holder.tvPoints.text = "${user.reputationPoints} Points"
        
        when (rank) {
            1 -> {
                holder.tvRank.setTextColor(Color.parseColor("#D4AF37")) // Gold
                holder.tvBadge.text = "🏆 Elite Contributor"
                holder.tvBadge.visibility = View.VISIBLE
                holder.tvBadge.setTextColor(Color.parseColor("#D4AF37"))
            }
            2 -> {
                holder.tvRank.setTextColor(Color.parseColor("#C0C0C0")) // Silver
                holder.tvBadge.text = "🥈 Pro Tutor"
                holder.tvBadge.visibility = View.VISIBLE
                holder.tvBadge.setTextColor(Color.parseColor("#C0C0C0"))
            }
            3 -> {
                holder.tvRank.setTextColor(Color.parseColor("#CD7F32")) // Bronze
                holder.tvBadge.text = "🥉 Rising Star"
                holder.tvBadge.visibility = View.VISIBLE
                holder.tvBadge.setTextColor(Color.parseColor("#CD7F32"))
            }
            else -> {
                holder.tvRank.setTextColor(Color.parseColor("#757575"))
                if (user.reputationPoints > 200) {
                    holder.tvBadge.text = "⭐ Active Contributor"
                    holder.tvBadge.visibility = View.VISIBLE
                    holder.tvBadge.setTextColor(Color.parseColor("#D4AF37"))
                } else {
                    holder.tvBadge.visibility = View.GONE
                }
            }
        }
    }

    override fun getItemCount(): Int = users.size
}
