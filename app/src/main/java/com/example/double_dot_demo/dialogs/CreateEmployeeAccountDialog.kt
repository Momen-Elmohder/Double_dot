package com.example.double_dot_demo.dialogs

import android.content.Context
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.example.double_dot_demo.R
import com.example.double_dot_demo.databinding.DialogCreateEmployeeAccountBinding
import com.example.double_dot_demo.models.Employee
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class CreateEmployeeAccountDialog(
    private val context: Context,
    private val currentUserRole: String
) {
    private lateinit var binding: DialogCreateEmployeeAccountBinding
    private lateinit var dialog: AlertDialog
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    private var onAccountCreatedListener: ((Employee) -> Unit)? = null

    fun setOnAccountCreatedListener(listener: (Employee) -> Unit) {
        onAccountCreatedListener = listener
    }

    fun show() {
        if (currentUserRole != "head_coach" && currentUserRole != "head coach" && currentUserRole != "admin") {
            Toast.makeText(context, "Only Head Coaches and Admins can create employee accounts", Toast.LENGTH_LONG).show()
            return
        }

        binding = DialogCreateEmployeeAccountBinding.inflate(LayoutInflater.from(context))
        
        setupViews()
        setupRoleDropdown()
        setupBranchDropdown()
        
        dialog = AlertDialog.Builder(context)
            .setView(binding.root)
            .setCancelable(false)
            .create()

        dialog.show()
    }

    private fun setupViews() {
        binding.btnCancel.setOnClickListener { dialog.dismiss() }
        binding.btnCreateAccount.setOnClickListener {
            if (validateInputs()) createEmployeeAccount()
        }
    }

    private fun setupRoleDropdown() {
        val availableRoles = when (currentUserRole) {
            "head_coach", "head coach" -> listOf("Coach", "Admin")
            "admin" -> listOf("Coach")
            else -> listOf("Coach")
        }
        val adapter = ArrayAdapter(context, android.R.layout.simple_dropdown_item_1line, availableRoles)
        binding.actvRole.setAdapter(adapter)
        binding.actvRole.setOnClickListener { binding.actvRole.showDropDown() }
        binding.actvRole.setOnFocusChangeListener { _, hasFocus -> if (hasFocus) binding.actvRole.showDropDown() }
    }

    private fun setupBranchDropdown() {
        val branches = listOf("نادي التوكيلات", "نادي اليخت", "المدينة الرياضية")
        val adapter = ArrayAdapter(context, android.R.layout.simple_dropdown_item_1line, branches)
        binding.actvBranch.setAdapter(adapter)
        binding.actvBranch.setOnClickListener { binding.actvBranch.showDropDown() }
        binding.actvBranch.setOnFocusChangeListener { _, hasFocus -> if (hasFocus) binding.actvBranch.showDropDown() }
    }

    private fun validateInputs(): Boolean {
        val name = binding.etName.text.toString().trim()
        val email = binding.etEmail.text.toString().trim()
        val phone = binding.etPhone.text.toString().trim()
        val role = binding.actvRole.text.toString().trim()
        val branch = binding.actvBranch.text.toString().trim()
        val password = binding.etPassword.text.toString()
        val confirmPassword = binding.etConfirmPassword.text.toString()
        val totalDaysText = binding.etTotalDays.text.toString().trim()

        if (name.isEmpty()) { binding.tilName.error = "Name is required"; return false }
        if (email.isEmpty()) { binding.tilEmail.error = "Email is required"; return false }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) { binding.tilEmail.error = "Please enter a valid email"; return false }
        if (phone.isEmpty()) { binding.tilPhone.error = "Phone number is required"; return false }
        if (branch.isEmpty()) { binding.tilBranch.error = "Please select a branch"; return false }
        if (role.isEmpty()) { binding.tilRole.error = "Please select a role"; return false }
        if (totalDaysText.isEmpty()) { binding.tilTotalDays.error = "Total days is required"; return false }
        val totalDays = totalDaysText.toIntOrNull(); if (totalDays == null || totalDays <= 0) { binding.tilTotalDays.error = "Please enter a valid number of days"; return false }
        if (password.isEmpty()) { binding.tilPassword.error = "Password is required"; return false }
        if (password.length < 6) { binding.tilPassword.error = "Password must be at least 6 characters"; return false }
        if (confirmPassword.isEmpty()) { binding.tilConfirmPassword.error = "Please confirm password"; return false }
        if (password != confirmPassword) { binding.tilConfirmPassword.error = "Passwords do not match"; return false }
        return true
    }

    private fun createEmployeeAccount() {
        val name = binding.etName.text.toString().trim()
        val email = binding.etEmail.text.toString().trim()
        val phone = binding.etPhone.text.toString().trim()
        val selectedRole = binding.actvRole.text.toString().trim()
        val branch = binding.actvBranch.text.toString().trim()
        val password = binding.etPassword.text.toString()
        val totalDays = binding.etTotalDays.text.toString().trim().toInt()

        val role = when (selectedRole.lowercase()) {
            "coach" -> "coach"
            "admin" -> "admin"
            else -> "coach"
        }

        binding.btnCreateAccount.isEnabled = false
        binding.btnCreateAccount.text = "Creating Account..."

        // Create Auth user FIRST to get the UID
        auth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener { result ->
                val newUser = result.user
                if (newUser == null) {
                    onCreateError("Failed to create user")
                    return@addOnSuccessListener
                }
                val uid = newUser.uid

                // Build employee with doc id = uid
                val employee = Employee(
                    id = uid,
                    name = name,
                    email = email,
                    role = role,
                    phone = phone,
                    branch = branch,
                    totalDays = totalDays,
                    remainingDays = totalDays,
                    status = "active",
                    createdAt = Timestamp.now(),
                    updatedAt = Timestamp.now()
                )

                val userDoc = hashMapOf(
                    "role" to role,
                    "email" to email,
                    "name" to name,
                    "phone" to phone,
                    "branch" to branch,
                    "status" to "active",
                    "createdAt" to Timestamp.now(),
                    "lastLogin" to Timestamp.now()
                )

                // Write Firestore docs keyed by UID
                firestore.collection("users").document(uid).set(userDoc)
                    .continueWithTask {
                        firestore.collection("employees").document(uid).set(employee)
                    }
                    .addOnSuccessListener {
                        // Update profile name; then sign out so creator stays unauthenticated new user
                        val profileUpdates = com.google.firebase.auth.UserProfileChangeRequest.Builder()
                            .setDisplayName(name)
                            .build()
                        newUser.updateProfile(profileUpdates).addOnCompleteListener {
                            auth.signOut()
                            binding.btnCreateAccount.isEnabled = true
                            binding.btnCreateAccount.text = "Create Account"
                            Toast.makeText(context, "Employee account created successfully!", Toast.LENGTH_LONG).show()
                            onAccountCreatedListener?.invoke(employee)
                            dialog.dismiss()
                        }
                    }
                    .addOnFailureListener { e ->
                        onCreateError(e.message ?: "Failed to save user data")
                    }
            }
            .addOnFailureListener { e -> onCreateError(e.message ?: "Auth creation failed") }
    }

    private fun onCreateError(message: String) {
        binding.btnCreateAccount.isEnabled = true
        binding.btnCreateAccount.text = "Create Account"
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }
} 