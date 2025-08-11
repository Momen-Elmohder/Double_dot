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
                    onAttendanceUpdated = { coach, isPresent -> updateCoachAttendance(coach, isPresent) },
                    onUndoAttendance = { coach -> undoCoachAttendance(coach) }
                )
                
                rv.adapter = currentAdapter
                currentAdapter?.setupSwipeCallback(rv)
            }
        } catch (e: Exception) {
            android.util.Log.e("CoachAttendanceFragment", "Error setting up RecyclerView: ${e.message}")
        }
    }

    private fun loadCoaches() {
        try {
            coachesListener?.remove()
            coachesListener = db.collection("employees")
                .whereIn("role", listOf("coach", "admin"))
                .orderBy("name", Query.Direction.ASCENDING)
                .addSnapshotListener { snapshot, e ->
                    if (e != null) return@addSnapshotListener
                    coaches.clear()
                    snapshot?.forEach { document ->
                        document.toObject(Employee::class.java)?.let { emp ->
                            coaches.add(emp.copy(id = document.id))
                        }
                    }
                    updateUI()
                }
        } catch (_: Exception) {}
    }

    private fun updateUI() {
        try {
            if (coaches.isEmpty()) {
                recyclerView?.visibility = View.GONE
                tvNoCoaches?.visibility = View.VISIBLE
                tvNoCoaches?.text = "No employees found"
            } else {
                recyclerView?.visibility = View.VISIBLE
                tvNoCoaches?.visibility = View.GONE
                currentAdapter?.notifyDataSetChanged()
            }
        } catch (_: Exception) {}
    }

    private fun updateCoachAttendance(coach: Employee, isPresent: Boolean) {
        try {
            val timestamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
            val index = coaches.indexOfFirst { it.id == coach.id }
            if (index != -1) {
                val updated = coaches[index].attendanceDays.toMutableMap()
                updated[timestamp] = isPresent
                coaches[index] = coaches[index].copy(attendanceDays = updated)
            }
            currentAdapter?.notifyDataSetChanged()
            android.widget.Toast.makeText(context, "${coach.name} marked as ${if (isPresent) "present" else "absent"}", android.widget.Toast.LENGTH_SHORT).show()
            if (!coach.id.startsWith("test_")) {
                val updated = coach.attendanceDays.toMutableMap(); updated[timestamp] = isPresent
                db.collection("employees").document(coach.id).update("attendanceDays", updated)
            }
        } catch (_: Exception) {}
    }

    private fun undoCoachAttendance(coach: Employee) {
        try {
            val sorted = coach.attendanceDays.entries.sortedByDescending { it.key }
            if (sorted.isNotEmpty()) {
                val last = sorted.first()
                val index = coaches.indexOfFirst { it.id == coach.id }
                if (index != -1) {
                    val updated = coaches[index].attendanceDays.toMutableMap()
                    updated.remove(last.key)
                    coaches[index] = coaches[index].copy(attendanceDays = updated)
                }
                currentAdapter?.notifyDataSetChanged()
                android.widget.Toast.makeText(context, "Last attendance entry undone for ${coach.name}", android.widget.Toast.LENGTH_SHORT).show()
                if (!coach.id.startsWith("test_")) {
                    val updated = coach.attendanceDays.toMutableMap(); updated.remove(last.key)
                    db.collection("employees").document(coach.id).update("attendanceDays", updated)
                }
            } else {
                android.widget.Toast.makeText(context, "No attendance entries to undo for ${coach.name}", android.widget.Toast.LENGTH_SHORT).show()
            }
        } catch (_: Exception) {}
    }

    override fun onDestroyView() {
        super.onDestroyView()
        try { coachesListener?.remove() } catch (_: Exception) {}
    }

    companion object { fun newInstance(): CoachAttendanceFragment = CoachAttendanceFragment() }
} 