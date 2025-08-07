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
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

class AddTraineeDialog(
    private val context: Context,
    private val trainee: Trainee? = null
) {
    private lateinit var binding: DialogAddTraineeBinding
    private lateinit var dialog: AlertDialog
    private val calendar = Calendar.getInstance()
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    private val db = FirebaseFirestore.getInstance()
    private val coaches = mutableListOf<String>()
    private val coachNameToId = mutableMapOf<String, String>()
    private var coachesListener: com.google.firebase.firestore.ListenerRegistration? = null

    private var onSaveClickListener: ((Trainee) -> Unit)? = null

    fun setOnSaveClickListener(listener: (Trainee) -> Unit) {
        onSaveClickListener = listener
    }

    fun show() {
        binding = DialogAddTraineeBinding.inflate(LayoutInflater.from(context))
        
        setupViews()
        setupBranchDropdown()
        setupStatusDropdown()
        
        if (trainee != null) {
            populateFields(trainee)
        }

        dialog = AlertDialog.Builder(context)
            .setView(binding.root)
            .setCancelable(false)
            .create()

        dialog.show()
        
        // Load coaches after dialog is shown to ensure proper initialization
        loadCoaches()
    }

    private fun setupViews() {
        binding.btnCancel.setOnClickListener {
            cleanup()
            dialog.dismiss()
        }

        binding.btnSave.setOnClickListener {
            if (validateInputs()) {
                val traineeData = createTraineeFromInputs()
                onSaveClickListener?.invoke(traineeData)
                cleanup()
                dialog.dismiss()
            }
        }
    }

    private fun loadCoaches() {
        try {
            // First, try to get coaches immediately with a one-time query
            db.collection("employees")
                .whereEqualTo("role", "coach")
                .get()
                .addOnSuccessListener { snapshot ->
                    coaches.clear()
                    coachNameToId.clear()
                    if (snapshot != null) {
                        for (document in snapshot) {
                            val coachId = document.id
                            val name = document.getString("name")
                            if (!name.isNullOrBlank()) {
                                coaches.add(name)
                                coachNameToId[name] = coachId
                            }
                        }
                    }
                    
                    android.util.Log.d("AddTraineeDialog", "Initial load: ${coaches.size} coaches: $coaches")
                    
                    // Setup coach dropdown immediately
                    val adapter = ArrayAdapter(context, android.R.layout.simple_dropdown_item_1line, coaches)
                    binding.actvCoach.setAdapter(adapter)
                    
                    // Set default selection if editing
                    if (trainee != null && trainee.coachName != null) {
                        binding.actvCoach.setText(trainee.coachName, false)
                    }
                }
                .addOnFailureListener { e ->
                    android.util.Log.e("AddTraineeDialog", "Error in initial coach load: ${e.message}")
                }
            
            // Then set up real-time listener for updates
            coachesListener = db.collection("employees")
                .whereEqualTo("role", "coach")
                .addSnapshotListener { snapshot, e ->
                    if (e != null) {
                        android.util.Log.e("AddTraineeDialog", "Error loading coaches: ${e.message}")
                        return@addSnapshotListener
                    }

                    coaches.clear()
                    coachNameToId.clear()
                    if (snapshot != null) {
                        for (document in snapshot) {
                            val coachId = document.id
                            val name = document.getString("name")
                            if (!name.isNullOrBlank()) {
                                coaches.add(name)
                                coachNameToId[name] = coachId
                            }
                        }
                    }
                    
                    android.util.Log.d("AddTraineeDialog", "Real-time update: ${coaches.size} coaches: $coaches")
                    
                    // Update coach dropdown
                    val adapter = ArrayAdapter(context, android.R.layout.simple_dropdown_item_1line, coaches)
                    binding.actvCoach.setAdapter(adapter)
                    
                    // Set default selection if editing
                    if (trainee != null && trainee.coachName != null) {
                        binding.actvCoach.setText(trainee.coachName, false)
                    }
                }
        } catch (e: Exception) {
            android.util.Log.e("AddTraineeDialog", "Error loading coaches: ${e.message}")
        }
    }

    private fun setupBranchDropdown() {
        val branches = listOf("المدينة الرياضية", "نادي اليخت", "نادي التوكيلات")
        val adapter = ArrayAdapter(context, android.R.layout.simple_dropdown_item_1line, branches)
        binding.actvBranch.setAdapter(adapter)
        binding.actvBranch.setText(branches[0], false) // Set default to first option
    }

    private fun setupStatusDropdown() {
        val statuses = listOf("academy", "team", "academy and Preparatonal")
        val adapter = ArrayAdapter(context, android.R.layout.simple_dropdown_item_1line, statuses)
        binding.actvStatus.setAdapter(adapter)
        binding.actvStatus.setText(statuses[0], false) // Set default to first option
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
        binding.actvBranch.setText(trainee.branch)
        binding.actvStatus.setText(trainee.status)
        binding.etTotalSessions.setText(trainee.totalSessions.toString())
        binding.actvCoach.setText(trainee.coachName)
        binding.etMonthlyFee.setText(trainee.monthlyFee.toString())
        // Removed switchPaid since trainees are automatically paid
    }

    private fun validateInputs(): Boolean {
        val name = binding.etName.text.toString().trim()
        val ageText = binding.etAge.text.toString().trim()
        val phoneNumber = binding.etPhoneNumber.text.toString().trim()
        val branch = binding.actvBranch.text.toString().trim()
        val status = binding.actvStatus.text.toString().trim()
        val totalSessionsText = binding.etTotalSessions.text.toString().trim()
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
            binding.tilBranch.error = "Please select a branch"
            return false
        }

        if (status.isEmpty()) {
            binding.tilStatus.error = "Please select a status"
            return false
        }

        if (totalSessionsText.isEmpty()) {
            binding.tilTotalSessions.error = "Total sessions is required"
            return false
        }

        val totalSessions = totalSessionsText.toIntOrNull()
        if (totalSessions == null || totalSessions <= 0) {
            binding.tilTotalSessions.error = "Please enter a valid number of sessions"
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
        val branch = binding.actvBranch.text.toString().trim()
        val status = binding.actvStatus.text.toString().trim()
        val totalSessions = binding.etTotalSessions.text.toString().toInt()
        val coachName = binding.actvCoach.text.toString().trim()
        val coachId = coachNameToId[coachName] ?: ""
        val monthlyFee = binding.etMonthlyFee.text.toString().toInt()
        val isPaid = true // Trainees are automatically paid
        val paymentAmount = if (isPaid) monthlyFee.toDouble() else 0.0

        return Trainee(
            id = trainee?.id ?: "",
            name = name,
            age = age,
            phoneNumber = phoneNumber,
            branch = branch,
            totalSessions = totalSessions,
            remainingSessions = totalSessions,
            coachId = coachId,
            coachName = coachName,
            monthlyFee = monthlyFee,
            paymentAmount = paymentAmount,
            isPaid = isPaid,
            status = status
        )
    }

    private fun parseDate(dateString: String): Timestamp? {
        return try {
            val date = dateFormat.parse(dateString)
            date?.let { Timestamp(it) }
        } catch (e: Exception) {
            null
        }
    }

    fun cleanup() {
        coachesListener?.remove()
        coachesListener = null
    }
} 