package com.example.double_dot_demo.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.double_dot_demo.R
import com.example.double_dot_demo.dialogs.AddExpenseDialog
import com.example.double_dot_demo.models.Expense
import com.example.double_dot_demo.models.MonthlyExpense
import com.google.android.material.button.MaterialButton
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

class ExpensesFragment : Fragment() {
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    
    private var currentMonth = ""
    private var currentYear = 0
    private var totalAmount = 0.0
    private var expenseCount = 0
    
    companion object {
        fun newInstance(): ExpensesFragment {
            return ExpensesFragment()
        }
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_expenses, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        try {
            setupCurrentMonth()
            setupButtons()
            loadCurrentMonthData()
        } catch (e: Exception) {
            Toast.makeText(context, "Error setting up expenses: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun setupCurrentMonth() {
        val calendar = Calendar.getInstance()
        currentYear = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH) + 1
        currentMonth = String.format("%04d-%02d", currentYear, month)
        
        val monthNames = arrayOf(
            "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December"
        )
        
        view?.findViewById<TextView>(R.id.tvCurrentMonth)?.text = "${monthNames[month - 1]} $currentYear"
    }
    
    private fun setupButtons() {
        view?.findViewById<MaterialButton>(R.id.btnAddExpense)?.setOnClickListener {
            showAddExpenseDialog()
        }
        
        view?.findViewById<MaterialButton>(R.id.btnArchiveMonth)?.setOnClickListener {
            archiveCurrentMonth()
        }
    }
    
    private fun loadCurrentMonthData() {
        firestore.collection("expenses")
            .whereEqualTo("month", currentMonth)
            .get()
            .addOnSuccessListener { documents ->
                totalAmount = 0.0
                expenseCount = documents.size()
                
                for (document in documents) {
                    val amount = document.getDouble("amount") ?: 0.0
                    totalAmount += amount
                }
                
                updateUI()
            }
            .addOnFailureListener { e ->
                Toast.makeText(context, "Failed to load expenses: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
    
    private fun updateUI() {
        view?.findViewById<TextView>(R.id.tvTotalAmount)?.text = String.format("$%.2f", totalAmount)
        view?.findViewById<TextView>(R.id.tvExpenseCount)?.text = expenseCount.toString()
    }
    
    private fun showAddExpenseDialog() {
        val dialog = AddExpenseDialog(requireContext())
        dialog.setOnExpenseAddedListener { expense ->
            addExpenseToFirestore(expense)
        }
        dialog.show()
    }
    
    private fun addExpenseToFirestore(expense: Expense) {
        val expenseData = hashMapOf<String, Any>(
            "title" to expense.title,
            "amount" to expense.amount,
            "category" to expense.category,
            "description" to expense.description,
            "date" to expense.date,
            "month" to currentMonth,
            "year" to currentYear,
            "createdBy" to (auth.currentUser?.uid ?: ""),
            "createdAt" to Timestamp.now(),
            "updatedAt" to Timestamp.now()
        )
        
        firestore.collection("expenses")
            .add(expenseData)
            .addOnSuccessListener { documentReference ->
                Toast.makeText(context, "Expense added successfully!", Toast.LENGTH_SHORT).show()
                loadCurrentMonthData()
            }
            .addOnFailureListener { e ->
                Toast.makeText(context, "Failed to add expense: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
    
    private fun archiveCurrentMonth() {
        if (expenseCount == 0) {
            Toast.makeText(context, "No expenses to archive for this month", Toast.LENGTH_SHORT).show()
            return
        }
        
        val monthlyExpense = MonthlyExpense(
            id = currentMonth,
            month = currentMonth,
            year = currentYear,
            totalAmount = totalAmount,
            expenseCount = expenseCount,
            archivedAt = Timestamp.now(),
            archivedBy = auth.currentUser?.uid ?: ""
        )
        
        firestore.collection("monthly_expenses")
            .document(currentMonth)
            .set(monthlyExpense)
            .addOnSuccessListener {
                deleteCurrentMonthExpenses()
            }
            .addOnFailureListener { e ->
                Toast.makeText(context, "Failed to archive month: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
    
    private fun deleteCurrentMonthExpenses() {
        firestore.collection("expenses")
            .whereEqualTo("month", currentMonth)
            .get()
            .addOnSuccessListener { documents ->
                val batch = firestore.batch()
                
                for (document in documents) {
                    batch.delete(document.reference)
                }
                
                batch.commit()
                    .addOnSuccessListener {
                        Toast.makeText(context, "Month archived successfully!", Toast.LENGTH_SHORT).show()
                        loadCurrentMonthData()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(context, "Failed to delete expenses: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener { e ->
                Toast.makeText(context, "Failed to get expenses: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}

