package com.example.double_dot_demo.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.double_dot_demo.R
import com.example.double_dot_demo.models.Employee
import com.example.double_dot_demo.models.Expense
import com.example.double_dot_demo.models.Trainee
import com.google.android.material.card.MaterialCardView
import java.text.NumberFormat
import java.util.*

class ExpensesAdapter(
    private val expenses: List<Expense>,
    private val trainees: List<Trainee>,
    private val coaches: List<Employee>,
    private var selectedMonth: String,
    private val onEditExpense: (Expense) -> Unit,
    private val onDeleteExpense: (Expense) -> Unit
) : RecyclerView.Adapter<ExpensesAdapter.ExpensesViewHolder>() {

    private val numberFormat = NumberFormat.getCurrencyInstance(Locale.US)

    inner class ExpensesViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val cardView: MaterialCardView = itemView.findViewById(R.id.cardView)
        val tvBranchName: TextView = itemView.findViewById(R.id.tvBranchName)
        val tvExpenseCount: TextView = itemView.findViewById(R.id.tvExpenseCount)
        val tvManualExpenses: TextView = itemView.findViewById(R.id.tvManualExpenses)
        val tvAutoSalaries: TextView = itemView.findViewById(R.id.tvAutoSalaries)
        val tvTotalAmount: TextView = itemView.findViewById(R.id.tvTotalAmount)
        val tvTotalIncome: TextView = itemView.findViewById(R.id.tvTotalIncome)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ExpensesViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_expenses_branch, parent, false)
        return ExpensesViewHolder(view)
    }

    override fun onBindViewHolder(holder: ExpensesViewHolder, position: Int) {
        try {
            val branches = getBranches()
            if (position < branches.size) {
                val branchName = branches[position]
                val branchData = getBranchData(branchName)
                
                holder.tvBranchName.text = branchName
                holder.tvExpenseCount.text = "${branchData.expenseCount} entries"
                holder.tvTotalIncome.text = "Income: ${numberFormat.format(branchData.totalIncome)}"
                holder.tvManualExpenses.text = "Manual: ${numberFormat.format(branchData.manualExpenses)}"
                holder.tvAutoSalaries.text = "Salaries: ${numberFormat.format(branchData.autoSalaries)}"
                holder.tvTotalAmount.text = "Net: ${numberFormat.format(branchData.totalAmount)}"
                
                val totalColor = if (branchData.totalAmount >= 0) {
                    holder.itemView.context.getColor(R.color.success_light)
                } else {
                    holder.itemView.context.getColor(R.color.error_light)
                }
                holder.tvTotalAmount.setTextColor(totalColor)
            }
        } catch (e: Exception) {
            android.util.Log.e("ExpensesAdapter", "Error binding view holder: ${e.message}")
        }
    }

    override fun getItemCount(): Int {
        return try {
            getBranches().size
        } catch (e: Exception) {
            android.util.Log.e("ExpensesAdapter", "Error getting item count: ${e.message}")
            0
        }
    }

    fun updateSelectedMonth(newMonth: String) {
        try {
            selectedMonth = newMonth
            notifyDataSetChanged()
            android.util.Log.d("ExpensesAdapter", "Updated selected month to: $selectedMonth")
        } catch (e: Exception) {
            android.util.Log.e("ExpensesAdapter", "Error updating selected month: ${e.message}")
        }
    }

    private fun getBranches(): List<String> {
        return try {
            val branches = mutableSetOf<String>()
            
            // Add branches from expenses
            expenses.filter { isExpenseInMonth(it, selectedMonth) }
                .forEach { expense ->
                    if (expense.branch.isNotEmpty()) {
                        branches.add(expense.branch)
                    }
                }
            
            // Add branches from trainees
            trainees.forEach { trainee ->
                if (trainee.branch.isNotEmpty()) {
                    branches.add(trainee.branch)
                }
            }
            
            branches.sorted().toList()
        } catch (e: Exception) {
            android.util.Log.e("ExpensesAdapter", "Error getting branches: ${e.message}")
            emptyList()
        }
    }

    private fun getBranchData(branchName: String): BranchData {
        return try {
            val manualExpenses = calculateManualExpensesForBranch(branchName)
            val autoSalaries = calculateAutoSalariesForBranch(branchName)
            val totalIncome = calculateTotalIncomeForBranch(branchName)
            
            // Calculate total amount (income - expenses)
            val totalAmount = totalIncome - (manualExpenses + autoSalaries)
            
            val expenseCount = expenses.count { 
                isExpenseInMonth(it, selectedMonth) && it.branch == branchName 
            }
            
            BranchData(
                manualExpenses = manualExpenses,
                autoSalaries = autoSalaries,
                totalIncome = totalIncome,
                totalAmount = totalAmount,
                expenseCount = expenseCount
            )
        } catch (e: Exception) {
            android.util.Log.e("ExpensesAdapter", "Error getting branch data: ${e.message}")
            BranchData()
        }
    }

    private fun calculateManualExpensesForBranch(branchName: String): Double {
        return try {
            expenses.filter { 
                isExpenseInMonth(it, selectedMonth) && 
                it.branch == branchName && 
                it.type == "EXPENSE" && 
                !it.isAutoCalculated 
            }.sumOf { it.amount }
        } catch (e: Exception) {
            android.util.Log.e("ExpensesAdapter", "Error calculating manual expenses for $branchName: ${e.message}")
            0.0
        }
    }

    private fun calculateAutoSalariesForBranch(branchName: String): Double {
        return try {
            coaches.filter { it.branch == branchName }
                .sumOf { coach ->
                    calculateCoachSalary(coach)
                }
        } catch (e: Exception) {
            android.util.Log.e("ExpensesAdapter", "Error calculating auto salaries for $branchName: ${e.message}")
            0.0
        }
    }

    private fun calculateTotalIncomeForBranch(branchName: String): Double {
        return try {
            trainees.filter { it.branch == branchName }
                .sumOf { it.paymentAmount }
        } catch (e: Exception) {
            android.util.Log.e("ExpensesAdapter", "Error calculating total income for $branchName: ${e.message}")
            0.0
        }
    }

    private fun calculateCoachSalary(coach: Employee): Double {
        return try {
            // Find all trainees assigned to this coach
            val coachTrainees = trainees.filter { it.coachId == coach.id }
            
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
            baseSalary - deduction
        } catch (e: Exception) {
            android.util.Log.e("ExpensesAdapter", "Error calculating coach salary: ${e.message}")
            0.0
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
            android.util.Log.e("ExpensesAdapter", "Error calculating attendance stats: ${e.message}")
            Pair(0, 0)
        }
    }

    private fun isExpenseInMonth(expense: Expense, month: String): Boolean {
        return try {
            val expenseDate = Calendar.getInstance()
            expenseDate.time = expense.date.toDate()
            val expenseMonth = java.text.SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(expenseDate.time)
            expenseMonth == month
        } catch (e: Exception) {
            false
        }
    }

    data class BranchData(
        val manualExpenses: Double = 0.0,
        val autoSalaries: Double = 0.0,
        val totalIncome: Double = 0.0,
        val totalAmount: Double = 0.0,
        val expenseCount: Int = 0
    )
} 