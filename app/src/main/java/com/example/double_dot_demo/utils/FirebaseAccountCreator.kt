package com.example.double_dot_demo.utils

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.Timestamp

object FirebaseAccountCreator {
    
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    
    fun createHeadCoachAccount(
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val email = "ahmedeltabey@doubledot.com"
        val password = "founder0987"
        val role = "head_coach"
        
        // Create user in Firebase Auth
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    if (user != null) {
                        // Create user data in Firestore
                        createUserInFirestore(user.uid, email, role, onSuccess, onError)
                    } else {
                        onError("Failed to create user")
                    }
                } else {
                    val errorMessage = when {
                        task.exception?.message?.contains("already in use") == true -> 
                            "Account already exists. You can sign in directly."
                        task.exception?.message?.contains("password") == true -> 
                            "Password is too weak"
                        task.exception?.message?.contains("email") == true -> 
                            "Invalid email format"
                        else -> task.exception?.message ?: "Failed to create account"
                    }
                    onError(errorMessage)
                }
            }
    }
    
    private fun createUserInFirestore(
        userId: String,
        email: String,
        role: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val userData = hashMapOf(
            "email" to email,
            "role" to role,
            "createdAt" to Timestamp.now(),
            "lastLogin" to Timestamp.now(),
            "name" to "Ahmed El Tabey",
            "isActive" to true,
            "permissions" to listOf("manage_trainees", "manage_coaches", "view_reports", "admin_access")
        )
        
        firestore.collection("users")
            .document(userId)
            .set(userData)
            .addOnSuccessListener {
                onSuccess()
            }
            .addOnFailureListener { e ->
                onError("Failed to save user data: ${e.message}")
            }
    }
    
    fun checkIfAccountExists(
        onExists: () -> Unit,
        onNotExists: () -> Unit
    ) {
        val email = "ahmedeltabey@doubledot.com"
        
        // Try to sign in to check if account exists
        auth.signInWithEmailAndPassword(email, "founder0987")
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    // Account exists, sign out and notify
                    auth.signOut()
                    onExists()
                } else {
                    onNotExists()
                }
            }
    }
} 