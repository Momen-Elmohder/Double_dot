package com.example.double_dot_demo.utils

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.Timestamp

object HeadCoachCreator {
    
    fun createHeadCoachAccount() {
        val email = "ahmedaltabey@gmail.com"
        val password = "headcoach123"
        
        val auth = FirebaseAuth.getInstance()
        val firestore = FirebaseFirestore.getInstance()
        
        // Create user in Firebase Auth
        auth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener { result ->
                val user = result.user
                if (user != null) {
                    // Create user document in Firestore
                    val userData = hashMapOf(
                        "role" to "head_coach",
                        "email" to email,
                        "name" to "Ahmed Al Tabey",
                        "lastLogin" to Timestamp.now(),
                        "createdAt" to Timestamp.now(),
                        "status" to "active"
                    )
                    
                    firestore.collection("users")
                        .document(user.uid)
                        .set(userData)
                        .addOnSuccessListener {
                            println("✅ Head Coach account created successfully!")
                            println("Email: $email")
                            println("Password: $password")
                            println("Role: head_coach")
                            println("User ID: ${user.uid}")
                        }
                        .addOnFailureListener { e ->
                            println("❌ Failed to create user document: ${e.message}")
                        }
                }
            }
            .addOnFailureListener { e ->
                if (e.message?.contains("already in use") == true) {
                    // User already exists, just update the role
                    val user = auth.currentUser
                    if (user != null) {
                        updateUserToHeadCoach(user.uid, email)
                    } else {
                        // Sign in first to get the user
                        auth.signInWithEmailAndPassword(email, password)
                            .addOnSuccessListener { result ->
                                updateUserToHeadCoach(result.user?.uid ?: "", email)
                            }
                            .addOnFailureListener { signInError ->
                                println("❌ Failed to sign in: ${signInError.message}")
                            }
                    }
                } else {
                    println("❌ Failed to create user: ${e.message}")
                }
            }
    }
    
    private fun updateUserToHeadCoach(userId: String, email: String) {
        val firestore = FirebaseFirestore.getInstance()
        
        val userData = hashMapOf(
            "role" to "head_coach",
            "email" to email,
            "name" to "Ahmed Al Tabey",
            "lastLogin" to Timestamp.now(),
            "updatedAt" to Timestamp.now(),
            "status" to "active"
        )
        
        firestore.collection("users")
            .document(userId)
            .set(userData)
            .addOnSuccessListener {
                println("✅ User updated to Head Coach successfully!")
                println("Email: $email")
                println("Role: head_coach")
                println("User ID: $userId")
            }
            .addOnFailureListener { e ->
                println("❌ Failed to update user: ${e.message}")
            }
    }
} 