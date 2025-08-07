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

class EditExpenseDialog : DialogFragment() {

    private var onExpenseUpdated: ((Expense) -> Unit)? = null
    private var onExpenseDeleted: ((Expense) -> Unit)? = null
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    // UI Elements
    private lateinit var etAmount: EditText
    private lateinit var etReason: EditText
    private lateinit var spinnerType: Spinner
    private lateinit var spinnerBranch: Spinner
    private lateinit var btnUpdate: Button
    private lateinit var btnDelete: Button
    private lateinit var btnCancel: Button
    private lateinit var tilAmount: TextInputLayout
    private lateinit var tilReason: TextInputLayout
    private lateinit var tvAddedBy: TextView
    private lateinit var tvDate: TextView

    // Data lists
    private val branches = mutableListOf<String>()
    private var currentUser: Employee? = null
    private var expenseToEdit: Expense? = null

    companion object {
        fun newInstance(
            expense: Expense,
            onExpenseUpdated: (Expense) -> Unit,
            onExpenseDeleted: (Expense) -> Unit
        ): EditExpenseDialog {
            return EditExpenseDialog().apply {
                this.onExpenseUpdated = onExpenseUpdated
                this.onExpenseDeleted = onExpenseDeleted
                this.expenseToEdit = expense
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return try {
            val builder = AlertDialog.Builder(requireContext())
            val inflater = requireActivity().layoutInflater
            val view = inflater.inflate(R.layout.dialog_edit_expense, null)

            initializeViews(view)
            setupSpinners()
            setupButtons()
            loadData()
            populateFields()

            builder.setView(view)
                .setTitle("Edit Expense/Income")
                .setCancelable(false)

            builder.create()
        } catch (e: Exception) {
            android.util.Log.e("EditExpenseDialog", "Error creating dialog: ${e.message}")
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
            btnUpdate = view.findViewById(R.id.btnUpdate)
            btnDelete = view.findViewById(R.id.btnDelete)
            btnCancel = view.findViewById(R.id.btnCancel)
            tilAmount = view.findViewById(R.id.tilAmount)
            tilReason = view.findViewById(R.id.tilReason)
            tvAddedBy = view.findViewById(R.id.tvAddedBy)
            tvDate = view.findViewById(R.id.tvDate)

            android.util.Log.d("EditExpenseDialog", "Views initialized successfully")
        } catch (e: Exception) {
            android.util.Log.e("EditExpenseDialog", "Error initializing views: ${e.message}")
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

            android.util.Log.d("EditExpenseDialog", "Spinners setup completed")
        } catch (e: Exception) {
            android.util.Log.e("EditExpenseDialog", "Error setting up spinners: ${e.message}")
        }
    }

    private fun setupButtons() {
        try {
            btnUpdate.setOnClickListener {
                if (validateInputs()) {
                    updateExpense()
                }
            }

            btnDelete.setOnClickListener {
                deleteExpense()
            }

            btnCancel.setOnClickListener {
                dismiss()
            }

            android.util.Log.d("EditExpenseDialog", "Buttons setup completed")
        } catch (e: Exception) {
            android.util.Log.e("EditExpenseDialog", "Error setting up buttons: ${e.message}")
        }
    }

    private fun loadData() {
        try {
            loadBranches()
            loadCurrentUser()
            android.util.Log.d("EditExpenseDialog", "Data loading initiated")
        } catch (e: Exception) {
            android.util.Log.e("EditExpenseDialog", "Error loading data: ${e.message}")
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
                        
                        android.util.Log.d("EditExpenseDialog", "Loaded ${branches.size} branches")
                    } catch (e: Exception) {
                        android.util.Log.e("EditExpenseDialog", "Error processing branches: ${e.message}")
                    }
                }
                .addOnFailureListener { e ->
                    android.util.Log.e("EditExpenseDialog", "Error loading branches: ${e.message}")
                }
        } catch (e: Exception) {
            android.util.Log.e("EditExpenseDialog", "Error setting up branches listener: ${e.message}")
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
                                    android.util.Log.d("EditExpenseDialog", "Current user loaded: ${user.name}")
                                }
                            } else {
                                android.util.Log.d("EditExpenseDialog", "Using Firebase Auth display name")
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("EditExpenseDialog", "Error processing current user: ${e.message}")
                        }
                    }
                    .addOnFailureListener { e ->
                        android.util.Log.e("EditExpenseDialog", "Error loading current user: ${e.message}")
                    }
            } else {
                android.util.Log.w("EditExpenseDialog", "No current user found")
            }
        } catch (e: Exception) {
            android.util.Log.e("EditExpenseDialog", "Error setting up current user: ${e.message}")
        }
    }

    private fun populateFields() {
        try {
            expenseToEdit?.let { expense ->
                // Populate amount
                etAmount.setText(expense.amount.toString())
                
                // Populate reason
                etReason.setText(expense.title)
                
                // Set type spinner
                val typePosition = when (expense.type) {
                    "EXPENSE" -> 0
                    "INCOME" -> 1
                    else -> 0
                }
                spinnerType.setSelection(typePosition)
                
                // Set branch spinner
                val branchPosition = branches.indexOf(expense.branch)
                if (branchPosition >= 0) {
                    spinnerBranch.setSelection(branchPosition)
                }
                
                // Set added by
                tvAddedBy.text = "Added By: ${expense.createdByName}"
                
                // Set date
                val date = expense.date.toDate()
                tvDate.text = "Date: ${dateFormat.format(date)}"
                
                android.util.Log.d("EditExpenseDialog", "Fields populated successfully")
            }
        } catch (e: Exception) {
            android.util.Log.e("EditExpenseDialog", "Error populating fields: ${e.message}")
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
            android.util.Log.e("EditExpenseDialog", "Error validating inputs: ${e.message}")
            return false
        }
    }

    private fun updateExpense() {
        try {
            val amount = etAmount.text.toString().toDouble()
            val reason = etReason.text.toString().trim()
            val type = spinnerType.selectedItem.toString()
            val branch = spinnerBranch.selectedItem.toString()
            
            expenseToEdit?.let { originalExpense ->
                val updatedExpense = originalExpense.copy(
                    title = reason,
                    amount = amount,
                    type = type,
                    description = reason,
                    branch = branch,
                    updatedAt = Timestamp.now()
                )

                // Update in Firebase
                db.collection("expenses").document(originalExpense.id)
                    .update(
                        mapOf(
                            "title" to reason,
                            "amount" to amount,
                            "type" to type,
                            "description" to reason,
                            "branch" to branch,
                            "updatedAt" to Timestamp.now()
                        )
                    )
                    .addOnSuccessListener {
                        try {
                            onExpenseUpdated?.invoke(updatedExpense)
                            
                            android.widget.Toast.makeText(
                                context,
                                "Expense updated successfully",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                            
                            dismiss()
                            
                            android.util.Log.d("EditExpenseDialog", "Expense updated successfully")
                        } catch (e: Exception) {
                            android.util.Log.e("EditExpenseDialog", "Error processing success: ${e.message}")
                        }
                    }
                    .addOnFailureListener { e ->
                        android.util.Log.e("EditExpenseDialog", "Error updating expense: ${e.message}")
                        android.widget.Toast.makeText(
                            context,
                            "Error updating expense: ${e.message}",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
            }
        } catch (e: Exception) {
            android.util.Log.e("EditExpenseDialog", "Error updating expense: ${e.message}")
            android.widget.Toast.makeText(
                context,
                "Error updating expense",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun deleteExpense() {
        try {
            expenseToEdit?.let { expense ->
                // Show confirmation dialog
                AlertDialog.Builder(requireContext())
                    .setTitle("Delete Expense")
                    .setMessage("Are you sure you want to delete this expense?")
                    .setPositiveButton("Delete") { _, _ ->
                        performDelete(expense)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        } catch (e: Exception) {
            android.util.Log.e("EditExpenseDialog", "Error showing delete confirmation: ${e.message}")
        }
    }

    private fun performDelete(expense: Expense) {
        try {
            db.collection("expenses").document(expense.id)
                .delete()
                .addOnSuccessListener {
                    try {
                        onExpenseDeleted?.invoke(expense)
                        
                        android.widget.Toast.makeText(
                            context,
                            "Expense deleted successfully",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                        
                        dismiss()
                        
                        android.util.Log.d("EditExpenseDialog", "Expense deleted successfully")
                    } catch (e: Exception) {
                        android.util.Log.e("EditExpenseDialog", "Error processing delete success: ${e.message}")
                    }
                }
                .addOnFailureListener { e ->
                    android.util.Log.e("EditExpenseDialog", "Error deleting expense: ${e.message}")
                    android.widget.Toast.makeText(
                        context,
                        "Error deleting expense: ${e.message}",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
        } catch (e: Exception) {
            android.util.Log.e("EditExpenseDialog", "Error performing delete: ${e.message}")
        }
    }
} 