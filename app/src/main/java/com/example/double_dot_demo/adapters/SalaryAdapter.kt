package com.example.double_dot_demo.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.double_dot_demo.R
import com.example.double_dot_demo.dialogs.SalaryDetailsDialog
import com.example.double_dot_demo.models.Salary
import com.google.android.material.card.MaterialCardView
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

class SalaryAdapter(
    private val salaries: List<Salary>
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
            val salary = salaries.getOrNull(position) ?: return
            
            android.util.Log.d("SalaryAdapter", "Binding salary for ${salary.employeeName}: final salary: $${String.format("%.2f", salary.finalSalary)}")
            
            // Basic info
            holder.tvCoachName.text = "${salary.employeeName} - ${getMonthDisplayName(salary.month)}"
            holder.tvTotalPayments.text = numberFormat.format(salary.totalPayments)
            // Show total income (100% of trainee fees) instead of base salary
            val totalIncome = salary.totalPayments / 0.4 // Convert 40% back to 100%
            holder.tvBaseSalary.text = numberFormat.format(totalIncome)
            holder.tvAbsencePercent.text = "${String.format("%.1f", salary.absencePercentage)}%"
            holder.tvDeduction.text = numberFormat.format(salary.deductionAmount)
            holder.tvFinalSalary.text = numberFormat.format(salary.finalSalary)
            holder.tvTraineeCount.text = "${salary.totalTrainees} trainees"
            
            // Set colors based on values
            val absenceColor = when {
                salary.absencePercentage <= 5 -> holder.itemView.context.getColor(R.color.success_light)
                salary.absencePercentage <= 15 -> holder.itemView.context.getColor(R.color.warning_light)
                else -> holder.itemView.context.getColor(R.color.error_light)
            }
            holder.tvAbsencePercent.setTextColor(absenceColor)
            
            val salaryColor = when {
                salary.finalSalary > 0 -> holder.itemView.context.getColor(R.color.success_light)
                else -> holder.itemView.context.getColor(R.color.error_light)
            }
            holder.tvFinalSalary.setTextColor(salaryColor)
            
            // Add long press listener to show details
            holder.cardView.setOnLongClickListener {
                val detailsDialog = SalaryDetailsDialog(holder.itemView.context, salary)
                detailsDialog.show()
                true // Consume the long click event
            }
            
        } catch (e: Exception) {
            android.util.Log.e("SalaryAdapter", "Error binding salary data: ${e.message}")
        }
    }

    override fun getItemCount(): Int = salaries.size
    
    private fun getMonthDisplayName(monthKey: String): String {
        return try {
            // The monthKey is now already in "MMMM yyyy" format, so just return it
            monthKey
        } catch (e: Exception) {
            monthKey
        }
    }
}