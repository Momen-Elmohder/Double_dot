package com.example.double_dot_demo.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.double_dot_demo.R
import com.example.double_dot_demo.adapters.TraineeAdapter
import com.example.double_dot_demo.models.Trainee
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class CompletedTraineesFragment : Fragment() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: TraineeAdapter
    private lateinit var searchInput: TextInputLayout
    private lateinit var sortBySpinner: AutoCompleteTextView
    private lateinit var branchSpinner: AutoCompleteTextView
    private lateinit var statusSpinner: AutoCompleteTextView


    private val trainees = mutableListOf<Trainee>()

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private var traineesListener: ListenerRegistration? = null
    private var coachesListener: ListenerRegistration? = null

    private var currentUserRole: String? = null
    private var selectedBranch: String = ""
    private var selectedStatus: String = ""


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_completed_trainees, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        try {
            initializeViews(view)
            setupRecyclerView()
            setupSearchAndSort()
            loadCurrentUserRole()
        } catch (e: Exception) {
            android.util.Log.e("CompletedTraineesFragment", "Error in onViewCreated: ${e.message}")
        }
    }

    private fun initializeViews(view: View) {
        try {
            recyclerView = view.findViewById(R.id.recyclerView)
            searchInput = view.findViewById(R.id.searchInput)
            sortBySpinner = view.findViewById(R.id.sortBySpinner)
            branchSpinner = view.findViewById(R.id.branchSpinner)
            statusSpinner = view.findViewById(R.id.statusSpinner)

        } catch (e: Exception) {
            android.util.Log.e("CompletedTraineesFragment", "Error initializing views: ${e.message}")
        }
    }

    private fun setupRecyclerView() {
        try {
            adapter = TraineeAdapter(
                onEditClick = { trainee ->
                    safeExecute {
                        showEditTraineeDialog(trainee)
                    }
                },
                onDeleteClick = { trainee ->
                    safeExecute {
                        deleteTrainee(trainee)
                    }
                },
                onRenewClick = { trainee ->
                    safeExecute {
                        showRenewTraineeDialog(trainee)
                    }
                },
                onFreezeClick = { trainee ->
                    safeExecute {
                        toggleFreezeStatus(trainee)
                    }
                },
                onShowDetailsClick = { trainee ->
                    safeExecute {
                        showTraineeDetailsDialog(trainee)
                    }
                },
                isCoachView = false // Full view for admin/head coach
            )

            recyclerView.layoutManager = LinearLayoutManager(context)
            recyclerView.adapter = adapter

        } catch (e: Exception) {
            android.util.Log.e("CompletedTraineesFragment", "Error setting up recycler view: ${e.message}")
        }
    }

    private fun setupSearchAndSort() {
        try {
            // Sort options - added separate status categories
            val sortOptions = arrayOf("Age", "Status", "Completed", "Active", "Frozen", "Complete")
            val sortAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, sortOptions)
            sortBySpinner.setAdapter(sortAdapter)
            sortBySpinner.setText(sortOptions[0], false)

            // Branch options
            val branchOptions = arrayOf("All Branches", "المدينة الرياضية", "نادي اليخت", "نادي التوكيلات")
            val branchAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, branchOptions)
            branchSpinner.setAdapter(branchAdapter)
            branchSpinner.setText(branchOptions[0], false)

            // Status options - only show actual program statuses
            val statusOptions = arrayOf("All Status", "academy", "team", "preparation")
            val statusAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, statusOptions)
            statusSpinner.setAdapter(statusAdapter)
            statusSpinner.setText(statusOptions[0], false)

            // Setup listeners
            sortBySpinner.setOnItemClickListener { _, _, position, _ ->
                safeExecute {
                    filterAndSortTrainees()
                }
            }

            branchSpinner.setOnItemClickListener { _, _, position, _ ->
                selectedBranch = if (position == 0) "" else branchOptions[position]
                safeExecute {
                    filterAndSortTrainees()
                }
            }

            statusSpinner.setOnItemClickListener { _, _, position, _ ->
                selectedStatus = if (position == 0) "" else statusOptions[position]
                safeExecute {
                    filterAndSortTrainees()
                }
            }



            // Setup search listener
            searchInput.editText?.addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: android.text.Editable?) {
                    safeExecute {
                        filterAndSortTrainees()
                    }
                }
            })

            // Load coaches


        } catch (e: Exception) {
            android.util.Log.e("CompletedTraineesFragment", "Error setting up search and sort: ${e.message}")
        }
    }



    private fun loadCurrentUserRole() {
        try {
            val currentUser = auth.currentUser
            if (currentUser != null) {
                db.collection("users").document(currentUser.uid)
                    .get()
                    .addOnSuccessListener { document ->
                        if (document != null && document.exists()) {
                            currentUserRole = document.getString("role")
                            android.util.Log.d("CompletedTraineesFragment", "User role loaded: $currentUserRole")
                            // Reload trainees after role is loaded
                            loadCompletedTrainees()
                        }
                    }
                    .addOnFailureListener { e ->
                        android.util.Log.e("CompletedTraineesFragment", "Error loading user role: ${e.message}")
                    }
            }
        } catch (e: Exception) {
            android.util.Log.e("CompletedTraineesFragment", "Error loading current user role: ${e.message}")
        }
    }

    private fun loadCompletedTrainees() {
        try {
            val currentUser = auth.currentUser
            if (currentUser == null) return

            android.util.Log.d("CompletedTraineesFragment", "Loading completed trainees. Current role: $currentUserRole")

            // Load all trainees and filter for completed ones
            val query = db.collection("trainees").orderBy("name")

            traineesListener = query.addSnapshotListener { snapshot, e ->
                if (e != null) {
                    android.util.Log.e("CompletedTraineesFragment", "Error loading trainees: ${e.message}")
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val newTrainees = mutableListOf<Trainee>()
                    android.util.Log.d("CompletedTraineesFragment", "Snapshot size: ${snapshot.size()}")

                    for (document in snapshot.documents) {
                        try {
                            val trainee = document.toObject(Trainee::class.java)
                            if (trainee != null) {
                                trainee.id = document.id

                                // Only show completed trainees
                                val totalAttended = trainee.attendanceSessions.size
                                if (totalAttended >= trainee.totalSessions && trainee.totalSessions > 0) {
                                    newTrainees.add(trainee)
                                    android.util.Log.d("CompletedTraineesFragment", "Added completed trainee: ${trainee.name}")
                                }
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("CompletedTraineesFragment", "Error parsing trainee: ${e.message}")
                        }
                    }

                    android.util.Log.d("CompletedTraineesFragment", "Total completed trainees: ${newTrainees.size}")

                    // Only update if the data has actually changed significantly
                    if (newTrainees.size != trainees.size ||
                        !newTrainees.all { newTrainee ->
                            trainees.any { existingTrainee ->
                                existingTrainee.id == newTrainee.id &&
                                        existingTrainee.name == newTrainee.name &&
                                        existingTrainee.status == newTrainee.status
                            }
                        }) {
                        trainees.clear()
                        trainees.addAll(newTrainees)
                        filterAndSortTrainees()
                    }
                }
            }

        } catch (e: Exception) {
            android.util.Log.e("CompletedTraineesFragment", "Error loading completed trainees: ${e.message}")
        }
    }

    private fun filterAndSortTrainees() {
        try {
            val searchQuery = searchInput.editText?.text.toString().lowercase()
            val sortBy = sortBySpinner.text.toString()

            val filteredList = trainees.filter { trainee ->
                // Search filter
                val matchesSearch = searchQuery.isEmpty() ||
                        trainee.name?.lowercase()?.contains(searchQuery) == true ||
                        trainee.branch?.lowercase()?.contains(searchQuery) == true ||
                        trainee.coachName?.lowercase()?.contains(searchQuery) == true ||
                        trainee.status?.lowercase()?.contains(searchQuery) == true

                // Branch filter
                val matchesBranch = selectedBranch.isEmpty() || trainee.branch == selectedBranch

                // Status filter
                val matchesStatus = selectedStatus.isEmpty() || trainee.status == selectedStatus

                matchesSearch && matchesBranch && matchesStatus
            }

            val sortedList = when (sortBy) {
                "Age" -> {
                    android.util.Log.d("CompletedTraineesFragment", "Sorting by Age")
                    filteredList.sortedBy { it.age }
                }
                "Status" -> {
                    android.util.Log.d("CompletedTraineesFragment", "Sorting by Status")
                    filteredList.sortedBy { it.status }
                }
                "Completed" -> {
                    android.util.Log.d("CompletedTraineesFragment", "Sorting by Completed sessions")
                    filteredList.sortedBy { trainee ->
                        val completedSessions = trainee.attendanceSessions.values.count { isPresent -> isPresent }
                        android.util.Log.d("CompletedTraineesFragment", "Trainee ${trainee.name}: $completedSessions completed sessions")
                        completedSessions
                    }
                }
                "Active" -> {
                    android.util.Log.d("CompletedTraineesFragment", "Sorting by Active status")
                    filteredList.sortedBy { trainee ->
                        val isActive = trainee.status == "active" || trainee.status == "academy" || trainee.status == "team" || trainee.status == "preparation"
                        !isActive // Put active trainees first
                    }
                }

                "Frozen" -> {
                    android.util.Log.d("CompletedTraineesFragment", "Sorting by Frozen status")
                    filteredList.sortedBy { trainee ->
                        trainee.status == "frozen"
                    }
                }
                "Complete" -> {
                    android.util.Log.d("CompletedTraineesFragment", "Sorting by Complete status")
                    filteredList.sortedBy { trainee ->
                        trainee.status == "completed"
                    }
                }
                else -> {
                    android.util.Log.d("CompletedTraineesFragment", "Default sorting by Age")
                    filteredList.sortedBy { it.age }
                }
            }

            // Log the first few trainees to verify sorting
            if (sortedList.isNotEmpty()) {
                android.util.Log.d("CompletedTraineesFragment", "First 3 trainees after sorting:")
                sortedList.take(3).forEachIndexed { index, trainee ->
                    when (sortBy) {
                        "Age" -> android.util.Log.d("CompletedTraineesFragment", "${index + 1}. ${trainee.name} - Age: ${trainee.age}")
                        "Status" -> android.util.Log.d("CompletedTraineesFragment", "${index + 1}. ${trainee.name} - Status: ${trainee.status}")
                        "Completed" -> {
                            val completedSessions = trainee.attendanceSessions.values.count { isPresent -> isPresent }
                            android.util.Log.d("CompletedTraineesFragment", "${index + 1}. ${trainee.name} - Completed: $completedSessions")
                        }
                    }
                }
            }

            // Update the adapter with the new sorted list
            // TraineeAdapter extends ListAdapter, so we must use submitList
            adapter.submitList(sortedList)

        } catch (e: Exception) {
            android.util.Log.e("CompletedTraineesFragment", "Error filtering and sorting: ${e.message}")
        }
    }

    // Placeholder methods for trainee operations
    private fun showEditTraineeDialog(trainee: Trainee) {
        // Implementation for editing trainee
        showToast("Edit trainee: ${trainee.name}")
    }

    private fun deleteTrainee(trainee: Trainee) {
        // Implementation for deleting trainee
        showToast("Delete trainee: ${trainee.name}")
    }

    private fun showRenewTraineeDialog(trainee: Trainee) {
        // Implementation for renewing trainee
        showToast("Renew trainee: ${trainee.name}")
    }

    private fun toggleFreezeStatus(trainee: Trainee) {
        // Implementation for freezing/unfreezing trainee
        showToast("Toggle freeze for: ${trainee.name}")
    }

    private fun showTraineeDetailsDialog(trainee: Trainee) {
        // Implementation for showing trainee details
        showToast("Show details for: ${trainee.name}")
    }

    private fun safeExecute(action: () -> Unit) {
        try {
            if (isAdded && context != null) {
                action()
            }
        } catch (e: Exception) {
            android.util.Log.e("CompletedTraineesFragment", "Error in safe execute: ${e.message}")
        }
    }

    private fun showToast(message: String) {
        try {
            if (isAdded && context != null) {
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            android.util.Log.e("CompletedTraineesFragment", "Error showing toast: ${e.message}")
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        traineesListener?.remove()
        coachesListener?.remove()
    }
} 