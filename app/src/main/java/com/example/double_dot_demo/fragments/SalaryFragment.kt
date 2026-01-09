package com.example.double_dot_demo.fragments

import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.double_dot_demo.R
import com.example.double_dot_demo.adapters.SalaryAdapter
import com.example.double_dot_demo.models.Salary
import com.example.double_dot_demo.utils.SalaryManager
import com.example.double_dot_demo.utils.MonthFormatMigration
import kotlinx.coroutines.launch
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import com.google.android.material.textfield.TextInputEditText

class SalaryFragment : Fragment() {

    private var recyclerView: RecyclerView? = null
    private var tvNoSalaries: TextView? = null
    private var actvMonth: AutoCompleteTextView? = null
    private var etSearch: TextInputEditText? = null
    private var btnExportPdf: android.widget.Button? = null

    private var currentAdapter: SalaryAdapter? = null
    private val salaries = mutableListOf<Salary>()
    private var filtered = listOf<Salary>()
    private var availableMonths = listOf<String>()
    private var selectedMonth: String? = null
    private var searchQuery: String = ""

    private lateinit var salaryManager: SalaryManager
    private var salariesListener: ListenerRegistration? = null
    private val firestore by lazy { FirebaseFirestore.getInstance() }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return try {
            inflater.inflate(R.layout.fragment_salary, container, false)
        } catch (e: Exception) {
            android.util.Log.e("SalaryFragment", "Error inflating layout: ${e.message}")
            TextView(requireContext()).apply {
                text = "Error loading salary page"
                gravity = android.view.Gravity.CENTER
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        try {
            initializeViews(view)
            setupRecyclerView()
            salaryManager = SalaryManager()
            observeSalaryMode()

            lifecycleScope.launch {
                try {
                    // Check if migration is needed and run it
                    val migrationNeeded = MonthFormatMigration.isMigrationNeeded()
                    if (migrationNeeded) {
                        android.util.Log.d("SalaryFragment", "Running month format migration...")
                        val migrationSuccess = MonthFormatMigration.migrateSalaryMonthFormats()
                        android.util.Log.d("SalaryFragment", "Migration completed: $migrationSuccess")
                        if (migrationSuccess) {
                            showToast("Salary data updated successfully")
                        }
                    }

                    val rolled = salaryManager.performMonthlyRolloverIfNeeded()
                    android.util.Log.d("SalaryFragment", "Monthly rollover executed: $rolled")
                } catch (e: Exception) {
                    android.util.Log.e("SalaryFragment", "Error in initialization: ${e.message}")
                }
            }

            setupSalariesListener()
            loadAllSalaries()
            loadMonths()
            setupExport()

        } catch (e: Exception) {
            android.util.Log.e("SalaryFragment", "Error in onViewCreated: ${e.message}")
            showToast("Error loading salary page: ${e.message}")
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        try {
            salariesListener?.remove()
            salariesListener = null
        } catch (_: Exception) {}
    }

    private fun initializeViews(view: View) {
        recyclerView = view.findViewById(R.id.recyclerViewSalaries)
        tvNoSalaries = view.findViewById(R.id.tvNoSalaries)
        actvMonth = view.findViewById(R.id.actvMonth)
        etSearch = view.findViewById(R.id.etSearch)
        btnExportPdf = view.findViewById(R.id.btnExportPdf)

        // Search
        etSearch?.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                searchQuery = s?.toString()?.trim() ?: ""
                applyFilters()
            }
        })

        // Debug: long press toolbar to dump db and run migration
        view.setOnLongClickListener {
            lifecycleScope.launch {
                try {
                    android.util.Log.d("SalaryFragment", "Manual migration triggered")
                    val success = MonthFormatMigration.migrateSalaryMonthFormats()
                    showToast("Migration ${if (success) "completed" else "failed"}")
                    debugDatabaseState()
                } catch (e: Exception) {
                    android.util.Log.e("SalaryFragment", "Manual migration error: ${e.message}")
                    showToast("Migration error: ${e.message}")
                }
            }
            true
        }
    }

    private fun setupSalariesListener() {
        try {
            salariesListener?.remove()
            salariesListener = firestore.collection("salaries")
                .orderBy("month", Query.Direction.DESCENDING)
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null) return@addSnapshotListener
                    val list = snapshot.documents.mapNotNull { doc ->
                        doc.toObject(Salary::class.java)?.copy(id = doc.id)
                    }
                    salaries.clear()
                    salaries.addAll(list)
                    applyFilters()
                }
        } catch (e: Exception) {
            android.util.Log.e("SalaryFragment", "Failed to set salaries listener: ${e.message}")
        }
    }

    private fun loadAllSalaries() {
        lifecycleScope.launch {
            try {
                val all = salaryManager.getAllSalaries()
                salaries.clear(); salaries.addAll(all)
                applyFilters()
            } catch (e: Exception) {
                showToast("Error loading salary data: ${e.message}")
            }
        }
    }

    private fun loadMonths() {
        lifecycleScope.launch {
            try {
                availableMonths = salaryManager.getAvailableMonths()
                val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, availableMonths)
                actvMonth?.setAdapter(adapter)
                if (selectedMonth == null && availableMonths.isNotEmpty()) {
                    selectedMonth = availableMonths.first()
                    actvMonth?.setText(selectedMonth, false)
                }
                actvMonth?.setOnItemClickListener { _, _, position, _ ->
                    selectedMonth = availableMonths.getOrNull(position)
                    applyFilters()
                }
                applyFilters()
            } catch (e: Exception) {
                android.util.Log.e("SalaryFragment", "Error loading months: ${e.message}")
            }
        }
    }

    private fun setupRecyclerView() {
        recyclerView?.layoutManager = LinearLayoutManager(requireContext())
        updateSalaryList()
    }

    private fun applyFilters() {
        val monthFiltered = if (selectedMonth.isNullOrEmpty()) salaries else salaries.filter { it.month == selectedMonth }
        filtered = if (searchQuery.isEmpty()) monthFiltered else monthFiltered.filter { it.employeeName.contains(searchQuery, ignoreCase = true) }
        updateSalaryList()
    }

    private fun updateSalaryList() {
        try {
            if (filtered.isEmpty()) {
                recyclerView?.visibility = View.GONE
                tvNoSalaries?.visibility = View.VISIBLE
            } else {
                recyclerView?.visibility = View.VISIBLE
                tvNoSalaries?.visibility = View.GONE
                currentAdapter = SalaryAdapter(filtered) { salary ->
                    // Reuse EXISTING PDF maker (single salary)
                    exportPdf(
                        month = salary.month,
                        salaries = listOf(salary)
                    )
                }
                recyclerView?.adapter = currentAdapter
            }
        } catch (e: Exception) {
            android.util.Log.e("SalaryFragment", "Error updating salary list: ${e.message}")
        }
    }

    private fun setupExport() {
        btnExportPdf?.setOnClickListener {
            val month = selectedMonth ?: run {
                Toast.makeText(requireContext(), "Select a month first", Toast.LENGTH_SHORT).show(); return@setOnClickListener
            }
            exportPdf(month, filtered)
        }
    }

    private fun exportPdf(month: String, salaries: List<Salary>) {
        try {
            if (salaries.isEmpty()) {
                Toast.makeText(requireContext(), "No data for $month", Toast.LENGTH_SHORT).show(); return
            }
            val pdf = android.graphics.pdf.PdfDocument()
            val paint = android.graphics.Paint()
            paint.textSize = 12f
            val titlePaint = android.graphics.Paint().apply { textSize = 18f; isFakeBoldText = true }
            val headerPaint = android.graphics.Paint().apply { textSize = 13f; isFakeBoldText = true }
            val pageWidth = 595; val pageHeight = 842 // A4 at 72dpi in points

            // Table column X positions (widened columns, compacted for better fit and no overlap)
            val colEmployee = 40f
            val colRole = 150f
            val colBranch = 240f
            val colBase = 320f
            val colPresent = 380f
            val colAbsent = 405f
            val colDeduction = 445f
            val colFinal = 505f

            val rowHeight = 30
            val textBaselineOffset = 20
            val startY = 80
            val headerY = 70
            val footerSpace = 50

            var y = startY
            var page: android.graphics.pdf.PdfDocument.Page? = null
            var canvas: android.graphics.Canvas? = null
            var rowCount = 0
            var totalFinal = 0.0

            // --- Table border helpers ---
            fun drawRowLine(canvas: android.graphics.Canvas, y: Int) {
                canvas.drawLine(30f, y.toFloat(), 560f, y.toFloat(), paint)
            }

            fun drawVerticalLines(canvas: android.graphics.Canvas, top: Int, bottom: Int) {
                val rightBorder = colFinal + 30f
                val xs = listOf(
                    30f,
                    colEmployee - 10f,
                    colRole - 10f,
                    colBranch - 10f,
                    colBase - 10f,
                    colPresent - 10f,
                    colAbsent - 10f,
                    colDeduction - 10f,
                    colFinal - 10f,
                    rightBorder
                )
                xs.forEach { x ->
                    canvas.drawLine(x, top.toFloat(), x, bottom.toFloat(), paint)
                }
            }
            // --- End table border helpers ---

            fun drawHeader(canvas: android.graphics.Canvas, y: Int) {
                canvas.drawText("Employee", colEmployee, y.toFloat(), headerPaint)
                canvas.drawText("Role", colRole, y.toFloat(), headerPaint)
                canvas.drawText("Branch", colBranch, y.toFloat(), headerPaint)
                canvas.drawText("Base", colBase, y.toFloat(), headerPaint)
                canvas.drawText("P", colPresent, y.toFloat(), headerPaint)
                canvas.drawText("A", colAbsent, y.toFloat(), headerPaint)
                canvas.drawText("Deduction", colDeduction, y.toFloat(), headerPaint)
                canvas.drawText("Final", colFinal, y.toFloat(), headerPaint)
            }

            fun newPage(): android.graphics.pdf.PdfDocument.Page {
                val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pdf.pages.size + 1).create()
                val page = pdf.startPage(pageInfo)
                val c = page.canvas
                // Title and month
                c.drawText("Double Dot Academy - Salaries", 40f, 30f, titlePaint)
                c.drawText("Month: $month", 40f, 50f, paint)
                drawHeader(c, headerY)
                // Draw horizontal line below header
                drawRowLine(c, headerY + 5)
                return page
            }

            fun finishCurrentPage() {
                page?.let { pdf.finishPage(it) }
            }

            // Track table top for vertical borders
            val tableTop = headerY - 10

            page = newPage()
            canvas = page!!.canvas
            y = startY
            rowCount = 0

            for (s in salaries) {
                // Check if we need a new page for next row (leave room for footer)
                if (y > pageHeight - footerSpace) {
                    // Draw vertical lines for previous page
                    drawVerticalLines(canvas!!, tableTop, y)
                    finishCurrentPage()
                    page = newPage()
                    canvas = page!!.canvas
                    y = startY
                }

                val textY = y + textBaselineOffset

                canvas!!.drawText(s.employeeName.take(14), colEmployee, textY.toFloat(), paint)
                canvas!!.drawText(s.role.take(10), colRole, textY.toFloat(), paint)
                canvas!!.drawText((s.branch ?: "").take(10), colBranch, textY.toFloat(), paint)
                canvas!!.drawText(String.format("%.2f", s.baseSalary), colBase, textY.toFloat(), paint)

                val presentDays = s.totalWorkingDays - s.absenceDays
                canvas!!.drawText(presentDays.toString(), colPresent, textY.toFloat(), paint)
                canvas!!.drawText(s.absenceDays.toString(), colAbsent, textY.toFloat(), paint)
                canvas!!.drawText(String.format("%.2f", s.deductionAmount), colDeduction, textY.toFloat(), paint)
                canvas!!.drawText(String.format("%.2f", s.finalSalary), colFinal, textY.toFloat(), paint)

                // bottom border of the row
                drawRowLine(canvas!!, y + rowHeight)

                y += rowHeight
                totalFinal += s.finalSalary
                rowCount++
            }

            // Draw final horizontal line to close the table
            drawRowLine(canvas!!, y)
            drawVerticalLines(canvas!!, tableTop, y)

            // Add spacing before TOTAL
            y += 30

            // Draw total below table, no vertical borders
            canvas!!.drawText("Total:", colDeduction - 20f, y.toFloat(), titlePaint)
            canvas!!.drawText(String.format("%.2f", totalFinal), colFinal - 10f, y.toFloat(), titlePaint)
            finishCurrentPage()

            // Save to Downloads using MediaStore
            val fileName = "salaries_${month.replace(" ", "_")}.pdf"
            val contentValues = android.content.ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                put(MediaStore.Downloads.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
            }
            val resolver = requireContext().contentResolver
            val itemUri = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            } else {
                // Fallback for older Android versions - save to app's internal storage
                val file = java.io.File(requireContext().getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS), fileName)
                file.outputStream().use { out -> pdf.writeTo(out) }
                null // We've already written the file, so return null to skip the resolver.insert
            }
            if (itemUri == null) { Toast.makeText(requireContext(), "Save failed", Toast.LENGTH_SHORT).show(); return }
            resolver.openOutputStream(itemUri)?.use { out -> pdf.writeTo(out) }
            pdf.close()
            Toast.makeText(requireContext(), "Saved to Downloads/$fileName", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            android.util.Log.e("SalaryFragment", "Error exporting PDF: ${e.message}")
            Toast.makeText(requireContext(), "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun showToast(message: String) { Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show() }

    private fun debugDatabaseState() {
        lifecycleScope.launch {
            try {
                // Test month format normalization
                MonthFormatMigration.testMonthFormatNormalization()
                salaryManager.debugDatabaseState()
            } catch (e: Exception) {
                android.util.Log.e("SalaryFragment", "Debug error: ${e.message}")
            }
        }
    }

    companion object { fun newInstance(): SalaryFragment = SalaryFragment() }

    private fun observeSalaryMode() {
        firestore.collection("settings")
            .document("payroll")
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null || !snap.exists()) return@addSnapshotListener
                val mode = snap.getString("salaryMode") ?: "monthly"
                if (mode == "live") {
                    lifecycleScope.launch {
                        try {
                            val employees = firestore.collection("employees")
                                .whereIn("role", listOf("coach", "admin"))
                                .get()
                                .await()
                            for (doc in employees.documents) {
                                salaryManager.recalculateSalaryForCoach(doc.id)
                            }
                        } catch (_: Exception) {}
                    }
                }
            }
    }
}
