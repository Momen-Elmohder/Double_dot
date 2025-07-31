package com.example.double_dot_demo.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class SignInViewModel : ViewModel() {
    
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    
    private val _signInState = MutableLiveData<SignInState>()
    val signInState: LiveData<SignInState> = _signInState
    
    fun signIn(email: String, password: String, defaultRole: String) {
        _signInState.value = SignInState.Loading
        
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    if (user != null) {
                        // Check if email is verified (optional - you can remove this check)
                        if (user.isEmailVerified) {
                            // Check user role from Firestore
                            getUserRoleFromFirestore(user.uid, defaultRole)
                        } else {
                            // Email not verified, but still proceed (you can change this behavior)
                            getUserRoleFromFirestore(user.uid, defaultRole)
                        }
                    } else {
                        _signInState.value = SignInState.Error("Authentication failed")
                    }
                } else {
                    val errorMessage = when {
                        task.exception?.message?.contains("password") == true -> "Invalid password"
                        task.exception?.message?.contains("email") == true -> "Email not found"
                        task.exception?.message?.contains("network") == true -> "Network error. Please check your connection"
                        else -> task.exception?.message ?: "Sign in failed"
                    }
                    _signInState.value = SignInState.Error(errorMessage)
                }
            }
    }
    
    private fun getUserRoleFromFirestore(userId: String, defaultRole: String) {
        firestore.collection("users")
            .document(userId)
            .get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val role = document.getString("role") ?: defaultRole
                    // Update last login time
                    updateLastLogin(userId)
                    _signInState.value = SignInState.Success(role)
                } else {
                    // User doesn't exist in Firestore, create with default role
                    createUserInFirestore(userId, defaultRole)
                }
            }
            .addOnFailureListener { e ->
                // If Firestore fails, still create user with default role
                createUserInFirestore(userId, defaultRole)
            }
    }
    
    private fun createUserInFirestore(userId: String, role: String) {
        val userData = hashMapOf(
            "role" to role,
            "email" to auth.currentUser?.email,
            "lastLogin" to com.google.firebase.Timestamp.now(),
            "createdAt" to com.google.firebase.Timestamp.now()
        )
        
        firestore.collection("users")
            .document(userId)
            .set(userData)
            .addOnSuccessListener {
                _signInState.value = SignInState.Success(role)
            }
            .addOnFailureListener { e ->
                // If creation fails, still proceed with default role
                _signInState.value = SignInState.Success(role)
            }
    }
    
    private fun updateLastLogin(userId: String) {
        val updateData = hashMapOf(
            "lastLogin" to com.google.firebase.Timestamp.now()
        )
        
        firestore.collection("users")
            .document(userId)
            .update(updateData as Map<String, Any>)
            .addOnFailureListener { e ->
                // Ignore errors for last login update
            }
    }
    
    sealed class SignInState {
        object Loading : SignInState()
        data class Success(val userRole: String) : SignInState()
        data class Error(val message: String) : SignInState()
    }
} 