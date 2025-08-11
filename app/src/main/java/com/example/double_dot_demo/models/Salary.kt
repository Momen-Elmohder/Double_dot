package com.example.double_dot_demo.models

import com.google.firebase.Timestamp

data class Salary(
    var id: String = "",
    val employeeId: String = "",
    val employeeName: String = "",
    val role: String = "",
    val branch: String = "",
    val month: String = "", // Format: "2024-01"
    val year: Int = 0,
    val baseSalary: Double = 0.0,
    val totalTrainees: Int = 0,
    val traineeDetails: List<TraineeDetail> = emptyList(),
    val totalPayments: Double = 0.0, // From trainees
    val absenceDays: Int = 0,
    val totalWorkingDays: Int = 0,
    val absencePercentage: Double = 0.0,
    val deductionAmount: Double = 0.0,
    val deductionDetails: List<DeductionDetail> = emptyList(),
    val finalSalary: Double = 0.0,
    val isPaid: Boolean = false,
    val createdAt: Timestamp = Timestamp.now(),
    val updatedAt: Timestamp = Timestamp.now(),
    val calculatedAt: Timestamp = Timestamp.now()
) {
    // Required empty constructor for Firestore
    constructor() : this("", "", "", "", "", "", 0, 0.0, 0, emptyList(), 0.0, 0, 0, 0.0, 0.0, emptyList(), 0.0, false, Timestamp.now(), Timestamp.now(), Timestamp.now())
}

data class TraineeDetail(
    val traineeId: String = "",
    val traineeName: String = "",
    val monthlyFee: Double = 0.0,
    val paymentDate: Timestamp? = null
) {
    constructor() : this("", "", 0.0, null)
}

data class DeductionDetail(
    val type: String = "", // "ABSENCE", "PENALTY", "OTHER"
    val description: String = "",
    val amount: Double = 0.0,
    val date: Timestamp? = null
) {
    constructor() : this("", "", 0.0, null)
}

data class SalaryInfo(
    val totalPayments: Double = 0.0,
    val baseSalary: Double = 0.0,
    val absencePercentage: Double = 0.0,
    val deductionAmount: Double = 0.0,
    val finalSalary: Double = 0.0,
    val traineeCount: Int = 0,
    val traineeDetails: List<TraineeDetail> = emptyList(),
    val deductionDetails: List<DeductionDetail> = emptyList()
)



