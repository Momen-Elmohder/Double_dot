package com.example.double_dot_demo.dialogs

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.example.double_dot_demo.R
import com.example.double_dot_demo.models.Employee
import com.example.double_dot_demo.models.Expense
import com.example.double_dot_demo.models.Trainee
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

class ExpenseDetailsDialog(
    private val context: Context,
    private val branchName: String,
    private val selectedMonth: String,
    private val expenses: List<Expense>,
    private val trainees: List<Trainee>,
    private val coaches: List<Employee>
) {
    private val numberFormat = NumberFormat.getCurrencyInstance(Locale.US)
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    
    fun show() {
        val builder = AlertDialog.Builder(context)
        
        // Create custom layout
        val scrollView = ScrollView(context)
        val mainLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 24, 32, 24)
        }
        
        // Title
        val titleView = TextView(context).apply {
            text = "$branchName - $selectedMonth"
            textSize = 22f
            setTextColor(ContextCompat.getColor(context, R.color.primary_light))
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 16)
        }
        mainLayout.addView(titleView)
        
        // Summary section
        addSummarySection(mainLayout)
        addDivider(mainLayout)
        
        // Income details
        addIncomeSection(mainLayout)
        addDivider(mainLayout)
        
        // Manual expenses details
        addManualExpensesSection(mainLayout)
        addDivider(mainLayout)
        
        // Auto-calculated salaries
        addSalariesSection(mainLayout)
        
        scrollView.addView(mainLayout)
        
        builder.apply {
            setTitle("Expense Details")
            setView(scrollView)
            setPositiveButton("Close") { dialog, _ ->
                dialog.dismiss()
            }
            setCancelable(true)
        }
        
        val dialog = builder.create()
        dialog.show()
        
        // Make the dialog wider for better readability
        dialog.window?.setLayout(
            (context.resources.displayMetrics.widthPixels * 0.95).toInt(),
            (context.resources.displayMetrics.heightPixels * 0.8).toInt()
        )
    }
    
    private fun addSummarySection(parent: LinearLayout) {
        addSectionHeader(parent, "Summary")
        
        val branchExpenses = expenses.filter { 
            isExpenseInMonth(it, selectedMonth) && it.branch == branchName 
        }
        val branchTrainees = trainees.filter { it.branch == branchName }
        val branchCoaches = coaches.filter { it.branch == branchName }
        
        val totalIncome = branchTrainees.sumOf { it.paymentAmount }
        val manualExpenses = branchExpenses.filter { 
            it.type == "EXPENSE" && !it.isAutoCalculated 
        }.sumOf { it.amount }
        val autoSalaries = calculateTotalSalaries(branchCoaches)
        val netAmount = totalIncome - (manualExpenses + autoSalaries)
        
        addInfoRow(parent, "Total Income", numberFormat.format(totalIncome), R.color.success_light)
        addInfoRow(parent, "Manual Expenses", numberFormat.format(manualExpenses), android.R.color.holo_red_dark)
        addInfoRow(parent, "Auto Salaries", numberFormat.format(autoSalaries), android.R.color.holo_red_dark)
        addInfoRow(parent, "Net Amount", numberFormat.format(netAmount), 
            if (netAmount >= 0) R.color.success_light else android.R.color.holo_red_dark, true)
    }
    
    private fun addIncomeSection(parent: LinearLayout) {
        addSectionHeader(parent, "Income Details")
        
        val branchTrainees = trainees.filter { it.branch == branchName }
        
        if (branchTrainees.isNotEmpty()) {
            branchTrainees.forEach { trainee ->
                addTraineeIncomeItem(parent, trainee)
            }
        } else {
            addEmptyMessage(parent, "No income entries for this branch")
        }
    }
    
    private fun addManualExpensesSection(parent: LinearLayout) {
        addSectionHeader(parent, "Manual Expenses")
        
        val manualExpenses = expenses.filter { 
            isExpenseInMonth(it, selectedMonth) && 
            it.branch == branchName && 
            it.type == "EXPENSE" && 
            !it.isAutoCalculated 
        }
        
        if (manualExpenses.isNotEmpty()) {
            manualExpenses.forEach { expense ->
                addExpenseItem(parent, expense)
            }
        } else {
            addEmptyMessage(parent, "No manual expenses for this branch")
        }
    }
    
    private fun addSalariesSection(parent: LinearLayout) {
        addSectionHeader(parent, "Auto-Calculated Salaries")
        
        val branchCoaches = coaches.filter { it.branch == branchName }
        
        if (branchCoaches.isNotEmpty()) {
            branchCoaches.forEach { coach ->
                addCoachSalaryItem(parent, coach)
            }
        } else {
            addEmptyMessage(parent, "No coaches assigned to this branch")
        }
    }
    
    private fun addTraineeIncomeItem(parent: LinearLayout, trainee: Trainee) {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 8, 16, 8)
            background = ContextCompat.getDrawable(context, R.drawable.status_background)
        }
        
        val nameView = TextView(context).apply {
            text = trainee.name
            textSize = 16f
            setTextColor(Color.BLACK)
            setTypeface(null, Typeface.BOLD)
        }
        
        val detailsView = TextView(context).apply {
            text = "Age: ${trainee.age} | Coach: ${trainee.coachName} | Status: ${trainee.status}"
            textSize = 12f
            setTextColor(Color.GRAY)
            setPadding(0, 4, 0, 4)
        }
        
        val amountView = TextView(context).apply {
            text = numberFormat.format(trainee.paymentAmount)
            textSize = 16f
            setTextColor(ContextCompat.getColor(context, R.color.success_light))
            setTypeface(null, Typeface.BOLD)
        }
        
        container.addView(nameView)
        container.addView(detailsView)
        container.addView(amountView)
        parent.addView(container)
        
        // Add spacing
        parent.addView(TextView(context).apply { 
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 8) 
        })
    }
    
    private fun addExpenseItem(parent: LinearLayout, expense: Expense) {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 8, 16, 8)
            background = ContextCompat.getDrawable(context, R.drawable.status_background)
        }
        
        val titleView = TextView(context).apply {
            text = expense.title
            textSize = 16f
            setTextColor(Color.BLACK)
            setTypeface(null, Typeface.BOLD)
        }
        
        val detailsView = TextView(context).apply {
            text = "Category: ${expense.category} | Date: ${dateFormat.format(expense.date.toDate())}"
            textSize = 12f
            setTextColor(Color.GRAY)
            setPadding(0, 4, 0, 4)
        }
        
        val descriptionView = TextView(context).apply {
            text = expense.description
            textSize = 12f
            setTextColor(Color.DKGRAY)
            setPadding(0, 4, 0, 4)
        }
        
        val amountView = TextView(context).apply {
            text = numberFormat.format(expense.amount)
            textSize = 16f
            setTextColor(ContextCompat.getColor(context, android.R.color.holo_red_dark))
            setTypeface(null, Typeface.BOLD)
        }
        
        container.addView(titleView)
        container.addView(detailsView)
        if (expense.description.isNotEmpty()) {
            container.addView(descriptionView)
        }
        container.addView(amountView)
        parent.addView(container)
        
        // Add spacing
        parent.addView(TextView(context).apply { 
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 8) 
        })
    }
    
    private fun addCoachSalaryItem(parent: LinearLayout, coach: Employee) {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 8, 16, 8)
            background = ContextCompat.getDrawable(context, R.drawable.status_background)
        }
        
        val nameView = TextView(context).apply {
            text = coach.name
            textSize = 16f
            setTextColor(Color.BLACK)
            setTypeface(null, Typeface.BOLD)
        }
        
        val coachTrainees = trainees.filter { it.coachId == coach.id }
        val totalPayments = coachTrainees.sumOf { it.paymentAmount }
        val baseSalary = coachTrainees.sumOf { trainee ->
            calculateCommission(coach.branch, trainee.paymentAmount)
        }
        
        val (presentCount, absentCount) = calculateAttendanceStats(coach)
        val totalDays = presentCount + absentCount
        val absencePercent = if (totalDays > 0) {
            (absentCount.toDouble() / totalDays.toDouble()) * 100.0
        } else {
            0.0
        }
        val deduction = baseSalary * (absencePercent / 100.0)
        val finalSalary = baseSalary - deduction
        
        val detailsView = TextView(context).apply {
            text = "Trainees: ${coachTrainees.size} | Base: ${numberFormat.format(baseSalary)}"
            textSize = 12f
            setTextColor(Color.GRAY)
            setPadding(0, 4, 0, 4)
        }
        
        val attendanceView = TextView(context).apply {
            text = "Attendance: $presentCount present, $absentCount absent (${String.format("%.1f", absencePercent)}% absence)"
            textSize = 12f
            setTextColor(Color.DKGRAY)
            setPadding(0, 4, 0, 4)
        }
        
        val salaryView = TextView(context).apply {
            text = "Final Salary: ${numberFormat.format(finalSalary)}"
            textSize = 16f
            setTextColor(ContextCompat.getColor(context, android.R.color.holo_red_dark))
            setTypeface(null, Typeface.BOLD)
        }
        
        container.addView(nameView)
        container.addView(detailsView)
        container.addView(attendanceView)
        container.addView(salaryView)
        parent.addView(container)
        
        // Add spacing
        parent.addView(TextView(context).apply { 
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 8) 
        })
    }
    
    private fun addSectionHeader(parent: LinearLayout, title: String) {
        val header = TextView(context).apply {
            text = title
            textSize = 18f
            setTextColor(ContextCompat.getColor(context, R.color.primary_light))
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 16, 0, 8)
        }
        parent.addView(header)
    }
    
    private fun addInfoRow(parent: LinearLayout, label: String, value: String, colorRes: Int, isBold: Boolean = false) {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 4, 0, 4)
        }
        
        val labelView = TextView(context).apply {
            text = "$label:"
            textSize = if (isBold) 16f else 14f
            setTextColor(Color.BLACK)
            setTypeface(null, if (isBold) Typeface.BOLD else Typeface.NORMAL)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        
        val valueView = TextView(context).apply {
            text = value
            textSize = if (isBold) 16f else 14f
            setTextColor(ContextCompat.getColor(context, colorRes))
            setTypeface(null, if (isBold) Typeface.BOLD else Typeface.NORMAL)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        
        container.addView(labelView)
        container.addView(valueView)
        parent.addView(container)
    }
    
    private fun addEmptyMessage(parent: LinearLayout, message: String) {
        val emptyView = TextView(context).apply {
            text = message
            textSize = 14f
            setTextColor(Color.GRAY)
            textAlignment = TextView.TEXT_ALIGNMENT_CENTER
            setPadding(16, 32, 16, 32)
            setTypeface(null, Typeface.ITALIC)
        }
        parent.addView(emptyView)
    }
    
    private fun addDivider(parent: LinearLayout) {
        val divider = TextView(context).apply {
            text = "────────────────────────────────"
            textSize = 14f
            setTextColor(Color.LTGRAY)
            textAlignment = TextView.TEXT_ALIGNMENT_CENTER
            setPadding(0, 12, 0, 12)
        }
        parent.addView(divider)
    }
    
    private fun calculateTotalSalaries(coaches: List<Employee>): Double {
        return coaches.sumOf { coach ->
            val coachTrainees = trainees.filter { it.coachId == coach.id }
            val totalPayments = coachTrainees.sumOf { it.paymentAmount }
            val baseSalary = totalPayments * 0.4
            
            val (presentCount, absentCount) = calculateAttendanceStats(coach)
            val totalDays = presentCount + absentCount
            val absencePercent = if (totalDays > 0) {
                (absentCount.toDouble() / totalDays.toDouble()) * 100.0
            } else {
                0.0
            }
            val deduction = baseSalary * (absencePercent / 100.0)
            
            baseSalary - deduction
        }
    }
    
    private fun calculateAttendanceStats(coach: Employee): Pair<Int, Int> {
        var presentCount = 0
        var absentCount = 0
        
        coach.attendanceDays.forEach { (_, isPresent) ->
            if (isPresent) presentCount++ else absentCount++
        }
        
        return Pair(presentCount, absentCount)
    }
    
    private fun isExpenseInMonth(expense: Expense, month: String): Boolean {
        return try {
            val expenseDate = Calendar.getInstance()
            expenseDate.time = expense.date.toDate()
            val expenseMonth = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(expenseDate.time)
            expenseMonth == month
        } catch (e: Exception) {
            false
        }
    }
    
    private fun calculateCommission(branch: String, traineeFee: Double): Double {
        return when (branch) {
            "نادي التوكيلات" -> traineeFee * 0.40 // 40%
            "نادي اليخت" -> traineeFee * 0.30 // 30%
            "المدينة الرياضية" -> 200.0 // Fixed 200 pounds
            else -> traineeFee * 0.40 // Default to 40%
        }
    }
}
