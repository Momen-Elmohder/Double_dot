package com.example.double_dot_demo.dialogs

import android.app.DatePickerDialog
import android.content.Context
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.example.double_dot_demo.R
import com.example.double_dot_demo.databinding.DialogAddTraineeBinding
import com.example.double_dot_demo.models.Trainee
import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.*

class AddTraineeDialog(
    private val context: Context,
    private val coaches: List<String>,
    private val trainee: Trainee? = null
) {
    private lateinit var binding: DialogAddTraineeBinding
    private lateinit var dialog: AlertDialog
    private val calendar = Calendar.getInstance()
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    private var onSaveClickListener: ((Trainee) -> Unit)? = null

    fun setOnSaveClickListener(listener: (Trainee) -> Unit) {
        onSaveClickListener = listener
    }

    fun show() {
        binding = DialogAddTraineeBinding.inflate(LayoutInflater.from(context))
        
        setupViews()
        setupDatePickers()
        setupCoachDropdown()
        
        if (trainee != null) {
            populateFields(trainee)
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
                val traineeData = createTraineeFromInputs()
                onSaveClickListener?.invoke(traineeData)
                dialog.dismiss()
            }
        }
    }

    private fun setupDatePickers() {
        binding.etStartingDate.setOnClickListener {
            showDatePicker(binding.etStartingDate)
        }

        binding.etEndingDate.setOnClickListener {
            showDatePicker(binding.etEndingDate)
        }
    }

    private fun setupCoachDropdown() {
        val adapter = ArrayAdapter(context, android.R.layout.simple_dropdown_item_1line, coaches)
        binding.actvCoach.setAdapter(adapter)
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

    private fun populateFields(trainee: Trainee) {
        binding.tvDialogTitle.text = "Edit Trainee"
        binding.etName.setText(trainee.name)
        binding.etAge.setText(trainee.age.toString())
        binding.etPhoneNumber.setText(trainee.phoneNumber)
        binding.etBranch.setText(trainee.branch)
        
        trainee.startingDate?.let { 
            binding.etStartingDate.setText(dateFormat.format(it.toDate()))
        }
        
        trainee.endingDate?.let { 
            binding.etEndingDate.setText(dateFormat.format(it.toDate()))
        }
        
        binding.actvCoach.setText(trainee.coachName)
        binding.etMonthlyFee.setText(trainee.monthlyFee.toString())
        binding.switchPaid.isChecked = trainee.isPaid
    }

    private fun validateInputs(): Boolean {
        val name = binding.etName.text.toString().trim()
        val ageText = binding.etAge.text.toString().trim()
        val phoneNumber = binding.etPhoneNumber.text.toString().trim()
        val branch = binding.etBranch.text.toString().trim()
        val startingDate = binding.etStartingDate.text.toString().trim()
        val coach = binding.actvCoach.text.toString().trim()
        val monthlyFeeText = binding.etMonthlyFee.text.toString().trim()

        if (name.isEmpty()) {
            binding.tilName.error = "Name is required"
            return false
        }

        if (ageText.isEmpty()) {
            binding.tilAge.error = "Age is required"
            return false
        }

        val age = ageText.toIntOrNull()
        if (age == null || age <= 0) {
            binding.tilAge.error = "Please enter a valid age"
            return false
        }

        if (phoneNumber.isEmpty()) {
            binding.tilPhoneNumber.error = "Phone number is required"
            return false
        }

        if (branch.isEmpty()) {
            binding.tilBranch.error = "Branch is required"
            return false
        }

        if (startingDate.isEmpty()) {
            binding.tilStartingDate.error = "Starting date is required"
            return false
        }

        if (coach.isEmpty()) {
            binding.tilCoach.error = "Please select a coach"
            return false
        }

        if (monthlyFeeText.isEmpty()) {
            binding.tilMonthlyFee.error = "Monthly fee is required"
            return false
        }

        val monthlyFee = monthlyFeeText.toIntOrNull()
        if (monthlyFee == null || monthlyFee < 0) {
            binding.tilMonthlyFee.error = "Please enter a valid fee"
            return false
        }

        return true
    }

    private fun createTraineeFromInputs(): Trainee {
        val name = binding.etName.text.toString().trim()
        val age = binding.etAge.text.toString().toInt()
        val phoneNumber = binding.etPhoneNumber.text.toString().trim()
        val branch = binding.etBranch.text.toString().trim()
        val startingDate = parseDate(binding.etStartingDate.text.toString())
        val endingDate = if (binding.etEndingDate.text.toString().isNotEmpty()) {
            parseDate(binding.etEndingDate.text.toString())
        } else null
        val coachName = binding.actvCoach.text.toString().trim()
        val monthlyFee = binding.etMonthlyFee.text.toString().toInt()
        val isPaid = binding.switchPaid.isChecked

        return Trainee(
            id = trainee?.id ?: "",
            name = name,
            age = age,
            phoneNumber = phoneNumber,
            branch = branch,
            startingDate = startingDate,
            endingDate = endingDate,
            coachName = coachName,
            monthlyFee = monthlyFee,
            isPaid = isPaid,
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