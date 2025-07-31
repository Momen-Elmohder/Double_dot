package com.example.double_dot_demo

import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

// This is a test file to verify imports are working
class TestImports {
    
    fun testFirebaseImports() {
        val auth = FirebaseAuth.getInstance()
        val firestore = FirebaseFirestore.getInstance()
        
        // Test ViewModel import
        val viewModel = object : ViewModel() {}
        
        println("All imports are working correctly!")
    }
} 