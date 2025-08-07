package com.example.double_dot_demo.models

import com.google.firebase.Timestamp

data class Trainee(
    var id: String = "",
    val name: String = "",
    val age: Int = 0,
    val phoneNumber: String = "",
    val branch: String = "",
    var coachId: String = "",
    val coachName: String = "",
    val monthlyFee: Int = 0,
    val paymentAmount: Double = 0.0,
    val isPaid: Boolean = false,
    val status: String = "active", // active, inactive, frozen, completed
    val totalSessions: Int = 0,
    val remainingSessions: Int = 0,
    var attendanceSessions: Map<String, Boolean> = mapOf(), // sessionId -> isPresent
    val lastRenewalDate: Timestamp? = null,
    var createdAt: Timestamp = Timestamp.now(),
    val updatedAt: Timestamp = Timestamp.now()
) 