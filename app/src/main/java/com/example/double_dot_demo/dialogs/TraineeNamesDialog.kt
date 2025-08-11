package com.example.double_dot_demo.dialogs

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat

class TraineeNamesDialog(
    private val context: Context,
    private val day: String,
    private val time: String,
    private val traineeNames: List<String>
) {
    
    fun show() {
        val builder = AlertDialog.Builder(context)
        
        // Create custom layout
        val scrollView = ScrollView(context)
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 32)
        }
        
        // Set title with day and time
        val title = "$day - $time"
        
        if (traineeNames.isNotEmpty()) {
            // Add each trainee name as a separate TextView for better readability
            traineeNames.forEachIndexed { index, name ->
                val nameView = TextView(context).apply {
                    text = "${index + 1}. $name"
                    textSize = 20f // Large text size
                    setTextColor(ContextCompat.getColor(context, android.R.color.black))
                    setPadding(16, 12, 16, 12)
                    textAlignment = TextView.TEXT_ALIGNMENT_VIEW_START
                    layoutDirection = android.util.LayoutDirection.RTL
                    setTypeface(null, Typeface.NORMAL)
                }
                layout.addView(nameView)
                
                // Add separator line except for last item
                if (index < traineeNames.size - 1) {
                    val separator = TextView(context).apply {
                        text = "────────────────"
                        textSize = 14f
                        setTextColor(Color.GRAY)
                        textAlignment = TextView.TEXT_ALIGNMENT_CENTER
                        setPadding(0, 8, 0, 8)
                    }
                    layout.addView(separator)
                }
            }
        } else {
            // Show empty message
            val emptyView = TextView(context).apply {
                text = "لا يوجد متدربين في هذا الوقت"
                textSize = 18f
                setTextColor(Color.GRAY)
                textAlignment = TextView.TEXT_ALIGNMENT_CENTER
                layoutDirection = android.util.LayoutDirection.RTL
                setPadding(16, 32, 16, 32)
                setTypeface(null, Typeface.ITALIC)
            }
            layout.addView(emptyView)
        }
        
        scrollView.addView(layout)
        
        builder.apply {
            setTitle(title)
            setView(scrollView)
            setPositiveButton("موافق") { dialog, _ ->
                dialog.dismiss()
            }
            setCancelable(true)
        }
        
        val dialog = builder.create()
        dialog.show()
        
        // Make the dialog wider for better readability
        dialog.window?.setLayout(
            (context.resources.displayMetrics.widthPixels * 0.9).toInt(),
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
    }
}