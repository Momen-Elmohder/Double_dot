package com.example.double_dot_demo.dialogs

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.example.double_dot_demo.R
import com.example.double_dot_demo.models.Employee
import com.example.double_dot_demo.models.Expense
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

class AddExpenseDialog : DialogFragment() {

    private var onExpenseAdded: ((Expense) -> Unit)? = null
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    // UI Elements
    private lateinit var etAmount: EditText
    private lateinit var etReason: EditText
    private lateinit var spinnerType: Spinner
    private lateinit var spinnerBranch: Spinner
    private lateinit var btnAdd: Button
    private lateinit var btnCancel: Button
    private lateinit var tilAmount: TextInputLayout
    private lateinit var tilReason: TextInputLayout
    private lateinit var tvAddedBy: TextView

    // Data lists
    private val branches = mutableListOf<String>()
    private var currentUser: Employee? = null

    companion object {
        fun newInstance(onExpenseAdded: (Expense) -> Unit): AddExpenseDialog {
            return AddExpenseDialog().apply {
                this.onExpenseAdded = onExpenseAdded
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return try {
            val builder = AlertDialog.Builder(requireContext())
            val inflater = requireActivity().layoutInflater
            val view = inflater.inflate(R.layout.dialog_add_expense, null)

            initializeViews(view)
            setupSpinners()
            setupButtons()
            loadData()

            builder.setView(view)
                .setTitle("Add Expense/Income")
                .setCancelable(false)

            builder.create()
        } catch (e: Exception) {
            android.util.Log.e("AddExpenseDialog", "Error creating dialog: ${e.message}")
            AlertDialog.Builder(requireContext())
                .setTitle("Error")
                .setMessage("Failed to load dialog")
                .setPositiveButton("OK") { _, _ -> dismiss() }
                .create()
        }
    }

    private fun initializeViews(view: View) {
        try {
            etAmount = view.findViewById(R.id.etAmount)
            etReason = view.findViewById(R.id.etReason)
            spinnerType = view.findViewById(R.id.spinnerType)
            spinnerBranch = view.findViewById(R.id.spinnerBranch)
            btnAdd = view.findViewById(R.id.btnAdd)
            btnCancel = view.findViewById(R.id.btnCancel)
            tilAmount = view.findViewById(R.id.tilAmount)
            tilReason = view.findViewById(R.id.tilReason)
            tvAddedBy = view.findViewById(R.id.tvAddedBy)

            android.util.Log.d("AddExpenseDialog", "Views initialized successfully")
        } catch (e: Exception) {
            android.util.Log.e("AddExpenseDialog", "Error initializing views: ${e.message}")
        }
    }

    private fun setupSpinners() {
        try {
            // Type spinner (Expense/Income)
            val typeOptions = arrayOf("EXPENSE", "INCOME")
            val typeAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, typeOptions)
            typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinnerType.adapter = typeAdapter

            // Branch spinner
            val branchAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, branches)
            branchAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinnerBranch.adapter = branchAdapter

            android.util.Log.d("AddExpenseDialog", "Spinners setup completed")
        } catch (e: Exception) {
            android.util.Log.e("AddExpenseDialog", "Error setting up spinners: ${e.message}")
        }
    }

    private fun setupButtons() {
        try {
            btnAdd.setOnClickListener {
                if (validateInputs()) {
                    addExpense()
                }
            }

            btnCancel.setOnClickListener {
                dismiss()
            }

            android.util.Log.d("AddExpenseDialog", "Buttons setup completed")
        } catch (e: Exception) {
            android.util.Log.e("AddExpenseDialog", "Error setting up buttons: ${e.message}")
        }
    }

    private fun loadData() {
        try {
            loadBranches()
            loadCurrentUser()
            android.util.Log.d("AddExpenseDialog", "Data loading initiated")
        } catch (e: Exception) {
            android.util.Log.e("AddExpenseDialog", "Error loading data: ${e.message}")
        }
    }

    private fun loadBranches() {
        try {
            // Load branches from trainees collection
            db.collection("trainees")
                .get()
                .addOnSuccessListener { snapshot ->
                    try {
                        branches.clear()
                        val branchSet = mutableSetOf<String>()
                        
                        for (document in snapshot) {
                            val branch = document.getString("branch")
                            if (!branch.isNullOrEmpty()) {
                                branchSet.add(branch)
                            }
                        }
                        
                        branches.addAll(branchSet.sorted())
                        
                        // Update spinner
                        (spinnerBranch.adapter as? ArrayAdapter<*>)?.notifyDataSetChanged()
                        
                        android.util.Log.d("AddExpenseDialog", "Loaded ${branches.size} branches")
                    } catch (e: Exception) {
                        android.util.Log.e("AddExpenseDialog", "Error processing branches: ${e.message}")
                    }
                }
                .addOnFailureListener { e ->
                    android.util.Log.e("AddExpenseDialog", "Error loading branches: ${e.message}")
                }
        } catch (e: Exception) {
            android.util.Log.e("AddExpenseDialog", "Error setting up branches listener: ${e.message}")
        }
    }

    private fun loadCurrentUser() {
        try {
            val currentUserId = auth.currentUser?.uid
            if (currentUserId != null) {
                // Load current user from employees collection
                db.collection("employees")
                    .document(currentUserId)
                    .get()
                    .addOnSuccessListener { document ->
                        try {
                            if (document != null && document.exists()) {
                                currentUser = document.toObject(Employee::class.java)?.copy(id = document.id)
                                currentUser?.let { user ->
                                    tvAddedBy.text = "Added By: ${user.name}"
                                    android.util.Log.d("AddExpenseDialog", "Current user loaded: ${user.name}")
                                }
                            } else {
                                // If user not found in employees, use Firebase Auth display name
                                val displayName = auth.currentUser?.displayName ?: "Unknown User"
                                tvAddedBy.text = "Added By: $displayName"
                                android.util.Log.d("AddExpenseDialog", "Using Firebase Auth display name: $displayName")
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("AddExpenseDialog", "Error processing current user: ${e.message}")
                            val displayName = auth.currentUser?.displayName ?: "Unknown User"
                            tvAddedBy.text = "Added By: $displayName"
                        }
                    }
                    .addOnFailureListener { e ->
                        android.util.Log.e("AddExpenseDialog", "Error loading current user: ${e.message}")
                        val displayName = auth.currentUser?.displayName ?: "Unknown User"
                        tvAddedBy.text = "Added By: $displayName"
                    }
            } else {
                tvAddedBy.text = "Added By: Unknown User"
                android.util.Log.w("AddExpenseDialog", "No current user found")
            }
        } catch (e: Exception) {
            android.util.Log.e("AddExpenseDialog", "Error setting up current user: ${e.message}")
            tvAddedBy.text = "Added By: Unknown User"
        }
    }

    private fun validateInputs(): Boolean {
        try {
            var isValid = true

            // Validate amount
            val amountText = etAmount.text.toString().trim()
            if (amountText.isEmpty()) {
                tilAmount.error = "Amount is required"
                isValid = false
            } else {
                try {
                    val amount = amountText.toDouble()
                    if (amount <= 0) {
                        tilAmount.error = "Amount must be greater than 0"
                        isValid = false
                    } else {
                        tilAmount.error = null
                    }
                } catch (e: NumberFormatException) {
                    tilAmount.error = "Invalid amount format"
                    isValid = false
                }
            }

            // Validate reason
            val reason = etReason.text.toString().trim()
            if (reason.isEmpty()) {
                tilReason.error = "Reason is required"
                isValid = false
            } else {
                tilReason.error = null
            }

            // Validate branch selection
            if (spinnerBranch.selectedItemPosition == -1 || branches.isEmpty()) {
                android.widget.Toast.makeText(context, "Please select a branch", android.widget.Toast.LENGTH_SHORT).show()
                isValid = false
            }

            return isValid
        } catch (e: Exception) {
            android.util.Log.e("AddExpenseDialog", "Error validating inputs: ${e.message}")
            return false
        }
    }

    private fun addExpense() {
        try {
            val amount = etAmount.text.toString().toDouble()
            val reason = etReason.text.toString().trim()
            val type = spinnerType.selectedItem.toString()
            val branch = spinnerBranch.selectedItem.toString()
            
            // Get current user info
            val currentUserId = auth.currentUser?.uid ?: ""
            val currentUserName = currentUser?.name ?: auth.currentUser?.displayName ?: "Unknown User"

            val expense = Expense(
                id = "",
                title = reason, // Use reason as title
                amount = amount,
                type = type,
                category = "Manual Entry",
                description = reason,
                branch = branch,
                date = Timestamp.now(),
                createdBy = currentUserId,
                createdByName = currentUserName,
                isAutoCalculated = false
            )

            // Save to Firebase
            db.collection("expenses")
                .add(expense)
                .addOnSuccessListener { documentReference ->
                    try {
                        val expenseWithId = expense.copy(id = documentReference.id)
                        onExpenseAdded?.invoke(expenseWithId)
                        
                        android.widget.Toast.makeText(
                            context,
                            "$type added successfully",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                        
                        dismiss()
                        
                        android.util.Log.d("AddExpenseDialog", "Expense added successfully with ID: ${documentReference.id}")
                    } catch (e: Exception) {
                        android.util.Log.e("AddExpenseDialog", "Error processing success: ${e.message}")
                    }
                }
                .addOnFailureListener { e ->
                    android.util.Log.e("AddExpenseDialog", "Error adding expense: ${e.message}")
                    android.widget.Toast.makeText(
                        context,
                        "Error adding expense: ${e.message}",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }

        } catch (e: Exception) {
            android.util.Log.e("AddExpenseDialog", "Error adding expense: ${e.message}")
            android.widget.Toast.makeText(
                context,
                "Error adding expense",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }
} 