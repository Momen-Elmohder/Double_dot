package com.example.double_dot_demo.models

import com.google.firebase.Timestamp

data class Trainee(
    val id: String = "",
    val name: String = "",
    val age: Int = 0,
    val startingDate: Timestamp? = null,
    val endingDate: Timestamp? = null,
    val coachId: String = "",
    val coachName: String = "",
    val monthlyFee: Int = 0,
    val isPaid: Boolean = false,
    val status: String = "active", // active, inactive, completed
    val createdAt: Timestamp = Timestamp.now(),
    val updatedAt: Timestamp = Timestamp.now()
) 