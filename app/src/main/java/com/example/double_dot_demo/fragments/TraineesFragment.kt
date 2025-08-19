package com.example.double_dot_demo.fragments

import android.os.Bundle
import android.Manifest
import android.app.Activity
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.result.contract.ActivityResultContracts
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.double_dot_demo.R
import com.example.double_dot_demo.adapters.TraineeAdapter
import com.example.double_dot_demo.databinding.FragmentTraineesBinding
import com.example.double_dot_demo.dialogs.AddTraineeDialog
import com.example.double_dot_demo.dialogs.RenewTraineeDialog
import com.example.double_dot_demo.models.Trainee
import com.example.double_dot_demo.utils.LoadingManager
import com.example.double_dot_demo.utils.PerformanceUtils
import com.example.double_dot_demo.utils.ScheduleManager
import com.example.double_dot_demo.utils.SalaryManager
import com.example.double_dot_demo.utils.Role
import com.example.double_dot_demo.utils.Permissions
import com.example.double_dot_demo.utils.NavigationUtils
import com.example.double_dot_demo.utils.ButtonUtils
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*
import androidx.recyclerview.widget.RecyclerView

class TraineesFragment : Fragment() {

    private var _binding: FragmentTraineesBinding? = null
    private val binding get() = _binding!!
    private lateinit var firestore: FirebaseFirestore
    private lateinit var traineeAdapter: TraineeAdapter
    private lateinit var loadingManager: LoadingManager
    private lateinit var scheduleManager: ScheduleManager
    private lateinit var salaryManager: SalaryManager

    private val trainees = mutableListOf<Trainee>()
    private val filteredTrainees = mutableListOf<Trainee>()

    private var currentUserRole: String = ""
    private val roleEnum: Role get() = Role.from(currentUserRole)
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    
    // Search and sort variables
    private var searchQuery: String = ""
    private var sortBy: String = "age"
    private var selectedBranch: String = ""
    private var selectedStatus: String = ""

    // Coroutine scope for background operations
    private val fragmentScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var activeAddTraineeDialog: AddTraineeDialog? = null

    private val contactPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            try {
                val uri: Uri? = result.data?.data
                if (uri != null) {
                    val cursor: Cursor? = requireContext().contentResolver.query(
                        uri,
                        arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                        null,
                        null,
                        null
                    )
                    cursor?.use {
                        if (it.moveToFirst()) {
                            val number = it.getString(0)
                            activeAddTraineeDialog?.setPickedPhoneNumber(number)
                        }
                    }
                }
            } catch (_: Exception) {}
        }
    }

    private val requestContactsPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) launchContactPicker() else showToast("Contacts permission denied")
    }
    
    // Listener registration for proper cleanup
    private var traineesListener: ListenerRegistration? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTraineesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        try {
            firestore = FirebaseFirestore.getInstance()
            
            // Initialize loading manager
            loadingManager = LoadingManager(binding.loadingOverlay.root)
            
            // Initialize schedule manager
            scheduleManager = ScheduleManager()
            
            // Initialize salary manager
            salaryManager = SalaryManager()
            
            // Get current user role from arguments
            currentUserRole = arguments?.getString("user_role") ?: "coach"
            
            // Debug: Log the role
            android.util.Log.d("TraineesFragment", "Initial role: $currentUserRole")
            android.util.Log.d("TraineesFragment", "Arguments: ${arguments?.keySet()}")
            
            // Load role from Firebase in background
            loadCurrentUserRole()
            
            setupRecyclerView()
            setupSearchAndSort()
            
            // Setup add button with initial role (will be updated when role is loaded)
            setupAddButton()

            // Hook contact picker for AddTraineeDialog
            // The dialog will call back to open the picker
            
            // Load trainees with loading animation
            fragmentScope.launch {
                try {
                    loadingManager.showLoading("Loading trainees...")
                    loadTrainees()
                } catch (e: Exception) {
                    android.util.Log.e("TraineesFragment", "Error loading data: ${e.message}")
                    if (isAdded) {
                        Toast.makeText(requireContext(), "Error loading data: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                } finally {
                    loadingManager.hideLoading()
                }
            }
            
        } catch (e: Exception) {
            android.util.Log.e("TraineesFragment", "Error in onViewCreated: ${e.message}")
            if (isAdded) {
                Toast.makeText(requireContext(), "Error loading trainees: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun setupRecyclerView() {
        try {
            binding.recyclerViewTrainees.layoutManager = LinearLayoutManager(requireContext())
            
            val initialCoachView = roleEnum == Role.COACH
            traineeAdapter = TraineeAdapter(
                isCoachView = initialCoachView,
                onEditClick = { trainee -> if (canEditTrainee()) showEditTraineeDialog(trainee) },
                onDeleteClick = { trainee -> if (canDeleteTrainee()) deleteTrainee(trainee) },
                onRenewClick = { trainee -> if (canRenewTrainee()) showRenewTraineeDialog(trainee) },
                onFreezeClick = { trainee -> if (canFreezeTrainee()) toggleFreezeStatus(trainee) },
                onShowDetailsClick = { trainee -> if (canShowDetails()) showTraineeDetailsDialog(trainee) }
            )
            
            binding.recyclerViewTrainees.adapter = traineeAdapter
            
            // Setup optimized scroll listener
            binding.recyclerViewTrainees.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    super.onScrollStateChanged(recyclerView, newState)
                    // Handle scroll state changes if needed
                }
                
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)
                    // Handle scroll events if needed
                }
            })
            
            android.util.Log.d("TraineesFragment", "RecyclerView setup completed")
        } catch (e: Exception) {
            android.util.Log.e("TraineesFragment", "Error setting up RecyclerView: ${e.message}")
        }
    }

    private fun setupAddButton() {
        android.util.Log.d("TraineesFragment", "Setting up add button. Current role: $currentUserRole")
        
        val isCoach = roleEnum == Role.COACH
        binding.btnAddTrainee.visibility = if (isCoach) View.GONE else View.VISIBLE
        binding.btnAddTrainee.setOnClickListener { if (!isCoach) showAddTraineeDialog() }
        
        // Proper role-based visibility
        if (currentUserRole == "head_coach" || currentUserRole == "admin") {
            binding.btnAddTrainee.visibility = View.VISIBLE
            android.util.Log.d("TraineesFragment", "Add button set to VISIBLE for role: $currentUserRole")
        } else {
            binding.btnAddTrainee.visibility = View.GONE
            android.util.Log.d("TraineesFragment", "Add button set to GONE for role: $currentUserRole")
        }
    }

    private fun setupSearchAndSort() {
        // Setup search functionality with debouncing
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            private var searchJob: Job? = null
            
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                searchJob?.cancel()
                searchJob = fragmentScope.launch {
                    delay(300) // Debounce search
                    searchQuery = s.toString().trim()
                    filterAndSortTrainees()
                }
            }
        })

        // Setup sort by dropdown - added separate status categories
        val sortByOptions = listOf("Age", "Status", "Completed", "Active", "Frozen", "Complete")
        val sortByAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, sortByOptions)
        binding.actvSortBy.setAdapter(sortByAdapter)
        binding.actvSortBy.setText("Age", false)

        binding.actvSortBy.setOnItemClickListener { _, _, position, _ ->
            sortBy = when (position) {
                0 -> "age"
                1 -> "status"
                2 -> "completed"
                3 -> "active"
                4 -> "frozen"
                5 -> "complete"
                else -> "age"
            }
            filterAndSortTrainees()
        }

        // Setup branch dropdown
        val branchOptions = listOf("All Branches", "نادي التوكيلات", "نادي اليخت", "المدينة الرياضية")
        val branchAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, branchOptions)
        binding.actvBranch.setAdapter(branchAdapter)
        binding.actvBranch.setText("All Branches", false)

        binding.actvBranch.setOnItemClickListener { _, _, position, _ ->
            selectedBranch = if (position == 0) "" else branchOptions[position]
            filterAndSortTrainees()
        }

        // Setup status dropdown - only show actual program statuses
        val statusOptions = listOf("All Status", "academy", "team", "preparation")
        val statusAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, statusOptions)
        binding.actvStatus.setAdapter(statusAdapter)
        binding.actvStatus.setText("All Status", false)

        binding.actvStatus.setOnItemClickListener { _, _, position, _ ->
            selectedStatus = if (position == 0) "" else statusOptions[position]
            filterAndSortTrainees()
        }
    }

    private fun loadCurrentUserRole() {
        try {
            val currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
            if (currentUser != null) {
                android.util.Log.d("TraineesFragment", "Loading role for user: ${currentUser.uid}")
                firestore.collection("users").document(currentUser.uid)
                    .get()
                    .addOnSuccessListener { document ->
                        if (document != null && document.exists()) {
                            val newRole = document.getString("role") ?: "coach"
                            android.util.Log.d("TraineesFragment", "Role from Firebase: $newRole")
                            currentUserRole = newRole
                            // Update adapter with new role
                            android.util.Log.d("TraineesFragment", "Updating adapter with new role: $currentUserRole")
                            if (isAdded) {
                                // Head coaches and admins see buttons; coaches get limited view
                                val isCoachView = currentUserRole == "coach"
                                traineeAdapter.setIsCoachView(isCoachView)
                                android.util.Log.d("TraineesFragment", "Adapter setIsCoachView: $isCoachView")
                            }
                            
                            // Update add button visibility based on new role
                            setupAddButton()
                        }
                    }
                    .addOnFailureListener { e ->
                        android.util.Log.e("TraineesFragment", "Error loading user role: ${e.message}")
                    }
            }
        } catch (e: Exception) {
            android.util.Log.e("TraineesFragment", "Error loading current user role: ${e.message}")
        }
    }

    private fun loadTrainees() {
        try {
            android.util.Log.d("TraineesFragment", "Loading trainees for role: $currentUserRole")
            
            // Load trainees based on user role
            val query = if (roleEnum == Role.COACH) {
                // Coaches can only see their own trainees
                val coachId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
                android.util.Log.d("TraineesFragment", "Coach ID: $coachId")
                // Temporarily load all trainees for coaches to test visibility
                firestore.collection("trainees")
                    .orderBy("name", com.google.firebase.firestore.Query.Direction.ASCENDING)
            } else {
                // Head coaches and admins can see all trainees
                android.util.Log.d("TraineesFragment", "Loading all trainees for admin/head coach")
                firestore.collection("trainees")
                    .orderBy("name", com.google.firebase.firestore.Query.Direction.ASCENDING)
            }
            
            traineesListener = query.addSnapshotListener { snapshot, e ->
                if (e != null) {
                    android.util.Log.w("TraineesFragment", "Query failed: ${e.message}")
                    return@addSnapshotListener
                }

                if (isAdded) {
                    // Process data in background
                    PerformanceUtils.launchInBackground {
                        try {
                            val newTrainees = mutableListOf<Trainee>()
                            android.util.Log.d("TraineesFragment", "Snapshot size: ${snapshot?.size() ?: 0}")
                            
                            if (snapshot != null) {
                                for (document in snapshot) {
                                    val trainee = document.toObject(Trainee::class.java)
                                    trainee?.let { originalTrainee ->
                                        val traineeWithId = originalTrainee.copy(id = document.id)
                                        newTrainees.add(traineeWithId)
                                        android.util.Log.d("TraineesFragment", "Added trainee: ${traineeWithId.name}")
                                    }
                                }
                            }

                            // Update UI on main thread
                            PerformanceUtils.runOnMainThread {
                                try {
                                    trainees.clear()
                                    trainees.addAll(newTrainees)
                                    android.util.Log.d("TraineesFragment", "Total trainees loaded: ${trainees.size}")
                                    filterAndSortTrainees()
                                } catch (e: Exception) {
                                    android.util.Log.e("TraineesFragment", "Error updating UI: ${e.message}")
                                }
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("TraineesFragment", "Error processing trainees: ${e.message}")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("TraineesFragment", "Error loading trainees: ${e.message}")
            if (isAdded) {
                Toast.makeText(requireContext(), "Error loading trainees: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun filterAndSortTrainees() {
        android.util.Log.d("TraineesFragment", "Filtering and sorting trainees. Original count: ${trainees.size}")
        android.util.Log.d("TraineesFragment", "Search query: '$searchQuery', Sort by: '$sortBy'")
        android.util.Log.d("TraineesFragment", "Filters - Branch: '$selectedBranch', Status: '$selectedStatus'")
        
        // Process filtering and sorting in background
        PerformanceUtils.launchInBackground {
            try {
                // Filter trainees based on search query and filters
                val newFilteredTrainees = mutableListOf<Trainee>()
                
                trainees.forEach { trainee ->
                    // Search filter
                    val matchesSearch = searchQuery.isEmpty() || 
                        trainee.name?.contains(searchQuery, ignoreCase = true) == true ||
                        trainee.branch?.contains(searchQuery, ignoreCase = true) == true ||
                        trainee.coachName?.contains(searchQuery, ignoreCase = true) == true ||
                        trainee.status?.contains(searchQuery, ignoreCase = true) == true ||
                        trainee.remainingSessions.toString().contains(searchQuery)
                    
                    // Branch filter
                    val matchesBranch = selectedBranch.isEmpty() || trainee.branch == selectedBranch
                    
                    // Status filter
                    val matchesStatus = selectedStatus.isEmpty() || trainee.status == selectedStatus
                    
                    if (matchesSearch && matchesBranch && matchesStatus) {
                        newFilteredTrainees.add(trainee)
                    }
                }

                android.util.Log.d("TraineesFragment", "After filtering: ${newFilteredTrainees.size} trainees")

                // Sort filtered trainees
                val sortedList = when (sortBy) {
                    "age" -> {
                        android.util.Log.d("TraineesFragment", "Sorting by Age")
                        newFilteredTrainees.sortedBy { it.age }
                    }
                    "status" -> {
                        android.util.Log.d("TraineesFragment", "Sorting by Status")
                        newFilteredTrainees.sortedBy { it.status }
                    }
                    "completed" -> {
                        android.util.Log.d("TraineesFragment", "Sorting by Completed sessions")
                        newFilteredTrainees.sortedBy { trainee ->
                            val completedSessions = trainee.attendanceSessions.values.count { isPresent -> isPresent }
                            android.util.Log.d("TraineesFragment", "Trainee ${trainee.name}: $completedSessions completed sessions")
                            completedSessions
                        }
                    }
                    "active" -> {
                        android.util.Log.d("TraineesFragment", "Sorting by Active status")
                        newFilteredTrainees.sortedBy { trainee ->
                            val isActive = trainee.status == "active" || trainee.status == "academy" || trainee.status == "team" || trainee.status == "preparation"
                            !isActive // Put active trainees first
                        }
                    }
                    "frozen" -> {
                        android.util.Log.d("TraineesFragment", "Sorting by Frozen status")
                        newFilteredTrainees.sortedBy { trainee ->
                            trainee.status == "frozen"
                        }
                    }
                    "complete" -> {
                        android.util.Log.d("TraineesFragment", "Sorting by Complete status")
                        newFilteredTrainees.sortedBy { trainee ->
                            trainee.status == "completed"
                        }
                    }
                    else -> {
                        android.util.Log.d("TraineesFragment", "Default sorting by Age")
                        newFilteredTrainees.sortedBy { it.age }
                    }
                }
                
                // Update UI on main thread
                PerformanceUtils.runOnMainThread {
                    try {
                        filteredTrainees.clear()
                        filteredTrainees.addAll(sortedList)
                        
                        // Update adapter with new data
                        traineeAdapter.submitList(filteredTrainees.toList())
                        
                        // Log the first few trainees to verify sorting
                        if (filteredTrainees.isNotEmpty()) {
                            android.util.Log.d("TraineesFragment", "First 3 trainees after sorting:")
                            filteredTrainees.take(3).forEachIndexed { index, trainee ->
                                when (sortBy) {
                                    "age" -> android.util.Log.d("TraineesFragment", "${index + 1}. ${trainee.name} - Age: ${trainee.age}")
                                    "status" -> android.util.Log.d("TraineesFragment", "${index + 1}. ${trainee.name} - Status: ${trainee.status}")
                                    "completed" -> {
                                        val completedSessions = trainee.attendanceSessions.values.count { isPresent -> isPresent }
                                        android.util.Log.d("TraineesFragment", "${index + 1}. ${trainee.name} - Completed sessions: $completedSessions")
                                    }
                                    "active" -> android.util.Log.d("TraineesFragment", "${index + 1}. ${trainee.name} - Status: ${trainee.status}")
                                    "frozen" -> android.util.Log.d("TraineesFragment", "${index + 1}. ${trainee.name} - Status: ${trainee.status}")
                                    "complete" -> android.util.Log.d("TraineesFragment", "${index + 1}. ${trainee.name} - Status: ${trainee.status}")
                                    else -> android.util.Log.d("TraineesFragment", "${index + 1}. ${trainee.name} - Age: ${trainee.age}")
                                }
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("TraineesFragment", "Error updating adapter: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("TraineesFragment", "Error filtering and sorting: ${e.message}")
            }
        }
    }

    private fun updateStats() {
        // Stats counters removed from layout
    }

    private fun showAddTraineeDialog() {
        try {
            if (!isAdded || context == null) return
            
            val dialog = AddTraineeDialog(requireContext())
            activeAddTraineeDialog = dialog
            dialog.setOnPickPhoneClickListener {
                checkContactsPermissionAndPick()
            }
            dialog.setOnSaveClickListener { trainee ->
                safeExecute {
                    addTrainee(trainee)
                }
            }
            dialog.show()
            
        } catch (e: Exception) {
            android.util.Log.e("TraineesFragment", "Error showing add trainee dialog: ${e.message}")
        }
    }

    private fun showEditTraineeDialog(trainee: Trainee) {
        try {
            if (!isAdded || context == null) return
            
            val dialog = AddTraineeDialog(requireContext(), trainee)
            activeAddTraineeDialog = dialog
            dialog.setOnPickPhoneClickListener { checkContactsPermissionAndPick() }
            dialog.setOnSaveClickListener { updatedTrainee ->
                safeExecute {
                    updateTrainee(updatedTrainee)
                }
            }
            dialog.show()
        } catch (e: Exception) {
            android.util.Log.e("TraineesFragment", "Error showing edit trainee dialog: ${e.message}")
        }
    }

    private fun addTrainee(trainee: Trainee) {
        try {
            if (!isAdded || context == null) return
            
            val progressDialog = showLoadingDialog()
            
            // Only set the current user as coach if no coach was selected
            val currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
            if (currentUser != null && trainee.coachId.isEmpty()) {
                trainee.coachId = currentUser.uid
                android.util.Log.d("TraineesFragment", "No coach selected, using current user as coach: ${currentUser.uid}")
            } else {
                android.util.Log.d("TraineesFragment", "Using selected coach ID: ${trainee.coachId}")
            }
            
            android.util.Log.d("TraineesFragment", "=== ADDING TRAINEE ===")
            android.util.Log.d("TraineesFragment", "Trainee Name: ${trainee.name}")
            android.util.Log.d("TraineeFragment", "Coach ID: ${trainee.coachId}")
            android.util.Log.d("TraineeFragment", "Coach Name: ${trainee.coachName}")
            android.util.Log.d("TraineeFragment", "Payment Amount: ${trainee.paymentAmount}")
            android.util.Log.d("TraineeFragment", "Status: ${trainee.status}")
            
            firestore.collection("trainees")
                .add(trainee)
                .addOnSuccessListener { documentReference ->
                    val traineeWithId = trainee.copy(id = documentReference.id)
                    
                    // Add to schedule automatically
                    scheduleManager.addTraineeToSchedule(traineeWithId) { scheduleSuccess ->
                        if (scheduleSuccess) {
                            android.util.Log.d("TraineesFragment", "Trainee added to schedule successfully")
                        } else {
                            android.util.Log.w("TraineesFragment", "Failed to add trainee to schedule")
                        }
                    }
                    
                    progressDialog.dismiss()
                    showToast("Trainee added successfully")
                    
                    // Automatically create expense entry since trainees are automatically paid
                    createTraineeExpenseEntry(trainee, documentReference.id, "TRAINEE_ADDED")
                    
                    // Automatically calculate salary for the coach when trainee is added
                    if (trainee.coachId.isNotEmpty()) {
                        android.util.Log.d("TraineesFragment", "Starting automatic salary calculation for coach: ${trainee.coachId}")
                        fragmentScope.launch {
                            try {
                                val success = salaryManager.recalculateSalaryForCoach(trainee.coachId)
                                if (success) {
                                    android.util.Log.d("TraineesFragment", "✅ Salary calculated automatically for coach: ${trainee.coachId}")
                                    showToast("Salary updated automatically")
                                } else {
                                    android.util.Log.w("TraineesFragment", "❌ Failed to calculate salary for coach: ${trainee.coachId}")
                                    showToast("Failed to update salary")
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("TraineesFragment", "Error calculating salary: ${e.message}")
                                e.printStackTrace()
                                showToast("Error updating salary: ${e.message}")
                            }
                        }
                    } else {
                        android.util.Log.w("TraineesFragment", "No coach ID found for trainee, skipping salary calculation")
                    }
                }
                .addOnFailureListener { e ->
                    progressDialog.dismiss()
                    android.util.Log.e("TraineesFragment", "Error adding trainee: ${e.message}")
                    showToast("Failed to add trainee")
                }
        } catch (e: Exception) {
            android.util.Log.e("TraineesFragment", "Error adding trainee: ${e.message}")
        }
    }

    private fun updateTrainee(trainee: Trainee) {
        try {
            if (!isAdded || context == null) return
            
            val progressDialog = showLoadingDialog()
            
            // Get the original trainee data to compare schedule changes
            val originalTrainee = trainees.find { it.id == trainee.id }
            
            firestore.collection("trainees").document(trainee.id)
                .set(trainee)
                .addOnSuccessListener {
                    // Update schedule if needed
                    if (originalTrainee != null) {
                        scheduleManager.updateTraineeInSchedule(originalTrainee, trainee) { scheduleSuccess ->
                            if (scheduleSuccess) {
                                android.util.Log.d("TraineesFragment", "Trainee schedule updated successfully")
                            } else {
                                android.util.Log.w("TraineesFragment", "Failed to update trainee schedule")
                            }
                        }
                    } else {
                        // If we don't have original data, just add to schedule
                        scheduleManager.addTraineeToSchedule(trainee) { scheduleSuccess ->
                            if (scheduleSuccess) {
                                android.util.Log.d("TraineesFragment", "Trainee added to schedule successfully")
                            } else {
                                android.util.Log.w("TraineesFragment", "Failed to add trainee to schedule")
                            }
                        }
                    }
                    
                    progressDialog.dismiss()
                    showToast("Trainee updated successfully")
                    
                    // Automatically create expense entry since trainees are automatically paid
                    createTraineeExpenseEntry(trainee, trainee.id, "TRAINEE_UPDATED")
                    
                    // Automatically calculate salary for the coach when trainee is updated
                    if (trainee.coachId.isNotEmpty()) {
                        fragmentScope.launch {
                            try {
                                val success = salaryManager.recalculateSalaryForCoach(trainee.coachId)
                                if (success) {
                                    android.util.Log.d("TraineesFragment", "Salary calculated automatically for coach after update: ${trainee.coachId}")
                                } else {
                                    android.util.Log.w("TraineesFragment", "Failed to calculate salary for coach after update: ${trainee.coachId}")
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("TraineesFragment", "Error calculating salary after update: ${e.message}")
                            }
                        }
                    }
                }
                .addOnFailureListener { e ->
                    progressDialog.dismiss()
                    android.util.Log.e("TraineesFragment", "Error updating trainee: ${e.message}")
                    showToast("Failed to update trainee")
                }
        } catch (e: Exception) {
            android.util.Log.e("TraineesFragment", "Error updating trainee: ${e.message}")
        }
    }

    private fun deleteTrainee(trainee: Trainee) {
        try {
            if (!isAdded || context == null) return
            
            android.util.Log.d("TraineesFragment", "Attempting to delete trainee: ${trainee.name} (ID: ${trainee.id})")
            
            val progressDialog = showLoadingDialog()
            
            firestore.collection("trainees").document(trainee.id)
                .delete()
                .addOnSuccessListener {
                    // Remove from schedule automatically
                    scheduleManager.removeTraineeFromSchedule(trainee) { scheduleSuccess ->
                        if (scheduleSuccess) {
                            android.util.Log.d("TraineesFragment", "Trainee removed from schedule successfully")
                        } else {
                            android.util.Log.w("TraineesFragment", "Failed to remove trainee from schedule")
                        }
                    }
                    
                    progressDialog.dismiss()
                    android.util.Log.d("TraineesFragment", "Trainee deleted successfully from Firestore: ${trainee.name}")
                    showToast("Trainee deleted successfully")
                    
                    // Automatically calculate salary for the coach when trainee is deleted
                    if (trainee.coachId.isNotEmpty()) {
                        fragmentScope.launch {
                            try {
                                val success = salaryManager.recalculateSalaryForCoach(trainee.coachId)
                                if (success) {
                                    android.util.Log.d("TraineesFragment", "Salary calculated automatically for coach after deletion: ${trainee.coachId}")
                                } else {
                                    android.util.Log.w("TraineesFragment", "Failed to calculate salary for coach after deletion: ${trainee.coachId}")
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("TraineesFragment", "Error calculating salary after deletion: ${e.message}")
                            }
                        }
                    }
                    
                    // Force refresh the list to ensure UI updates
                    loadTrainees()
                }
                .addOnFailureListener { e ->
                    progressDialog.dismiss()
                    android.util.Log.e("TraineesFragment", "Error deleting trainee: ${e.message}")
                    android.util.Log.e("TraineesFragment", "Error details: ${e.cause}")
                    showToast("Failed to delete trainee: ${e.message}")
                }
        } catch (e: Exception) {
            android.util.Log.e("TraineesFragment", "Exception in deleteTrainee: ${e.message}")
            android.util.Log.e("TraineesFragment", "Exception stack trace: ${e.stackTraceToString()}")
        }
    }

    private fun showRenewTraineeDialog(trainee: Trainee) {
        try {
            if (!isAdded || context == null) return
            
            val dialog = RenewTraineeDialog(requireContext(), trainee)
            dialog.setOnRenewClickListener { traineeToRenew, newSessions, newFee ->
                safeExecute {
                    renewTrainee(traineeToRenew, newSessions, newFee)
                }
            }
            dialog.show()
        } catch (e: Exception) {
            android.util.Log.e("TraineesFragment", "Error showing renew dialog: ${e.message}")
        }
    }

    private fun renewTrainee(trainee: Trainee, newSessions: Int, newFee: Int) {
        try {
            if (!isAdded || context == null) return
            
            val progressDialog = showLoadingDialog()
            
            val newPaymentAmount = newFee.toDouble() // Trainees are automatically paid
            
            val updates = hashMapOf<String, Any>(
                "totalSessions" to newSessions,
                "remainingSessions" to newSessions,
                "monthlyFee" to newFee,
                "paymentAmount" to newPaymentAmount,
                "isPaid" to true, // Trainees are automatically paid
                "lastRenewalDate" to Timestamp.now(),
                "attendanceSessions" to hashMapOf<String, Boolean>() // Reset attendance to empty
            )
            
            firestore.collection("trainees").document(trainee.id)
                .update(updates)
                .addOnSuccessListener {
                    progressDialog.dismiss()
                    showToast("Trainee renewed successfully")
                    
                    // Automatically create expense entry since trainees are automatically paid
                    val updatedTrainee = trainee.copy(
                        monthlyFee = newFee,
                        paymentAmount = newPaymentAmount,
                        isPaid = true
                    )
                    createTraineeExpenseEntry(updatedTrainee, trainee.id, "TRAINEE_RENEWED")
                }
                .addOnFailureListener { e ->
                    progressDialog.dismiss()
                    android.util.Log.e("TraineesFragment", "Error renewing trainee: ${e.message}")
                    showToast("Failed to renew trainee")
                }
        } catch (e: Exception) {
            android.util.Log.e("TraineesFragment", "Error renewing trainee: ${e.message}")
        }
    }

    private fun toggleFreezeStatus(trainee: Trainee) {
        try {
            if (!isAdded || context == null) return
            
            val progressDialog = showLoadingDialog()
            
            val newStatus = if (trainee.status == "frozen") "active" else "frozen"
            
            firestore.collection("trainees").document(trainee.id)
                .update("status", newStatus)
                .addOnSuccessListener {
                    // Handle schedule changes based on status
                    if (newStatus == "frozen") {
                        // Remove from schedule when frozen
                        scheduleManager.removeTraineeByStatus(trainee.id, trainee.branch) { scheduleSuccess ->
                            if (scheduleSuccess) {
                                android.util.Log.d("TraineesFragment", "Frozen trainee removed from schedule")
                            } else {
                                android.util.Log.w("TraineesFragment", "Failed to remove frozen trainee from schedule")
                            }
                        }
                    } else {
                        // Add back to schedule when unfrozen (if they have schedule info)
                        val updatedTrainee = trainee.copy(status = newStatus)
                        if (updatedTrainee.scheduleDays.isNotEmpty() && updatedTrainee.scheduleTime.isNotEmpty()) {
                            scheduleManager.addTraineeToSchedule(updatedTrainee) { scheduleSuccess ->
                                if (scheduleSuccess) {
                                    android.util.Log.d("TraineesFragment", "Unfrozen trainee added back to schedule")
                                } else {
                                    android.util.Log.w("TraineesFragment", "Failed to add unfrozen trainee to schedule")
                                }
                            }
                        }
                    }
                    
                    progressDialog.dismiss()
                    showToast("Trainee ${if (newStatus == "frozen") "frozen" else "unfrozen"}")
                }
                .addOnFailureListener { e ->
                    progressDialog.dismiss()
                    android.util.Log.e("TraineesFragment", "Error updating freeze status: ${e.message}")
                    showToast("Failed to update freeze status")
                }
        } catch (e: Exception) {
            android.util.Log.e("TraineesFragment", "Error updating freeze status: ${e.message}")
        }
    }

    private fun showTraineeDetailsDialog(trainee: Trainee) {
        try {
            if (!isAdded || context == null) return
            
            val details = StringBuilder()
            details.append("Name: ${trainee.name}\n")
            details.append("Age: ${trainee.age}\n")
            details.append("Phone: ${trainee.phoneNumber}\n")
            details.append("Branch: ${trainee.branch}\n")
            details.append("Coach: ${trainee.coachName}\n")
            details.append("Fee: $${trainee.monthlyFee}\n")
            details.append("Status: ${trainee.status}\n")
            details.append("Sessions: ${trainee.remainingSessions}/${trainee.totalSessions}\n")
            details.append("Payment: ${if (trainee.isPaid) "Paid" else "Unpaid"}")
            
            if (trainee.lastRenewalDate != null) {
                details.append("\nLast Renewal: ${dateFormat.format(trainee.lastRenewalDate.toDate())}")
            }
            
            android.app.AlertDialog.Builder(requireContext())
                .setTitle("Trainee Details")
                .setMessage(details.toString())
                .setPositiveButton("OK", null)
                .show()
        } catch (e: Exception) {
            android.util.Log.e("TraineesFragment", "Error showing trainee details: ${e.message}")
        }
    }

    private fun showLoadingDialog(): android.app.ProgressDialog {
        val dialog = android.app.ProgressDialog(requireContext())
        dialog.setMessage("Loading...")
        dialog.setCancelable(false)
        dialog.show()
        return dialog
    }

    private fun safeExecute(action: () -> Unit) {
        try {
            if (isAdded && context != null) {
                action()
            }
        } catch (e: Exception) {
            android.util.Log.e("TraineesFragment", "Error in safe execute: ${e.message}")
        }
    }

    private fun checkContactsPermissionAndPick() {
        try {
            if (!isAdded) return
            val permission = Manifest.permission.READ_CONTACTS
            if (androidx.core.content.ContextCompat.checkSelfPermission(requireContext(), permission) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                launchContactPicker()
            } else {
                requestContactsPermissionLauncher.launch(permission)
            }
        } catch (_: Exception) {}
    }

    private fun launchContactPicker() {
        try {
            val intent = Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
            contactPickerLauncher.launch(intent)
        } catch (_: Exception) {}
    }

    private fun showToast(message: String) {
        NavigationUtils.safeShowToast(context, message)
    }

    override fun onPause() {
        super.onPause()
        // Cancel any pending operations when fragment is paused
        fragmentScope.coroutineContext.cancelChildren()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        
        // Remove listeners to prevent memory leaks
        try {
            traineesListener?.remove()
            traineesListener = null
        } catch (e: Exception) {
            android.util.Log.e("TraineesFragment", "Error removing listeners: ${e.message}")
        }
        
        // Cancel any pending coroutines
        try {
            fragmentScope.coroutineContext.cancelChildren()
        } catch (e: Exception) {
            android.util.Log.e("TraineesFragment", "Error canceling coroutines: ${e.message}")
        }
        
        _binding = null
    }

    // Permission check methods
    private fun canAddTrainee(): Boolean {
        return currentUserRole == "head_coach" || currentUserRole == "admin"
    }

    private fun canEditTrainee(): Boolean {
        return currentUserRole == "head_coach" || currentUserRole == "admin"
    }

    private fun canDeleteTrainee(): Boolean {
        return currentUserRole == "head_coach" || currentUserRole == "admin"
    }

    private fun canTogglePayment(): Boolean {
        return currentUserRole == "head_coach" || currentUserRole == "admin"
    }

    private fun canRenewTrainee(): Boolean {
        return currentUserRole == "head_coach" || currentUserRole == "admin"
    }

    private fun canFreezeTrainee(): Boolean {
        return currentUserRole == "head_coach" || currentUserRole == "admin"
    }

    private fun canShowDetails(): Boolean {
        return currentUserRole == "head_coach" || currentUserRole == "admin"
    }

    private fun createTraineeExpenseEntry(trainee: Trainee, traineeId: String, operationType: String) {
        try {
            if (!isAdded || context == null) return
            
            // Get current month and year using consistent format
            val calendar = java.util.Calendar.getInstance()
            val monthFormat = java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale.getDefault())
            val currentMonth = monthFormat.format(calendar.time)
            val currentYear = calendar.get(java.util.Calendar.YEAR)
            
            // Get current user info
            val currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
            val currentUserId = currentUser?.uid ?: ""
            val currentUserName = currentUser?.displayName ?: "Unknown User"
            
            // Create expense entry for trainee payment
            val expense = com.example.double_dot_demo.models.Expense(
                id = "",
                title = "Trainee Payment - ${trainee.name}",
                amount = trainee.paymentAmount,
                type = "INCOME", // Trainee payments are income
                category = "TRAINEE_PAYMENT",
                description = "${operationType}: ${trainee.name} (${trainee.age} years) - ${trainee.coachName}",
                branch = trainee.branch,
                date = com.google.firebase.Timestamp.now(),
                month = currentMonth,
                year = currentYear,
                createdBy = currentUserId,
                createdByName = currentUserName,
                isAutoCalculated = true, // Auto-calculated from trainee payment
                relatedTraineeId = traineeId,
                createdAt = com.google.firebase.Timestamp.now(),
                updatedAt = com.google.firebase.Timestamp.now()
            )
            
            // Add to expenses collection
            firestore.collection("expenses")
                .add(expense)
                .addOnSuccessListener { documentReference ->
                    android.util.Log.d("TraineesFragment", "Auto-created expense entry for trainee ${trainee.name}: $${String.format("%.2f", trainee.paymentAmount)}")
                }
                .addOnFailureListener { e ->
                    android.util.Log.e("TraineesFragment", "Error creating expense entry for trainee ${trainee.name}: ${e.message}")
                }
                
        } catch (e: Exception) {
            android.util.Log.e("TraineesFragment", "Error creating trainee expense entry: ${e.message}")
        }
    }

    companion object {
        fun newInstance(): TraineesFragment {
            return TraineesFragment()
        }
    }
} 