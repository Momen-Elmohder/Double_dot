package com.example.double_dot_demo

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import com.example.double_dot_demo.databinding.ActivityMainBinding
import com.example.double_dot_demo.fragments.AttendanceFragment
import com.example.double_dot_demo.fragments.EmployeesFragment
import com.example.double_dot_demo.fragments.SignInFragment
import com.example.double_dot_demo.fragments.TraineesFragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var bottomNavigation: BottomNavigationView
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Set up global exception handler
        setupGlobalExceptionHandler()
        
        try {
            // Force the app to follow system night mode
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            
            binding = ActivityMainBinding.inflate(layoutInflater)
            setContentView(binding.root)
            
            // Initialize Firebase Auth
            auth = FirebaseAuth.getInstance()
            
            // Set up bottom navigation
            setupBottomNavigation()
            
            // Check if user is already signed in
            checkCurrentUser()
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error in onCreate: ${e.message}")
            Toast.makeText(this, "Error initializing app: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun setupBottomNavigation() {
        try {
            bottomNavigation = binding.bottomNavigation!!
            
            bottomNavigation.setOnItemSelectedListener { menuItem ->
                try {
                                    when (menuItem.itemId) {
                    R.id.nav_calendar -> {
                        loadFragment(AttendanceFragment())
                        true
                    }
                        R.id.nav_trainees -> {
                            loadFragment(TraineesFragment().apply {
                                arguments = Bundle().apply {
                                    putString("user_role", "coach") // Default role for MainActivity
                                }
                            })
                            true
                        }
                        R.id.nav_employees -> {
                            loadFragment(EmployeesFragment())
                            true
                        }
                        else -> false
                    }
                } catch (e: Exception) {
                    android.util.Log.e("MainActivity", "Error in navigation: ${e.message}")
                    Toast.makeText(this, "Navigation error: ${e.message}", Toast.LENGTH_LONG).show()
                    false
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error setting up bottom navigation: ${e.message}")
            Toast.makeText(this, "Error setting up navigation: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun checkCurrentUser() {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            // User is already signed in, navigate to dashboard
            // Get user role from Firestore
            getUserRoleAndNavigate(currentUser.uid)
        } else {
            // Show sign-in fragment
            loadFragment(SignInFragment())
            hideBottomNavigation()
        }
    }
    
    private fun getUserRoleAndNavigate(userId: String) {
        try {
            val firestore = FirebaseFirestore.getInstance()
            
            firestore.collection("users")
                .document(userId)
                .get()
                .addOnSuccessListener { document ->
                    try {
                        if (document != null && document.exists()) {
                            // Enhanced role reading with multiple fallbacks
                            val allData = document.data
                            var role = "user"
                            
                            // Try multiple ways to read the role
                            role = document.getString("role") ?: 
                                   document.getString("Role") ?: 
                                   document.getString("userRole") ?: 
                                   document.getString("user_role") ?:
                                   allData?.get("role")?.toString() ?: "coach" // Default to coach for safety
                            
                            // Debug: Log all available data
                            android.util.Log.d("MainActivity", "All document data: $allData")
                            android.util.Log.d("MainActivity", "User role from Firestore: $role")
                            
                            navigateToDashboard(role)
                        } else {
                            // User exists in Auth but not in Firestore, create user data
                            android.util.Log.d("MainActivity", "User not found in Firestore, creating document")
                            Toast.makeText(this@MainActivity, "Creating user document...", Toast.LENGTH_SHORT).show()
                            createUserInFirestore(userId, "coach") // Default to coach for safety
                        }
                    } catch (e: Exception) {
                        // If role reading fails, use default role
                        android.util.Log.e("MainActivity", "Error reading role: ${e.message}")
                        Toast.makeText(this@MainActivity, "Error reading role, creating user document...", Toast.LENGTH_SHORT).show()
                        createUserInFirestore(userId, "coach") // Default to coach for safety
                    }
                }
                .addOnFailureListener { e ->
                    // If Firestore fails, still navigate with default role
                    navigateToDashboard("coach")
                }
        } catch (e: Exception) {
            // If anything fails, use default role
            navigateToDashboard("coach")
        }
    }
    
    private fun createUserInFirestore(userId: String, role: String) {
        try {
            val firestore = FirebaseFirestore.getInstance()
            
            val userData = hashMapOf(
                "role" to role,
                "email" to auth.currentUser?.email,
                "lastLogin" to com.google.firebase.Timestamp.now()
            )
            
            firestore.collection("users")
                .document(userId)
                .set(userData)
                .addOnSuccessListener {
                    navigateToDashboard(role)
                }
                .addOnFailureListener { e ->
                    // If creation fails, still navigate with default role
                    navigateToDashboard(role)
                }
        } catch (e: Exception) {
            // If anything fails, still navigate with default role
            navigateToDashboard(role)
        }
    }
    
    fun navigateToDashboard(userRole: String) {
        val intent = Intent(this, DashboardActivity::class.java).apply {
            putExtra("user_role", userRole)
        }
        startActivity(intent)
        finish() // Close MainActivity so user can't go back to sign-in screen
    }
    
    private fun loadFragment(fragment: Fragment) {
        try {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, fragment)
                .commit()
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error loading fragment: ${e.message}")
            Toast.makeText(this, "Error loading page: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun hideBottomNavigation() {
        bottomNavigation.visibility = android.view.View.GONE
    }
    
    private fun showBottomNavigation() {
        bottomNavigation.visibility = android.view.View.VISIBLE
    }

    private fun setupGlobalExceptionHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            android.util.Log.e("MainActivity", "Uncaught exception in thread ${thread.name}: ${throwable.message}")
            throwable.printStackTrace()
            
            // Show user-friendly error message
            runOnUiThread {
                android.widget.Toast.makeText(
                    this@MainActivity,
                    "App error occurred. Please restart.",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
            
            // Call the default handler
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}