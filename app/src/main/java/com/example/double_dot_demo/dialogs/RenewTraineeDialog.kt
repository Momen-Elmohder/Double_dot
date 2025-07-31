package com.example.double_dot_demo.dialogs

import android.app.DatePickerDialog
import android.content.Context
import android.view.LayoutInflater
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.example.double_dot_demo.R
import com.example.double_dot_demo.databinding.DialogRenewTraineeBinding
import com.example.double_dot_demo.models.Trainee
import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.*

class RenewTraineeDialog(
    private val context: Context,
    private val trainee: Trainee
) {
    private lateinit var binding: DialogRenewTraineeBinding
    private lateinit var dialog: AlertDialog
    private val calendar = Calendar.getInstance()
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    private var onRenewClickListener: ((Trainee, Timestamp) -> Unit)? = null

    fun setOnRenewClickListener(listener: (Trainee, Timestamp) -> Unit) {
        onRenewClickListener = listener
    }

    fun show() {
        binding = DialogRenewTraineeBinding.inflate(LayoutInflater.from(context))
        
        setupViews()
        setupDatePicker()
        populateFields()
        
        dialog = AlertDialog.Builder(context)
            .setView(binding.root)
            .setCancelable(false)
            .create()

        dialog.show()
    }

    private fun setupViews() {
        binding.btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        binding.btnRenew.setOnClickListener {
            if (validateInputs()) {
                val newEndDate = parseDate(binding.etNewEndDate.text.toString())
                if (newEndDate != null) {
                    onRenewClickListener?.invoke(trainee, newEndDate)
                    dialog.dismiss()
                } else {
                    Toast.makeText(context, "Please enter a valid date", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun setupDatePicker() {
        binding.etNewEndDate.setOnClickListener {
            showDatePicker(binding.etNewEndDate)
        }
    }

    private fun showDatePicker(editText: com.google.android.material.textfield.TextInputEditText) {
        val datePickerDialog = DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                calendar.set(year, month, dayOfMonth)
                editText.setText(dateFormat.format(calendar.time))
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        
        // Set minimum date to today
        datePickerDialog.datePicker.minDate = System.currentTimeMillis()
        datePickerDialog.show()
    }

    private fun populateFields() {
        binding.tvTraineeName.text = trainee.name
        binding.tvCurrentEndDate.text = trainee.endingDate?.let { 
            "Current End Date: ${dateFormat.format(it.toDate())}" 
        } ?: "No end date set"
        
        // Set default new end date to 3 months from now
        calendar.add(Calendar.MONTH, 3)
        binding.etNewEndDate.setText(dateFormat.format(calendar.time))
    }

    private fun validateInputs(): Boolean {
        val newEndDate = binding.etNewEndDate.text.toString().trim()

        if (newEndDate.isEmpty()) {
            binding.tilNewEndDate.error = "New end date is required"
            return false
        }

        val parsedDate = parseDate(newEndDate)
        if (parsedDate == null) {
            binding.tilNewEndDate.error = "Please enter a valid date"
            return false
        }

        // Check if new date is in the future
        if (parsedDate.toDate().before(Date())) {
            binding.tilNewEndDate.error = "End date must be in the future"
            return false
        }

        return true
    }

    private fun parseDate(dateString: String): Timestamp? {
        return try {
            val date = dateFormat.parse(dateString)
            Timestamp(date)
        } catch (e: Exception) {
            null
        }
    }
} 