package com.example.double_dot_demo.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.double_dot_demo.R
import com.example.double_dot_demo.adapters.CoachAttendanceAdapter
import com.example.double_dot_demo.models.Employee
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class CoachAttendanceFragment : Fragment() {

    private var recyclerView: RecyclerView? = null
    private var tvNoCoaches: TextView? = null
    
    private var currentAdapter: CoachAttendanceAdapter? = null
    private val coaches = mutableListOf<Employee>()
    
    private var coachesListener: com.google.firebase.firestore.ListenerRegistration? = null
    
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return try {
            inflater.inflate(R.layout.fragment_coach_attendance, container, false)
        } catch (e: Exception) {
            android.util.Log.e("CoachAttendanceFragment", "Error inflating layout: ${e.message}")
            TextView(requireContext()).apply {
                text = "Error loading coach attendance page"
                gravity = android.view.Gravity.CENTER
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        try {
            recyclerView = view.findViewById(R.id.recyclerView)
            tvNoCoaches = view.findViewById(R.id.tvNoCoaches)
            
            if (recyclerView == null || tvNoCoaches == null) {
                android.util.Log.e("CoachAttendanceFragment", "One or more views not found")
                android.widget.Toast.makeText(context, "Error: Some UI elements not found", android.widget.Toast.LENGTH_SHORT).show()
                return
            }
            
            setupRecyclerView()
            loadCoaches()
            
            android.util.Log.d("CoachAttendanceFragment", "Coach attendance fragment setup completed successfully")
        } catch (e: Exception) {
            android.util.Log.e("CoachAttendanceFragment", "Error in onViewCreated: ${e.message}")
            android.widget.Toast.makeText(context, "Error setting up coach attendance page: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupRecyclerView() {
        try {
            recyclerView?.let { rv ->
                rv.layoutManager = LinearLayoutManager(context)
                
                currentAdapter = CoachAttendanceAdapter(
                    coaches = coaches,
                    onAttendanceUpdated = { coach, isPresent ->
                        updateCoachAttendance(coach, isPresent)
                    },
                    onUndoAttendance = { coach ->
                        undoCoachAttendance(coach)
                    }
                )
                
                rv.adapter = currentAdapter
                
                // Setup swipe callback
                currentAdapter?.setupSwipeCallback(rv)
                
                android.util.Log.d("CoachAttendanceFragment", "RecyclerView setup completed")
            } ?: run {
                android.util.Log.e("CoachAttendanceFragment", "RecyclerView is null")
            }
        } catch (e: Exception) {
            android.util.Log.e("CoachAttendanceFragment", "Error setting up RecyclerView: ${e.message}")
        }
    }

    private fun loadCoaches() {
        try {
            android.util.Log.d("CoachAttendanceFragment", "Starting to load coaches...")
            coachesListener?.remove()
            
            coachesListener = db.collection("employees")
                .whereEqualTo("role", "coach")
                .orderBy("name", Query.Direction.ASCENDING)
                .addSnapshotListener { snapshot, e ->
                    if (e != null) {
                        android.util.Log.e("CoachAttendanceFragment", "Error loading coaches: ${e.message}")
                        return@addSnapshotListener
                    }

                    android.util.Log.d("CoachAttendanceFragment", "Snapshot received: ${snapshot?.size() ?: 0} documents")
                    coaches.clear()
                    if (snapshot != null) {
                        for (document in snapshot) {
                            try {
                                val coach = document.toObject(Employee::class.java)
                                if (coach != null) {
                                    val coachWithId = coach.copy(id = document.id)
                                    coaches.add(coachWithId)
                                    android.util.Log.d("CoachAttendanceFragment", "Added coach: ${coachWithId.name} (ID: ${coachWithId.id})")
                                } else {
                                    android.util.Log.w("CoachAttendanceFragment", "Coach object is null for document: ${document.id}")
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("CoachAttendanceFragment", "Error parsing coach: ${e.message}")
                            }
                        }
                    } else {
                        android.util.Log.w("CoachAttendanceFragment", "Snapshot is null")
                    }
                    
                    // If no coaches found, add some test data for debugging
                    if (coaches.isEmpty()) {
                        android.util.Log.w("CoachAttendanceFragment", "No coaches found in database, adding test data")
                        addTestCoaches()
                    }
                    
                    android.util.Log.d("CoachAttendanceFragment", "Total coaches loaded: ${coaches.size}")
                    updateUI()
                    android.util.Log.d("CoachAttendanceFragment", "Loaded ${coaches.size} coaches")
                }
                
        } catch (e: Exception) {
            android.util.Log.e("CoachAttendanceFragment", "Error setting up coaches listener: ${e.message}")
        }
    }

    private fun addTestCoaches() {
        try {
            val currentTime = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
            val testAttendanceData = mapOf(currentTime to true) // Add one present attendance entry
            
            val testCoaches = listOf(
                Employee(
                    id = "test_coach_1",
                    name = "Test Coach 1",
                    email = "coach1@test.com",
                    phone = "1234567890",
                    role = "coach",
                    branch = "Test Branch",
                    totalDays = 30,
                    attendanceDays = testAttendanceData
                ),
                Employee(
                    id = "test_coach_2",
                    name = "Test Coach 2",
                    email = "coach2@test.com",
                    phone = "0987654321",
                    role = "coach",
                    branch = "Test Branch",
                    totalDays = 25,
                    attendanceDays = emptyMap() // No attendance for second coach
                )
            )
            
            coaches.addAll(testCoaches)
            android.util.Log.d("CoachAttendanceFragment", "Added ${testCoaches.size} test coaches")
            android.util.Log.d("CoachAttendanceFragment", "Test Coach 1 has attendance: ${testCoaches[0].attendanceDays}")
            android.util.Log.d("CoachAttendanceFragment", "Test Coach 2 has attendance: ${testCoaches[1].attendanceDays}")
        } catch (e: Exception) {
            android.util.Log.e("CoachAttendanceFragment", "Error adding test coaches: ${e.message}")
        }
    }

    private fun updateUI() {
        try {
            android.util.Log.d("CoachAttendanceFragment", "updateUI called - coaches.size: ${coaches.size}")
            if (coaches.isEmpty()) {
                android.util.Log.d("CoachAttendanceFragment", "No coaches found - showing empty state")
                recyclerView?.visibility = View.GONE
                tvNoCoaches?.visibility = View.VISIBLE
                tvNoCoaches?.text = "No coaches found"
            } else {
                android.util.Log.d("CoachAttendanceFragment", "Coaches found - showing RecyclerView")
                recyclerView?.visibility = View.VISIBLE
                tvNoCoaches?.visibility = View.GONE
                // Always notify adapter of data changes to ensure UI updates
                currentAdapter?.notifyDataSetChanged()
                android.util.Log.d("CoachAttendanceFragment", "Adapter notified of data changes")
            }
        } catch (e: Exception) {
            android.util.Log.e("CoachAttendanceFragment", "Error updating UI: ${e.message}")
        }
    }

    private fun updateCoachAttendance(coach: Employee, isPresent: Boolean) {
        try {
            val timestamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
            
            // Update local data immediately
            val coachIndex = coaches.indexOfFirst { it.id == coach.id }
            if (coachIndex != -1) {
                val updatedAttendanceDays = coaches[coachIndex].attendanceDays.toMutableMap()
                updatedAttendanceDays[timestamp] = isPresent
                coaches[coachIndex] = coaches[coachIndex].copy(attendanceDays = updatedAttendanceDays)
            }
            
            // Update UI immediately
            currentAdapter?.notifyDataSetChanged()
            
            // Show immediate feedback
            android.widget.Toast.makeText(
                context,
                "${coach.name} marked as ${if (isPresent) "present" else "absent"}",
                android.widget.Toast.LENGTH_SHORT
            ).show()
            
            // Update Firestore in background (non-blocking)
            if (!coach.id.startsWith("test_")) { // Only update Firestore for real coaches
                val updatedAttendanceDays = coach.attendanceDays.toMutableMap()
                updatedAttendanceDays[timestamp] = isPresent
                
                db.collection("employees").document(coach.id)
                    .update("attendanceDays", updatedAttendanceDays)
                    .addOnFailureListener { e ->
                        android.util.Log.e("CoachAttendanceFragment", "Error updating coach attendance: ${e.message}")
                    }
            }
        } catch (e: Exception) {
            android.util.Log.e("CoachAttendanceFragment", "Error updating coach attendance: ${e.message}")
        }
    }

    private fun undoCoachAttendance(coach: Employee) {
        try {
            // Find the most recent attendance entry and remove it
            val sortedEntries = coach.attendanceDays.entries.sortedByDescending { it.key }
            
            if (sortedEntries.isNotEmpty()) {
                val lastEntry = sortedEntries.first()
                
                // Update local data immediately
                val coachIndex = coaches.indexOfFirst { it.id == coach.id }
                if (coachIndex != -1) {
                    val updatedAttendanceDays = coaches[coachIndex].attendanceDays.toMutableMap()
                    updatedAttendanceDays.remove(lastEntry.key)
                    coaches[coachIndex] = coaches[coachIndex].copy(attendanceDays = updatedAttendanceDays)
                }
                
                // Update UI immediately
                currentAdapter?.notifyDataSetChanged()
                
                // Show immediate feedback
                android.widget.Toast.makeText(
                    context,
                    "Last attendance entry undone for ${coach.name}",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
                
                // Update Firestore in background (non-blocking)
                if (!coach.id.startsWith("test_")) { // Only update Firestore for real coaches
                    val updatedAttendanceDays = coach.attendanceDays.toMutableMap()
                    updatedAttendanceDays.remove(lastEntry.key)
                    
                    db.collection("employees").document(coach.id)
                        .update("attendanceDays", updatedAttendanceDays)
                        .addOnFailureListener { e ->
                            android.util.Log.e("CoachAttendanceFragment", "Error undoing coach attendance: ${e.message}")
                        }
                }
            } else {
                android.widget.Toast.makeText(
                    context,
                    "No attendance entries to undo for ${coach.name}",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        } catch (e: Exception) {
            android.util.Log.e("CoachAttendanceFragment", "Error undoing coach attendance: ${e.message}")
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        try {
            coachesListener?.remove()
        } catch (e: Exception) {
            android.util.Log.e("CoachAttendanceFragment", "Error cleaning up listeners: ${e.message}")
        }
    }

    companion object {
        fun newInstance(): CoachAttendanceFragment {
            return CoachAttendanceFragment()
        }
    }
} 