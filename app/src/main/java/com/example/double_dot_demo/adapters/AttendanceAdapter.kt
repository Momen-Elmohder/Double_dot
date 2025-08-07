package com.example.double_dot_demo.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.double_dot_demo.R
import com.example.double_dot_demo.models.Trainee
import com.google.android.material.card.MaterialCardView
import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.*

class AttendanceAdapter(
    private val trainees: MutableList<Trainee>,
    private val onAttendanceUpdate: (Trainee, String, Boolean) -> Unit,
    private val onEditTrainee: (Trainee) -> Unit
) : RecyclerView.Adapter<AttendanceAdapter.AttendanceViewHolder>() {

    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    private val expandedPositions = mutableSetOf<Int>()

    inner class AttendanceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val cardView: MaterialCardView = itemView.findViewById(R.id.cardView)
        val tvName: TextView = itemView.findViewById(R.id.tvName)
        val tvRemainingSessions: TextView = itemView.findViewById(R.id.tvRemainingSessions)
        val tvStatus: TextView = itemView.findViewById(R.id.tvStatus)
        val tvPresentCount: TextView = itemView.findViewById(R.id.tvPresentCount)
        val tvAbsentCount: TextView = itemView.findViewById(R.id.tvAbsentCount)
        val tvTotalSessions: TextView = itemView.findViewById(R.id.tvTotalSessions)
        val expandedDetails: View = itemView.findViewById(R.id.expandedDetails)
        val tvDetailedPresentCount: TextView = itemView.findViewById(R.id.tvDetailedPresentCount)
        val tvDetailedAbsentCount: TextView = itemView.findViewById(R.id.tvDetailedAbsentCount)
        val rvSessionButtons: RecyclerView = itemView.findViewById(R.id.rvSessionButtons)
                 val btnUndo: TextView = itemView.findViewById(R.id.btnUndo)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AttendanceViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_attendance, parent, false)
        return AttendanceViewHolder(view)
    }

    override fun onBindViewHolder(holder: AttendanceViewHolder, position: Int) {
        try {
            val trainee = trainees.getOrNull(position) ?: return
            
            holder.tvName.text = trainee.name
            holder.tvRemainingSessions.text = "${trainee.remainingSessions} sessions remaining"
            holder.tvStatus.text = (trainee.status ?: "unknown").replaceFirstChar { it.uppercase() }
            
            // Calculate attendance stats
            val (presentCount, absentCount) = calculateAttendanceStats(trainee)
            holder.tvPresentCount.text = presentCount.toString()
            holder.tvAbsentCount.text = absentCount.toString()
            holder.tvTotalSessions.text = trainee.totalSessions.toString()
            
            // Update card background based on completion
            updateCardBackground(holder, trainee, presentCount, absentCount)
            
            // Set status color
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
            
            // Show expanded details if position is expanded
            if (expandedPositions.contains(position)) {
                holder.expandedDetails.visibility = View.VISIBLE
                setupDetailedAttendance(holder, trainee, presentCount, absentCount)
            } else {
                holder.expandedDetails.visibility = View.GONE
            }
            
            // Setup long press for detailed attendance editing
            holder.cardView.setOnLongClickListener {
                toggleExpanded(position)
                true
            }
            
                         // Setup undo button
             val totalCompleted = presentCount + absentCount
             
             android.util.Log.d("AttendanceAdapter", "Trainee: ${trainee.name}, Present: $presentCount, Absent: $absentCount, Total: $totalCompleted")
             
             // Show undo button only if there are completed sessions
             if (totalCompleted > 0) {
                 holder.btnUndo.visibility = View.VISIBLE
                 android.util.Log.d("AttendanceAdapter", "Showing undo button for ${trainee.name} - Setting visibility to VISIBLE")
                 
                 // Force a layout update
                 holder.btnUndo.requestLayout()
                 
                 holder.btnUndo.setOnClickListener {
                     // Find the last completed session and remove it
                     val lastCompletedSession = findLastCompletedSession(trainee)
                     if (lastCompletedSession != null) {
                         android.util.Log.d("AttendanceAdapter", "Undoing session: $lastCompletedSession")
                         // Call the update with false to indicate removal
                         onAttendanceUpdate(trainee, lastCompletedSession, false)
                     }
                 }
             } else {
                 holder.btnUndo.visibility = View.GONE
                 android.util.Log.d("AttendanceAdapter", "Hiding undo button for ${trainee.name} - no completed sessions")
             }
            
        } catch (e: Exception) {
            android.util.Log.e("AttendanceAdapter", "Error binding view holder: ${e.message}")
        }
    }

    override fun getItemCount(): Int = trainees.size

    private fun calculateAttendanceStats(trainee: Trainee): Pair<Int, Int> {
        return try {
            var presentCount = 0
            var absentCount = 0
            
            trainee.attendanceSessions.forEach { (_, isPresent) ->
                if (isPresent) presentCount++ else absentCount++
            }
            
            Pair(presentCount, absentCount)
        } catch (e: Exception) {
            android.util.Log.e("AttendanceAdapter", "Error calculating stats: ${e.message}")
            Pair(0, 0)
        }
    }

    private fun updateCardBackground(holder: AttendanceViewHolder, trainee: Trainee, presentCount: Int, absentCount: Int) {
        try {
            val totalCompleted = presentCount + absentCount
            val remainingSessions = trainee.totalSessions - totalCompleted
            
            val backgroundColor = when {
                totalCompleted >= trainee.totalSessions -> {
                    holder.itemView.context.getColor(R.color.error_light) // Red background when completed
                }
                remainingSessions == 1 -> {
                    holder.itemView.context.getColor(R.color.warning_light) // Yellow background when 1 session left
                }
                else -> {
                    holder.itemView.context.getColor(R.color.surface_light) // Normal background
                }
            }
            
            holder.cardView.setCardBackgroundColor(backgroundColor)
        } catch (e: Exception) {
            android.util.Log.e("AttendanceAdapter", "Error updating background: ${e.message}")
        }
    }

    private fun setupDetailedAttendance(holder: AttendanceViewHolder, trainee: Trainee, presentCount: Int, absentCount: Int) {
        try {
            // Update detailed counters
            holder.tvDetailedPresentCount.text = presentCount.toString()
            holder.tvDetailedAbsentCount.text = absentCount.toString()
            
            // Show session buttons as simple text display
            val sessionButtonsText = buildString {
                for (i in 1..trainee.totalSessions) {
                    val sessionId = "session_$i"
                    val isPresent = trainee.attendanceSessions[sessionId]
                    when (isPresent) {
                        true -> append("🟢") // Green circle for present
                        false -> append("🔴") // Red circle for absent
                        null -> append("⚪") // White circle for not completed
                    }
                    if (i < trainee.totalSessions) append(" ")
                }
            }
            
            // Create a simple TextView to show session status
            val sessionStatusView = android.widget.TextView(holder.itemView.context).apply {
                text = sessionButtonsText
                textSize = 16f
                setPadding(16, 8, 16, 8)
                gravity = android.view.Gravity.CENTER
            }
            
            // Clear existing views and add the new one
            holder.rvSessionButtons.removeAllViews()
            holder.rvSessionButtons.addView(sessionStatusView)
            
        } catch (e: Exception) {
            android.util.Log.e("AttendanceAdapter", "Error setting up detailed attendance: ${e.message}")
        }
    }

    fun toggleExpanded(position: Int) {
        if (expandedPositions.contains(position)) {
            expandedPositions.remove(position)
        } else {
            expandedPositions.add(position)
        }
        notifyItemChanged(position)
    }

    // Swipe callback for attendance tracking
    inner class AttendanceSwipeCallback : ItemTouchHelper.SimpleCallback(
        0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
    ) {
        override fun onChildDraw(
            c: android.graphics.Canvas,
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            dX: Float,
            dY: Float,
            actionState: Int,
            isCurrentlyActive: Boolean
        ) {
            super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
            
            // Add visual feedback for swipe direction
            val itemView = viewHolder.itemView
            val background = android.graphics.drawable.ColorDrawable()
            
            when {
                dX > 0 -> {
                    // Swiping right (present) - green background
                    background.color = itemView.context.getColor(R.color.success_light)
                    background.setBounds(itemView.left, itemView.top, itemView.left + dX.toInt(), itemView.bottom)
                    background.draw(c)
                }
                dX < 0 -> {
                    // Swiping left (absent) - red background
                    background.color = itemView.context.getColor(R.color.error_light)
                    background.setBounds(itemView.right + dX.toInt(), itemView.top, itemView.right, itemView.bottom)
                    background.draw(c)
                }
            }
        }
        override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
            return false
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
            try {
                val position = viewHolder.adapterPosition
                if (position == RecyclerView.NO_POSITION) return
                
                val trainee = trainees.getOrNull(position) ?: return
                
                // Check if all sessions are completed
                val (presentCount, absentCount) = calculateAttendanceStats(trainee)
                val totalCompleted = presentCount + absentCount
                
                if (totalCompleted >= trainee.totalSessions) {
                    // Block further swiping - restore the item
                    notifyItemChanged(position)
                    return
                }
                
                // Find the next available session
                val nextSessionId = findNextAvailableSession(trainee)
                if (nextSessionId != null) {
                    val isPresent = direction == ItemTouchHelper.RIGHT
                    android.util.Log.d("AttendanceAdapter", "Swipe detected - Direction: $direction, Session: $nextSessionId, IsPresent: $isPresent")
                    onAttendanceUpdate(trainee, nextSessionId, isPresent)
                } else {
                    android.util.Log.d("AttendanceAdapter", "No available sessions to mark")
                }
                
                // Don't call notifyItemChanged here - let the fragment handle the UI update
                
            } catch (e: Exception) {
                android.util.Log.e("AttendanceAdapter", "Error handling swipe: ${e.message}")
            }
        }

        private fun findNextAvailableSession(trainee: Trainee): String? {
            return try {
                for (i in 1..trainee.totalSessions) {
                    val sessionId = "session_$i"
                    if (!trainee.attendanceSessions.containsKey(sessionId)) {
                        return sessionId
                    }
                }
                null
            } catch (e: Exception) {
                android.util.Log.e("AttendanceAdapter", "Error finding next session: ${e.message}")
                null
            }
        }
    }
    
    private fun findLastCompletedSession(trainee: Trainee): String? {
        return try {
            for (i in trainee.totalSessions downTo 1) {
                val sessionId = "session_$i"
                if (trainee.attendanceSessions.containsKey(sessionId)) {
                    return sessionId
                }
            }
            null
        } catch (e: Exception) {
            android.util.Log.e("AttendanceAdapter", "Error finding last completed session: ${e.message}")
            null
        }
    }
} 