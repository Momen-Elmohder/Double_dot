package com.example.double_dot_demo.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.double_dot_demo.R
import com.example.double_dot_demo.adapters.EmployeeAdapter
import com.example.double_dot_demo.databinding.FragmentEmployeesBinding
import com.example.double_dot_demo.dialogs.AddEmployeeDialog
import com.example.double_dot_demo.dialogs.CreateEmployeeAccountDialog
import com.example.double_dot_demo.models.Employee
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

class EmployeesFragment : Fragment() {

    private var _binding: FragmentEmployeesBinding? = null
    private val binding get() = _binding!!
    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var employeeAdapter: EmployeeAdapter
    private val employees = mutableListOf<Employee>()
    private var currentUserRole: String = ""

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEmployeesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        firestore = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()
        
        // Get current user role from parent activity
        currentUserRole = (activity as? com.example.double_dot_demo.DashboardActivity)?.let { activity ->
            activity.intent.getStringExtra("user_role") ?: "unknown"
        } ?: "unknown"
        
        setupRecyclerView()
        setupAddButton()
        setupRoleBasedUI()
        loadEmployees()
    }

    private fun setupRecyclerView() {
        employeeAdapter = EmployeeAdapter(
            employees = employees,
            onEditClick = { employee -> 
                if (canEditEmployee(employee)) {
                    showEditEmployeeDialog(employee)
                } else {
                    Toast.makeText(requireContext(), "You don't have permission to edit this employee", Toast.LENGTH_SHORT).show()
                }
            },
            onDeleteClick = { employee -> 
                if (canDeleteEmployee(employee)) {
                    deleteEmployee(employee)
                } else {
                    Toast.makeText(requireContext(), "You don't have permission to delete this employee", Toast.LENGTH_SHORT).show()
                }
            }
        )

        binding.recyclerViewEmployees.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = employeeAdapter
        }
    }

    private fun setupAddButton() {
        binding.btnAddEmployee.setOnClickListener {
            if (currentUserRole == "head_coach") {
                showAccountCreationOptions()
            } else {
                showAddEmployeeDialog()
            }
        }
    }

    private fun setupRoleBasedUI() {
        // Show/hide add button based on role
        if (currentUserRole != "head_coach") {
            binding.btnAddEmployee.visibility = View.GONE
        }
    }

    private fun showAccountCreationOptions() {
        val options = arrayOf("Create Account", "Add Employee (No Account)")
        
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Add Employee")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showCreateAccountDialog()
                    1 -> showAddEmployeeDialog()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showCreateAccountDialog() {
        val dialog = CreateEmployeeAccountDialog(requireContext(), currentUserRole)
        dialog.setOnAccountCreatedListener { employee ->
            // Account created successfully, refresh the list
            loadEmployees()
        }
        dialog.show()
    }

    private fun canEditEmployee(employee: Employee): Boolean {
        return when (currentUserRole) {
            "head_coach" -> true // Head coaches can edit anyone
            "coach" -> employee.role != "head_coach" // Coaches can't edit head coaches
            "admin" -> employee.role != "head_coach" && employee.role != "admin" // Admins can't edit head coaches or other admins
            else -> false
        }
    }

    private fun canDeleteEmployee(employee: Employee): Boolean {
        return when (currentUserRole) {
            "head_coach" -> employee.role != "head_coach" // Head coaches can't delete themselves
            "coach" -> false // Coaches can't delete anyone
            "admin" -> employee.role == "coach" // Admins can only delete coaches
            else -> false
        }
    }

    private fun loadEmployees() {
        firestore.collection("employees")
            .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Toast.makeText(requireContext(), "Error loading employees: ${e.message}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                employees.clear()
                if (snapshot != null) {
                    for (document in snapshot) {
                        val employee = document.toObject(Employee::class.java)
                        employee?.let {
                            it.id = document.id
                            employees.add(it)
                        }
                    }
                }

                updateStats()
                employeeAdapter.notifyDataSetChanged()
            }
    }

    private fun updateStats() {
        val total = employees.size
        val coaches = employees.count { it.role == "coach" }
        val headCoaches = employees.count { it.role == "head_coach" }
        val admins = employees.count { it.role == "admin" }
        
        binding.tvTotalCount.text = total.toString()
        binding.tvCoachesCount.text = coaches.toString()
        
        // Update stats text based on role
        when (currentUserRole) {
            "head_coach" -> {
                binding.tvStatsTitle.text = "Staff Overview"
                binding.tvCoachesCount.text = "${coaches + headCoaches + admins}"
            }
            "coach" -> {
                binding.tvStatsTitle.text = "Team Members"
                binding.tvCoachesCount.text = coaches.toString()
            }
            "admin" -> {
                binding.tvStatsTitle.text = "Management"
                binding.tvCoachesCount.text = "${coaches + admins}"
            }
        }
    }

    private fun showAddEmployeeDialog() {
        val dialog = AddEmployeeDialog(requireContext())
        dialog.setOnSaveClickListener { employee ->
            addEmployee(employee)
        }
        dialog.show()
    }

    private fun showEditEmployeeDialog(employee: Employee) {
        val dialog = AddEmployeeDialog(requireContext(), employee)
        dialog.setOnSaveClickListener { updatedEmployee ->
            updateEmployee(updatedEmployee)
        }
        dialog.show()
    }

    private fun addEmployee(employee: Employee) {
        val employeeData = hashMapOf(
            "name" to employee.name,
            "email" to employee.email,
            "role" to employee.role,
            "phone" to employee.phone,
            "hireDate" to employee.hireDate,
            "salary" to employee.salary,
            "status" to employee.status,
            "createdAt" to Timestamp.now(),
            "updatedAt" to Timestamp.now()
        )

        firestore.collection("employees")
            .add(employeeData)
            .addOnSuccessListener { documentReference ->
                Toast.makeText(requireContext(), "Employee added successfully", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Error adding employee: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun updateEmployee(employee: Employee) {
        val employeeData = hashMapOf(
            "name" to employee.name,
            "email" to employee.email,
            "role" to employee.role,
            "phone" to employee.phone,
            "hireDate" to employee.hireDate,
            "salary" to employee.salary,
            "status" to employee.status,
            "updatedAt" to Timestamp.now()
        )

        firestore.collection("employees").document(employee.id)
            .update(employeeData as Map<String, Any>)
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "Employee updated successfully", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Error updating employee: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun deleteEmployee(employee: Employee) {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Delete Employee")
            .setMessage("Are you sure you want to delete ${employee.name}? This action cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                firestore.collection("employees").document(employee.id)
                    .delete()
                    .addOnSuccessListener {
                        Toast.makeText(requireContext(), "Employee deleted successfully", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(requireContext(), "Error deleting employee: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(): EmployeesFragment {
            return EmployeesFragment()
        }
    }
} 