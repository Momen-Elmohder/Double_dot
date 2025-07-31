package com.example.double_dot_demo

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import com.example.double_dot_demo.fragments.CalendarFragment
import com.example.double_dot_demo.fragments.EmployeesFragment
import com.example.double_dot_demo.fragments.TraineesFragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.appbar.MaterialToolbar
import com.google.firebase.auth.FirebaseAuth

class DashboardActivity : AppCompatActivity() {
    
    private lateinit var auth: FirebaseAuth
    private lateinit var bottomNavigation: BottomNavigationView
    private lateinit var toolbar: MaterialToolbar
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Force the app to follow system night mode
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        
        setContentView(R.layout.activity_dashboard)
        
        // Initialize Firebase Auth
        auth = FirebaseAuth.getInstance()
        
        // Check if user is authenticated
        if (auth.currentUser == null) {
            goToSignIn()
            return
        }
        
        val userRole = intent.getStringExtra("user_role") ?: "unknown"
        
        // Set up toolbar
        setupToolbar()
        
        // Set up bottom navigation
        setupBottomNavigation()
        
        // Load default fragment based on role
        loadDefaultFragment(userRole)
    }
    
    private fun setupToolbar() {
        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = "Double Dot Academy"
    }
    
    private fun setupBottomNavigation() {
        bottomNavigation = findViewById(R.id.bottomNavigation)
        
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
    
    private fun loadDefaultFragment(userRole: String) {
        // For head coach, start with calendar
        if (userRole == "head_coach") {
            bottomNavigation.selectedItemId = R.id.nav_calendar
            loadFragment(CalendarFragment.newInstance())
        } else {
            // For other roles, you can customize this
            bottomNavigation.selectedItemId = R.id.nav_calendar
            loadFragment(CalendarFragment.newInstance())
        }
    }
    
    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }
    
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.dashboard_menu, menu)
        return true
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_sign_out -> {
                signOut()
                true
            }
            R.id.action_profile -> {
                // TODO: Implement profile functionality
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    override fun onBackPressed() {
        // Prevent going back to sign-in screen
        // You can customize this behavior
    }
    
    private fun signOut() {
        auth.signOut()
        goToSignIn()
    }
    
    private fun goToSignIn() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
} 