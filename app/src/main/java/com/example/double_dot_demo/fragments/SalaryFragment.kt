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
import com.example.double_dot_demo.adapters.SalaryAdapter
import com.example.double_dot_demo.models.Employee
import com.example.double_dot_demo.models.Trainee
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class SalaryFragment : Fragment() {

    private var recyclerView: RecyclerView? = null
    private var tvNoCoaches: TextView? = null
    
    private var currentAdapter: SalaryAdapter? = null
    private val coaches = mutableListOf<Employee>()
    private val trainees = mutableListOf<Trainee>()
    
    private var coachesListener: com.google.firebase.firestore.ListenerRegistration? = null
    private var traineesListener: com.google.firebase.firestore.ListenerRegistration? = null
    
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return try {
            inflater.inflate(R.layout.fragment_salary, container, false)
        } catch (e: Exception) {
            android.util.Log.e("SalaryFragment", "Error inflating layout: ${e.message}")
            TextView(requireContext()).apply {
                text = "Error loading salary page"
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
                android.util.Log.e("SalaryFragment", "One or more views not found")
                android.widget.Toast.makeText(context, "Error: Some UI elements not found", android.widget.Toast.LENGTH_SHORT).show()
                return
            }
            
            setupRecyclerView()
            loadData()
            
            android.util.Log.d("SalaryFragment", "Salary fragment setup completed successfully")
        } catch (e: Exception) {
            android.util.Log.e("SalaryFragment", "Error in onViewCreated: ${e.message}")
            android.widget.Toast.makeText(context, "Error setting up salary page: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupRecyclerView() {
        try {
            recyclerView?.let { rv ->
                rv.layoutManager = LinearLayoutManager(context)
                
                currentAdapter = SalaryAdapter(
                    coaches = coaches,
                    trainees = trainees
                )
                
                rv.adapter = currentAdapter
                
                android.util.Log.d("SalaryFragment", "RecyclerView setup completed")
            } ?: run {
                android.util.Log.e("SalaryFragment", "RecyclerView is null")
            }
        } catch (e: Exception) {
            android.util.Log.e("SalaryFragment", "Error setting up RecyclerView: ${e.message}")
        }
    }

    private fun loadData() {
        try {
            loadCoaches()
            loadTrainees()
            android.util.Log.d("SalaryFragment", "Data loading initiated")
        } catch (e: Exception) {
            android.util.Log.e("SalaryFragment", "Error loading data: ${e.message}")
        }
    }

    private fun loadCoaches() {
        try {
            coachesListener?.remove()
            
            coachesListener = db.collection("employees")
                .whereEqualTo("role", "coach")
                .orderBy("name", Query.Direction.ASCENDING)
                .addSnapshotListener { snapshot, e ->
                    if (e != null) {
                        android.util.Log.e("SalaryFragment", "Error loading coaches: ${e.message}")
                        return@addSnapshotListener
                    }

                    coaches.clear()
                    if (snapshot != null) {
                        for (document in snapshot) {
                            try {
                                val coach = document.toObject(Employee::class.java)
                                if (coach != null) {
                                    val coachWithId = coach.copy(id = document.id)
                                    coaches.add(coachWithId)
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("SalaryFragment", "Error parsing coach: ${e.message}")
                            }
                        }
                    }
                    
                    updateUI()
                    android.util.Log.d("SalaryFragment", "Loaded ${coaches.size} coaches")
                }
                
        } catch (e: Exception) {
            android.util.Log.e("SalaryFragment", "Error setting up coaches listener: ${e.message}")
        }
    }

    private fun loadTrainees() {
        try {
            traineesListener?.remove()
            
            traineesListener = db.collection("trainees")
                .orderBy("name", Query.Direction.ASCENDING)
                .addSnapshotListener { snapshot, e ->
                    if (e != null) {
                        android.util.Log.e("SalaryFragment", "Error loading trainees: ${e.message}")
                        return@addSnapshotListener
                    }

                    trainees.clear()
                    if (snapshot != null) {
                        for (document in snapshot) {
                            try {
                                val trainee = document.toObject(Trainee::class.java)
                                if (trainee != null) {
                                    val traineeWithId = trainee.copy(id = document.id)
                                    trainees.add(traineeWithId)
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("SalaryFragment", "Error parsing trainee: ${e.message}")
                            }
                        }
                    }
                    
                    updateUI()
                    android.util.Log.d("SalaryFragment", "Loaded ${trainees.size} trainees")
                }
                
        } catch (e: Exception) {
            android.util.Log.e("SalaryFragment", "Error setting up trainees listener: ${e.message}")
        }
    }

    private fun updateUI() {
        try {
            android.util.Log.d("SalaryFragment", "updateUI called - coaches: ${coaches.size}, trainees: ${trainees.size}")
            
            if (coaches.isEmpty()) {
                recyclerView?.visibility = View.GONE
                tvNoCoaches?.visibility = View.VISIBLE
                tvNoCoaches?.text = "No coaches found"
                android.util.Log.d("SalaryFragment", "No coaches found - showing empty state")
            } else {
                recyclerView?.visibility = View.VISIBLE
                tvNoCoaches?.visibility = View.GONE
                
                // Always update the adapter when data changes
                currentAdapter?.notifyDataSetChanged()
                android.util.Log.d("SalaryFragment", "Adapter updated with ${coaches.size} coaches and ${trainees.size} trainees")
                
                // Log salary calculations for debugging
                coaches.forEach { coach ->
                    val coachTrainees = trainees.filter { it.coachId == coach.id }
                    val totalPayments = coachTrainees.sumOf { it.paymentAmount }
                    val baseSalary = totalPayments * 0.4
                    android.util.Log.d("SalaryFragment", "Coach ${coach.name}: ${coachTrainees.size} trainees, total payments: $${String.format("%.2f", totalPayments)}, base salary: $${String.format("%.2f", baseSalary)}")
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("SalaryFragment", "Error updating UI: ${e.message}")
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        try {
            coachesListener?.remove()
            traineesListener?.remove()
        } catch (e: Exception) {
            android.util.Log.e("SalaryFragment", "Error cleaning up listeners: ${e.message}")
        }
    }

    companion object {
        fun newInstance(): SalaryFragment {
            return SalaryFragment()
        }
    }
} 