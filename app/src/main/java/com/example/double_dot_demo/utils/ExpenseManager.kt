package com.example.double_dot_demo.utils

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class ExpenseManager {
    private val firestore by lazy { FirebaseFirestore.getInstance() }
    private val monthFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())

    suspend fun ensureMonthlyRolloverIfNeeded(): Boolean {
        return try {
            val serverDate = ServerTime.now()
            val cal = Calendar.getInstance().apply { time = serverDate }
            val currentMonth = monthFormat.format(cal.time)
            val docRef = firestore.collection("expense_months").document(currentMonth)
            val snap = docRef.get().await()
            if (!snap.exists()) {
                val data = hashMapOf(
                    "month" to currentMonth,
                    "createdAt" to Timestamp.now()
                )
                docRef.set(data).await()
                true
            } else { false }
        } catch (e: Exception) {
            android.util.Log.e("ExpenseManager", "ensureMonthlyRolloverIfNeeded failed: ${e.message}")
            false
        }
    }

    suspend fun getAvailableMonths(limit: Int = 18): List<String> {
        return try {
            val query = firestore.collection("expense_months").get().await()
            val months = query.documents.mapNotNull { it.getString("month") }
            val sorted = months.sortedByDescending { parseMonth(it) }
            if (sorted.isEmpty()) generateFallbackMonths(limit) else sorted
        } catch (e: Exception) {
            android.util.Log.e("ExpenseManager", "getAvailableMonths error: ${e.message}")
            generateFallbackMonths(limit)
        }
    }

    private fun parseMonth(monthLabel: String): Long {
        return try { monthFormat.parse(monthLabel)?.time ?: 0L } catch (_: Exception) { 0L }
    }

    private fun generateFallbackMonths(limit: Int): List<String> {
        val list = mutableListOf<String>()
        val serverNow = java.util.Date()
        val cal = Calendar.getInstance().apply { time = serverNow }
        repeat(limit) {
            list.add(monthFormat.format(cal.time))
            cal.add(Calendar.MONTH, -1)
        }
        return list
    }
}
