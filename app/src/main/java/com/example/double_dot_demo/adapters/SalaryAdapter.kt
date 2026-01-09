package com.example.double_dot_demo.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.double_dot_demo.R
import com.example.double_dot_demo.models.Salary
import com.google.android.material.card.MaterialCardView
import java.text.NumberFormat
import java.util.*

class SalaryAdapter(
    private val salaries: List<Salary>,
    private val onExportPdf: (Salary) -> Unit
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
                val context = holder.itemView.context

                val roleLower = salary.role.trim().lowercase().replace(" ", "_")
                val isCoach = roleLower == "coach" || roleLower == "head_coach"

                val coachTypeLine =
                    if (isCoach) {
                        "\nCoach Type : ${salary.coachType ?: "academy"}"
                    } else {
                        ""
                    }

                val traineesText =
                    if (!isCoach) {
                        "Not applicable"
                    } else if (salary.traineeDetails.isEmpty()) {
                        "No trainees"
                    } else {
                        salary.traineeDetails.joinToString("\n") {
                            "• ${it.traineeName}  ${numberFormat.format(it.monthlyFee)}"
                        }
                    }

                val notesText =
                    if (salary.deductionDetails.isEmpty()) {
                        "No deductions applied"
                    } else {
                        salary.deductionDetails.joinToString("\n") {
                            "• ${it.description} (${numberFormat.format(it.amount)})"
                        }
                    }

                val showTraineesSection = !(roleLower == "admin" || roleLower == "head_admin")

                val traineesSection = if (showTraineesSection) {
                    """
──────────── Trainees (${salary.totalTrainees}) ────────────
$traineesText

""".trimIndent()
                } else {
                    ""
                }

                val message = """
Employee Name : ${salary.employeeName}
Month         : ${salary.month}
Role          : ${salary.role}$coachTypeLine

──────────── Salary Summary ────────────
Gross Income (100%) : ${numberFormat.format(salary.totalPayments / 0.4)}
Total Payments (40%): ${numberFormat.format(salary.totalPayments)}
Final Salary        : ${numberFormat.format(salary.finalSalary)}

$traineesSection──────────── Attendance Details ────────────
Working Days : ${if (salary.totalWorkingDays > 0) salary.totalWorkingDays else "Not recorded"}
Absence Days : ${salary.absenceDays}
Absence Rate : ${String.format("%.1f", salary.absencePercentage)}%

──────────── Deductions ────────────
Total Deduction : ${numberFormat.format(salary.deductionAmount)}

──────────── Deduction Details ────────────
$notesText
""".trimIndent()

                androidx.appcompat.app.AlertDialog.Builder(context)
                    .setTitle("Salary Details")
                    .setMessage(message)
                    .setPositiveButton("OK", null)
                    .setNeutralButton("Export PDF") { _, _ ->
                        onExportPdf(salary)
                    }
                    .show()

                true
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