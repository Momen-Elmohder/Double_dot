package com.example.double_dot_demo.models

import com.google.firebase.Timestamp

data class Expense(
    val id: String = "",
    val title: String = "",
    val amount: Double = 0.0,
    val category: String = "",
    val description: String = "",
    val date: Timestamp = Timestamp.now(),
    val month: String = "", // Format: "YYYY-MM" (e.g., "2024-01")
    val year: Int = 0,
    val createdBy: String = "",
    val createdAt: Timestamp = Timestamp.now(),
    val updatedAt: Timestamp = Timestamp.now()
) 