package com.example.double_dot_demo.models

import com.google.firebase.Timestamp

data class Employee(
    val id: String = "",
    val name: String = "",
    val email: String = "",
    val role: String = "", // head_coach, coach, admin
    val phone: String = "",
    val hireDate: Timestamp? = null,
    val salary: Int = 0,
    val status: String = "active", // active, inactive
    val createdAt: Timestamp = Timestamp.now(),
    val updatedAt: Timestamp = Timestamp.now()
) 