package com.example.double_dot_demo.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.double_dot_demo.R
import com.example.double_dot_demo.adapters.AttendanceAdapter
import com.example.double_dot_demo.dialogs.AddTraineeDialog
import com.example.double_dot_demo.models.Trainee
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.example.double_dot_demo.utils.NavigationUtils
import com.example.double_dot_demo.utils.ButtonUtils

class AttendanceFragment : Fragment() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: AttendanceAdapter
    private lateinit var searchInput: TextInputLayout
    private lateinit var sortBySpinner: AutoCompleteTextView
    private lateinit var branchSpinner: AutoCompleteTextView
    private lateinit var statusSpinner: AutoCompleteTextView


    
    private val trainees = mutableListOf<Trainee>()
    private val filteredTrainees = mutableListOf<Trainee>()

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private var traineesListener: ListenerRegistration? = null

    
    private var currentUserRole: String? = null
    private var selectedBranch: String = ""
    private var selectedStatus: String = ""


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_attendance, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        try {
            initializeViews(view)
            setupRecyclerView()
            setupSearchAndSort()
            loadCurrentUserRole()
            // loadTrainees() will be called after role is loaded
        } catch (e: Exception) {
            android.util.Log.e("AttendanceFragment", "Error in onViewCreated: ${e.message}")
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
            android.util.Log.e("AttendanceFragment", "Error initializing views: ${e.message}")
        }
    }

    private fun updateStats() {
        try {
            if (!isAdded || view == null) return
            
            val totalCount = view?.findViewById<android.widget.TextView>(R.id.tvTotalCount)
            val activeCount = view?.findViewById<android.widget.TextView>(R.id.tvActiveCount)
            val frozenCount = view?.findViewById<android.widget.TextView>(R.id.tvFrozenCount)
            val completedCount = view?.findViewById<android.widget.TextView>(R.id.tvCompletedCount)
            
                         val total = filteredTrainees.size
             // Count active trainees (academy, team, preparation, active - but not frozen, completed, or inactive)
             val active = filteredTrainees.count { trainee ->
                 trainee.status != "frozen" && 
                 trainee.status != "completed" && 
                 trainee.status != "inactive" &&
                 (trainee.status == "academy" || trainee.status == "team" || trainee.status == "preparation" || trainee.status == "active")
             }
            val frozen = filteredTrainees.count { it.status == "frozen" }
            val completed = filteredTrainees.count { trainee ->
                // Count trainees who have completed all their sessions (total attended = total sessions)
                val totalAttended = trainee.attendanceSessions.size
                totalAttended >= trainee.totalSessions && trainee.totalSessions > 0
            }
            
            totalCount?.text = total.toString()
            activeCount?.text = active.toString()
            frozenCount?.text = frozen.toString()
            completedCount?.text = completed.toString()
            
        } catch (e: Exception) {
            android.util.Log.e("AttendanceFragment", "Error updating stats: ${e.message}")
        }
    }

    private fun setupRecyclerView() {
        try {
            adapter = AttendanceAdapter(
                trainees = filteredTrainees,
                onAttendanceUpdate = { trainee, sessionId, isPresent ->
                    safeExecute {
                        updateAttendance(trainee, sessionId, isPresent)
                    }
                },
                onEditTrainee = { trainee ->
                    safeExecute {
                        showEditTraineeDialog(trainee)
                    }
                }
            )
            
            recyclerView.layoutManager = LinearLayoutManager(context)
            recyclerView.adapter = adapter
            
            // Setup swipe functionality
            val itemTouchHelper = ItemTouchHelper(adapter.AttendanceSwipeCallback())
            itemTouchHelper.attachToRecyclerView(recyclerView)
            
        } catch (e: Exception) {
            android.util.Log.e("AttendanceFragment", "Error setting up recycler view: ${e.message}")
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
            

            
        } catch (e: Exception) {
            android.util.Log.e("AttendanceFragment", "Error setting up search and sort: ${e.message}")
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
                            android.util.Log.d("AttendanceFragment", "User role loaded: $currentUserRole")
                            // Reload trainees after role is loaded
                            loadTrainees()
                        }
                    }
                    .addOnFailureListener { e ->
                        android.util.Log.e("AttendanceFragment", "Error loading user role: ${e.message}")
                    }
            }
        } catch (e: Exception) {
            android.util.Log.e("AttendanceFragment", "Error loading current user role: ${e.message}")
        }
    }

    private fun loadTrainees() {
        try {
            val currentUser = auth.currentUser
            if (currentUser == null) return
            
            android.util.Log.d("AttendanceFragment", "Loading trainees. Current role: $currentUserRole, User ID: ${currentUser.uid}")
            
            // Load all trainees and filter based on role
            val query = db.collection("trainees").orderBy("name")
            
            traineesListener = query.addSnapshotListener { snapshot, e ->
                if (e != null) {
                    android.util.Log.e("AttendanceFragment", "Error loading trainees: ${e.message}")
                    return@addSnapshotListener
                }
                
                if (snapshot != null) {
                    val newTrainees = mutableListOf<Trainee>()
                    android.util.Log.d("AttendanceFragment", "Snapshot size: ${snapshot.size()}")
                    
                    for (document in snapshot.documents) {
                        try {
                            val trainee = document.toObject(Trainee::class.java)
                            if (trainee != null) {
                                trainee.id = document.id
                                android.util.Log.d("AttendanceFragment", "Trainee: ${trainee.name}, Coach ID: ${trainee.coachId}, Status: ${trainee.status}")
                                
                                // For now, show all trainees to debug the issue
                                newTrainees.add(trainee)
                                android.util.Log.d("AttendanceFragment", "Added trainee: ${trainee.name}, Coach ID: ${trainee.coachId}, Status: ${trainee.status}, Current User: ${currentUser.uid}")
                                
                                // TODO: Re-enable coach filtering once we confirm the coach ID matching works
                                // if (currentUserRole == "coach") {
                                //     if (trainee.coachId == currentUser.uid) {
                                //         newTrainees.add(trainee)
                                //         android.util.Log.d("AttendanceFragment", "Added trainee for coach: ${trainee.name}")
                                //     }
                                // } else {
                                //     // Admin and head coach can see all trainees
                                //     newTrainees.add(trainee)
                                //     android.util.Log.d("AttendanceFragment", "Added trainee for admin/head coach: ${trainee.name}")
                                // }
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("AttendanceFragment", "Error parsing trainee: ${e.message}")
                        }
                    }
                    
                    android.util.Log.d("AttendanceFragment", "Total trainees after filtering: ${newTrainees.size}")
                    
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
                        updateStats()
                    }
                }
            }
            
        } catch (e: Exception) {
            android.util.Log.e("AttendanceFragment", "Error loading trainees: ${e.message}")
        }
    }

        private fun filterAndSortTrainees() {
        try {
            val searchQuery = searchInput.editText?.text.toString().lowercase()
            val sortBy = sortBySpinner.text.toString()
            
            android.util.Log.d("AttendanceFragment", "Starting filter and sort - Search: '$searchQuery', Sort: '$sortBy'")
            android.util.Log.d("AttendanceFragment", "Filters - Branch: '$selectedBranch', Status: '$selectedStatus'")
            
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
                
                val matches = matchesSearch && matchesBranch && matchesStatus
                
                if (!matches) {
                    android.util.Log.d("AttendanceFragment", "Trainee ${trainee.name} filtered out - Search: $matchesSearch, Branch: $matchesBranch, Status: $matchesStatus")
                }
                
                matches
            }
            
            android.util.Log.d("AttendanceFragment", "After filtering: ${filteredList.size} trainees")
            
                         val sortedList = when (sortBy) {
                 "Age" -> {
                     android.util.Log.d("AttendanceFragment", "Sorting by Age")
                     filteredList.sortedBy { it.age }
                 }
                 "Status" -> {
                     android.util.Log.d("AttendanceFragment", "Sorting by Status")
                     filteredList.sortedBy { it.status }
                 }
                 "Completed" -> {
                     android.util.Log.d("AttendanceFragment", "Sorting by Completed sessions")
                     filteredList.sortedBy { trainee ->
                         val completedSessions = trainee.attendanceSessions.values.count { isPresent -> isPresent }
                         android.util.Log.d("AttendanceFragment", "Trainee ${trainee.name}: $completedSessions completed sessions")
                         completedSessions
                     }
                 }
                 "Active" -> {
                     android.util.Log.d("AttendanceFragment", "Sorting by Active status")
                     filteredList.sortedBy { trainee ->
                         val isActive = trainee.status == "active" || trainee.status == "academy" || trainee.status == "team" || trainee.status == "preparation"
                         !isActive // Put active trainees first
                     }
                 }

                 "Frozen" -> {
                     android.util.Log.d("AttendanceFragment", "Sorting by Frozen status")
                     filteredList.sortedBy { trainee ->
                         trainee.status == "frozen"
                     }
                 }
                 "Complete" -> {
                     android.util.Log.d("AttendanceFragment", "Sorting by Complete status")
                     filteredList.sortedBy { trainee ->
                         trainee.status == "completed"
                     }
                 }
                 else -> {
                     android.util.Log.d("AttendanceFragment", "Default sorting by Age")
                     filteredList.sortedBy { it.age }
                 }
             }
            
            android.util.Log.d("AttendanceFragment", "After sorting: ${sortedList.size} trainees")
            
            // Update the filtered list instead of the main trainees list
            filteredTrainees.clear()
            filteredTrainees.addAll(sortedList)
            
            android.util.Log.d("AttendanceFragment", "Filtering complete: ${filteredTrainees.size} trainees shown out of ${trainees.size} total")
            android.util.Log.d("AttendanceFragment", "Search query: '$searchQuery', Branch: '$selectedBranch', Status: '$selectedStatus', Sort: '$sortBy'")
            
            // Debug: Show what trainees are being filtered
            if (filteredTrainees.isEmpty() && trainees.isNotEmpty()) {
                android.util.Log.w("AttendanceFragment", "WARNING: No trainees match current filters!")
                android.util.Log.d("AttendanceFragment", "Available statuses in data: ${trainees.map { it.status }.distinct()}")
                android.util.Log.d("AttendanceFragment", "Available branches in data: ${trainees.map { it.branch }.distinct()}")
                
            }
            
            // Log the first few trainees to verify sorting
            if (filteredTrainees.isNotEmpty()) {
                android.util.Log.d("AttendanceFragment", "First 3 trainees after sorting:")
                filteredTrainees.take(3).forEachIndexed { index, trainee ->
                                         when (sortBy) {
                         "Age" -> android.util.Log.d("AttendanceFragment", "${index + 1}. ${trainee.name} - Age: ${trainee.age}")
                         "Status" -> android.util.Log.d("AttendanceFragment", "${index + 1}. ${trainee.name} - Status: ${trainee.status}")
                         "Completed" -> {
                             val completedSessions = trainee.attendanceSessions.values.count { isPresent -> isPresent }
                             android.util.Log.d("AttendanceFragment", "${index + 1}. ${trainee.name} - Completed: $completedSessions")
                         }
                         "Active" -> android.util.Log.d("AttendanceFragment", "${index + 1}. ${trainee.name} - Status: ${trainee.status}")
                         "Frozen" -> android.util.Log.d("AttendanceFragment", "${index + 1}. ${trainee.name} - Status: ${trainee.status}")
                         "Complete" -> android.util.Log.d("AttendanceFragment", "${index + 1}. ${trainee.name} - Status: ${trainee.status}")
                     }
                }
            }
            
            adapter.notifyDataSetChanged()
            updateStats()
            
        } catch (e: Exception) {
            android.util.Log.e("AttendanceFragment", "Error filtering and sorting: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun updateAttendance(trainee: Trainee, sessionId: String, isPresent: Boolean) {
        try {
            if (!isAdded || context == null) return
            
            android.util.Log.d("AttendanceFragment", "updateAttendance called - Session: $sessionId, IsPresent: $isPresent")
            android.util.Log.d("AttendanceFragment", "Original trainee sessions: ${trainee.attendanceSessions}")
            
            // Find the trainee in both lists and update them
            val traineeIndex = trainees.indexOfFirst { it.id == trainee.id }
            val filteredIndex = filteredTrainees.indexOfFirst { it.id == trainee.id }
            
            // Check if this is an undo operation (session exists and we're trying to remove it)
            val isUndoOperation = trainee.attendanceSessions.containsKey(sessionId) && !isPresent
            
            android.util.Log.d("AttendanceFragment", "Is undo operation: $isUndoOperation")
            
            if (traineeIndex != -1) {
                val localTrainee = trainees[traineeIndex]
                val updatedAttendanceSessions = localTrainee.attendanceSessions.toMutableMap()
                
                if (isUndoOperation) {
                    // This is an undo - remove the session
                    updatedAttendanceSessions.remove(sessionId)
                    android.util.Log.d("AttendanceFragment", "Undo operation - removing session: $sessionId")
                } else {
                    // This is a normal update - set the session
                    updatedAttendanceSessions[sessionId] = isPresent
                    android.util.Log.d("AttendanceFragment", "Normal update - setting session: $sessionId to $isPresent")
                }
                
                // Update the local trainee object
                localTrainee.attendanceSessions = updatedAttendanceSessions
                android.util.Log.d("AttendanceFragment", "Updated local trainee sessions: ${localTrainee.attendanceSessions}")
            }
            
            if (filteredIndex != -1) {
                val filteredTrainee = filteredTrainees[filteredIndex]
                val updatedAttendanceSessions = filteredTrainee.attendanceSessions.toMutableMap()
                
                if (isUndoOperation) {
                    // This is an undo - remove the session
                    updatedAttendanceSessions.remove(sessionId)
                } else {
                    // This is a normal update - set the session
                    updatedAttendanceSessions[sessionId] = isPresent
                }
                
                // Update the filtered trainee object
                filteredTrainee.attendanceSessions = updatedAttendanceSessions
                
                // Update the UI immediately
                adapter.notifyItemChanged(filteredIndex)
                android.util.Log.d("AttendanceFragment", "Updated UI for trainee: ${filteredTrainee.name}, Sessions: ${filteredTrainee.attendanceSessions}")
            }
            
            // Then update Firestore in background
            val firestoreSessions = if (isUndoOperation) {
                // For undo, remove the session from the original trainee
                val updatedSessions = trainee.attendanceSessions.toMutableMap()
                updatedSessions.remove(sessionId)
                updatedSessions
            } else {
                // For normal update, add the new session to the original trainee
                trainee.attendanceSessions.toMutableMap().apply {
                    put(sessionId, isPresent)
                }
            }
            
            android.util.Log.d("AttendanceFragment", "Firestore sessions to update: $firestoreSessions")
            // Prepare update map and mark as completed if all sessions done
            val totalCompleted = firestoreSessions.size
            val shouldComplete = trainee.totalSessions > 0 && totalCompleted >= trainee.totalSessions
            val remaining = (trainee.totalSessions - totalCompleted).coerceAtLeast(0)

            val updateData = hashMapOf<String, Any>(
                "attendanceSessions" to firestoreSessions
            ).apply {
                put("remainingSessions", remaining)
                if (shouldComplete) put("status", "completed")
            }

            if (shouldComplete) {
                android.util.Log.d("AttendanceFragment", "Trainee ${trainee.name} completed all sessions. Marking status as 'completed'.")
            }

            db.collection("trainees").document(trainee.id)
                .update(updateData as Map<String, Any>)
                .addOnSuccessListener {
                    android.util.Log.d("AttendanceFragment", "Firestore updated successfully")
                }
                .addOnFailureListener { e ->
                    android.util.Log.e("AttendanceFragment", "Error updating attendance: ${e.message}")
                    showToast("Failed to update attendance")
                    // The Firestore listener will handle reverting the change
                }
                
        } catch (e: Exception) {
            android.util.Log.e("AttendanceFragment", "Error updating attendance: ${e.message}")
        }
    }



    private fun showEditTraineeDialog(trainee: Trainee) {
        try {
            if (!isAdded || context == null) return
            
            // Show edit trainee dialog
            
            val dialog = AddTraineeDialog(requireContext(), trainee)
            dialog.setOnSaveClickListener { updatedTrainee: Trainee ->
                safeExecute {
                    updateTrainee(updatedTrainee)
                }
            }
            dialog.show()
        } catch (e: Exception) {
            android.util.Log.e("AttendanceFragment", "Error showing edit trainee dialog: ${e.message}")
        }
    }



    private fun updateTrainee(trainee: Trainee) {
        try {
            if (!isAdded || context == null) return
            
            db.collection("trainees").document(trainee.id)
                .set(trainee)
                .addOnSuccessListener {
                    showToast("Trainee updated successfully")
                }
                .addOnFailureListener { e ->
                    android.util.Log.e("AttendanceFragment", "Error updating trainee: ${e.message}")
                    showToast("Failed to update trainee")
                }
                
        } catch (e: Exception) {
            android.util.Log.e("AttendanceFragment", "Error updating trainee: ${e.message}")
        }
    }

    private fun safeExecute(action: () -> Unit) {
        try {
            if (isAdded && context != null) {
                action()
            }
        } catch (e: Exception) {
            android.util.Log.e("AttendanceFragment", "Error in safe execute: ${e.message}")
        }
    }

    private fun showToast(message: String) {
        NavigationUtils.safeShowToast(context, message)
    }

    override fun onPause() {
        super.onPause()
        // Cancel any pending operations when fragment is paused
    }

    override fun onDestroyView() {
        super.onDestroyView()
        try {
            traineesListener?.remove()
        } catch (e: Exception) {
            android.util.Log.e("AttendanceFragment", "Error removing listeners: ${e.message}")
        }
    }
} 