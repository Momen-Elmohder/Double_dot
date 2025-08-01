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
import com.example.double_dot_demo.fragments.ExpensesFragment
import com.example.double_dot_demo.fragments.TraineesFragment
import com.example.double_dot_demo.utils.RoleFixer
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import android.widget.Toast

class DashboardActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {
    
    private lateinit var auth: FirebaseAuth
    private lateinit var bottomNavigation: BottomNavigationView
    private lateinit var toolbar: MaterialToolbar
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private var currentUserRole: String = ""
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Set up global exception handler
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            android.util.Log.e("DashboardActivity", "Uncaught exception in thread ${thread.name}: ${throwable.message}")
            Toast.makeText(this, "App error occurred. Please restart.", Toast.LENGTH_LONG).show()
        }
        
        try {
            // Force the app to follow system night mode
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            
            setContentView(R.layout.activity_dashboard)
            
            // Initialize Firebase Auth
            auth = FirebaseAuth.getInstance()
            
            // Check if user is authenticated
            if (auth.currentUser == null) {
                android.util.Log.e("DashboardActivity", "No authenticated user found")
                goToSignIn()
                return
            }
            
            // Get user role from intent
            currentUserRole = intent.getStringExtra("user_role") ?: "coach"
            
            // Log the role for debugging
            android.util.Log.d("DashboardActivity", "Current user role: $currentUserRole")
            
            // For coach accounts, use ultra-simplified setup
            if (currentUserRole == "coach") {
                setupUltraSimpleCoachView()
            } else {
                // Full setup for other roles
                setupToolbar()
                setupNavigationDrawer()
                setupBottomNavigation()
                loadDefaultFragment(currentUserRole)
            }
        } catch (e: Exception) {
            android.util.Log.e("DashboardActivity", "Error in onCreate: ${e.message}")
            Toast.makeText(this, "Error starting app: ${e.message}", Toast.LENGTH_LONG).show()
            
            // Emergency recovery - try to load a simple fragment
            try {
                // Try to load a basic fragment without any complex setup
                val simpleFragment = CalendarFragment.newInstance()
                supportFragmentManager.beginTransaction()
                    .replace(R.id.fragmentContainer, simpleFragment)
                    .commit()
            } catch (recoveryException: Exception) {
                android.util.Log.e("DashboardActivity", "Emergency recovery failed: ${recoveryException.message}")
                // Last resort - show a simple message
                Toast.makeText(this, "App initialization failed. Please restart the app.", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    private fun setupUltraSimpleCoachView() {
        try {
            // Setup navigation drawer for coaches (without expenses)
            setupCoachNavigationDrawer()
            
            // Setup basic toolbar
            toolbar = findViewById(R.id.toolbar)
            if (toolbar != null) {
                setSupportActionBar(toolbar)
                supportActionBar?.title = "Double Dot Academy"
                // Enable navigation icon for coaches
                supportActionBar?.setDisplayHomeAsUpEnabled(true)
                
                // Set up navigation icon click listener
                toolbar.setNavigationOnClickListener {
                    try {
                        drawerLayout.openDrawer(GravityCompat.START)
                    } catch (e: Exception) {
                        android.util.Log.e("DashboardActivity", "Error opening drawer: ${e.message}")
                    }
                }
            }
            
            // Setup bottom navigation with calendar and employees
            setupCoachBottomNavigation()
            
            // Load trainees fragment directly
            loadFragment(TraineesFragment.newInstance().apply {
                arguments = Bundle().apply {
                    putString("user_role", currentUserRole)
                }
            })
            
        } catch (e: Exception) {
            android.util.Log.e("DashboardActivity", "Error setting up coach view: ${e.message}")
            Toast.makeText(this, "Error setting up coach view: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun setupSimpleToolbar() {
        try {
            toolbar = findViewById(R.id.toolbar)
            if (toolbar != null) {
                setSupportActionBar(toolbar)
                supportActionBar?.title = "Double Dot Academy"
                // No navigation drawer for coaches
            } else {
                android.util.Log.e("DashboardActivity", "Toolbar not found")
            }
        } catch (e: Exception) {
            android.util.Log.e("DashboardActivity", "Error setting up simple toolbar: ${e.message}")
        }
    }
    
    private fun setupToolbar() {
        try {
            toolbar = findViewById(R.id.toolbar)
            if (toolbar != null) {
                setSupportActionBar(toolbar)
                supportActionBar?.title = "Double Dot Academy"
                
                // Set up navigation icon click listener
                toolbar.setNavigationOnClickListener {
                    try {
                        drawerLayout.openDrawer(GravityCompat.START)
                    } catch (e: Exception) {
                        android.util.Log.e("DashboardActivity", "Error opening drawer: ${e.message}")
                    }
                }
            } else {
                android.util.Log.e("DashboardActivity", "Toolbar not found")
            }
        } catch (e: Exception) {
            android.util.Log.e("DashboardActivity", "Error setting up toolbar: ${e.message}")
        }
    }
    
    private fun setupNavigationDrawer() {
        try {
            drawerLayout = findViewById(R.id.drawerLayout)
            navigationView = findViewById(R.id.navigationView)
            
            if (drawerLayout != null && navigationView != null) {
                // Set navigation item selected listener
                navigationView.setNavigationItemSelectedListener(this)
                
                // Update user info in header
                updateUserInfo()
                
                // Set up role-based menu visibility
                setupRoleBasedMenuVisibility()
                
                // TEMPORARY: Add role fix button for debugging
                addRoleFixButton()
            } else {
                android.util.Log.e("DashboardActivity", "DrawerLayout or NavigationView not found")
            }
        } catch (e: Exception) {
            android.util.Log.e("DashboardActivity", "Error setting up navigation drawer: ${e.message}")
        }
    }
    
    private fun setupRoleBasedMenuVisibility() {
        val menu = navigationView.menu
        

        
        // Hide expenses for admin role
        if (currentUserRole == "admin") {
            menu.findItem(R.id.nav_expenses)?.isVisible = false
        }
        
        // Hide expenses for coach role
        if (currentUserRole == "coach") {
            menu.findItem(R.id.nav_expenses)?.isVisible = false
        }
        
        // Head coach should have full access (no limitations)
        if (currentUserRole == "head_coach") {
            // Ensure all menu items are visible for head coach
            menu.findItem(R.id.nav_expenses)?.isVisible = true
        }
    }
    
    private fun addRoleFixButton() {
        // ENABLED FOR DEBUGGING - Long press toolbar to fix roles
        toolbar?.setOnLongClickListener {
            try {
                showRoleFixDialog()
            } catch (e: Exception) {
                android.util.Log.e("DashboardActivity", "Error showing role fix dialog: ${e.message}")
            }
            true
        }
    }
    
    private fun showRoleFixDialog() {
        val editText = android.widget.EditText(this).apply {
            hint = "Enter email"
            layoutParams = android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("User Role Management")
            .setMessage("Enter the email of the user:")
            .setView(editText)
            .setPositiveButton("Check Role") { _, _ ->
                val email = editText.text.toString()
                if (email.isNotEmpty()) {
                    RoleFixer.checkUserRole(this, email)
                }
            }
            .setNegativeButton("Create as Head Coach") { _, _ ->
                val email = editText.text.toString()
                if (email.isNotEmpty()) {
                    RoleFixer.createUserDocument(this, email, "head_coach")
                }
            }
            .setNeutralButton("Create as Coach") { _, _ ->
                val email = editText.text.toString()
                if (email.isNotEmpty()) {
                    RoleFixer.createUserDocument(this, email, "coach")
                }
            }
            .create()
        
        dialog.show()
    }
    
    private fun updateUserInfo() {
        try {
            if (navigationView != null) {
                val headerView = navigationView.getHeaderView(0)
                if (headerView != null) {
                    val userNameText = headerView.findViewById<TextView>(R.id.nav_header_name)
                    val userEmailText = headerView.findViewById<TextView>(R.id.nav_header_email)
                    
                    if (userNameText != null && userEmailText != null) {
                        val currentUser = auth.currentUser
                        if (currentUser != null) {
                            val displayName = currentUser.displayName ?: "User"
                            val email = currentUser.email ?: "user@example.com"
                            
                            userNameText.text = displayName
                            userEmailText.text = email
                        }
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("DashboardActivity", "Error updating user info: ${e.message}")
        }
    }
    
    private fun setupBottomNavigation() {
        try {
            bottomNavigation = findViewById(R.id.bottomNavigation)
            
            if (currentUserRole == "coach") {
                // Simplified navigation for coaches
                setupSimpleBottomNavigation()
            } else {
                // Full navigation for other roles
                setupRoleBasedBottomNavigation()
                setupFullBottomNavigation()
            }
        } catch (e: Exception) {
            android.util.Log.e("DashboardActivity", "Error setting up bottom navigation: ${e.message}")
            Toast.makeText(this, "Error setting up navigation: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun setupCoachNavigationDrawer() {
        try {
            drawerLayout = findViewById(R.id.drawerLayout)
            navigationView = findViewById(R.id.navigationView)
            
            if (drawerLayout != null && navigationView != null) {
                // Set navigation item selected listener
                navigationView.setNavigationItemSelectedListener(this)
                
                // Update user info in header
                updateUserInfo()
                
                // Set up role-based menu visibility for coaches
                setupCoachMenuVisibility()
            } else {
                android.util.Log.e("DashboardActivity", "DrawerLayout or NavigationView not found")
            }
        } catch (e: Exception) {
            android.util.Log.e("DashboardActivity", "Error setting up coach navigation drawer: ${e.message}")
        }
    }
    
    private fun setupCoachMenuVisibility() {
        try {
            val menu = navigationView.menu
            
            // Hide expenses for coach role
            menu.findItem(R.id.nav_expenses)?.isVisible = false
            
            // Show account and settings for coaches
            menu.findItem(R.id.nav_account)?.isVisible = true
            menu.findItem(R.id.nav_settings)?.isVisible = true
            menu.findItem(R.id.nav_sign_out)?.isVisible = true
        } catch (e: Exception) {
            android.util.Log.e("DashboardActivity", "Error setting up coach menu visibility: ${e.message}")
        }
    }
    
    private fun setupCoachBottomNavigation() {
        try {
            bottomNavigation = findViewById(R.id.bottomNavigation)
            
            // Show all tabs for coaches (calendar, trainees, employees)
            bottomNavigation.menu.findItem(R.id.nav_calendar)?.isVisible = true
            bottomNavigation.menu.findItem(R.id.nav_trainees)?.isVisible = true
            bottomNavigation.menu.findItem(R.id.nav_employees)?.isVisible = true
            
            // Start with trainees tab
            bottomNavigation.selectedItemId = R.id.nav_trainees
            
            bottomNavigation.setOnItemSelectedListener { menuItem ->
                try {
                    when (menuItem.itemId) {
                        R.id.nav_calendar -> {
                            loadFragment(CalendarFragment.newInstance().apply {
                                arguments = Bundle().apply {
                                    putString("user_role", currentUserRole)
                                }
                            })
                            true
                        }
                        R.id.nav_trainees -> {
                            loadFragment(TraineesFragment.newInstance().apply {
                                arguments = Bundle().apply {
                                    putString("user_role", currentUserRole)
                                }
                            })
                            true
                        }
                        R.id.nav_employees -> {
                            // Coaches can view employees but with limited access
                            loadFragment(EmployeesFragment.newInstance().apply {
                                arguments = Bundle().apply {
                                    putString("user_role", currentUserRole)
                                }
                            })
                            true
                        }
                        else -> false
                    }
                } catch (e: Exception) {
                    android.util.Log.e("DashboardActivity", "Error in coach navigation: ${e.message}")
                    Toast.makeText(this, "Navigation error: ${e.message}", Toast.LENGTH_LONG).show()
                    false
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("DashboardActivity", "Error setting up coach bottom navigation: ${e.message}")
        }
    }
    
    private fun setupUltraSimpleBottomNavigation() {
        try {
            bottomNavigation = findViewById(R.id.bottomNavigation)
            
            // Hide employees and calendar tabs for coaches
            bottomNavigation.menu.findItem(R.id.nav_employees)?.isVisible = false
            bottomNavigation.menu.findItem(R.id.nav_calendar)?.isVisible = false
            
            // Only show trainees tab
            bottomNavigation.selectedItemId = R.id.nav_trainees
            
            bottomNavigation.setOnItemSelectedListener { menuItem ->
                try {
                    when (menuItem.itemId) {
                        R.id.nav_trainees -> {
                            loadFragment(TraineesFragment.newInstance().apply {
                                arguments = Bundle().apply {
                                    putString("user_role", currentUserRole)
                                }
                            })
                            true
                        }
                        else -> false
                    }
                } catch (e: Exception) {
                    android.util.Log.e("DashboardActivity", "Error in ultra simple navigation: ${e.message}")
                    Toast.makeText(this, "Navigation error: ${e.message}", Toast.LENGTH_LONG).show()
                    false
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("DashboardActivity", "Error setting up ultra simple bottom navigation: ${e.message}")
        }
    }
    
    private fun setupSimpleBottomNavigation() {
        try {
            // Hide employees tab for coaches
            bottomNavigation.menu.findItem(R.id.nav_employees)?.isVisible = false
            
            bottomNavigation.setOnItemSelectedListener { menuItem ->
                try {
                    when (menuItem.itemId) {
                        R.id.nav_calendar -> {
                            loadFragment(CalendarFragment.newInstance().apply {
                                arguments = Bundle().apply {
                                    putString("user_role", currentUserRole)
                                }
                            })
                            true
                        }
                        R.id.nav_trainees -> {
                            loadFragment(TraineesFragment.newInstance().apply {
                                arguments = Bundle().apply {
                                    putString("user_role", currentUserRole)
                                }
                            })
                            true
                        }
                        else -> false
                    }
                } catch (e: Exception) {
                    android.util.Log.e("DashboardActivity", "Error in simple navigation: ${e.message}")
                    Toast.makeText(this, "Navigation error: ${e.message}", Toast.LENGTH_LONG).show()
                    false
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("DashboardActivity", "Error setting up simple bottom navigation: ${e.message}")
        }
    }
    
    private fun setupFullBottomNavigation() {
        try {
            bottomNavigation.setOnItemSelectedListener { menuItem ->
                try {
                    when (menuItem.itemId) {
                        R.id.nav_calendar -> {
                            loadFragment(CalendarFragment.newInstance().apply {
                                arguments = Bundle().apply {
                                    putString("user_role", currentUserRole)
                                }
                            })
                            true
                        }
                        R.id.nav_trainees -> {
                            loadFragment(TraineesFragment.newInstance().apply {
                                arguments = Bundle().apply {
                                    putString("user_role", currentUserRole)
                                }
                            })
                            true
                        }
                        R.id.nav_employees -> {
                            // Only allow head coaches and admins to access employees
                            if (currentUserRole == "head_coach" || currentUserRole == "admin") {
                                loadFragment(EmployeesFragment.newInstance().apply {
                                    arguments = Bundle().apply {
                                        putString("user_role", currentUserRole)
                                    }
                                })
                                true
                            } else {
                                showToast("You don't have permission to access employees")
                                false
                            }
                        }
                        else -> false
                    }
                } catch (e: Exception) {
                    android.util.Log.e("DashboardActivity", "Error in navigation: ${e.message}")
                    Toast.makeText(this, "Navigation error: ${e.message}", Toast.LENGTH_LONG).show()
                    false
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("DashboardActivity", "Error setting up full bottom navigation: ${e.message}")
        }
    }
    
    private fun setupRoleBasedBottomNavigation() {
        try {
            // Hide employees tab for coaches
            if (currentUserRole == "coach" && bottomNavigation != null) {
                bottomNavigation.menu.findItem(R.id.nav_employees)?.isVisible = false
            }
        } catch (e: Exception) {
            android.util.Log.e("DashboardActivity", "Error setting up role-based bottom navigation: ${e.message}")
        }
    }
    
    private fun loadDefaultFragment(userRole: String) {
        try {
            // For head coach, start with calendar
            if (userRole == "head_coach") {
                bottomNavigation.selectedItemId = R.id.nav_calendar
                loadFragment(CalendarFragment.newInstance().apply {
                    arguments = Bundle().apply {
                        putString("user_role", userRole)
                    }
                })
            } else if (userRole == "coach") {
                // Coaches start with trainees
                bottomNavigation.selectedItemId = R.id.nav_trainees
                loadFragment(TraineesFragment.newInstance().apply {
                    arguments = Bundle().apply {
                        putString("user_role", userRole)
                    }
                })
            } else {
                // For other roles, start with calendar
                bottomNavigation.selectedItemId = R.id.nav_calendar
                loadFragment(CalendarFragment.newInstance().apply {
                    arguments = Bundle().apply {
                        putString("user_role", userRole)
                    }
                })
            }
        } catch (e: Exception) {
            android.util.Log.e("DashboardActivity", "Error loading default fragment: ${e.message}")
            Toast.makeText(this, "Error loading default page: ${e.message}", Toast.LENGTH_LONG).show()
            
            // Fallback: try to load trainees fragment
            try {
                bottomNavigation.selectedItemId = R.id.nav_trainees
                loadFragment(TraineesFragment.newInstance().apply {
                    arguments = Bundle().apply {
                        putString("user_role", userRole)
                    }
                })
            } catch (fallbackException: Exception) {
                android.util.Log.e("DashboardActivity", "Fallback fragment also failed: ${fallbackException.message}")
                Toast.makeText(this, "Critical error loading app", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    private fun loadFragment(fragment: Fragment) {
        try {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, fragment)
                .commit()
        } catch (e: Exception) {
            android.util.Log.e("DashboardActivity", "Error loading fragment: ${e.message}")
            Toast.makeText(this, "Error loading page: ${e.message}", Toast.LENGTH_LONG).show()
        }
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
            }
            R.id.nav_settings -> {
                // TODO: Navigate to settings fragment/activity
            }
            R.id.nav_expenses -> {
                // Check role-based access for expenses
                if (currentUserRole == "head_coach") {
                    loadFragment(ExpensesFragment.newInstance().apply {
                        arguments = Bundle().apply {
                            putString("user_role", currentUserRole)
                        }
                    })
                } else {
                    showToast("You don't have permission to access expenses")
                }
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
            super.onBackPressed()
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