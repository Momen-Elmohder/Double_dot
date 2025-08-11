package com.example.double_dot_demo.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
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
                        getUserRoleFromFirestore(user.uid, defaultRole)
                    } else {
                        _signInState.value = SignInState.Error("Authentication failed")
                    }
                } else {
                    val message = mapAuthError(task.exception)
                    _signInState.value = SignInState.Error(message)
                }
            }
    }

    fun sendPasswordReset(email: String, onResult: (Boolean, String?) -> Unit) {
        auth.sendPasswordResetEmail(email)
            .addOnSuccessListener { onResult(true, null) }
            .addOnFailureListener { e -> onResult(false, mapAuthError(e)) }
    }
    
    private fun mapAuthError(throwable: Throwable?): String {
        val raw = throwable?.message ?: "Sign in failed"
        val code = (throwable as? FirebaseAuthException)?.errorCode ?: ""
        return when {
            code.equals("ERROR_INVALID_EMAIL", true) -> "Invalid email format"
            code.equals("ERROR_USER_NOT_FOUND", true) -> "Email not found"
            code.equals("ERROR_WRONG_PASSWORD", true) -> "Invalid password"
            code.equals("ERROR_USER_DISABLED", true) -> "Account disabled"
            code.equals("ERROR_INVALID_CREDENTIAL", true) || raw.contains("invalid credential", true) -> "Invalid email or password"
            raw.contains("password", true) -> "Invalid password"
            raw.contains("email", true) -> "Email not found"
            raw.contains("network", true) -> "Network error. Please check your connection"
            else -> raw
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
                            val allData = document.data
                            var role = defaultRole
                            
                            role = document.getString("role") ?: 
                                   document.getString("Role") ?: 
                                   document.getString("userRole") ?: 
                                   document.getString("user_role") ?:
                                   allData?.get("role")?.toString() ?: defaultRole
                            
                            android.util.Log.d("SignInViewModel", "All document data: $allData")
                            android.util.Log.d("SignInViewModel", "User role from Firestore: $role")
                            
                            updateLastLogin(userId)
                            _signInState.value = SignInState.Success(role)
                        } else {
                            android.util.Log.d("SignInViewModel", "User not found in Firestore, creating document")
                            createUserInFirestore(userId, "head_coach")
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("SignInViewModel", "Error reading role: ${e.message}")
                        createUserInFirestore(userId, "head_coach")
                    }
                }
                .addOnFailureListener { _ ->
                    createUserInFirestore(userId, defaultRole)
                }
        } catch (e: Exception) {
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
                .addOnFailureListener { _ ->
                    _signInState.value = SignInState.Success(role)
                }
        } catch (e: Exception) {
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
            .addOnFailureListener { _ -> }
    }
    
    sealed class SignInState {
        object Loading : SignInState()
        data class Success(val userRole: String) : SignInState()
        data class Error(val message: String) : SignInState()
    }
} 