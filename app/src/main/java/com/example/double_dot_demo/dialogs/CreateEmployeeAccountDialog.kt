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
import java.text.SimpleDateFormat
import java.util.*

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
        // Allow head coaches and admins to create employee accounts
        if (currentUserRole != "head_coach" && currentUserRole != "head coach" && currentUserRole != "admin") {
            Toast.makeText(context, "Only Head Coaches and Admins can create employee accounts", Toast.LENGTH_LONG).show()
            return
        }

        binding = DialogCreateEmployeeAccountBinding.inflate(LayoutInflater.from(context))
        
        setupViews()
        setupRoleDropdown()
        
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

        binding.btnCreateAccount.setOnClickListener {
            if (validateInputs()) {
                createEmployeeAccount()
            }
        }
    }

    private fun setupRoleDropdown() {
        // Set available roles based on current user's role
        val availableRoles = when (currentUserRole) {
            "head_coach", "head coach" -> listOf("Coach", "Admin") // Head coaches can create coaches and admins
            "admin" -> listOf("Coach") // Admins can only create coaches
            else -> listOf("Coach") // Default fallback
        }
        
        val adapter = ArrayAdapter(context, android.R.layout.simple_dropdown_item_1line, availableRoles)
        binding.actvRole.setAdapter(adapter)
    }

    private fun validateInputs(): Boolean {
        val name = binding.etName.text.toString().trim()
        val email = binding.etEmail.text.toString().trim()
        val phone = binding.etPhone.text.toString().trim()
        val role = binding.actvRole.text.toString().trim()
        val password = binding.etPassword.text.toString()
        val confirmPassword = binding.etConfirmPassword.text.toString()
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

        if (totalDaysText.isEmpty()) {
            binding.tilTotalDays.error = "Total days is required"
            return false
        }

        val totalDays = totalDaysText.toIntOrNull()
        if (totalDays == null || totalDays <= 0) {
            binding.tilTotalDays.error = "Please enter a valid number of days"
            return false
        }

        if (password.isEmpty()) {
            binding.tilPassword.error = "Password is required"
            return false
        }

        if (password.length < 6) {
            binding.tilPassword.error = "Password must be at least 6 characters"
            return false
        }

        if (confirmPassword.isEmpty()) {
            binding.tilConfirmPassword.error = "Please confirm password"
            return false
        }

        if (password != confirmPassword) {
            binding.tilConfirmPassword.error = "Passwords do not match"
            return false
        }

        return true
    }

    private fun createEmployeeAccount() {
        val name = binding.etName.text.toString().trim()
        val email = binding.etEmail.text.toString().trim()
        val phone = binding.etPhone.text.toString().trim()
        val selectedRole = binding.actvRole.text.toString().trim()
        val password = binding.etPassword.text.toString()
        val totalDaysText = binding.etTotalDays.text.toString().trim()

        // Convert role to proper format
        val role = when (selectedRole.lowercase()) {
            "coach" -> "coach"
            "admin" -> "admin"
            else -> "coach" // Default to coach if something goes wrong
        }

        // Show loading
        binding.btnCreateAccount.isEnabled = false
        binding.btnCreateAccount.text = "Creating Account..."

        // Generate a unique ID for the employee
        val employeeId = firestore.collection("employees").document().id

        // Create employee document first (without Firebase Auth account)
        createEmployeeDocumentOnly(employeeId, name, email, phone, role, totalDaysText.toInt(), password)
    }

    private fun createEmployeeDocumentOnly(employeeId: String, name: String, email: String, phone: String, role: String, totalDays: Int, password: String) {
        val employee = Employee(
            id = employeeId,
            name = name,
            email = email,
            role = role,
            phone = phone,
            totalDays = totalDays,
            remainingDays = totalDays,
            status = "active",
            createdAt = Timestamp.now(),
            updatedAt = Timestamp.now()
        )

        // Create user document in "users" collection
        val userData = hashMapOf(
            "role" to role,
            "email" to email,
            "name" to name,
            "phone" to phone,
            "status" to "active",
            "createdAt" to Timestamp.now(),
            "lastLogin" to Timestamp.now(),
            "password" to password // Store password temporarily for Firebase Auth creation
        )

        // Create both documents
        firestore.collection("users")
            .document(employeeId)
            .set(userData)
            .addOnSuccessListener {
                // Now create employee document
                firestore.collection("employees")
                    .document(employeeId)
                    .set(employee)
                    .addOnSuccessListener {
                        // Now create the Firebase Auth account (this will sign in automatically)
                        auth.createUserWithEmailAndPassword(email, password)
                            .addOnSuccessListener { result ->
                                val newUser = result.user
                                if (newUser != null) {
                                    // Update user profile with display name
                                    val profileUpdates = com.google.firebase.auth.UserProfileChangeRequest.Builder()
                                        .setDisplayName(name)
                                        .build()

                                    newUser.updateProfile(profileUpdates)
                                        .addOnSuccessListener {
                                            // Sign out immediately to prevent staying signed in
                                            auth.signOut()
                                            
                                            // Show success message
                                            binding.btnCreateAccount.isEnabled = true
                                            binding.btnCreateAccount.text = "Create Account"
                                            Toast.makeText(context, "Employee account created successfully!", Toast.LENGTH_LONG).show()
                                            onAccountCreatedListener?.invoke(employee)
                                            dialog.dismiss()
                                        }
                                        .addOnFailureListener { e ->
                                            // Even if profile update fails, sign out and show success
                                            auth.signOut()
                                            binding.btnCreateAccount.isEnabled = true
                                            binding.btnCreateAccount.text = "Create Account"
                                            Toast.makeText(context, "Employee account created successfully!", Toast.LENGTH_LONG).show()
                                            onAccountCreatedListener?.invoke(employee)
                                            dialog.dismiss()
                                        }
                                }
                            }
                            .addOnFailureListener { e ->
                                // If Firebase Auth creation fails, still show success for document creation
                                binding.btnCreateAccount.isEnabled = true
                                binding.btnCreateAccount.text = "Create Account"
                                Toast.makeText(context, "Employee documents created, but Firebase Auth creation failed: ${e.message}", Toast.LENGTH_LONG).show()
                                onAccountCreatedListener?.invoke(employee)
                                dialog.dismiss()
                            }
                    }
                    .addOnFailureListener { e ->
                        binding.btnCreateAccount.isEnabled = true
                        binding.btnCreateAccount.text = "Create Account"
                        Toast.makeText(context, "Failed to save employee data: ${e.message}", Toast.LENGTH_LONG).show()
                    }
            }
            .addOnFailureListener { e ->
                binding.btnCreateAccount.isEnabled = true
                binding.btnCreateAccount.text = "Create Account"
                Toast.makeText(context, "Failed to create user document: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }
} 