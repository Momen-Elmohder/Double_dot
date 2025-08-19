package com.example.double_dot_demo.fragments

import android.os.Bundle
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
import android.widget.AutoCompleteTextView
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
import com.google.firebase.firestore.ListenerRegistration
import com.example.double_dot_demo.utils.NavigationUtils
import com.example.double_dot_demo.utils.ButtonUtils
import java.text.SimpleDateFormat
import java.util.*

class EmployeesFragment : Fragment() {

    private var _binding: FragmentEmployeesBinding? = null
    private val binding get() = _binding!!
    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var employeeAdapter: EmployeeAdapter
    private val employees = mutableListOf<Employee>()
    private val filteredEmployees = mutableListOf<Employee>()
    private var currentUserRole: String = ""
    
    // Search and sort variables
    private var searchQuery: String = ""
    private var sortBy: String = "name"
    private var sortOrder: String = "asc"
    
    // Listener registration for proper cleanup
    private var employeesListener: ListenerRegistration? = null

    private var activeAddDialog: AddEmployeeDialog? = null
    private var activeCreateDialog: CreateEmployeeAccountDialog? = null

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
                            activeAddDialog?.setPickedPhoneNumber(number)
                            activeCreateDialog?.setPickedPhoneNumber(number)
                        }
                    }
                }
            } catch (_: Exception) {}
        }
    }

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

        try {
            firestore = FirebaseFirestore.getInstance()
            auth = FirebaseAuth.getInstance()
            
            // Get current user role from arguments
            currentUserRole = arguments?.getString("user_role") ?: "unknown"
            android.util.Log.d("EmployeesFragment", "Current user role: $currentUserRole")
            
            // Check if user has permission to access employees
            if (currentUserRole == "coach") {
                showAccessDeniedMessage()
                return
            }
            
            setupRecyclerView()
            setupAddButton()
            setupRoleBasedUI()
            setupSearchAndSort()
            loadEmployees()
        } catch (e: Exception) {
            android.util.Log.e("EmployeesFragment", "Error in onViewCreated: ${e.message}")
            if (isAdded) {
                Toast.makeText(requireContext(), "Error loading employees: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showAccessDeniedMessage() {
        binding.root.removeAllViews()
        val messageView = android.widget.TextView(requireContext()).apply {
            text = "Access Denied\n\nCoaches cannot access employee information."
            textSize = 18f
            gravity = android.view.Gravity.CENTER
            setTextColor(resources.getColor(android.R.color.holo_red_dark, null))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        binding.root.addView(messageView)
    }

    private fun setupRecyclerView() {
        employeeAdapter = EmployeeAdapter(
            employees = filteredEmployees,
            onEditClick = { employee -> 
                if (canEditEmployee(employee)) {
                    showEditEmployeeDialog(employee)
                } else {
                    if (isAdded) {
                        Toast.makeText(requireContext(), "You don't have permission to edit this employee", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onDeleteClick = { employee -> 
                if (canDeleteEmployee(employee)) {
                    deleteEmployee(employee)
                } else {
                    if (isAdded) {
                        Toast.makeText(requireContext(), "You don't have permission to delete this employee", Toast.LENGTH_SHORT).show()
                    }
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
            showCreateAccountDialog()
        }
    }

    private fun setupRoleBasedUI() {
        // Hide add button for coaches
        if (currentUserRole == "coach") {
            binding.btnAddEmployee.visibility = View.GONE
        }
    }

    private fun setupSearchAndSort() {
        // Setup search functionality
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                searchQuery = s.toString().trim()
                filterAndSortEmployees()
            }
        })

        // Setup sort by dropdown
        val sortByOptions = listOf("Name", "Role", "Total Days", "Status")
        val sortByAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, sortByOptions)
        binding.actvSortBy.setAdapter(sortByAdapter)
        binding.actvSortBy.setText("Name", false)

        binding.actvSortBy.setOnItemClickListener { _, _, position, _ ->
            sortBy = when (position) {
                0 -> "name"
                1 -> "role"
                2 -> "totalDays"
                3 -> "status"
                else -> "name"
            }
            filterAndSortEmployees()
        }

        // Setup sort order dropdown
        val sortOrderOptions = listOf("Ascending", "Descending")
        val sortOrderAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, sortOrderOptions)
        binding.actvSortOrder.setAdapter(sortOrderAdapter)
        binding.actvSortOrder.setText("Ascending", false)

        binding.actvSortOrder.setOnItemClickListener { _, _, position, _ ->
            sortOrder = if (position == 0) "asc" else "desc"
            filterAndSortEmployees()
        }
    }

    private fun filterAndSortEmployees() {
        // Filter employees based on search query
        filteredEmployees.clear()
        
        employees.forEach { employee ->
            val matchesSearch = searchQuery.isEmpty() || 
                employee.name.contains(searchQuery, ignoreCase = true) ||
                employee.role.contains(searchQuery, ignoreCase = true) ||
                employee.status.contains(searchQuery, ignoreCase = true) ||
                employee.totalDays.toString().contains(searchQuery)
            
            if (matchesSearch) {
                filteredEmployees.add(employee)
            }
        }

        // Sort filtered employees
        filteredEmployees.sortWith { employee1, employee2 ->
            val comparison = when (sortBy) {
                "name" -> employee1.name.compareTo(employee2.name, ignoreCase = true)
                "role" -> employee1.role.compareTo(employee2.role, ignoreCase = true)
                "totalDays" -> employee1.totalDays.compareTo(employee2.totalDays)
                "status" -> employee1.status.compareTo(employee2.status, ignoreCase = true)
                else -> employee1.name.compareTo(employee2.name, ignoreCase = true)
            }
            
            if (sortOrder == "desc") -comparison else comparison
        }

        if (isAdded) {
            updateStats()
            employeeAdapter.notifyDataSetChanged()
        }
    }

    // Removed showAccountCreationOptions method - now directly shows create account dialog

    private fun showCreateAccountDialog() {
        if (isAdded) {
            val dialog = CreateEmployeeAccountDialog(requireContext(), currentUserRole)
            activeCreateDialog = dialog
            dialog.setOnPickPhoneClickListener { launchContactPicker() }
            dialog.setOnAccountCreatedListener { employee ->
                // Account created successfully, refresh the list
                loadEmployees()
            }
            dialog.show()
        }
    }

    private fun canEditEmployee(employee: Employee): Boolean {
        val canEdit = when (currentUserRole) {
            "head_coach" -> true // Head coaches can edit anyone
            "admin" -> employee.role != "head_coach" && employee.role != "admin" // Admins can't edit head coaches or other admins
            else -> false
        }
        android.util.Log.d("EmployeesFragment", "Can edit employee ${employee.name} (role: ${employee.role}) with user role $currentUserRole: $canEdit")
        return canEdit
    }

    private fun canDeleteEmployee(employee: Employee): Boolean {
        val canDelete = when (currentUserRole) {
            "head_coach" -> employee.role != "head_coach" // Head coaches can't delete themselves
            "admin" -> employee.role == "coach" // Admins can only delete coaches
            else -> false
        }
        android.util.Log.d("EmployeesFragment", "Can delete employee ${employee.name} (role: ${employee.role}) with user role $currentUserRole: $canDelete")
        return canDelete
    }

    private fun loadEmployees() {
        try {
            employeesListener = firestore.collection("employees")
                .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .addSnapshotListener { snapshot, e ->
                    if (e != null) {
                        if (isAdded) {
                            Toast.makeText(requireContext(), "Error loading employees: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                        return@addSnapshotListener
                    }

                    if (isAdded) {
                        employees.clear()
                        if (snapshot != null) {
                            for (document in snapshot) {
                                val employee = document.toObject(Employee::class.java)
                                employee?.let { originalEmployee ->
                                    val employeeWithId = originalEmployee.copy(id = document.id)
                                    employees.add(employeeWithId)
                                }
                            }
                        }

                        filterAndSortEmployees()
                    }
                }
        } catch (e: Exception) {
            if (isAdded) {
                Toast.makeText(requireContext(), "Error loading employees: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateStats() {
        if (!isAdded) return
        
        val total = filteredEmployees.size
        val coaches = filteredEmployees.count { it.role == "coach" }
        val headCoaches = filteredEmployees.count { it.role == "head_coach" }
        val admins = filteredEmployees.count { it.role == "admin" }
        
        binding.tvTotalCount.text = total.toString()
        binding.tvCoachesCount.text = coaches.toString()
        
        // Update stats text based on role
        when (currentUserRole) {
            "head_coach" -> {
                binding.tvStatsTitle.text = "Staff Overview"
                binding.tvCoachesCount.text = "${coaches + headCoaches + admins}"
            }
            "admin" -> {
                binding.tvStatsTitle.text = "Management"
                binding.tvCoachesCount.text = "${coaches + admins}"
            }
        }
    }

    // Removed showAddEmployeeDialog method - no longer needed since we only allow account creation

    private fun showEditEmployeeDialog(employee: Employee) {
        if (isAdded) {
            val dialog = AddEmployeeDialog(requireContext(), employee)
            activeAddDialog = dialog
            dialog.setOnPickPhoneClickListener { launchContactPicker() }
            dialog.setOnSaveClickListener { updatedEmployee ->
                updateEmployee(updatedEmployee)
            }
            dialog.show()
        }
    }

    private fun launchContactPicker() {
        try {
            val intent = Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
            contactPickerLauncher.launch(intent)
        } catch (_: Exception) {}
    }
    
    private fun showEmployeeDetailsDialog(employee: Employee) {
        if (!isAdded) return
        
        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val message = """
            Name: ${employee.name}
            Email: ${employee.email}
            Role: ${employee.role}
            Phone: ${employee.phone}
            Total Days: ${employee.totalDays}
            Status: ${employee.status}
        """.trimIndent()
        
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Employee Details")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    // Removed addEmployee method - no longer needed since we only allow account creation

    private fun updateEmployee(employee: Employee) {
        if (!isAdded) return
        
        // Show loading indicator
        val progressDialog = androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setView(R.layout.dialog_loading)
            .setCancelable(false)
            .create()
        progressDialog.show()

        val employeeData = hashMapOf(
            "name" to employee.name,
            "email" to employee.email,
            "role" to employee.role,
            "phone" to employee.phone,
            "totalDays" to employee.totalDays,
            "remainingDays" to employee.remainingDays,
            "status" to employee.status,
            "updatedAt" to Timestamp.now()
        )

        firestore.collection("employees").document(employee.id)
            .update(employeeData as Map<String, Any>)
            .addOnSuccessListener {
                if (isAdded) {
                    progressDialog.dismiss()
                    Toast.makeText(requireContext(), "Employee updated successfully", Toast.LENGTH_SHORT).show()
                    // Refresh the list to show the updated employee
                    loadEmployees()
                }
            }
            .addOnFailureListener { e ->
                if (isAdded) {
                    progressDialog.dismiss()
                    Toast.makeText(requireContext(), "Error updating employee: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun deleteEmployee(employee: Employee) {
        if (!isAdded) return
        
        android.util.Log.d("EmployeesFragment", "Attempting to delete employee: ${employee.name} (ID: ${employee.id})")
        
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Delete Employee")
            .setMessage("Are you sure you want to delete ${employee.name}? This action cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                // Show loading indicator
                val progressDialog = androidx.appcompat.app.AlertDialog.Builder(requireContext())
                    .setView(R.layout.dialog_loading)
                    .setCancelable(false)
                    .create()
                progressDialog.show()

                firestore.collection("employees").document(employee.id)
                    .delete()
                    .addOnSuccessListener {
                        if (isAdded) {
                            progressDialog.dismiss()
                            android.util.Log.d("EmployeesFragment", "Employee deleted successfully from Firestore: ${employee.name}")
                            Toast.makeText(requireContext(), "Employee deleted successfully", Toast.LENGTH_SHORT).show()
                            // Refresh the list to show the updated data
                            loadEmployees()
                        }
                    }
                    .addOnFailureListener { e ->
                        if (isAdded) {
                            progressDialog.dismiss()
                            android.util.Log.e("EmployeesFragment", "Error deleting employee: ${e.message}")
                            android.util.Log.e("EmployeesFragment", "Error details: ${e.cause}")
                            Toast.makeText(requireContext(), "Error deleting employee: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onPause() {
        super.onPause()
        // Cancel any pending operations when fragment is paused
    }

    override fun onDestroyView() {
        super.onDestroyView()
        
        // Remove listeners to prevent memory leaks
        try {
            employeesListener?.remove()
            employeesListener = null
        } catch (e: Exception) {
            android.util.Log.e("EmployeesFragment", "Error removing listeners: ${e.message}")
        }
        
        _binding = null
    }

    companion object {
        fun newInstance(userRole: String = ""): EmployeesFragment {
            return EmployeesFragment().apply {
                arguments = Bundle().apply {
                    putString("user_role", userRole)
                }
            }
        }
    }
} 