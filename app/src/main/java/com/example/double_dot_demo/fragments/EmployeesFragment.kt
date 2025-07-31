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
import com.example.double_dot_demo.models.Employee
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

class EmployeesFragment : Fragment() {

    private var _binding: FragmentEmployeesBinding? = null
    private val binding get() = _binding!!
    private lateinit var firestore: FirebaseFirestore
    private lateinit var employeeAdapter: EmployeeAdapter
    private val employees = mutableListOf<Employee>()

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
        setupRecyclerView()
        setupAddButton()
        loadEmployees()
    }

    private fun setupRecyclerView() {
        employeeAdapter = EmployeeAdapter(
            employees = employees,
            onEditClick = { employee -> showEditEmployeeDialog(employee) },
            onDeleteClick = { employee -> deleteEmployee(employee) }
        )

        binding.recyclerViewEmployees.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = employeeAdapter
        }
    }

    private fun setupAddButton() {
        binding.btnAddEmployee.setOnClickListener {
            showAddEmployeeDialog()
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
        
        binding.tvTotalCount.text = total.toString()
        binding.tvCoachesCount.text = coaches.toString()
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
        firestore.collection("employees").document(employee.id)
            .delete()
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "Employee deleted successfully", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Error deleting employee: ${e.message}", Toast.LENGTH_SHORT).show()
            }
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