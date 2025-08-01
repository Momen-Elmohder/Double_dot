package com.example.double_dot_demo.models

import com.google.firebase.Timestamp

data class MonthlyExpense(
    val id: String = "",
    val month: String = "", // Format: "YYYY-MM" (e.g., "2024-01")
    val year: Int = 0,
    val totalAmount: Double = 0.0,
    val expenseCount: Int = 0,
    val categories: Map<String, Double> = mapOf(), // Category -> Total Amount
    val archivedAt: Timestamp = Timestamp.now(),
    val archivedBy: String = ""
) 