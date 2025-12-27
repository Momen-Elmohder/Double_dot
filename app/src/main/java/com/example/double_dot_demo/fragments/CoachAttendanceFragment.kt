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
import androidx.lifecycle.lifecycleScope
import com.example.double_dot_demo.utils.SalaryManager
import kotlinx.coroutines.launch

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

            checkAndHandleNewMonth()
            setupRecyclerView()
            loadCoaches()

        } catch (e: Exception) {
            android.util.Log.e("CoachAttendanceFragment", "Error in onViewCreated: ${e.message}")
            android.widget.Toast.makeText(context, "Error setting up coach attendance page: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun getCurrentMonth(): String {
        return java.text.SimpleDateFormat(
            "yyyy-MM",
            java.util.Locale.getDefault()
        ).format(java.util.Date())
    }

    private fun checkAndHandleNewMonth() {

        val currentMonth = getCurrentMonth()
        val settingsRef = db.collection("settings").document("monthControl")

        settingsRef.get().addOnSuccessListener { doc ->

            val lastMonth = doc.getString("lastMonth")

            if (lastMonth == null) {
                // First run
                settingsRef.set(mapOf("lastMonth" to currentMonth))
                return@addOnSuccessListener
            }

            if (lastMonth != currentMonth) {
                // 🔥 Month changed
                resetCoachAttendance()
                settingsRef.update("lastMonth", currentMonth)
            }
        }
    }

    private fun resetCoachAttendance() {
        coaches.forEach { coach ->
            db.collection("employees")
                .document(coach.id)
                .update("attendanceDays", emptyMap<String, Boolean>())
        }

        // Reset local cache
        for (i in coaches.indices) {
            coaches[i] = coaches[i].copy(attendanceDays = emptyMap())
        }

        currentAdapter?.notifyDataSetChanged()
    }

    private fun setupRecyclerView() {
        try {
            recyclerView?.let { rv ->
                rv.layoutManager = LinearLayoutManager(context)

                currentAdapter = CoachAttendanceAdapter(
                    coaches = coaches,
                    onAttendanceUpdated = { coach, isPresent, note ->

                        // 1️⃣ Save attendance
                        updateCoachAttendance(coach, isPresent)

                        // 2️⃣ Save note
                        saveCoachAttendanceNote(coach, isPresent, note)

                        // 3️⃣ Recalculate salary ONLY if live mode is enabled
                        isLiveSalaryEnabled { isLive ->
                            if (isLive) {
                                lifecycleScope.launch {
                                    SalaryManager().recalculateSalaryForCoach(coach.id)
                                }
                            }
                        }
                    },
                    onUndoAttendance = { coach ->
                        undoCoachAttendance(coach)
                    },
                    onShowDetails = { coach ->
                        showCoachAttendanceDetails(coach)
                    }
                )

                rv.adapter = currentAdapter
                currentAdapter?.setupSwipeCallback(rv)
            }
        } catch (e: Exception) {
            android.util.Log.e("CoachAttendanceFragment", "Error setting up RecyclerView: ${e.message}")
        }
    }

    private fun showCoachAttendanceDetails(coach: Employee) {

        val monthKey = java.text.SimpleDateFormat(
            "yyyy-MM",
            java.util.Locale.getDefault()
        ).format(java.util.Date())

        db.collection("employees")
            .document(coach.id)
            .collection("coachAttendance")
            .document(monthKey)
            .get()
            .addOnSuccessListener { doc ->

                val records =
                    doc.get("records") as? List<Map<String, Any>> ?: emptyList()

                if (records.isEmpty()) {
                    android.widget.Toast.makeText(
                        requireContext(),
                        "No attendance notes for this month",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                    return@addOnSuccessListener
                }

                val present = records.filter { it["isPresent"] == true }
                val absent = records.filter { it["isPresent"] == false }

                val message = buildString {
                    if (present.isNotEmpty()) {
                        append("Present:\n")
                        present.forEachIndexed { i, r ->
                            append("${i + 1}) ${r["note"]}\n")
                        }
                        append("\n")
                    }

                    if (absent.isNotEmpty()) {
                        append("Absent:\n")
                        absent.forEachIndexed { i, r ->
                            append("${i + 1}) ${r["note"]}\n")
                        }
                    }
                }

                android.app.AlertDialog.Builder(requireContext())
                    .setTitle("${coach.name} Attendance ($monthKey)")
                    .setMessage(message.trim())
                    .setPositiveButton("OK", null)
                    .show()
            }
            .addOnFailureListener {
                android.widget.Toast.makeText(
                    requireContext(),
                    "Failed to load attendance details",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
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

    private fun saveCoachAttendanceNote(
        coach: Employee,
        isPresent: Boolean,
        note: String
    ) {
        if (note.isBlank()) return

        val date = java.text.SimpleDateFormat(
            "yyyy-MM-dd",
            java.util.Locale.getDefault()
        ).format(java.util.Date())

        val monthKey = java.text.SimpleDateFormat(
            "yyyy-MM",
            java.util.Locale.getDefault()
        ).format(java.util.Date())

        val record = hashMapOf(
            "date" to date,
            "isPresent" to isPresent,
            "note" to note
        )

        val docRef = db.collection("employees")
            .document(coach.id)
            .collection("coachAttendance")
            .document(monthKey)

        docRef.update(
            "records",
            com.google.firebase.firestore.FieldValue.arrayUnion(record)
        ).addOnFailureListener {
            // month document doesn't exist → create it
            docRef.set(mapOf("records" to listOf(record)))
        }
    }

    private fun undoCoachAttendanceNote(
        coach: Employee,
        lastTimestamp: String
    ) {

        val dateKey = lastTimestamp.substring(0, 10) // yyyy-MM-dd
        val monthKey = dateKey.substring(0, 7)       // yyyy-MM

        val docRef = db.collection("employees")
            .document(coach.id)
            .collection("coachAttendance")
            .document(monthKey)

        docRef.get().addOnSuccessListener { doc ->
            val records =
                doc.get("records") as? MutableList<Map<String, Any>> ?: return@addOnSuccessListener

            val updated = records.filterNot {
                it["date"] == dateKey
            }

            docRef.update("records", updated)
        }
    }

    private fun undoCoachAttendance(coach: Employee) {
        try {
            val sorted = coach.attendanceDays.entries.sortedByDescending { it.key }
            if (sorted.isNotEmpty()) {
                val last = sorted.first()
                val lastTimestamp = last.key
                val index = coaches.indexOfFirst { it.id == coach.id }
                if (index != -1) {
                    val updated = coaches[index].attendanceDays.toMutableMap()
                    updated.remove(last.key)
                    coaches[index] = coaches[index].copy(attendanceDays = updated)
                }
                currentAdapter?.notifyDataSetChanged()
                android.widget.Toast.makeText(context, "Last attendance entry undone for ${coach.name}", android.widget.Toast.LENGTH_SHORT).show()
                undoCoachAttendanceNote(coach, lastTimestamp)
                if (!coach.id.startsWith("test_")) {
                    val updated = coach.attendanceDays.toMutableMap(); updated.remove(last.key)
                    db.collection("employees").document(coach.id).update("attendanceDays", updated)
                }
            } else {
                android.widget.Toast.makeText(context, "No attendance entries to undo for ${coach.name}", android.widget.Toast.LENGTH_SHORT).show()
            }
        } catch (_: Exception) {}
    }

    private fun isLiveSalaryEnabled(onResult: (Boolean) -> Unit) {
        FirebaseFirestore.getInstance()
            .collection("settings")
            .document("payroll")
            .get()
            .addOnSuccessListener { doc ->
                val mode = doc.getString("salaryMode") ?: "monthly"
                onResult(mode == "live")
            }
            .addOnFailureListener {
                onResult(false)
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        try { coachesListener?.remove() } catch (_: Exception) {}
    }

    companion object { fun newInstance(): CoachAttendanceFragment = CoachAttendanceFragment() }
}
