package com.example.double_dot_demo.dialogs

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.provider.MediaStore
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.example.double_dot_demo.R
import com.example.double_dot_demo.models.Salary
import java.text.NumberFormat
import java.util.*

class SalaryDetailsDialog(
    private val context: Context,
    private val salary: Salary
) {
    private val numberFormat = NumberFormat.getCurrencyInstance(Locale.US)
    
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
            text = "${salary.employeeName} - Salary Details"
            textSize = 22f
            setTextColor(ContextCompat.getColor(context, R.color.primary_light))
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 16)
        }
        mainLayout.addView(titleView)
        
        // Month and Role info
        addInfoSection(mainLayout, "Month", salary.month)
        addInfoSection(mainLayout, "Role", salary.role.replace("_", " ").replaceFirstChar { it.uppercase() })
        
        addDivider(mainLayout)
        
        // Salary breakdown
        addSectionHeader(mainLayout, "💰 Salary Breakdown")
        val totalIncome = if (salary.totalPayments > 0) salary.totalPayments / 0.4 else 0.0
        addAmountSection(mainLayout, "Total Income", totalIncome, R.color.primary_light)
        if (salary.deductionAmount > 0) { addAmountSection(mainLayout, "Total Deductions", -salary.deductionAmount, android.R.color.holo_red_dark) }
        addAmountSection(mainLayout, "Final Salary", salary.finalSalary, R.color.primary_light, true)
        
        addDivider(mainLayout)
        
        // Trainees section
        if (salary.traineeDetails.isNotEmpty()) {
            addSectionHeader(mainLayout, "👥 Trainees (${salary.traineeDetails.size})")
            salary.traineeDetails.forEach { trainee -> addTraineeItem(mainLayout, trainee.traineeName, trainee.monthlyFee) }
            addAmountSection(mainLayout, "Total from Trainees", salary.totalPayments, R.color.primary_light)
        } else { addInfoSection(mainLayout, "Trainees", "No trainees assigned") }
        
        addDivider(mainLayout)
        
        if (salary.deductionDetails.isNotEmpty()) {
            addSectionHeader(mainLayout, "📉 Deductions")
            salary.deductionDetails.forEach { d -> addDeductionItem(mainLayout, d.type, d.description, d.amount) }
        } else { addInfoSection(mainLayout, "Deductions", "No deductions") }
        
        if (salary.totalWorkingDays > 0) {
            addDivider(mainLayout)
            addSectionHeader(mainLayout, "📅 Attendance")
            addInfoSection(mainLayout, "Working Days", "${salary.totalWorkingDays} days")
            addInfoSection(mainLayout, "Absence Days", "${salary.absenceDays} days")
            addInfoSection(mainLayout, "Absence Rate", "${String.format("%.1f", salary.absencePercentage)}%")
        }
        
        scrollView.addView(mainLayout)
        
        builder.apply {
            setView(scrollView)
            setPositiveButton("موافق") { dialog, _ -> dialog.dismiss() }
            setNeutralButton("Export PDF") { _, _ -> exportPdf() }
            setCancelable(true)
        }
        
        val dialog = builder.create(); dialog.show()
        dialog.window?.setLayout((context.resources.displayMetrics.widthPixels * 0.9).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT)
    }
    
    private fun exportPdf() {
        try {
            val pdf = android.graphics.pdf.PdfDocument()
            val paint = android.graphics.Paint()
            val titlePaint = android.graphics.Paint().apply { textSize = 18f; isFakeBoldText = true }
            val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(595, 842, 1).create()
            val page = pdf.startPage(pageInfo)
            var y = 40

            fun drawLine(text: String, bold: Boolean = false) {
                val p = android.graphics.Paint(paint).apply { textSize = if (bold) 16f else 14f; isFakeBoldText = bold }
                page.canvas.drawText(text, 40f, y.toFloat(), p); y += if (bold) 20 else 18
            }

            page.canvas.drawText("Double Dot Academy - Salary Details", 40f, y.toFloat(), titlePaint); y += 22
            drawLine("Employee: ${salary.employeeName}", true)
            drawLine("Month: ${salary.month}")
            drawLine("Role: ${salary.role}")
            y += 8
            drawLine("Breakdown", true)
            val totalIncome = if (salary.totalPayments > 0) salary.totalPayments / 0.4 else 0.0
            drawLine("Total Income: ${numberFormat.format(totalIncome)}")
            if (salary.deductionAmount > 0) drawLine("Total Deductions: -${numberFormat.format(salary.deductionAmount)}")
            drawLine("Final Salary: ${numberFormat.format(salary.finalSalary)}", true)
            y += 8
            if (salary.traineeDetails.isNotEmpty()) {
                drawLine("Trainees (${salary.traineeDetails.size})", true)
                salary.traineeDetails.forEach { t -> drawLine("• ${t.traineeName}  ${numberFormat.format(t.monthlyFee)}") }
            }
            y += 8
            if (salary.totalWorkingDays > 0) {
                drawLine("Attendance", true)
                drawLine("Working Days: ${salary.totalWorkingDays}")
                drawLine("Absence Days: ${salary.absenceDays}")
                drawLine("Absence Rate: ${String.format("%.1f", salary.absencePercentage)}%")
            }

            pdf.finishPage(page)

            val fileName = "salary_${salary.employeeName.replace(" ", "_")}_${salary.month.replace(" ", "_")}.pdf"
            val values = android.content.ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                put(MediaStore.Downloads.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            if (uri == null) { android.widget.Toast.makeText(context, "Save failed", android.widget.Toast.LENGTH_SHORT).show(); return }
            resolver.openOutputStream(uri)?.use { out -> pdf.writeTo(out) }
            pdf.close()
            android.widget.Toast.makeText(context, "Saved to Downloads/$fileName", android.widget.Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            android.util.Log.e("SalaryDetailsDialog", "PDF export failed: ${e.message}")
            android.widget.Toast.makeText(context, "Export failed: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
        }
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
    
    private fun addInfoSection(parent: LinearLayout, label: String, value: String) {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 4, 0, 4)
        }
        
        val labelView = TextView(context).apply {
            text = "$label:"
            textSize = 16f
            setTextColor(Color.BLACK)
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        
        val valueView = TextView(context).apply {
            text = value
            textSize = 16f
            setTextColor(Color.GRAY)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        
        container.addView(labelView)
        container.addView(valueView)
        parent.addView(container)
    }
    
    private fun addAmountSection(parent: LinearLayout, label: String, amount: Double, colorRes: Int, isBold: Boolean = false) {
        val container = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 8, 0, 8) }
        val labelView = TextView(context).apply { text = "$label:"; textSize = if (isBold) 18f else 16f; setTextColor(Color.BLACK); setTypeface(null, if (isBold) Typeface.BOLD else Typeface.NORMAL); layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) }
        val valueView = TextView(context).apply { text = numberFormat.format(amount); textSize = if (isBold) 18f else 16f; setTextColor(ContextCompat.getColor(context, colorRes)); setTypeface(null, if (isBold) Typeface.BOLD else Typeface.NORMAL); layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) }
        container.addView(labelView); container.addView(valueView); parent.addView(container)
    }
    
    private fun addTraineeItem(parent: LinearLayout, name: String, fee: Double) {
        val container = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; setPadding(16, 4, 0, 4) }
        val nameView = TextView(context).apply { text = "• $name"; textSize = 14f; setTextColor(Color.BLACK); layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) }
        val feeView = TextView(context).apply { text = numberFormat.format(fee); textSize = 14f; setTextColor(ContextCompat.getColor(context, R.color.primary_light)); layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) }
        container.addView(nameView); container.addView(feeView); parent.addView(container)
    }
    
    private fun addDeductionItem(parent: LinearLayout, type: String, description: String, amount: Double) {
        val container = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(16, 4, 0, 8) }
        val typeView = TextView(context).apply { text = "• $type"; textSize = 14f; setTextColor(Color.BLACK); setTypeface(null, Typeface.BOLD) }
        val descView = TextView(context).apply { text = description; textSize = 12f; setTextColor(Color.GRAY); setPadding(16, 2, 0, 2) }
        val amountView = TextView(context).apply { text = "- ${numberFormat.format(amount)}"; textSize = 14f; setTextColor(ContextCompat.getColor(context, android.R.color.holo_red_dark)); setTypeface(null, Typeface.BOLD) }
        container.addView(typeView); container.addView(descView); container.addView(amountView); parent.addView(container)
    }
    
    private fun addDivider(parent: LinearLayout) {
        val divider = TextView(context).apply { text = "────────────────────────────────"; textSize = 14f; setTextColor(Color.LTGRAY); textAlignment = TextView.TEXT_ALIGNMENT_CENTER; setPadding(0, 12, 0, 12) }
        parent.addView(divider)
    }
}
