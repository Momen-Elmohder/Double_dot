package com.example.double_dot_demo

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import com.example.double_dot_demo.fragments.CalendarFragment
import com.example.double_dot_demo.fragments.EmployeesFragment
import com.example.double_dot_demo.fragments.TraineesFragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth

class DashboardActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {
    
    private lateinit var auth: FirebaseAuth
    private lateinit var bottomNavigation: BottomNavigationView
    private lateinit var toolbar: MaterialToolbar
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    
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
        
        // Set up navigation drawer
        setupNavigationDrawer()
        
        // Set up bottom navigation
        setupBottomNavigation()
        
        // Load default fragment based on role
        loadDefaultFragment(userRole)
    }
    
    private fun setupToolbar() {
        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = "Double Dot Academy"
        
        // Set up navigation icon click listener
        toolbar.setNavigationOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }
    }
    
    private fun setupNavigationDrawer() {
        drawerLayout = findViewById(R.id.drawerLayout)
        navigationView = findViewById(R.id.navigationView)
        
        // Set navigation item selected listener
        navigationView.setNavigationItemSelectedListener(this)
        
        // Update user info in header
        updateUserInfo()
    }
    
    private fun updateUserInfo() {
        val headerView = navigationView.getHeaderView(0)
        val userNameText = headerView.findViewById<TextView>(R.id.nav_header_name)
        val userEmailText = headerView.findViewById<TextView>(R.id.nav_header_email)
        
        val currentUser = auth.currentUser
        if (currentUser != null) {
            val displayName = currentUser.displayName ?: "User"
            val email = currentUser.email ?: "user@example.com"
            
            userNameText.text = displayName
            userEmailText.text = email
        }
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
    
    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_account -> {
                // TODO: Navigate to account fragment/activity
                showToast("Account clicked")
            }
            R.id.nav_settings -> {
                // TODO: Navigate to settings fragment/activity
                showToast("Settings clicked")
            }
            R.id.nav_expenses -> {
                // TODO: Navigate to expenses fragment/activity
                showToast("Expenses clicked")
            }
            R.id.nav_sign_out -> {
                signOut()
            }
        }
        
        // Close the drawer
        drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }
    
    private fun showToast(message: String) {
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show()
    }
    
    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            // Prevent going back to sign-in screen
            // You can customize this behavior
        }
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