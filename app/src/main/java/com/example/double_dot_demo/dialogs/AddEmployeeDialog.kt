package com.example.double_dot_demo.dialogs

import android.app.DatePickerDialog
import android.content.Context
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.example.double_dot_demo.R
import com.example.double_dot_demo.databinding.DialogAddEmployeeBinding
import com.example.double_dot_demo.models.Employee
import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.*

class AddEmployeeDialog(
    private val context: Context,
    private val employee: Employee? = null
) {
    private lateinit var binding: DialogAddEmployeeBinding
    private lateinit var dialog: AlertDialog
    private val calendar = Calendar.getInstance()
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    private var onSaveClickListener: ((Employee) -> Unit)? = null

    fun setOnSaveClickListener(listener: (Employee) -> Unit) {
        onSaveClickListener = listener
    }

    fun show() {
        binding = DialogAddEmployeeBinding.inflate(LayoutInflater.from(context))
        
        setupViews()
        setupRoleDropdown()
        
        if (employee != null) {
            populateFields(employee)
        }

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

        binding.btnSave.setOnClickListener {
            if (validateInputs()) {
                val employeeData = createEmployeeFromInputs()
                onSaveClickListener?.invoke(employeeData)
                dialog.dismiss()
            }
        }
    }

    // Date picker setup removed - no longer needed for hire date

    private fun setupRoleDropdown() {
        val roles = listOf("Coach", "Admin", "Head Coach")
        val adapter = ArrayAdapter(context, android.R.layout.simple_dropdown_item_1line, roles)
        binding.actvRole.setAdapter(adapter)
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
        datePickerDialog.show()
    }

    private fun populateFields(employee: Employee) {
        binding.tvDialogTitle.text = "Edit Employee"
        binding.etName.setText(employee.name)
        binding.etEmail.setText(employee.email)
        binding.etPhone.setText(employee.phone)
        binding.actvRole.setText(employee.role.replace("_", " ").replaceFirstChar { it.uppercase() })
        binding.etBranch.setText(employee.branch)
        binding.etTotalDays.setText(employee.totalDays.toString())
    }

    private fun validateInputs(): Boolean {
        val name = binding.etName.text.toString().trim()
        val email = binding.etEmail.text.toString().trim()
        val phone = binding.etPhone.text.toString().trim()
        val role = binding.actvRole.text.toString().trim()
        val branch = binding.etBranch.text.toString().trim()
        val totalDaysText = binding.etTotalDays.text.toString().trim()

        if (name.isEmpty()) {
            binding.tilName.error = "Name is required"
            return false
        }

        if (email.isEmpty()) {
            binding.tilEmail.error = "Email is required"
            return false
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.tilEmail.error = "Please enter a valid email"
            return false
        }

        if (phone.isEmpty()) {
            binding.tilPhone.error = "Phone number is required"
            return false
        }

        if (role.isEmpty()) {
            binding.tilRole.error = "Please select a role"
            return false
        }

        if (branch.isEmpty()) {
            binding.tilBranch.error = "Branch is required"
            return false
        }

        if (totalDaysText.isEmpty()) {
            binding.tilTotalDays.error = "Total days is required"
            return false
        }

        val totalDays = totalDaysText.toIntOrNull()
        if (totalDays == null || totalDays < 0) {
            binding.tilTotalDays.error = "Please enter a valid number of days"
            return false
        }

        return true
    }

    private fun createEmployeeFromInputs(): Employee {
        val name = binding.etName.text.toString().trim()
        val email = binding.etEmail.text.toString().trim()
        val phone = binding.etPhone.text.toString().trim()
        val role = binding.actvRole.text.toString().trim().lowercase().replace(" ", "_")
        val branch = binding.etBranch.text.toString().trim()
        val totalDays = binding.etTotalDays.text.toString().toInt()

        return Employee(
            id = employee?.id ?: "",
            name = name,
            email = email,
            role = role,
            phone = phone,
            branch = branch,
            totalDays = totalDays,
            remainingDays = totalDays,
            status = "active"
        )
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