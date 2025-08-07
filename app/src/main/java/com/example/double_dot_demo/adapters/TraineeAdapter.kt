package com.example.double_dot_demo.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.double_dot_demo.R
import com.example.double_dot_demo.models.Trainee
import java.text.SimpleDateFormat
import java.util.*

class TraineeAdapter(
    private val trainees: List<Trainee>,
    private val isCoachView: Boolean = false,
    private val onEditClick: (Trainee) -> Unit,
    private val onDeleteClick: (Trainee) -> Unit,
    private val onRenewClick: (Trainee) -> Unit,
    private val onFreezeClick: (Trainee) -> Unit,
    private val onShowDetailsClick: (Trainee) -> Unit = {}
) : RecyclerView.Adapter<TraineeAdapter.TraineeViewHolder>() {

    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    private val expandedPositions = mutableSetOf<Int>()

    class TraineeViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvName: TextView = itemView.findViewById(R.id.tvName)
        val tvDetails: TextView = itemView.findViewById(R.id.tvDetails)
        val tvStatus: TextView = itemView.findViewById(R.id.tvStatus)
        val tvPaymentStatus: TextView = itemView.findViewById(R.id.tvPaymentStatus)
        val btnEdit: ImageView = itemView.findViewById(R.id.btnEdit)
        val btnDelete: ImageView = itemView.findViewById(R.id.btnDelete)
        val btnShowDetails: ImageView = itemView.findViewById(R.id.btnShowDetails)
        val btnRenew: ImageView = itemView.findViewById(R.id.btnRenew)
        val btnFreeze: ImageView = itemView.findViewById(R.id.btnFreeze)
        val cardBackground: View = itemView.findViewById(R.id.cardBackground)
        val actionButtonsContainer: View = itemView.findViewById(R.id.actionButtonsContainer)
        
        // Expanded details views
        val expandedDetails: View = itemView.findViewById(R.id.expandedDetails)
        val tvExpandedAge: TextView = itemView.findViewById(R.id.tvExpandedAge)
        val tvExpandedPhone: TextView = itemView.findViewById(R.id.tvExpandedPhone)
        val tvExpandedBranch: TextView = itemView.findViewById(R.id.tvExpandedBranch)
        val tvExpandedCoach: TextView = itemView.findViewById(R.id.tvExpandedCoach)
        val tvExpandedFee: TextView = itemView.findViewById(R.id.tvExpandedFee)
        val tvExpandedSessions: TextView = itemView.findViewById(R.id.tvExpandedSessions)
        val tvExpandedLastRenewal: TextView = itemView.findViewById(R.id.tvExpandedLastRenewal)
        val tvExpandedCreated: TextView = itemView.findViewById(R.id.tvExpandedCreated)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TraineeViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_trainee, parent, false)
        return TraineeViewHolder(view)
    }

    override fun onBindViewHolder(holder: TraineeViewHolder, position: Int) {
        try {
            val trainee = trainees.getOrNull(position) ?: return
            
            // Set basic info
            holder.tvName.text = trainee.name ?: "Unknown"
            
            if (isCoachView) {
                android.util.Log.d("TraineeAdapter", "Rendering coach view for trainee: ${trainee.name}")
                // Simplified coach view - only name, age, coach, branch, status
                holder.tvDetails.text = "Age: ${trainee.age} • Coach: ${trainee.coachName} • Branch: ${trainee.branch}"
                
                // Show status but hide payment status
                holder.tvStatus.text = (trainee.status ?: "unknown").replaceFirstChar { it.uppercase() }
                holder.tvStatus.visibility = View.VISIBLE
                holder.tvPaymentStatus.visibility = View.GONE
                
                // Set status color safely
                try {
                    val statusColor = when (trainee.status) {
                        "active" -> holder.itemView.context.getColor(R.color.success_light)
                        "frozen" -> holder.itemView.context.getColor(R.color.error_light)
                        else -> holder.itemView.context.getColor(R.color.text_secondary_light)
                    }
                    holder.tvStatus.setTextColor(statusColor)
                } catch (e: Exception) {
                    // Use default color if error
                }
                
                // Hide entire action buttons container
                holder.actionButtonsContainer.visibility = View.GONE
                
                // Hide expanded details
                holder.expandedDetails.visibility = View.GONE
                
                // Remove click listeners for coach view
                holder.cardBackground.setOnClickListener(null)
            } else {
                android.util.Log.d("TraineeAdapter", "Rendering full view for trainee: ${trainee.name}")
                // Full view for head coaches and admins
                holder.tvDetails.text = buildDetailsText(trainee)
                holder.tvStatus.text = (trainee.status ?: "unknown").replaceFirstChar { it.uppercase() }
                holder.tvPaymentStatus.text = if (trainee.isPaid) "Paid" else "Unpaid"
                
                // Set status color safely
                try {
                    val statusColor = when (trainee.status) {
                        "active" -> holder.itemView.context.getColor(R.color.success_light)
                        "frozen" -> holder.itemView.context.getColor(R.color.error_light)
                        else -> holder.itemView.context.getColor(R.color.text_secondary_light)
                    }
                    holder.tvStatus.setTextColor(statusColor)
                } catch (e: Exception) {
                    // Use default color if error
                }
                
                // Set payment status color safely
                try {
                    val paymentColor = if (trainee.isPaid) {
                        holder.itemView.context.getColor(R.color.success_light)
                    } else {
                        holder.itemView.context.getColor(R.color.error_light)
                    }
                    holder.tvPaymentStatus.setTextColor(paymentColor)
                } catch (e: Exception) {
                    // Use default color if error
                }

                // Show/hide buttons based on sessions
                holder.btnRenew.visibility = View.VISIBLE

                // Show freeze button for active/frozen trainees
                holder.btnFreeze.visibility = View.VISIBLE

                // Show all buttons for admin/head coach view
                holder.btnEdit.visibility = View.VISIBLE
                holder.btnDelete.visibility = View.VISIBLE
                holder.btnShowDetails.visibility = View.VISIBLE
                
                // Setup button click listeners with error handling
                setupClickListeners(holder, trainee)

                // Show expanded details if position is expanded
                if (expandedPositions.contains(position)) {
                    holder.expandedDetails.visibility = View.VISIBLE
                    populateExpandedDetails(holder, trainee)
                } else {
                    holder.expandedDetails.visibility = View.GONE
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("TraineeAdapter", "Error binding view holder: ${e.message}")
        }
    }

    private fun setupClickListeners(holder: TraineeViewHolder, trainee: Trainee) {
        // Card click for expand/collapse
        holder.cardBackground.setOnClickListener {
            try {
                toggleExpanded(holder.adapterPosition)
            } catch (e: Exception) {
                android.util.Log.e("TraineeAdapter", "Error toggling expanded state: ${e.message}")
            }
        }

        // Button click listeners
        holder.btnEdit.setOnClickListener {
            try {
                onEditClick(trainee)
            } catch (e: Exception) {
                android.util.Log.e("TraineeAdapter", "Error in edit click: ${e.message}")
            }
        }

        holder.btnDelete.setOnClickListener {
            try {
                onDeleteClick(trainee)
            } catch (e: Exception) {
                android.util.Log.e("TraineeAdapter", "Error in delete click: ${e.message}")
            }
        }

        holder.btnRenew.setOnClickListener {
            try {
                onRenewClick(trainee)
            } catch (e: Exception) {
                android.util.Log.e("TraineeAdapter", "Error in renew click: ${e.message}")
            }
        }

        holder.btnFreeze.setOnClickListener {
            try {
                onFreezeClick(trainee)
            } catch (e: Exception) {
                android.util.Log.e("TraineeAdapter", "Error in freeze click: ${e.message}")
            }
        }

        holder.btnShowDetails.setOnClickListener {
            try {
                onShowDetailsClick(trainee)
            } catch (e: Exception) {
                android.util.Log.e("TraineeAdapter", "Error in show details click: ${e.message}")
            }
        }
    }

    private fun toggleExpanded(position: Int) {
        if (position == RecyclerView.NO_POSITION) return
        
        if (expandedPositions.contains(position)) {
            expandedPositions.remove(position)
        } else {
            expandedPositions.add(position)
        }
        notifyItemChanged(position)
    }

    private fun populateExpandedDetails(holder: TraineeViewHolder, trainee: Trainee) {
        try {
            holder.tvExpandedAge.text = "Age: ${trainee.age}"
            holder.tvExpandedPhone.text = "Phone: ${trainee.phoneNumber ?: "N/A"}"
            holder.tvExpandedBranch.text = "Branch: ${trainee.branch ?: "N/A"}"
            holder.tvExpandedCoach.text = "Coach: ${trainee.coachName ?: "N/A"}"
            holder.tvExpandedFee.text = "Monthly Fee: $${trainee.monthlyFee}"
            holder.tvExpandedSessions.text = "Sessions: ${trainee.remainingSessions}/${trainee.totalSessions}"
            
            // Safely handle the lastRenewalDate
            val lastRenewalText = try {
                trainee.lastRenewalDate?.let { renewalDate ->
                    dateFormat.format(renewalDate.toDate())
                } ?: "Never"
            } catch (e: Exception) {
                "Unknown"
            }
            holder.tvExpandedLastRenewal.text = "Last Renewal: $lastRenewalText"
            
            // Safely handle the createdAt date
            val createdDate = try {
                trainee.createdAt?.toDate()?.let { dateFormat.format(it) } ?: "Unknown"
            } catch (e: Exception) {
                "Unknown"
            }
            holder.tvExpandedCreated.text = "Created: $createdDate"
        } catch (e: Exception) {
            android.util.Log.e("TraineeAdapter", "Error populating expanded details: ${e.message}")
        }
    }

    override fun getItemCount(): Int = trainees.size

    private fun buildDetailsText(trainee: Trainee): String {
        return try {
            val details = mutableListOf<String>()
            
            details.add("Age: ${trainee.age}")
            details.add("Phone: ${trainee.phoneNumber ?: "N/A"}")
            details.add("Branch: ${trainee.branch ?: "N/A"}")
            details.add("Coach: ${trainee.coachName ?: "N/A"}")
            details.add("Fee: $${trainee.monthlyFee}")
            details.add("Sessions: ${trainee.remainingSessions}/${trainee.totalSessions}")
            
            // Safely handle the lastRenewalDate
            trainee.lastRenewalDate?.let { renewalDate ->
                try {
                    val formattedDate = dateFormat.format(renewalDate.toDate())
                    details.add("Last Renewal: $formattedDate")
                } catch (e: Exception) {
                    // Skip this detail if date conversion fails
                }
            }
            
            details.joinToString(" • ")
        } catch (e: Exception) {
            android.util.Log.e("TraineeAdapter", "Error building details text: ${e.message}")
            "Error loading details"
        }
    }

    fun updateCoachView(isCoach: Boolean) {
        android.util.Log.d("TraineeAdapter", "Updating coach view: $isCoach")
        // This function is no longer needed as isCoachView is passed to the constructor
    }
} 