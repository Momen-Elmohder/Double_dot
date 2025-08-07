package com.example.double_dot_demo.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.example.double_dot_demo.R
import com.example.double_dot_demo.models.Employee
import com.google.android.material.card.MaterialCardView
import java.text.SimpleDateFormat
import java.util.*

class CoachAttendanceAdapter(
    private val coaches: List<Employee>,
    private val onAttendanceUpdated: (Employee, Boolean) -> Unit,
    private val onUndoAttendance: (Employee) -> Unit
) : RecyclerView.Adapter<CoachAttendanceAdapter.CoachAttendanceViewHolder>() {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    inner class CoachAttendanceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val cardView: MaterialCardView = itemView.findViewById(R.id.cardView)
        val tvCoachName: TextView = itemView.findViewById(R.id.tvCoachName)
        val tvBranch: TextView = itemView.findViewById(R.id.tvBranch)
        val tvTotalDays: TextView = itemView.findViewById(R.id.tvTotalDays)
        val tvPresentCount: TextView = itemView.findViewById(R.id.tvPresentCount)
        val tvAbsentCount: TextView = itemView.findViewById(R.id.tvAbsentCount)
        val btnUndo: TextView = itemView.findViewById(R.id.btnUndo)
        val tvTodayStatus: TextView = itemView.findViewById(R.id.tvTodayStatus)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CoachAttendanceViewHolder {
        try {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_coach_attendance, parent, false)
            return CoachAttendanceViewHolder(view)
        } catch (e: Exception) {
            android.util.Log.e("CoachAttendanceAdapter", "Error creating view holder: ${e.message}")
            throw e
        }
    }

    override fun onBindViewHolder(holder: CoachAttendanceViewHolder, position: Int) {
        try {
            val coach = coaches[position]
            
            holder.tvCoachName.text = coach.name
            holder.tvBranch.text = "Branch: ${coach.branch}"
            holder.tvTotalDays.text = "Total Days: ${coach.totalDays}"
            
            // Calculate attendance stats
            val (presentCount, absentCount) = calculateAttendanceStats(coach)
            holder.tvPresentCount.text = "Present: $presentCount"
            holder.tvAbsentCount.text = "Absent: $absentCount"
            
            // Check most recent attendance (instead of just today's)
            val mostRecentAttendance = getMostRecentAttendance(coach)
            when (mostRecentAttendance) {
                true -> {
                    holder.tvTodayStatus.text = "Last: PRESENT"
                    holder.tvTodayStatus.setTextColor(holder.itemView.context.getColor(R.color.success_light))
                    holder.btnUndo.visibility = View.VISIBLE
                }
                false -> {
                    holder.tvTodayStatus.text = "Last: ABSENT"
                    holder.tvTodayStatus.setTextColor(holder.itemView.context.getColor(R.color.error_light))
                    holder.btnUndo.visibility = View.VISIBLE
                }
                else -> {
                    holder.tvTodayStatus.text = "No attendance marked"
                    holder.tvTodayStatus.setTextColor(holder.itemView.context.getColor(R.color.text_secondary_light))
                    holder.btnUndo.visibility = View.GONE
                }
            }
            
            // Setup undo button
            holder.btnUndo.setOnClickListener {
                onUndoAttendance(coach)
            }
            
            // Check if attendance is complete
            val totalMarkedDays = presentCount + absentCount
            if (totalMarkedDays >= coach.totalDays) {
                holder.cardView.alpha = 0.6f
                holder.tvTodayStatus.text = "ATTENDANCE COMPLETE"
                holder.tvTodayStatus.setTextColor(holder.itemView.context.getColor(R.color.text_secondary_light))
            } else {
                holder.cardView.alpha = 1.0f
            }
        } catch (e: Exception) {
            android.util.Log.e("CoachAttendanceAdapter", "Error binding view holder: ${e.message}")
        }
    }

    override fun getItemCount(): Int {
        return try {
            coaches.size
        } catch (e: Exception) {
            android.util.Log.e("CoachAttendanceAdapter", "Error getting item count: ${e.message}")
            0
        }
    }

    private fun calculateAttendanceStats(coach: Employee): Pair<Int, Int> {
        return try {
            var presentCount = 0
            var absentCount = 0
            
            coach.attendanceDays.forEach { (_, isPresent) ->
                if (isPresent) presentCount++ else absentCount++
            }
            
            Pair(presentCount, absentCount)
        } catch (e: Exception) {
            android.util.Log.e("CoachAttendanceAdapter", "Error calculating attendance stats: ${e.message}")
            Pair(0, 0)
        }
    }

    private fun getMostRecentAttendance(coach: Employee): Boolean? {
        return try {
            val sortedAttendanceDays = coach.attendanceDays.entries.sortedByDescending { it.key }
            sortedAttendanceDays.firstOrNull()?.value
        } catch (e: Exception) {
            android.util.Log.e("CoachAttendanceAdapter", "Error getting most recent attendance: ${e.message}")
            null
        }
    }

    fun setupSwipeCallback(recyclerView: RecyclerView) {
        try {
            val swipeCallback = object : ItemTouchHelper.SimpleCallback(
                0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
            ) {
                override fun onMove(
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder,
                    target: RecyclerView.ViewHolder
                ): Boolean = false

                override fun onChildDraw(
                    c: android.graphics.Canvas,
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder,
                    dX: Float,
                    dY: Float,
                    actionState: Int,
                    isCurrentlyActive: Boolean
                ) {
                    try {
                        if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
                            val itemView = viewHolder.itemView
                            val background = android.graphics.drawable.ColorDrawable()
                            
                            // Set background color based on swipe direction
                            if (dX > 0) {
                                // Swiping right (present) - green background
                                background.color = android.graphics.Color.parseColor("#4CAF50")
                            } else {
                                // Swiping left (absent) - red background
                                background.color = android.graphics.Color.parseColor("#F44336")
                            }
                            
                            background.setBounds(
                                itemView.left,
                                itemView.top,
                                itemView.right,
                                itemView.bottom
                            )
                            background.draw(c)
                        }
                        
                        super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
                    } catch (e: Exception) {
                        android.util.Log.e("CoachAttendanceAdapter", "Error in onChildDraw: ${e.message}")
                        super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
                    }
                }

                override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                    try {
                        val position = viewHolder.adapterPosition
                        if (position != RecyclerView.NO_POSITION && position < coaches.size) {
                            val coach = coaches[position]
                            
                            // Check if attendance is already complete
                            val (presentCount, absentCount) = calculateAttendanceStats(coach)
                            val totalMarkedDays = presentCount + absentCount
                            
                            if (totalMarkedDays >= coach.totalDays) {
                                android.widget.Toast.makeText(
                                    recyclerView.context,
                                    "Attendance already complete for ${coach.name}",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                                return
                            }
                            
                            // Mark attendance based on swipe direction
                            val isPresent = direction == ItemTouchHelper.RIGHT
                            onAttendanceUpdated(coach, isPresent)
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("CoachAttendanceAdapter", "Error handling swipe: ${e.message}")
                    }
                }
            }
            
            ItemTouchHelper(swipeCallback).attachToRecyclerView(recyclerView)
        } catch (e: Exception) {
            android.util.Log.e("CoachAttendanceAdapter", "Error setting up swipe callback: ${e.message}")
        }
    }
} 