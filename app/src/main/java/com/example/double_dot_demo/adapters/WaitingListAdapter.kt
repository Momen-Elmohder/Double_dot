package com.example.double_dot_demo.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.double_dot_demo.R
import com.example.double_dot_demo.models.WaitingListPlayer
import com.example.double_dot_demo.utils.PerformanceUtils
import java.text.SimpleDateFormat
import java.util.*

class WaitingListAdapter(
    private val onEditClick: (WaitingListPlayer) -> Unit,
    private val onDeleteClick: (WaitingListPlayer) -> Unit,
    private val onContactClick: (WaitingListPlayer) -> Unit,
    private val onEnrollClick: (WaitingListPlayer) -> Unit,
    private val onRejectClick: (WaitingListPlayer) -> Unit
) : ListAdapter<WaitingListPlayer, WaitingListAdapter.WaitingListViewHolder>(WaitingListDiffCallback()) {

    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    class WaitingListViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvName: TextView = itemView.findViewById(R.id.tvName)
        val tvDetails: TextView = itemView.findViewById(R.id.tvDetails)
        val tvStatus: TextView = itemView.findViewById(R.id.tvStatus)
        val tvDate: TextView = itemView.findViewById(R.id.tvDate)
        val btnEdit: ImageView = itemView.findViewById(R.id.btnEdit)
        val btnDelete: ImageView = itemView.findViewById(R.id.btnDelete)
        val btnContact: ImageView = itemView.findViewById(R.id.btnContact)
        val btnEnroll: ImageView = itemView.findViewById(R.id.btnEnroll)
        val btnReject: ImageView = itemView.findViewById(R.id.btnReject)
        val cardBackground: View = itemView.findViewById(R.id.cardBackground)
        val actionButtonsContainer: View = itemView.findViewById(R.id.actionButtonsContainer)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WaitingListViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_waiting_list_player, parent, false)
        return WaitingListViewHolder(view)
    }

    override fun onBindViewHolder(holder: WaitingListViewHolder, position: Int) {
        try {
            val player = getItem(position) ?: return
            
            // Set basic info
            holder.tvName.text = player.name
            holder.tvDetails.text = buildDetailsText(player)
            holder.tvStatus.text = player.status.replaceFirstChar { it.uppercase() }
            
            // Set date
            val dateText = PerformanceUtils.formatDateSafely(player.createdAt)
            holder.tvDate.text = "Added: $dateText"
            
            // Set status color
            setStatusColor(holder.tvStatus, player.status)

            // Show all action buttons
            holder.btnEdit.visibility = View.VISIBLE
            holder.btnDelete.visibility = View.VISIBLE
            holder.btnContact.visibility = View.VISIBLE
            holder.btnEnroll.visibility = View.VISIBLE
            holder.btnReject.visibility = View.VISIBLE
            
            // Setup button click listeners with temporary disable
            setupClickListeners(holder, player)

        } catch (e: Exception) {
            android.util.Log.e("WaitingListAdapter", "Error binding view holder: ${e.message}")
        }
    }

    private fun setupClickListeners(holder: WaitingListViewHolder, player: WaitingListPlayer) {
        // Button click listeners with temporary disable
        holder.btnEdit.setOnClickListener {
            try {
                holder.btnEdit.isEnabled = false
                onEditClick(player)
                holder.btnEdit.postDelayed({ holder.btnEdit.isEnabled = true }, 1000)
            } catch (e: Exception) {
                android.util.Log.e("WaitingListAdapter", "Error in edit click: ${e.message}")
            }
        }

        holder.btnDelete.setOnClickListener {
            try {
                holder.btnDelete.isEnabled = false
                onDeleteClick(player)
                holder.btnDelete.postDelayed({ holder.btnDelete.isEnabled = true }, 1000)
            } catch (e: Exception) {
                android.util.Log.e("WaitingListAdapter", "Error in delete click: ${e.message}")
            }
        }

        holder.btnContact.setOnClickListener {
            try {
                holder.btnContact.isEnabled = false
                onContactClick(player)
                holder.btnContact.postDelayed({ holder.btnContact.isEnabled = true }, 1000)
            } catch (e: Exception) {
                android.util.Log.e("WaitingListAdapter", "Error in contact click: ${e.message}")
            }
        }

        holder.btnEnroll.setOnClickListener {
            try {
                holder.btnEnroll.isEnabled = false
                onEnrollClick(player)
                holder.btnEnroll.postDelayed({ holder.btnEnroll.isEnabled = true }, 1000)
            } catch (e: Exception) {
                android.util.Log.e("WaitingListAdapter", "Error in enroll click: ${e.message}")
            }
        }

        holder.btnReject.setOnClickListener {
            try {
                holder.btnReject.isEnabled = false
                onRejectClick(player)
                holder.btnReject.postDelayed({ holder.btnReject.isEnabled = true }, 1000)
            } catch (e: Exception) {
                android.util.Log.e("WaitingListAdapter", "Error in reject click: ${e.message}")
            }
        }
    }

    private fun setStatusColor(textView: TextView, status: String) {
        try {
            val statusColor = when (status) {
                "waiting" -> textView.context.getColor(R.color.warning_light)
                "contacted" -> textView.context.getColor(R.color.info_light)
                "enrolled" -> textView.context.getColor(R.color.success_light)
                "rejected" -> textView.context.getColor(R.color.error_light)
                else -> textView.context.getColor(R.color.text_secondary_light)
            }
            textView.setTextColor(statusColor)
        } catch (e: Exception) {
            android.util.Log.e("WaitingListAdapter", "Error setting status color: ${e.message}")
        }
    }

    private fun buildDetailsText(player: WaitingListPlayer): String {
        return try {
            val details = mutableListOf<String>()
            
            details.add("Age: ${player.age}")
            details.add("Phone: ${player.phoneNumber}")
            details.add("Branch: ${player.branch}")
            
            details.joinToString(" • ")
        } catch (e: Exception) {
            android.util.Log.e("WaitingListAdapter", "Error building details text: ${e.message}")
            "Error loading details"
        }
    }
}

// DiffUtil callback for efficient list updates
class WaitingListDiffCallback : DiffUtil.ItemCallback<WaitingListPlayer>() {
    override fun areItemsTheSame(oldItem: WaitingListPlayer, newItem: WaitingListPlayer): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: WaitingListPlayer, newItem: WaitingListPlayer): Boolean {
        return oldItem == newItem
    }
}
