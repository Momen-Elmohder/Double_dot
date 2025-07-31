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
    private val onEditClick: (Trainee) -> Unit,
    private val onDeleteClick: (Trainee) -> Unit,
    private val onTogglePayment: (Trainee) -> Unit,
    private val onRenewClick: (Trainee) -> Unit,
    private val isCoachView: Boolean = false
) : RecyclerView.Adapter<TraineeAdapter.TraineeViewHolder>() {

    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    class TraineeViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvName: TextView = itemView.findViewById(R.id.tvName)
        val tvDetails: TextView = itemView.findViewById(R.id.tvDetails)
        val tvStatus: TextView = itemView.findViewById(R.id.tvStatus)
        val tvPaymentStatus: TextView = itemView.findViewById(R.id.tvPaymentStatus)
        val btnEdit: ImageView = itemView.findViewById(R.id.btnEdit)
        val btnDelete: ImageView = itemView.findViewById(R.id.btnDelete)
        val btnTogglePayment: ImageView = itemView.findViewById(R.id.btnTogglePayment)
        val btnRenew: ImageView = itemView.findViewById(R.id.btnRenew)
        val cardBackground: View = itemView.findViewById(R.id.cardBackground)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TraineeViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_trainee, parent, false)
        return TraineeViewHolder(view)
    }

    override fun onBindViewHolder(holder: TraineeViewHolder, position: Int) {
        val trainee = trainees[position]
        
        // Check if trainee is expired
        val isExpired = isTraineeExpired(trainee)
        
        holder.tvName.text = trainee.name
        
        if (isCoachView) {
            // Coach view - only show name and age
            holder.tvDetails.text = "Age: ${trainee.age}"
            holder.tvStatus.visibility = View.GONE
            holder.tvPaymentStatus.visibility = View.GONE
            holder.btnEdit.visibility = View.GONE
            holder.btnDelete.visibility = View.GONE
            holder.btnTogglePayment.visibility = View.GONE
            holder.btnRenew.visibility = View.GONE
        } else {
            // Full view for head coaches and admins
            holder.tvDetails.text = buildDetailsText(trainee)
            holder.tvStatus.text = trainee.status.replaceFirstChar { it.uppercase() }
            holder.tvPaymentStatus.text = if (trainee.isPaid) "Paid" else "Unpaid"
            
            // Set status color
            val statusColor = if (trainee.status == "active") {
                holder.itemView.context.getColor(R.color.success_light)
            } else {
                holder.itemView.context.getColor(R.color.error_light)
            }
            holder.tvStatus.setTextColor(statusColor)
            
            // Set payment status color
            val paymentColor = if (trainee.isPaid) {
                holder.itemView.context.getColor(R.color.success_light)
            } else {
                holder.itemView.context.getColor(R.color.error_light)
            }
            holder.tvPaymentStatus.setTextColor(paymentColor)

            // Show/hide renew button based on expiration
            if (isExpired) {
                holder.btnRenew.visibility = View.VISIBLE
                holder.btnRenew.setOnClickListener {
                    onRenewClick(trainee)
                }
            } else {
                holder.btnRenew.visibility = View.GONE
            }

            holder.btnEdit.setOnClickListener {
                onEditClick(trainee)
            }

            holder.btnDelete.setOnClickListener {
                onDeleteClick(trainee)
            }

            holder.btnTogglePayment.setOnClickListener {
                onTogglePayment(trainee)
            }
        }
        
        // Set card background color based on expiration
        if (isExpired) {
            holder.cardBackground.setBackgroundColor(holder.itemView.context.getColor(R.color.error_light))
        } else {
            holder.cardBackground.setBackgroundColor(holder.itemView.context.getColor(R.color.background_light))
        }
    }

    override fun getItemCount(): Int = trainees.size

    private fun isTraineeExpired(trainee: Trainee): Boolean {
        return trainee.endingDate?.let { endDate ->
            endDate.toDate().before(Date())
        } ?: false
    }

    private fun buildDetailsText(trainee: Trainee): String {
        val details = mutableListOf<String>()
        
        details.add("Age: ${trainee.age}")
        details.add("Coach: ${trainee.coachName}")
        details.add("Fee: $${trainee.monthlyFee}")
        
        trainee.startingDate?.let {
            details.add("Started: ${dateFormat.format(it.toDate())}")
        }
        
        trainee.endingDate?.let {
            details.add("Ends: ${dateFormat.format(it.toDate())}")
        }
        
        return details.joinToString(" • ")
    }
} 