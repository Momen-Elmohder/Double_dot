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
                        // Check user role from Firestore
                        getUserRoleFromFirestore(user.uid, defaultRole)
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
        try {
            firestore.collection("users")
                .document(userId)
                .get()
                .addOnSuccessListener { document ->
                    try {
                        if (document != null && document.exists()) {
                            // Enhanced role reading with multiple fallbacks
                            val allData = document.data
                            var role = defaultRole
                            
                            // Try multiple ways to read the role
                            role = document.getString("role") ?: 
                                   document.getString("Role") ?: 
                                   document.getString("userRole") ?: 
                                   document.getString("user_role") ?:
                                   allData?.get("role")?.toString() ?: defaultRole
                            
                            // Debug: Log all available data
                            android.util.Log.d("SignInViewModel", "All document data: $allData")
                            android.util.Log.d("SignInViewModel", "User role from Firestore: $role")
                            
                            // Update last login time
                            updateLastLogin(userId)
                            _signInState.value = SignInState.Success(role)
                        } else {
                            // User doesn't exist in Firestore, create with head_coach role for existing users
                            android.util.Log.d("SignInViewModel", "User not found in Firestore, creating document")
                            createUserInFirestore(userId, "head_coach") // Default to head_coach for existing users
                        }
                    } catch (e: Exception) {
                        // If role reading fails, use head_coach role for existing users
                        android.util.Log.e("SignInViewModel", "Error reading role: ${e.message}")
                        createUserInFirestore(userId, "head_coach") // Default to head_coach for existing users
                    }
                }
                .addOnFailureListener { e ->
                    // If Firestore fails, still create user with default role
                    createUserInFirestore(userId, defaultRole)
                }
        } catch (e: Exception) {
            // If anything fails, use default role
            createUserInFirestore(userId, defaultRole)
        }
    }
    
    private fun createUserInFirestore(userId: String, role: String) {
        try {
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
        } catch (e: Exception) {
            // If anything fails, still proceed with default role
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