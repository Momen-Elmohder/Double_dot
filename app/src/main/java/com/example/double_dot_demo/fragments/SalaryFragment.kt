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
import kotlinx.coroutines.launch
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
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
            
            lifecycleScope.launch {
                try {
                    val rolled = salaryManager.performMonthlyRolloverIfNeeded()
                    android.util.Log.d("SalaryFragment", "Monthly rollover executed: $rolled")
                } catch (_: Exception) {}
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

        // Debug: long press toolbar to dump db
        view.setOnLongClickListener {
            debugDatabaseState()
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
                currentAdapter = SalaryAdapter(filtered)
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
            val titlePaint = android.graphics.Paint().apply { textSize = 18f; isFakeBoldText = true }
            val pageWidth = 595; val pageHeight = 842 // A4 at 72dpi in points

            var y = 60
            fun newPage(): android.graphics.pdf.PdfDocument.Page {
                val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pdf.pages.size + 1).create()
                val page = pdf.startPage(pageInfo)
                y = 60
                page.canvas.drawText("Double Dot Academy - Salaries", 40f, 30f, titlePaint)
                page.canvas.drawText("Month: $month", 40f, 50f, paint)
                return page
            }

            var page = newPage()
            var totalFinal = 0.0
            salaries.forEach { s ->
                val line = "${s.employeeName}  |  ${s.role}  |  ${String.format("%.2f", s.finalSalary)}"
                if (y > pageHeight - 40) { pdf.finishPage(page); page = newPage() }
                page.canvas.drawText(line, 40f, y.toFloat(), paint)
                y += 18
                totalFinal += s.finalSalary
            }
            if (y > pageHeight - 40) { pdf.finishPage(page); page = newPage() }
            page.canvas.drawText("Total: ${String.format("%.2f", totalFinal)}", 40f, (y + 20).toFloat(), titlePaint)
            pdf.finishPage(page)

            // Save to Downloads using MediaStore
            val fileName = "salaries_${month.replace(" ", "_")}.pdf"
            val contentValues = android.content.ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                put(MediaStore.Downloads.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
            }
            val resolver = requireContext().contentResolver
            val uri = MediaStore.Downloads.EXTERNAL_CONTENT_URI
            val itemUri = resolver.insert(uri, contentValues)
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
        lifecycleScope.launch { try { salaryManager.debugDatabaseState() } catch (_: Exception) {} }
    }

    companion object { fun newInstance(): SalaryFragment = SalaryFragment() }
}

