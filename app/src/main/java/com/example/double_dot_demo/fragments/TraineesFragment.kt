package com.example.double_dot_demo.fragments

import android.os.Bundle
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

    private val trainees = mutableListOf<Trainee>()
    private val filteredTrainees = mutableListOf<Trainee>()

    private var currentUserRole: String = ""
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    
    // Search and sort variables
    private var searchQuery: String = ""
    private var sortBy: String = "age"
    private var selectedBranch: String = ""
    private var selectedStatus: String = ""

    // Coroutine scope for background operations
    private val fragmentScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
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
            
            traineeAdapter = TraineeAdapter(
                isCoachView = false, // Always show full view with buttons
                onEditClick = { trainee -> showEditTraineeDialog(trainee) },
                onDeleteClick = { trainee -> deleteTrainee(trainee) },
                onRenewClick = { trainee -> showRenewTraineeDialog(trainee) },
                onFreezeClick = { trainee -> toggleFreezeStatus(trainee) },
                onShowDetailsClick = { trainee -> showTraineeDetailsDialog(trainee) }
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
        
        binding.btnAddTrainee.setOnClickListener {
            safeExecute {
                if (canAddTrainee()) {
                    showAddTraineeDialog()
                } else {
                    showToast("You don't have permission to add trainees")
                }
            }
        }
        
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
                            if (isAdded && traineeAdapter != null) {
                                // Head coaches and admins get full view, coaches get limited view
                                val isCoachView = currentUserRole == "coach"
                                // Note: updateCoachView function was removed, buttons are always visible now
                                traineeAdapter.notifyDataSetChanged()
                                android.util.Log.d("TraineesFragment", "Adapter updated with isCoachView: $isCoachView")
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
            val query = if (currentUserRole == "coach") {
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
        if (!isAdded) return
        
        val total = filteredTrainees.size
        val active = filteredTrainees.count { it.status == "active" }
        
        binding.tvTotalCount.text = total.toString()
        binding.tvActiveCount.text = active.toString()
    }

    private fun showAddTraineeDialog() {
        try {
            if (!isAdded || context == null) return
            
            val dialog = AddTraineeDialog(requireContext())
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
            
            firestore.collection("trainees")
                .add(trainee)
                .addOnSuccessListener { documentReference ->
                    progressDialog.dismiss()
                    showToast("Trainee added successfully")
                    
                    // Automatically create expense entry since trainees are automatically paid
                    createTraineeExpenseEntry(trainee, documentReference.id, "TRAINEE_ADDED")
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
            
            firestore.collection("trainees").document(trainee.id)
                .set(trainee)
                .addOnSuccessListener {
                    progressDialog.dismiss()
                    showToast("Trainee updated successfully")
                    
                    // Automatically create expense entry since trainees are automatically paid
                    createTraineeExpenseEntry(trainee, trainee.id, "TRAINEE_UPDATED")
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
                    progressDialog.dismiss()
                    android.util.Log.d("TraineesFragment", "Trainee deleted successfully from Firestore: ${trainee.name}")
                    showToast("Trainee deleted successfully")
                    
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

    private fun showToast(message: String) {
        try {
            if (isAdded && context != null) {
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            android.util.Log.e("TraineesFragment", "Error showing toast: ${e.message}")
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        
        // Remove listeners to prevent memory leaks
                 traineesListener?.remove()
        traineesListener = null
        
        // Cancel any pending coroutines
        fragmentScope.coroutineContext.cancelChildren()
        
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
            
            // Get current month and year
            val calendar = java.util.Calendar.getInstance()
            val currentMonth = String.format("%04d-%02d", calendar.get(java.util.Calendar.YEAR), calendar.get(java.util.Calendar.MONTH) + 1)
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