package com.example.double_dot_demo.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.double_dot_demo.R
import com.example.double_dot_demo.databinding.ItemScheduleRowBinding
import com.example.double_dot_demo.dialogs.TraineeNamesDialog
import com.example.double_dot_demo.models.WeeklySchedule

class WeeklyScheduleAdapter(
    private val onCellClick: (String, String, List<String>) -> Unit
) : RecyclerView.Adapter<WeeklyScheduleAdapter.ScheduleRowViewHolder>() {

    private var scheduleData: Map<String, Map<String, List<String>>> = emptyMap()
    private var traineeNames: Map<String, String> = emptyMap()

    fun updateSchedule(schedule: Map<String, Map<String, List<String>>>, names: Map<String, String>) {
        android.util.Log.d("WeeklyScheduleAdapter", "updateSchedule called")
        android.util.Log.d("WeeklyScheduleAdapter", "Schedule data: $schedule")
        android.util.Log.d("WeeklyScheduleAdapter", "Trainee names: $names")
        
        scheduleData = schedule
        traineeNames = names
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ScheduleRowViewHolder {
        val binding = ItemScheduleRowBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ScheduleRowViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ScheduleRowViewHolder, position: Int) {
        val day = WeeklySchedule.DAYS_OF_WEEK[position]
        holder.bind(day, scheduleData[day] ?: emptyMap())
    }

    override fun getItemCount(): Int = WeeklySchedule.DAYS_OF_WEEK.size

    inner class ScheduleRowViewHolder(
        private val binding: ItemScheduleRowBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(day: String, daySchedule: Map<String, List<String>>) {
            binding.tvDayName.text = day
            android.util.Log.d("WeeklyScheduleAdapter", "Binding day: $day, daySchedule: $daySchedule")

            // Bind each time slot cell
            WeeklySchedule.TIME_SLOTS.forEach { timeSlot ->
                val traineeIds = daySchedule[timeSlot] ?: emptyList()
                val cell = getCellForTimeSlot(timeSlot)
                val traineeNames = traineeIds.mapNotNull { this@WeeklyScheduleAdapter.traineeNames[it] }
                
                android.util.Log.d("WeeklyScheduleAdapter", "Time slot $timeSlot: traineeIds=$traineeIds, names=$traineeNames")
                
                cell.text = if (traineeNames.isNotEmpty()) {
                    traineeNames.joinToString("\n")
                } else {
                    ""
                }
                
                // Set background based on whether cell has trainees
                cell.setBackgroundResource(
                    if (traineeNames.isNotEmpty()) R.drawable.schedule_cell_filled_background
                    else R.drawable.schedule_cell_background
                )
                
                // Set click listener
                cell.setOnClickListener {
                    onCellClick(day, timeSlot, traineeIds)
                }
                
                // Set long click listener to show trainee names in larger text
                cell.setOnLongClickListener {
                    val dialog = TraineeNamesDialog(cell.context, day, timeSlot, traineeNames)
                    dialog.show()
                    true // Consume the long click event
                }
            }
        }

        private fun getCellForTimeSlot(timeSlot: String): TextView {
            return when (timeSlot) {
                "٤" -> binding.cell4
                "٥" -> binding.cell5
                "٦" -> binding.cell6
                "٧" -> binding.cell7
                "٨" -> binding.cell8
                "٩" -> binding.cell9
                "١٠" -> binding.cell10
                else -> binding.cell4
            }
        }
    }
}
