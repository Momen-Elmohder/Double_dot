package com.example.double_dot_demo.utils

import android.content.Context
import android.widget.Toast
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.Timestamp

object RoleFixer {
    
    fun fixUserRole(context: Context, email: String, newRole: String) {
        try {
            val auth = FirebaseAuth.getInstance()
            val firestore = FirebaseFirestore.getInstance()
            
            // First find the user by email
            firestore.collection("users")
                .whereEqualTo("email", email)
                .get()
                .addOnSuccessListener { documents ->
                    if (documents.isEmpty) {
                        Toast.makeText(context, "User not found with email: $email", Toast.LENGTH_LONG).show()
                        return@addOnSuccessListener
                    }
                    
                    val document = documents.documents[0]
                    val userId = document.id
                
                // Update the role
                val updateData = hashMapOf(
                    "role" to newRole,
                    "updatedAt" to Timestamp.now()
                )
                
                firestore.collection("users")
                    .document(userId)
                    .update(updateData as Map<String, Any>)
                    .addOnSuccessListener {
                        Toast.makeText(context, "Role updated successfully! User: $email, New Role: $newRole", Toast.LENGTH_LONG).show()
                    }
                                    .addOnFailureListener { e ->
                    Toast.makeText(context, "Failed to update role: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(context, "Failed to find user: ${e.message}", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    fun checkUserRole(context: Context, email: String) {
        try {
            val firestore = FirebaseFirestore.getInstance()
            
            firestore.collection("users")
                .whereEqualTo("email", email)
                .get()
                .addOnSuccessListener { documents ->
                    if (documents.isEmpty) {
                        Toast.makeText(context, "User not found with email: $email", Toast.LENGTH_LONG).show()
                        return@addOnSuccessListener
                    }
                    
                    val document = documents.documents[0]
                    val role = document.getString("role") ?: "No role found"
                    val userId = document.id
                    
                    Toast.makeText(context, "User: $email, Current Role: $role, User ID: $userId", Toast.LENGTH_LONG).show()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(context, "Failed to check role: ${e.message}", Toast.LENGTH_LONG).show()
                }
        } catch (e: Exception) {
            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    fun createUserDocument(context: Context, email: String, role: String) {
        try {
            val auth = FirebaseAuth.getInstance()
            val firestore = FirebaseFirestore.getInstance()
            
            // Get current user's UID
            val currentUser = auth.currentUser
            if (currentUser == null) {
                Toast.makeText(context, "No authenticated user found", Toast.LENGTH_LONG).show()
                return
            }
            
            val userId = currentUser.uid
            
            // Create user document
            val userData = hashMapOf(
                "email" to email,
                "role" to role,
                "createdAt" to Timestamp.now(),
                "lastLogin" to Timestamp.now()
            )
            
            firestore.collection("users")
                .document(userId)
                .set(userData)
                .addOnSuccessListener {
                    Toast.makeText(context, "User document created successfully! Role: $role", Toast.LENGTH_LONG).show()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(context, "Failed to create user document: ${e.message}", Toast.LENGTH_LONG).show()
                }
        } catch (e: Exception) {
            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
} 