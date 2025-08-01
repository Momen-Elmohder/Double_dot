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
        if (currentUserRole != "head_coach" && currentUserRole != "admin") {
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
            "head_coach" -> listOf("Coach", "Admin") // Head coaches can create coaches and admins
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

        // Convert role to proper format
        val role = when (selectedRole.lowercase()) {
            "coach" -> "coach"
            "admin" -> "admin"
            else -> "coach" // Default to coach if something goes wrong
        }

        // Show loading
        binding.btnCreateAccount.isEnabled = false
        binding.btnCreateAccount.text = "Creating Account..."

        // First create Firebase Auth account
        auth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener { result ->
                val user = result.user
                if (user != null) {
                    // Update user profile with display name
                    val profileUpdates = com.google.firebase.auth.UserProfileChangeRequest.Builder()
                        .setDisplayName(name)
                        .build()

                    user.updateProfile(profileUpdates)
                        .addOnSuccessListener {
                            // Create employee document in Firestore
                            createEmployeeDocument(user.uid, name, email, phone, role)
                        }
                        .addOnFailureListener { e ->
                            binding.btnCreateAccount.isEnabled = true
                            binding.btnCreateAccount.text = "Create Account"
                            Toast.makeText(context, "Failed to update profile: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                }
            }
            .addOnFailureListener { e ->
                binding.btnCreateAccount.isEnabled = true
                binding.btnCreateAccount.text = "Create Account"
                Toast.makeText(context, "Failed to create account: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun createEmployeeDocument(uid: String, name: String, email: String, phone: String, role: String) {
        val employee = Employee(
            id = uid,
            name = name,
            email = email,
            role = role,
            phone = phone,
            hireDate = Timestamp.now(),
            salary = 0, // Will be set by head coach later
            status = "active",
            createdAt = Timestamp.now(),
            updatedAt = Timestamp.now()
        )

        // Create user document in "users" collection for Firebase Auth
        val userData = hashMapOf(
            "role" to role,
            "email" to email,
            "name" to name,
            "phone" to phone,
            "status" to "active",
            "createdAt" to Timestamp.now(),
            "lastLogin" to Timestamp.now()
        )

        // Create both documents
        firestore.collection("users")
            .document(uid)
            .set(userData)
            .addOnSuccessListener {
                // Now create employee document
                firestore.collection("employees")
                    .document(uid)
                    .set(employee)
                    .addOnSuccessListener {
                        binding.btnCreateAccount.isEnabled = true
                        binding.btnCreateAccount.text = "Create Account"
                        Toast.makeText(context, "Employee account created successfully!", Toast.LENGTH_LONG).show()
                        onAccountCreatedListener?.invoke(employee)
                        dialog.dismiss()
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