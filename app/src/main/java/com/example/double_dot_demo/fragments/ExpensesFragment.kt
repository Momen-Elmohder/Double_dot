package com.example.double_dot_demo.fragments

import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.double_dot_demo.R
import com.example.double_dot_demo.adapters.ExpensesAdapter
import com.example.double_dot_demo.dialogs.AddExpenseDialog
import com.example.double_dot_demo.dialogs.EditExpenseDialog
import com.example.double_dot_demo.models.Employee
import com.example.double_dot_demo.models.Expense
import com.example.double_dot_demo.models.Trainee
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.*
import android.widget.Button
import android.widget.AutoCompleteTextView
import androidx.lifecycle.lifecycleScope
import com.example.double_dot_demo.utils.ExpenseManager
import com.example.double_dot_demo.utils.SalaryManager
import kotlinx.coroutines.launch

class ExpensesFragment : Fragment() {

    private var recyclerView: RecyclerView? = null
    private var actvMonth: AutoCompleteTextView? = null
    private var fabAddExpense: FloatingActionButton? = null
    private var tvTotalExpenses: TextView? = null
    private var tvTotalIncome: TextView? = null
    private var tvNetAmount: TextView? = null
    private var btnExport: Button? = null
    
    private var currentAdapter: Any? = null
    private val expenses = mutableListOf<Expense>()
    private val trainees = mutableListOf<Trainee>()
    private val coaches = mutableListOf<Employee>()
    private val users = mutableListOf<Employee>()
    
    private var expensesListener: com.google.firebase.firestore.ListenerRegistration? = null
    private var traineesListener: com.google.firebase.firestore.ListenerRegistration? = null
    private var coachesListener: com.google.firebase.firestore.ListenerRegistration? = null
    private var usersListener: com.google.firebase.firestore.ListenerRegistration? = null
    
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    
    private var selectedMonth = getCurrentMonth()
    private val dateFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
    private val expenseManager by lazy { ExpenseManager() }
    private val salaryManager by lazy { com.example.double_dot_demo.utils.SalaryManager() }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return try {
            inflater.inflate(R.layout.fragment_expenses, container, false)
        } catch (e: Exception) {
            android.util.Log.e("ExpensesFragment", "Error inflating layout: ${e.message}")
            TextView(requireContext()).apply {
                text = "Error loading expenses page"
                gravity = android.view.Gravity.CENTER
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        try {
            recyclerView = view.findViewById(R.id.recyclerView)
            actvMonth = view.findViewById(R.id.actvMonth)
            fabAddExpense = view.findViewById(R.id.fabAddExpense)
            tvTotalExpenses = view.findViewById(R.id.tvTotalExpenses)
            tvTotalIncome = view.findViewById(R.id.tvTotalIncome)
            tvNetAmount = view.findViewById(R.id.tvNetAmount)
            btnExport = view.findViewById(R.id.btnExport)
            
            if (recyclerView == null || actvMonth == null || fabAddExpense == null || 
                tvTotalExpenses == null || tvTotalIncome == null || tvNetAmount == null || btnExport == null) {
                android.util.Log.e("ExpensesFragment", "One or more views not found")
                android.widget.Toast.makeText(context, "Error: Some UI elements not found", android.widget.Toast.LENGTH_SHORT).show()
                return
            }
            
            setupRecyclerView()
            setupMonthDropdown()
            setupAddExpenseButton()
            setupExportButton()
            
            // Monthly rollover using server time: ensure expenses month and salaries are rolled
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    expenseManager.ensureMonthlyRolloverIfNeeded()
                    salaryManager.performMonthlyRolloverIfNeeded()
                } catch (_: Exception) {}
            }
            
            loadData()
            
            android.util.Log.d("ExpensesFragment", "Expenses fragment setup completed successfully")
        } catch (e: Exception) {
            android.util.Log.e("ExpensesFragment", "Error in onViewCreated: ${e.message}")
            android.widget.Toast.makeText(context, "Error setting up expenses page: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupRecyclerView() {
        try {
            recyclerView?.let { rv ->
                rv.layoutManager = LinearLayoutManager(context)
                
                val adapter = ExpensesAdapter(
                    expenses = expenses,
                    trainees = trainees,
                    coaches = coaches,
                    selectedMonth = selectedMonth,
                    onEditExpense = { expense: Expense ->
                        showEditExpenseDialog(expense)
                    },
                    onDeleteExpense = { expense: Expense ->
                        deleteExpense(expense)
                    }
                )
                
                rv.adapter = adapter
                currentAdapter = adapter
            } ?: run {
                android.util.Log.e("ExpensesFragment", "RecyclerView is null")
            }
        } catch (e: Exception) {
            android.util.Log.e("ExpensesFragment", "Error setting up RecyclerView: ${e.message}")
        }
    }

    private fun setupMonthDropdown() {
        lifecycleScope.launch {
            try {
                expenseManager.ensureMonthlyRolloverIfNeeded()
                val months = expenseManager.getAvailableMonths()
                val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, months)
                actvMonth?.setAdapter(adapter)
                if (months.isNotEmpty()) {
                    val current = getCurrentMonth()
                    selectedMonth = if (months.contains(current)) current else months.first()
                    actvMonth?.setText(selectedMonth, false)
                }
                actvMonth?.setOnItemClickListener { _, _, position, _ ->
                    val list = (actvMonth?.adapter as? ArrayAdapter<String>)
                    selectedMonth = list?.getItem(position) ?: selectedMonth
                    updateExpensesForMonth()
                }
                actvMonth?.setOnClickListener { actvMonth?.showDropDown() }
                actvMonth?.setOnFocusChangeListener { _, hasFocus -> if (hasFocus) actvMonth?.showDropDown() }
            } catch (e: Exception) {
                android.util.Log.e("ExpensesFragment", "Error setting up month dropdown: ${e.message}")
            }
        }
    }

    private fun setupAddExpenseButton() {
        try {
            fabAddExpense?.setOnClickListener { showAddExpenseDialog() }
        } catch (e: Exception) {
            android.util.Log.e("ExpensesFragment", "Error setting up add expense button: ${e.message}")
        }
    }

    private fun setupExportButton() {
        try {
            btnExport?.setOnClickListener { exportAsPDF() }
            android.util.Log.d("ExpensesFragment", "Export button setup completed")
        } catch (e: Exception) {
            android.util.Log.e("ExpensesFragment", "Error setting up export button: ${e.message}")
        }
    }

    private fun exportAsPDF() {
        try {
            val month = selectedMonth
            val monthExpenses = expenses.filter { isExpenseInMonth(it, month) && it.type == "EXPENSE" }
            val monthIncome = expenses.filter { isExpenseInMonth(it, month) && it.type == "INCOME" }
            val totalExpenses = monthExpenses.sumOf { it.amount }
            val totalIncome = monthIncome.sumOf { it.amount }
            val netAmount = totalIncome - totalExpenses

            val pdf = android.graphics.pdf.PdfDocument()
            val paint = android.graphics.Paint()
            val bold = android.graphics.Paint().apply { textSize = 16f; isFakeBoldText = true }
            val titleBold = android.graphics.Paint().apply { textSize = 18f; isFakeBoldText = true }
            val smallPaint = android.graphics.Paint().apply { textSize = 10f }
            
            var pageNumber = 1
            var page = createNewPage(pdf, pageNumber, month, titleBold, paint)
            var y = 80

            fun draw(text: String, isBold: Boolean = false, isSmall: Boolean = false) {
                val p = when {
                    isBold -> bold
                    isSmall -> smallPaint
                    else -> paint
                }
                val size = when {
                    isBold -> 16f
                    isSmall -> 10f
                    else -> 14f
                }
                p.textSize = size
                page.canvas.drawText(text, 40f, y.toFloat(), p)
                y += if (isSmall) 12 else if (isBold) 20 else 18
            }

            fun checkNewPage() {
                if (y > 800) {
                    pdf.finishPage(page)
                    pageNumber++
                    page = createNewPage(pdf, pageNumber, month, titleBold, paint)
                    y = 80
                }
            }

            draw("Summary", true)
            draw("Total Income: ${String.format("%.2f", totalIncome)}")
            draw("Total Expenses: ${String.format("%.2f", totalExpenses)}")
            draw("Net: ${String.format("%.2f", netAmount)}", true)
            y += 10

            // Branch Details with detailed breakdown
            draw("Branch Details", true)
            val branches = getBranches()
            branches.forEach { branch ->
                checkNewPage()
                draw("$branch", true)
                
                val branchData = getBranchData(branch)
                draw("  Income: ${String.format("%.2f", branchData.totalIncome)}")
                draw("  Manual Expenses: ${String.format("%.2f", branchData.manualExpenses)}")
                draw("  Auto Salaries: ${String.format("%.2f", branchData.autoSalaries)}")
                draw("  Net: ${String.format("%.2f", branchData.totalAmount)}", true)
                
                // Add detailed breakdown for each branch
                val branchExpenses = expenses.filter { 
                    isExpenseInMonth(it, month) && it.branch == branch 
                }
                val branchTrainees = trainees.filter { it.branch == branch }
                val branchCoaches = coaches.filter { it.branch == branch }
                
                // Income details
                if (branchTrainees.isNotEmpty()) {
                    checkNewPage()
                    draw("  Income Details:", true)
                    branchTrainees.forEach { trainee ->
                        checkNewPage()
                        draw("    ${trainee.name} - ${String.format("%.2f", trainee.paymentAmount)}", false, true)
                    }
                }
                
                // Manual expenses details
                val manualExpenses = branchExpenses.filter { 
                    it.type == "EXPENSE" && !it.isAutoCalculated 
                }
                if (manualExpenses.isNotEmpty()) {
                    checkNewPage()
                    draw("  Manual Expenses:", true)
                    manualExpenses.forEach { expense ->
                        checkNewPage()
                        draw("    ${expense.title} - ${String.format("%.2f", expense.amount)}", false, true)
                        if (expense.description.isNotEmpty()) {
                            draw("      ${expense.description}", false, true)
                        }
                    }
                }
                
                // Salary details
                if (branchCoaches.isNotEmpty()) {
                    checkNewPage()
                    draw("  Salary Details:", true)
                    branchCoaches.forEach { coach ->
                        checkNewPage()
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
                        
                        draw("    ${coach.name} - ${String.format("%.2f", finalSalary)}", false, true)
                        draw("      Trainees: ${coachTrainees.size}, Base: ${String.format("%.2f", baseSalary)}", false, true)
                        draw("      Attendance: $presentCount present, $absentCount absent", false, true)
                    }
                }
                
                y += 10
            }

            pdf.finishPage(page)
            val fileName = "expenses_report_${month.replace(" ", "_")}.pdf"
            val values = android.content.ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                put(MediaStore.Downloads.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
            }
            val resolver = requireContext().contentResolver
            val uri = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            } else {
                // Fallback for older Android versions - save to app's internal storage
                val file = java.io.File(requireContext().getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS), fileName)
                file.outputStream().use { out -> pdf.writeTo(out) }
                null // We've already written the file, so return null to skip the resolver.insert
            }
            if (uri == null) { android.widget.Toast.makeText(context, "Save failed", android.widget.Toast.LENGTH_SHORT).show(); return }
            resolver.openOutputStream(uri)?.use { out -> pdf.writeTo(out) }
            pdf.close()
            android.widget.Toast.makeText(context, "Saved to Downloads/$fileName", android.widget.Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            android.util.Log.e("ExpensesFragment", "Error exporting PDF: ${e.message}")
            android.widget.Toast.makeText(context, "Export failed: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
        }
    }
    
    private fun createNewPage(pdf: android.graphics.pdf.PdfDocument, pageNumber: Int, month: String, titleBold: android.graphics.Paint, paint: android.graphics.Paint): android.graphics.pdf.PdfDocument.Page {
        val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(595, 842, pageNumber).create()
        val page = pdf.startPage(pageInfo)
        
        // Draw header
        titleBold.textSize = 18f
        page.canvas.drawText("Double Dot Academy - Expenses Report", 40f, 30f, titleBold)
        paint.textSize = 14f
        page.canvas.drawText("Month: $month", 40f, 50f, paint)
        page.canvas.drawText("Page: $pageNumber", 500f, 50f, paint)
        
        return page
    }
    


    private fun generateCSVContent(): String {
        return try {
            val csvBuilder = StringBuilder()
            
            // Header
            csvBuilder.append("Double Dot Academy - Financial Report\n")
            csvBuilder.append("Month: $selectedMonth\n")
            csvBuilder.append("Generated: ${java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())}\n\n")
            
            // Summary
            val monthExpenses = expenses.filter { 
                isExpenseInMonth(it, selectedMonth) && it.type == "EXPENSE"
            }
            val monthIncome = expenses.filter { 
                isExpenseInMonth(it, selectedMonth) && it.type == "INCOME"
            }
            val totalExpenses = monthExpenses.sumOf { it.amount }
            val totalIncome = monthIncome.sumOf { it.amount }
            val netAmount = totalIncome - totalExpenses
            
            csvBuilder.append("SUMMARY\n")
            csvBuilder.append("Total Income,${String.format("%.2f", totalIncome)}\n")
            csvBuilder.append("Total Expenses,${String.format("%.2f", totalExpenses)}\n")
            csvBuilder.append("Net Amount,${String.format("%.2f", netAmount)}\n\n")
            
            // Branch Details
            csvBuilder.append("BRANCH DETAILS\n")
            csvBuilder.append("Branch,Total Income,Manual Expenses,Auto Salaries,Net Amount\n")
            
            val branches = getBranches()
            branches.forEach { branchName ->
                val branchData = getBranchData(branchName)
                csvBuilder.append("$branchName,${String.format("%.2f", branchData.totalIncome)},${String.format("%.2f", branchData.manualExpenses)},${String.format("%.2f", branchData.autoSalaries)},${String.format("%.2f", branchData.totalAmount)}\n")
            }
            
            csvBuilder.toString()
        } catch (e: Exception) {
            android.util.Log.e("ExpensesFragment", "Error generating CSV content: ${e.message}")
            "Error generating CSV content"
        }
    }

    private fun generatePDFContent(): String {
        return try {
            val pdfBuilder = StringBuilder()
            
            // Header
            pdfBuilder.append("Double Dot Academy - Financial Report\n")
            pdfBuilder.append("Month: $selectedMonth\n")
            pdfBuilder.append("Generated: ${java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())}\n\n")
            
            // Summary
            val monthExpenses = expenses.filter { 
                isExpenseInMonth(it, selectedMonth) && it.type == "EXPENSE"
            }
            val monthIncome = expenses.filter { 
                isExpenseInMonth(it, selectedMonth) && it.type == "INCOME"
            }
            val totalExpenses = monthExpenses.sumOf { it.amount }
            val totalIncome = monthIncome.sumOf { it.amount }
            val netAmount = totalIncome - totalExpenses
            
            pdfBuilder.append("SUMMARY\n")
            pdfBuilder.append("Total Income: $${String.format("%.2f", totalIncome)}\n")
            pdfBuilder.append("Total Expenses: $${String.format("%.2f", totalExpenses)}\n")
            pdfBuilder.append("Net Amount: $${String.format("%.2f", netAmount)}\n\n")
            
            // Branch Details
            pdfBuilder.append("BRANCH DETAILS\n")
            pdfBuilder.append("Branch | Total Income | Manual Expenses | Auto Salaries | Net Amount\n")
            pdfBuilder.append("-------|-------------|-----------------|---------------|------------\n")
            
            val branches = getBranches()
            branches.forEach { branchName ->
                val branchData = getBranchData(branchName)
                pdfBuilder.append("$branchName | $${String.format("%.2f", branchData.totalIncome)} | $${String.format("%.2f", branchData.manualExpenses)} | $${String.format("%.2f", branchData.autoSalaries)} | $${String.format("%.2f", branchData.totalAmount)}\n")
            }
            
            pdfBuilder.toString()
        } catch (e: Exception) {
            android.util.Log.e("ExpensesFragment", "Error generating PDF content: ${e.message}")
            "Error generating PDF content"
        }
    }

    private fun getBranches(): List<String> {
        return try {
            val branches = mutableSetOf<String>()
            // Always include known branches so cards are visible
            branches.addAll(listOf("نادي التوكيلات", "نادي اليخت", "المدينة الرياضية"))
            // Add from expenses of selected month
            expenses.filter { isExpenseInMonth(it, selectedMonth) }
                .forEach { expense -> if (expense.branch.isNotEmpty()) branches.add(expense.branch) }
            // Add from trainees
            trainees.forEach { trainee -> if (trainee.branch.isNotEmpty()) branches.add(trainee.branch) }
            // Add from employees (coaches)
            coaches.forEach { coach -> if (coach.branch.isNotEmpty()) branches.add(coach.branch) }
            branches.sorted().toList()
        } catch (e: Exception) {
            android.util.Log.e("ExpensesFragment", "Error getting branches: ${e.message}")
            listOf("نادي التوكيلات", "نادي اليخت", "المدينة الرياضية")
        }
    }

    private fun getBranchData(branchName: String): BranchData {
        return try {
            val manualExpenses = calculateManualExpensesForBranch(branchName)
            val autoSalaries = calculateAutoSalariesForBranch(branchName)
            val totalIncome = calculateTotalIncomeForBranch(branchName)
            val totalAmount = totalIncome - (manualExpenses + autoSalaries)
            val expenseCount = expenses.count { isExpenseInMonth(it, selectedMonth) && it.branch == branchName }
            BranchData(
                manualExpenses = manualExpenses,
                autoSalaries = autoSalaries,
                totalIncome = totalIncome,
                totalAmount = totalAmount,
                expenseCount = expenseCount
            )
        } catch (e: Exception) {
            android.util.Log.e("ExpensesFragment", "Error getting branch data: ${e.message}")
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
            android.util.Log.e("ExpensesFragment", "Error calculating manual expenses for $branchName: ${e.message}")
            0.0
        }
    }

    private fun calculateAutoSalariesForBranch(branchName: String): Double {
        return try {
            coaches.filter { it.branch == branchName }
                .sumOf { coach -> calculateCoachSalary(coach) }
        } catch (e: Exception) {
            android.util.Log.e("ExpensesFragment", "Error calculating auto salaries for $branchName: ${e.message}")
            0.0
        }
    }

    private fun calculateTotalIncomeForBranch(branchName: String): Double {
        return try {
            // Use recorded income entries for the selected month (e.g., trainee payments)
            expenses.filter { 
                isExpenseInMonth(it, selectedMonth) &&
                it.branch == branchName &&
                it.type == "INCOME"
            }.sumOf { it.amount }
        } catch (e: Exception) {
            android.util.Log.e("ExpensesFragment", "Error calculating total income for $branchName: ${e.message}")
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
            android.util.Log.e("ExpensesFragment", "Error calculating coach salary: ${e.message}")
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
            android.util.Log.e("ExpensesFragment", "Error calculating attendance stats: ${e.message}")
            Pair(0, 0)
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

    private fun saveFile(fileName: String, content: String) {
        try {
            // For now, we'll save to internal storage and show a toast
            // In a real app, you'd want to save to external storage and share the file
            val file = java.io.File(requireContext().filesDir, fileName)
            file.writeText(content)
            
            android.widget.Toast.makeText(
                context,
                "Report saved as $fileName",
                android.widget.Toast.LENGTH_LONG
            ).show()
            
            android.util.Log.d("ExpensesFragment", "File saved: ${file.absolutePath}")
        } catch (e: Exception) {
            android.util.Log.e("ExpensesFragment", "Error saving file: ${e.message}")
            android.widget.Toast.makeText(
                context,
                "Error saving file: ${e.message}",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }

    data class BranchData(
        val manualExpenses: Double = 0.0,
        val autoSalaries: Double = 0.0,
        val totalIncome: Double = 0.0,
        val totalAmount: Double = 0.0,
        val expenseCount: Int = 0
    )

    private fun generateMonthList(): List<String> {
        return try {
            val months = mutableListOf<String>()
            val calendar = Calendar.getInstance()
            
            // Generate last 12 months
            for (i in 0..11) {
                calendar.add(Calendar.MONTH, -i)
                months.add(dateFormat.format(calendar.time))
                calendar.add(Calendar.MONTH, i) // Reset
            }
            
            months.reversed()
        } catch (e: Exception) {
            android.util.Log.e("ExpensesFragment", "Error generating month list: ${e.message}")
            listOf("January 2024", "February 2024", "March 2024") // Fallback
        }
    }

    private fun getCurrentMonth(): String {
        return try {
            dateFormat.format(Calendar.getInstance().time)
        } catch (e: Exception) {
            android.util.Log.e("ExpensesFragment", "Error getting current month: ${e.message}")
            "January 2024" // Fallback
        }
    }

    private fun loadData() {
        try {
            loadExpenses()
            loadTrainees()
            loadCoaches()
            loadUsers()
            android.util.Log.d("ExpensesFragment", "Data loading initiated")
        } catch (e: Exception) {
            android.util.Log.e("ExpensesFragment", "Error loading data: ${e.message}")
        }
    }

    private fun loadExpenses() {
        try {
            expensesListener?.remove()
            
            expensesListener = db.collection("expenses")
                .orderBy("date", Query.Direction.DESCENDING)
                .addSnapshotListener { snapshot, e ->
                    if (e != null) {
                        android.util.Log.e("ExpensesFragment", "Error loading expenses: ${e.message}")
                        return@addSnapshotListener
                    }

                    expenses.clear()
                    if (snapshot != null) {
                        for (document in snapshot) {
                            try {
                                val expense = document.toObject(Expense::class.java)
                                if (expense != null) {
                                    val expenseWithId = expense.copy(id = document.id)
                                    expenses.add(expenseWithId)
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("ExpensesFragment", "Error parsing expense: ${e.message}")
                            }
                        }
                    }
                    
                    updateExpensesForMonth()
                    android.util.Log.d("ExpensesFragment", "Loaded ${expenses.size} expenses")
                }
                
        } catch (e: Exception) {
            android.util.Log.e("ExpensesFragment", "Error setting up expenses listener: ${e.message}")
        }
    }

    private fun loadTrainees() {
        try {
            traineesListener?.remove()
            
            traineesListener = db.collection("trainees")
                .orderBy("name", Query.Direction.ASCENDING)
                .addSnapshotListener { snapshot, e ->
                    if (e != null) {
                        android.util.Log.e("ExpensesFragment", "Error loading trainees: ${e.message}")
                        return@addSnapshotListener
                    }

                    trainees.clear()
                    if (snapshot != null) {
                        for (document in snapshot) {
                            try {
                                val trainee = document.toObject(Trainee::class.java)
                                if (trainee != null) {
                                    val traineeWithId = trainee.copy(id = document.id)
                                    trainees.add(traineeWithId)
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("ExpensesFragment", "Error parsing trainee: ${e.message}")
                            }
                        }
                    }
                    
                    android.util.Log.d("ExpensesFragment", "Loaded ${trainees.size} trainees")
                }
                
        } catch (e: Exception) {
            android.util.Log.e("ExpensesFragment", "Error setting up trainees listener: ${e.message}")
        }
    }

    private fun loadCoaches() {
        try {
            coachesListener?.remove()
            
            coachesListener = db.collection("employees")
                .whereEqualTo("role", "coach")
                .orderBy("name", Query.Direction.ASCENDING)
                .addSnapshotListener { snapshot, e ->
                    if (e != null) {
                        android.util.Log.e("ExpensesFragment", "Error loading coaches: ${e.message}")
                        return@addSnapshotListener
                    }

                    coaches.clear()
                    if (snapshot != null) {
                        for (document in snapshot) {
                            try {
                                val coach = document.toObject(Employee::class.java)
                                if (coach != null) {
                                    val coachWithId = coach.copy(id = document.id)
                                    coaches.add(coachWithId)
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("ExpensesFragment", "Error parsing coach: ${e.message}")
                            }
                        }
                    }
                    
                    android.util.Log.d("ExpensesFragment", "Loaded ${coaches.size} coaches")
                }
                
        } catch (e: Exception) {
            android.util.Log.e("ExpensesFragment", "Error setting up coaches listener: ${e.message}")
        }
    }

    private fun loadUsers() {
        try {
            usersListener?.remove()
            
            usersListener = db.collection("employees")
                .orderBy("name", Query.Direction.ASCENDING)
                .addSnapshotListener { snapshot, e ->
                    if (e != null) {
                        android.util.Log.e("ExpensesFragment", "Error loading users: ${e.message}")
                        return@addSnapshotListener
                    }

                    users.clear()
                    if (snapshot != null) {
                        for (document in snapshot) {
                            try {
                                val user = document.toObject(Employee::class.java)
                                if (user != null) {
                                    val userWithId = user.copy(id = document.id)
                                    users.add(userWithId)
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("ExpensesFragment", "Error parsing user: ${e.message}")
                            }
                        }
                    }
                    
                    android.util.Log.d("ExpensesFragment", "Loaded ${users.size} users")
                }
                
        } catch (e: Exception) {
            android.util.Log.e("ExpensesFragment", "Error setting up users listener: ${e.message}")
        }
    }

    private fun updateExpensesForMonth() {
        try {
            // Update adapter's month and refresh totals
            (currentAdapter as? ExpensesAdapter)?.updateSelectedMonth(selectedMonth)
            updateTotals()
        } catch (e: Exception) {
            android.util.Log.e("ExpensesFragment", "Error updating expenses for month: ${e.message}")
        }
    }

    private fun updateTotals() {
        try {
            // Aggregate per-branch for selected month to drive top counters
            val branches = getBranches()
            var totalIncome = 0.0
            var totalManual = 0.0
            var totalAutoSalaries = 0.0
            branches.forEach { branch ->
                val data = getBranchData(branch)
                totalIncome += data.totalIncome
                totalManual += data.manualExpenses
                totalAutoSalaries += data.autoSalaries
            }
            val totalExpenses = totalManual + totalAutoSalaries
            val netAmount = totalIncome - totalExpenses

            tvTotalIncome?.text = "Total Income: $${String.format("%.2f", totalIncome)}"
            tvTotalExpenses?.text = "Total Expenses: $${String.format("%.2f", totalExpenses)}"
            tvNetAmount?.text = "Net: $${String.format("%.2f", netAmount)}"

            tvNetAmount?.let { netTextView ->
                val netColor = if (netAmount >= 0) requireContext().getColor(R.color.success_light) else requireContext().getColor(R.color.error_light)
                netTextView.setTextColor(netColor)
            }
        } catch (e: Exception) {
            android.util.Log.e("ExpensesFragment", "Error updating totals: ${e.message}")
        }
    }

    private fun isExpenseInMonth(expense: Expense, month: String): Boolean {
        return try {
            val expenseDate = Calendar.getInstance()
            expenseDate.time = expense.date.toDate()
            val expenseMonth = dateFormat.format(expenseDate.time)
            expenseMonth == month
        } catch (e: Exception) {
            false
        }
    }

    private fun showAddExpenseDialog() {
        try {
            val dialog = AddExpenseDialog.newInstance { expense: Expense ->
                // Expense was added successfully
                android.util.Log.d("ExpensesFragment", "Expense added: ${expense.title}")
                // The Firebase listener will automatically update the UI
            }
            
            dialog.show(childFragmentManager, "AddExpenseDialog")
            
        } catch (e: Exception) {
            android.util.Log.e("ExpensesFragment", "Error showing add expense dialog: ${e.message}")
            android.widget.Toast.makeText(context, "Error showing dialog: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun showEditExpenseDialog(expense: Expense) {
        try {
            val dialog = EditExpenseDialog.newInstance(
                expense = expense,
                onExpenseUpdated = { updatedExpense ->
                    android.util.Log.d("ExpensesFragment", "Expense updated: ${updatedExpense.title}")
                    // The adapter will automatically update due to Firebase listener
                },
                onExpenseDeleted = { deletedExpense ->
                    android.util.Log.d("ExpensesFragment", "Expense deleted: ${deletedExpense.title}")
                    // The adapter will automatically update due to Firebase listener
                }
            )
            dialog.show(childFragmentManager, "EditExpenseDialog")
        } catch (e: Exception) {
            android.util.Log.e("ExpensesFragment", "Error showing edit dialog: ${e.message}")
            android.widget.Toast.makeText(
                context,
                "Error opening edit dialog: ${e.message}",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun deleteExpense(expense: Expense) {
        try {
            // Show confirmation dialog
            androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Delete Expense")
                .setMessage("Are you sure you want to delete this expense?")
                .setPositiveButton("Delete") { _, _ ->
                    performDeleteExpense(expense)
                }
                .setNegativeButton("Cancel", null)
                .show()
        } catch (e: Exception) {
            android.util.Log.e("ExpensesFragment", "Error showing delete confirmation: ${e.message}")
        }
    }

    private fun performDeleteExpense(expense: Expense) {
        try {
            db.collection("expenses").document(expense.id)
                .delete()
                .addOnSuccessListener {
                    android.util.Log.d("ExpensesFragment", "Expense deleted successfully")
                    android.widget.Toast.makeText(
                        context,
                        "Expense deleted successfully",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
                .addOnFailureListener { e ->
                    android.util.Log.e("ExpensesFragment", "Error deleting expense: ${e.message}")
                    android.widget.Toast.makeText(
                        context,
                        "Error deleting expense: ${e.message}",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
        } catch (e: Exception) {
            android.util.Log.e("ExpensesFragment", "Error performing delete: ${e.message}")
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        try {
            expensesListener?.remove()
            traineesListener?.remove()
            coachesListener?.remove()
            usersListener?.remove()
        } catch (e: Exception) {
            android.util.Log.e("ExpensesFragment", "Error cleaning up listeners: ${e.message}")
        }
    }

    companion object {
        fun newInstance(): ExpensesFragment = ExpensesFragment()
    }
}


