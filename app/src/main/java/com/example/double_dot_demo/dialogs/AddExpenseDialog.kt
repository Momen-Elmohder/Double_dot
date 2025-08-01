package com.example.double_dot_demo.dialogs

import android.content.Context
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.example.double_dot_demo.R
import com.example.double_dot_demo.databinding.DialogAddExpenseBinding
import com.example.double_dot_demo.models.Expense
import com.google.firebase.Timestamp
import java.util.*

class AddExpenseDialog(private val context: Context) {
    private lateinit var binding: DialogAddExpenseBinding
    private lateinit var dialog: AlertDialog
    
    private var onExpenseAddedListener: ((Expense) -> Unit)? = null
    
    fun setOnExpenseAddedListener(listener: (Expense) -> Unit) {
        onExpenseAddedListener = listener
    }
    
    fun show() {
        binding = DialogAddExpenseBinding.inflate(LayoutInflater.from(context))
        
        setupViews()
        setupCategoryDropdown()
        
        dialog = AlertDialog.Builder(context)
            .setView(binding.root)
            .setCancelable(false)
            .create()
        
        dialog.show()
    }
    
    private fun setupViews() {
        binding.btnCancel.setOnClickListener {
            dialog.dismiss()
        }
        
        binding.btnAddExpense.setOnClickListener {
            if (validateInputs()) {
                createExpense()
            }
        }
    }
    
    private fun setupCategoryDropdown() {
        val categories = listOf(
            "Equipment", "Utilities", "Rent", "Salaries", 
            "Marketing", "Insurance", "Maintenance", "Other"
        )
        
        val adapter = ArrayAdapter(context, android.R.layout.simple_dropdown_item_1line, categories)
        binding.actvCategory.setAdapter(adapter)
    }
    
    private fun validateInputs(): Boolean {
        val title = binding.etTitle.text.toString().trim()
        val amountText = binding.etAmount.text.toString().trim()
        val category = binding.actvCategory.text.toString().trim()
        val description = binding.etDescription.text.toString().trim()
        
        if (title.isEmpty()) {
            binding.tilTitle.error = "Title is required"
            return false
        }
        
        if (amountText.isEmpty()) {
            binding.tilAmount.error = "Amount is required"
            return false
        }
        
        val amount = amountText.toDoubleOrNull()
        if (amount == null || amount <= 0) {
            binding.tilAmount.error = "Please enter a valid amount"
            return false
        }
        
        if (category.isEmpty()) {
            binding.tilCategory.error = "Please select a category"
            return false
        }
        
        return true
    }
    
    private fun createExpense() {
        val title = binding.etTitle.text.toString().trim()
        val amount = binding.etAmount.text.toString().trim().toDouble()
        val category = binding.actvCategory.text.toString().trim()
        val description = binding.etDescription.text.toString().trim()
        
        val expense = Expense(
            title = title,
            amount = amount,
            category = category,
            description = description,
            date = Timestamp.now()
        )
        
        onExpenseAddedListener?.invoke(expense)
        dialog.dismiss()
    }
} 