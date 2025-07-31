package com.example.double_dot_demo

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.Timestamp

class CreateHeadCoachActivity : AppCompatActivity() {
    
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var statusText: TextView
    private lateinit var createButton: Button
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_head_coach)
        
        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()
        
        statusText = findViewById(R.id.tvStatus)
        createButton = findViewById(R.id.btnCreateHeadCoach)
        
        createButton.setOnClickListener {
            createHeadCoachAccount()
        }
    }
    
    private fun createHeadCoachAccount() {
        val email = "ahmedaltabey@gmail.com"
        val password = "headcoach123"
        
        statusText.text = "Creating Head Coach account..."
        createButton.isEnabled = false
        
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
                            statusText.text = """
                                ✅ Head Coach account created successfully!
                                
                                Email: $email
                                Password: $password
                                Role: head_coach
                                User ID: ${user.uid}
                                
                                You can now sign in with these credentials.
                            """.trimIndent()
                            createButton.text = "Account Created!"
                        }
                        .addOnFailureListener { e ->
                            statusText.text = "❌ Failed to create user document: ${e.message}"
                            createButton.isEnabled = true
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
                                statusText.text = "❌ Failed to sign in: ${signInError.message}"
                                createButton.isEnabled = true
                            }
                    }
                } else {
                    statusText.text = "❌ Failed to create user: ${e.message}"
                    createButton.isEnabled = true
                }
            }
    }
    
    private fun updateUserToHeadCoach(userId: String, email: String) {
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
                statusText.text = """
                    ✅ User updated to Head Coach successfully!
                    
                    Email: $email
                    Role: head_coach
                    User ID: $userId
                    
                    You can now sign in with these credentials.
                """.trimIndent()
                createButton.text = "Account Updated!"
            }
            .addOnFailureListener { e ->
                statusText.text = "❌ Failed to update user: ${e.message}"
                createButton.isEnabled = true
            }
    }
} 