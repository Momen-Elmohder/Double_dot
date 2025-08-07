package com.example.double_dot_demo.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.double_dot_demo.R
import com.example.double_dot_demo.models.Employee
import com.example.double_dot_demo.models.Trainee
import com.google.android.material.card.MaterialCardView
import java.text.NumberFormat
import java.util.*

class SalaryAdapter(
    private val coaches: List<Employee>,
    private val trainees: List<Trainee>
) : RecyclerView.Adapter<SalaryAdapter.SalaryViewHolder>() {

    private val numberFormat = NumberFormat.getCurrencyInstance(Locale.US)

    inner class SalaryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val cardView: MaterialCardView = itemView.findViewById(R.id.cardView)
        val tvCoachName: TextView = itemView.findViewById(R.id.tvCoachName)
        val tvTotalPayments: TextView = itemView.findViewById(R.id.tvTotalPayments)
        val tvBaseSalary: TextView = itemView.findViewById(R.id.tvBaseSalary)
        val tvAbsencePercent: TextView = itemView.findViewById(R.id.tvAbsencePercent)
        val tvDeduction: TextView = itemView.findViewById(R.id.tvDeduction)
        val tvFinalSalary: TextView = itemView.findViewById(R.id.tvFinalSalary)
        val tvTraineeCount: TextView = itemView.findViewById(R.id.tvTraineeCount)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SalaryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_salary, parent, false)
        return SalaryViewHolder(view)
    }

    override fun onBindViewHolder(holder: SalaryViewHolder, position: Int) {
        try {
            val coach = coaches.getOrNull(position) ?: return
            
            // Calculate salary information
            val salaryInfo = calculateSalaryInfo(coach)
            
            android.util.Log.d("SalaryAdapter", "Binding coach ${coach.name}: total payments: $${String.format("%.2f", salaryInfo.totalPayments)}, base salary: $${String.format("%.2f", salaryInfo.baseSalary)}, final salary: $${String.format("%.2f", salaryInfo.finalSalary)}")
            
            holder.tvCoachName.text = coach.name
            holder.tvTotalPayments.text = numberFormat.format(salaryInfo.totalPayments)
            holder.tvBaseSalary.text = numberFormat.format(salaryInfo.baseSalary)
            holder.tvAbsencePercent.text = "${String.format("%.1f", salaryInfo.absencePercent)}%"
            holder.tvDeduction.text = numberFormat.format(salaryInfo.deduction)
            holder.tvFinalSalary.text = numberFormat.format(salaryInfo.finalSalary)
            holder.tvTraineeCount.text = "${salaryInfo.traineeCount} trainees"
            
            // Set color for absence percentage
            val absenceColor = when {
                salaryInfo.absencePercent <= 5 -> holder.itemView.context.getColor(R.color.success_light)
                salaryInfo.absencePercent <= 15 -> holder.itemView.context.getColor(R.color.warning_light)
                else -> holder.itemView.context.getColor(R.color.error_light)
            }
            holder.tvAbsencePercent.setTextColor(absenceColor)
            
            // Set color for final salary
            val salaryColor = when {
                salaryInfo.finalSalary > 0 -> holder.itemView.context.getColor(R.color.success_light)
                else -> holder.itemView.context.getColor(R.color.error_light)
            }
            holder.tvFinalSalary.setTextColor(salaryColor)
            
        } catch (e: Exception) {
            android.util.Log.e("SalaryAdapter", "Error binding view holder: ${e.message}")
        }
    }

    override fun getItemCount(): Int = coaches.size

    private fun calculateSalaryInfo(coach: Employee): SalaryInfo {
        try {
            // Find all trainees assigned to this coach
            val coachTrainees = trainees.filter { it.coachId == coach.id }
            
            android.util.Log.d("SalaryAdapter", "Calculating salary for ${coach.name} (ID: ${coach.id}): ${coachTrainees.size} trainees")
            
            // Debug: Log all trainees and their coach IDs
            trainees.forEach { trainee ->
                android.util.Log.d("SalaryAdapter", "Trainee: ${trainee.name}, Coach ID: ${trainee.coachId}, Coach Name: ${trainee.coachName}, Payment: $${String.format("%.2f", trainee.paymentAmount)}")
            }
            
            // Calculate total payments from trainees
            val totalPayments = coachTrainees.sumOf { it.paymentAmount }
            
            // Calculate base salary (40% of total payments)
            val baseSalary = totalPayments * 0.4
            
            // Calculate attendance stats
            val (presentCount, absentCount) = calculateAttendanceStats(coach)
            val totalDays = presentCount + absentCount
            
            // Calculate absence percentage
            val absencePercent = if (totalDays > 0) {
                (absentCount.toDouble() / totalDays.toDouble()) * 100.0
            } else {
                0.0
            }
            
            // Calculate deduction
            val deduction = baseSalary * (absencePercent / 100.0)
            
            // Calculate final salary
            val finalSalary = baseSalary - deduction
            
            android.util.Log.d("SalaryAdapter", "Salary calculation for ${coach.name}: total payments: $${String.format("%.2f", totalPayments)}, base salary: $${String.format("%.2f", baseSalary)}, absence: ${String.format("%.1f", absencePercent)}%, deduction: $${String.format("%.2f", deduction)}, final: $${String.format("%.2f", finalSalary)}")
            
            return SalaryInfo(
                totalPayments = totalPayments,
                baseSalary = baseSalary,
                absencePercent = absencePercent,
                deduction = deduction,
                finalSalary = finalSalary,
                traineeCount = coachTrainees.size
            )
            
        } catch (e: Exception) {
            android.util.Log.e("SalaryAdapter", "Error calculating salary info: ${e.message}")
            return SalaryInfo()
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
            android.util.Log.e("SalaryAdapter", "Error calculating attendance stats: ${e.message}")
            Pair(0, 0)
        }
    }

    data class SalaryInfo(
        val totalPayments: Double = 0.0,
        val baseSalary: Double = 0.0,
        val absencePercent: Double = 0.0,
        val deduction: Double = 0.0,
        val finalSalary: Double = 0.0,
        val traineeCount: Int = 0
    )
} 