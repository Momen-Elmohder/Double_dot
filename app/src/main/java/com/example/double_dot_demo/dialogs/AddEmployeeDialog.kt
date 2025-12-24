package com.example.double_dot_demo.dialogs

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.example.double_dot_demo.R
import com.example.double_dot_demo.databinding.DialogAddEmployeeBinding
import com.example.double_dot_demo.models.Employee
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore

class AddEmployeeDialog(
    private val context: Context,
    private val employee: Employee? = null
) {
    private lateinit var binding: DialogAddEmployeeBinding
    private lateinit var dialog: AlertDialog
    private val firestore = FirebaseFirestore.getInstance()

    private var onSaveClickListener: ((Employee) -> Unit)? = null
    private var onPickPhoneClickListener: (() -> Unit)? = null

    fun setOnSaveClickListener(listener: (Employee) -> Unit) {
        onSaveClickListener = listener
    }

    fun setOnPickPhoneClickListener(listener: () -> Unit) {
        onPickPhoneClickListener = listener
    }

    fun setPickedPhoneNumber(number: String) {
        if (::binding.isInitialized) {
            binding.etPhone.setText(number)
        }
    }

    fun show() {
        binding = DialogAddEmployeeBinding.inflate(LayoutInflater.from(context))

        setupViews()
        setupBranchDropdown()

        dialog = AlertDialog.Builder(context)
            .setView(binding.root)
            .setCancelable(true)
            .create()

        dialog.show()
    }

    private fun setupBranchDropdown() {
        val branches = listOf("نادي التوكيلات", "نادي اليخت", "المدينة الرياضية")
        val adapter = ArrayAdapter(context, android.R.layout.simple_dropdown_item_1line, branches)
        binding.actvBranch.setAdapter(adapter)

        binding.actvBranch.setOnClickListener { binding.actvBranch.showDropDown() }
        binding.actvBranch.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) binding.actvBranch.showDropDown()
        }

        binding.actvBranch.setOnItemClickListener { _, _, _, _ ->
            val branch = binding.actvBranch.text.toString().trim()

            if (branch == "المدينة الرياضية") {
                binding.tilSalary.visibility = View.VISIBLE
                binding.tilTotalDays.visibility = View.GONE
                binding.etTotalDays.setText("")
            } else {
                binding.tilSalary.visibility = View.GONE
                binding.etSalary.setText("")
                binding.tilTotalDays.visibility = View.VISIBLE
            }
        }
    }

    private fun setupViews() {
        if (employee != null) {
            binding.etName.setText(employee.name)
            binding.etEmail.setText(employee.email)
            binding.etPhone.setText(employee.phone)
            binding.etTotalDays.setText(employee.totalDays.toString())
            binding.actvBranch.setText(employee.branch, false)
            if (employee.branch.trim() == "المدينة الرياضية") {
                binding.tilSalary.visibility = View.VISIBLE
                binding.tilTotalDays.visibility = View.GONE
                binding.etSalary.setText(employee.salary.toString())
            } else {
                binding.tilSalary.visibility = View.GONE
                binding.tilTotalDays.visibility = View.VISIBLE
            }
        }

        binding.btnCancel.setOnClickListener { dialog.dismiss() }
        binding.btnSave.setOnClickListener { saveEmployee() }

        // contact picker end icon
        binding.tilPhone.setEndIconOnClickListener {
            try { onPickPhoneClickListener?.invoke() } catch (_: Exception) {}
        }
    }

    private fun saveEmployee() {
        val name = binding.etName.text.toString().trim()
        val email = binding.etEmail.text.toString().trim()
        val phone = binding.etPhone.text.toString().trim()
        val branch = binding.actvBranch.text.toString().trim()
        val salary =
            if (branch == "المدينة الرياضية")
                binding.etSalary.text.toString().toDoubleOrNull() ?: 0.0
            else
                0.0
        val totalDays =
            if (branch == "المدينة الرياضية") 50
            else binding.etTotalDays.text.toString().trim().toIntOrNull() ?: 0

        if (name.isEmpty() || email.isEmpty() || phone.isEmpty() || branch.isEmpty() || totalDays <= 0) {
            Toast.makeText(context, "Please fill all fields", Toast.LENGTH_SHORT).show()
            return
        }

        if (branch == "المدينة الرياضية" && salary <= 0.0) {
            Toast.makeText(context, "Please enter salary for المدينة الرياضية", Toast.LENGTH_SHORT).show()
            return
        }

        val updated = (employee ?: Employee()).copy(
            name = name,
            email = email,
            phone = phone,
            branch = branch,
            salary = salary,
            totalDays = totalDays,
            remainingDays = if (employee == null) totalDays else employee.remainingDays,
            updatedAt = Timestamp.now()
        )

        if (employee == null) {
            firestore.collection("employees").add(updated)
                .addOnSuccessListener {
                    Toast.makeText(context, "Employee saved", Toast.LENGTH_SHORT).show()
                    onSaveClickListener?.invoke(updated)
                    dialog.dismiss()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        } else {
            val id = employee.id
            if (id.isBlank()) {
                Toast.makeText(context, "Invalid employee id", Toast.LENGTH_SHORT).show()
                return
            }
            firestore.collection("employees").document(id)
                .set(updated)
                .addOnSuccessListener {
                    Toast.makeText(context, "Employee updated", Toast.LENGTH_SHORT).show()
                    onSaveClickListener?.invoke(updated)
                    dialog.dismiss()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }
}