package com.example.double_dot_demo

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import com.example.double_dot_demo.databinding.ActivityMainBinding
import com.example.double_dot_demo.fragments.CalendarFragment
import com.example.double_dot_demo.fragments.EmployeesFragment
import com.example.double_dot_demo.fragments.SignInFragment
import com.example.double_dot_demo.fragments.TraineesFragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth

class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var bottomNavigation: BottomNavigationView
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Force the app to follow system night mode
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Initialize Firebase Auth
        auth = FirebaseAuth.getInstance()
        
        // Set up bottom navigation
        setupBottomNavigation()
        
        // Check if user is already signed in first
        checkCurrentUser()
    }
    
    private fun setupBottomNavigation() {
        bottomNavigation = binding.bottomNavigation!!
        
        bottomNavigation.setOnItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_calendar -> {
                    loadFragment(CalendarFragment.newInstance())
                    true
                }
                R.id.nav_trainees -> {
                    loadFragment(TraineesFragment.newInstance())
                    true
                }
                R.id.nav_employees -> {
                    loadFragment(EmployeesFragment.newInstance())
                    true
                }
                else -> false
            }
        }
    }
    
    private fun checkCurrentUser() {
        val currentUser = auth.currentUser
        if (currentUser != null && currentUser.isEmailVerified) {
            // User is already signed in and email is verified, navigate to dashboard
            // Get user role from Firestore
            getUserRoleAndNavigate(currentUser.uid)
        } else {
            // Show sign-in fragment
            loadFragment(SignInFragment.newInstance())
            hideBottomNavigation()
        }
    }
    
    private fun getUserRoleAndNavigate(userId: String) {
        val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
        firestore.collection("users")
            .document(userId)
            .get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val role = document.getString("role") ?: "user"
                    navigateToDashboard(role)
                } else {
                    // User exists in Auth but not in Firestore, create user data
                    createUserInFirestore(userId, "user")
                }
            }
            .addOnFailureListener { e ->
                // If Firestore fails, still navigate with default role
                navigateToDashboard("user")
            }
    }
    
    private fun createUserInFirestore(userId: String, role: String) {
        val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
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
    }
    
    fun navigateToDashboard(userRole: String) {
        val intent = Intent(this, DashboardActivity::class.java).apply {
            putExtra("user_role", userRole)
        }
        startActivity(intent)
        finish() // Close MainActivity so user can't go back to sign-in screen
    }
    
    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }
    
    private fun hideBottomNavigation() {
        bottomNavigation.visibility = android.view.View.GONE
    }
    
    private fun showBottomNavigation() {
        bottomNavigation.visibility = android.view.View.VISIBLE
    }
}