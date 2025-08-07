package com.example.double_dot_demo.models

import com.google.firebase.Timestamp

data class Employee(
    var id: String = "",
    val name: String = "",
    val email: String = "",
    val role: String = "", // head_coach, coach, admin
    val phone: String = "",
    val branch: String = "",
    val totalDays: Int = 0,
    val remainingDays: Int = 0,
    val status: String = "active", // active, inactive
    val attendanceDays: Map<String, Boolean> = emptyMap(), // day_1, day_2, etc. -> true (present) / false (absent)
    val createdAt: Timestamp = Timestamp.now(),
    val updatedAt: Timestamp = Timestamp.now()
) 