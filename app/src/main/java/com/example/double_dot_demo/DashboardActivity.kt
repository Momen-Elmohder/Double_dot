package com.example.double_dot_demo

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentTransaction
import com.example.double_dot_demo.fragments.AttendanceFragment
import com.example.double_dot_demo.fragments.CompletedTraineesFragment
import com.example.double_dot_demo.fragments.EmployeesFragment
import com.example.double_dot_demo.fragments.ExpensesFragment
import com.example.double_dot_demo.fragments.TraineesFragment
import com.example.double_dot_demo.fragments.CoachAttendanceFragment
import com.example.double_dot_demo.fragments.SalaryFragment
import com.example.double_dot_demo.fragments.WaitingListFragment
import com.example.double_dot_demo.fragments.WeeklyScheduleFragment
import com.example.double_dot_demo.utils.RoleFixer
import com.example.double_dot_demo.utils.Role
import com.example.double_dot_demo.utils.Permissions
import com.example.double_dot_demo.utils.NavigationUtils
import com.example.double_dot_demo.utils.ButtonUtils
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import android.widget.Toast
import kotlinx.coroutines.*

class DashboardActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {
    
    private lateinit var auth: FirebaseAuth
    private lateinit var bottomNavigation: BottomNavigationView
    private lateinit var toolbar: MaterialToolbar
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private var currentUserRole: String = ""
    private val roleEnum: Role get() = Role.from(currentUserRole)
    
    // Navigation safety management
    private var currentFragment: Fragment? = null
    private var isNavigating = false
    private var navigationDebounceJob: Job? = null
    private val navigationScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var lastNavigationTime = 0L
    private val NAVIGATION_DEBOUNCE_TIME = 300L
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Improved exception handler
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            android.util.Log.e("DashboardActivity", "Uncaught exception in thread ${thread.name}: ${throwable.message}")
            runOnUiThread {
                Toast.makeText(this, "App error occurred. Please restart.", Toast.LENGTH_LONG).show()
            }
        }
        
        try {
            // Force light mode regardless of system setting
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            setContentView(R.layout.activity_dashboard)

            // Simplified watermark overlay
            setupWatermark()

            auth = FirebaseAuth.getInstance()
            if (auth.currentUser == null) { goToSignIn(); return }
            currentUserRole = intent.getStringExtra("user_role") ?: "coach"
            
            if (currentUserRole == "coach") {
                setupUltraSimpleCoachView()
            } else {
                setupToolbar()
                setupNavigationDrawer()
                setupBottomNavigation()
                loadDefaultFragment(currentUserRole)
            }
        } catch (e: Exception) {
            android.util.Log.e("DashboardActivity", "Error in onCreate: ${e.message}")
            Toast.makeText(this, "Error starting app: ${e.message}", Toast.LENGTH_LONG).show()
            try {
                val simpleFragment = AttendanceFragment()
                supportFragmentManager.beginTransaction().replace(R.id.fragmentContainer, simpleFragment).commit()
            } catch (_: Exception) { }
        }
    }

    override fun onPause() {
        super.onPause()
        // Cancel any pending navigation operations
        navigationDebounceJob?.cancel()
        isNavigating = false
    }

    override fun onDestroy() {
        super.onDestroy()
        // Clean up coroutines and navigation
        navigationScope.cancel()
        navigationDebounceJob?.cancel()
        ButtonUtils.cancelAllClicks()
        NavigationUtils.cancelNavigation()
    }

    private fun setupWatermark() {
        try {
            val container = findViewById<FrameLayout>(R.id.fragmentContainer)
            container.viewTreeObserver.addOnGlobalLayoutListener(object : android.view.ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    container.viewTreeObserver.removeOnGlobalLayoutListener(this)
                    val size = (container.width * 0.5f).toInt().coerceAtLeast(200) // Reduced size
                    val params = FrameLayout.LayoutParams(size, size, Gravity.CENTER)
                    val watermarkView = ImageView(this@DashboardActivity).apply {
                        setImageResource(R.drawable.double_dot_logo)
                        alpha = 0.08f // Reduced opacity
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        isClickable = false
                        isFocusable = false
                        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    }
                    container.addView(watermarkView, params)
                }
            })
        } catch (_: Exception) {}
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
                
                // Set up navigation icon click listener with debouncing
                toolbar.setNavigationOnClickListener {
                    if (NavigationUtils.isNavigationInProgress()) return@setNavigationOnClickListener
                    
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
                
                // Set up navigation icon click listener with debouncing
                toolbar.setNavigationOnClickListener {
                    if (NavigationUtils.isNavigationInProgress()) return@setNavigationOnClickListener
                    
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
        val role = roleEnum
        
        // Completed/Coach Attendance/Salary/Waiting List/Weekly Schedule
        menu.findItem(R.id.nav_completed)?.isVisible = Permissions.canAccessEmployees(role)
        menu.findItem(R.id.nav_coach_attendance)?.isVisible = Permissions.canAccessEmployeeAttendance(role)
        menu.findItem(R.id.nav_salary)?.isVisible = Permissions.canAccessSalaries(role)
        menu.findItem(R.id.nav_waiting_list)?.isVisible = Permissions.canAccessWaitingList(role)
        menu.findItem(R.id.nav_weekly_schedule)?.isVisible = Permissions.canAccessWaitingList(role)
        
        // Expenses hidden for admin and coach
        menu.findItem(R.id.nav_expenses)?.isVisible = Permissions.canAccessExpenses(role)
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
            val role = roleEnum
            // Employees tab visible only for head coach/admin
            bottomNavigation.menu.findItem(R.id.nav_employees)?.isVisible = Permissions.canAccessEmployees(role)
            
            bottomNavigation.setOnItemSelectedListener { menuItem ->
                // Prevent rapid navigation
                if (isNavigating) return@setOnItemSelectedListener false
                
                try {
                    when (menuItem.itemId) {
                        R.id.nav_calendar -> { safeLoadFragment(AttendanceFragment()) }
                        R.id.nav_trainees -> { safeLoadFragment(TraineesFragment()) }
                        R.id.nav_employees -> {
                            if (Permissions.canAccessEmployees(role)) {
                                safeLoadFragment(EmployeesFragment.newInstance(currentUserRole))
                            } else { 
                                showToast("You don't have permission to access employees")
                                false 
                            }
                        }
                        else -> false
                    }
                } catch (e: Exception) {
                    android.util.Log.e("DashboardActivity", "Error in navigation: ${e.message}")
                    if (!isFinishing && !isDestroyed) {
                        Toast.makeText(this, "Navigation error: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                    false
                }
            }
        } catch (_: Exception) {}
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
            menu.findItem(R.id.nav_sign_out)?.isVisible = true
        } catch (e: Exception) {
            android.util.Log.e("DashboardActivity", "Error setting up coach menu visibility: ${e.message}")
        }
    }
    
    private fun setupCoachBottomNavigation() {
        try {
            bottomNavigation = findViewById(R.id.bottomNavigation)
            
            // Show only calendar and trainees tabs for coaches (NO EMPLOYEES)
            bottomNavigation.menu.findItem(R.id.nav_calendar)?.isVisible = true
            bottomNavigation.menu.findItem(R.id.nav_trainees)?.isVisible = true
            bottomNavigation.menu.findItem(R.id.nav_employees)?.isVisible = false
            
            // Start with trainees tab
            bottomNavigation.selectedItemId = R.id.nav_trainees
            
            bottomNavigation.setOnItemSelectedListener { menuItem ->
                // Prevent rapid navigation
                if (isNavigating) return@setOnItemSelectedListener false
                
                try {
                    when (menuItem.itemId) {
                        R.id.nav_calendar -> {
                            safeLoadFragment(AttendanceFragment())
                        }
                        R.id.nav_trainees -> {
                            safeLoadFragment(TraineesFragment().apply {
                                arguments = Bundle().apply {
                                    putString("user_role", currentUserRole)
                                }
                            })
                        }
                        R.id.nav_employees -> {
                            // Coaches cannot access employees
                            showToast("You don't have permission to access employees")
                            false
                        }
                        else -> false
                    }
                } catch (e: Exception) {
                    android.util.Log.e("DashboardActivity", "Error in coach navigation: ${e.message}")
                    if (!isFinishing && !isDestroyed) {
                        Toast.makeText(this, "Navigation error: ${e.message}", Toast.LENGTH_LONG).show()
                    }
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
                            loadFragment(AttendanceFragment())
                            true
                        }
                        R.id.nav_trainees -> {
                            loadFragment(TraineesFragment().apply {
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
                            loadFragment(AttendanceFragment())
                            true
                        }
                        R.id.nav_trainees -> {
                            loadFragment(TraineesFragment())
                            true
                        }
                        R.id.nav_employees -> {
                            // Only allow head coaches and admins to access employees
                            if (Permissions.canAccessEmployees(roleEnum)) {
                                loadFragment(EmployeesFragment.newInstance(currentUserRole))
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
            // For head coach, start with attendance
            if (userRole == "head_coach") {
                bottomNavigation.selectedItemId = R.id.nav_calendar
                loadFragment(AttendanceFragment())
            } else if (userRole == "coach") {
                // Coaches start with trainees
                bottomNavigation.selectedItemId = R.id.nav_trainees
                loadFragment(TraineesFragment().apply {
                    arguments = Bundle().apply {
                        putString("user_role", currentUserRole)
                    }
                })
            } else {
                // For other roles, start with attendance
                bottomNavigation.selectedItemId = R.id.nav_calendar
                loadFragment(AttendanceFragment())
            }
        } catch (e: Exception) {
            android.util.Log.e("DashboardActivity", "Error loading default fragment: ${e.message}")
            Toast.makeText(this, "Error loading default page: ${e.message}", Toast.LENGTH_LONG).show()
            
            // Fallback: try to load trainees fragment
            try {
                bottomNavigation.selectedItemId = R.id.nav_trainees
                loadFragment(TraineesFragment().apply {
                    arguments = Bundle().apply {
                        putString("user_role", currentUserRole)
                    }
                })
            } catch (fallbackException: Exception) {
                android.util.Log.e("DashboardActivity", "Fallback fragment also failed: ${fallbackException.message}")
                Toast.makeText(this, "Critical error loading app", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    private fun loadFragment(fragment: Fragment) {
        // Comprehensive navigation safety checks
        if (isFinishing || isDestroyed) return
        if (isNavigating) return
        if (supportFragmentManager.isDestroyed || supportFragmentManager.isStateSaved) return
        
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastNavigationTime < NAVIGATION_DEBOUNCE_TIME) {
            // Debounce rapid navigation attempts
            return
        }
        
        try {
            isNavigating = true
            lastNavigationTime = currentTime
            
            // Cancel any pending navigation operations
            navigationDebounceJob?.cancel()
            
            // Execute pending transactions safely
            if (!supportFragmentManager.isDestroyed && !supportFragmentManager.isStateSaved) {
                supportFragmentManager.executePendingTransactions()
            }
            
            // Check if we're still in a valid state
            if (isFinishing || isDestroyed || supportFragmentManager.isDestroyed) {
                isNavigating = false
                return
            }
            
            // Use optimized transaction with safety checks
            val transaction = supportFragmentManager.beginTransaction()
                .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
                .replace(R.id.fragmentContainer, fragment)
            
            // Add to back stack only if not the first fragment
            if (currentFragment != null) {
                transaction.addToBackStack(null)
            }
            
            // Commit with state loss protection
            if (supportFragmentManager.isStateSaved) {
                transaction.commitAllowingStateLoss()
            } else {
                transaction.commit()
            }
            
            currentFragment = fragment
            
            // Reset navigation flag after a delay
            navigationDebounceJob = navigationScope.launch {
                delay(NAVIGATION_DEBOUNCE_TIME)
                isNavigating = false
            }
            
        } catch (e: Exception) {
            android.util.Log.e("DashboardActivity", "Error loading fragment: ${e.message}")
            if (!isFinishing && !isDestroyed) {
                Toast.makeText(this, "Error loading page: ${e.message}", Toast.LENGTH_LONG).show()
            }
            isNavigating = false
        }
    }

    private fun safeLoadFragment(fragment: Fragment): Boolean {
        return NavigationUtils.safeLoadFragment(this, fragment, R.id.fragmentContainer, true)
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
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        val role = roleEnum
        when (item.itemId) {
            R.id.nav_account -> loadFragment(com.example.double_dot_demo.fragments.AccountFragment())
            R.id.nav_expenses -> if (Permissions.canAccessExpenses(role)) loadFragment(ExpensesFragment.newInstance()) else showToast("No permission")
            R.id.nav_completed -> if (Permissions.canAccessEmployees(role)) loadFragment(CompletedTraineesFragment()) else showToast("No permission")
            R.id.nav_coach_attendance -> if (Permissions.canAccessEmployeeAttendance(role)) loadFragment(CoachAttendanceFragment.newInstance()) else showToast("No permission")
            R.id.nav_salary -> if (Permissions.canAccessSalaries(role)) loadFragment(SalaryFragment.newInstance()) else showToast("No permission")
            R.id.nav_waiting_list -> if (Permissions.canAccessWaitingList(role)) loadFragment(WaitingListFragment().apply { arguments = Bundle().apply { putString("user_role", currentUserRole) } }) else showToast("No permission")
            R.id.nav_weekly_schedule -> if (Permissions.canAccessWaitingList(role)) loadFragment(WeeklyScheduleFragment().apply { arguments = Bundle().apply { putString("user_role", currentUserRole) } }) else showToast("No permission")
            R.id.nav_sign_out -> signOut()
        }
        drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }
    
    private fun showToast(message: String) {
        NavigationUtils.safeShowToast(this, message)
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