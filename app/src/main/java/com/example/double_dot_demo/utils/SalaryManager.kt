package com.example.double_dot_demo.utils

import android.util.Log
import com.example.double_dot_demo.models.*
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.util.*

class SalaryManager {
    private val db = FirebaseFirestore.getInstance()
    
    companion object {
        private const val TAG = "SalaryManager"
        private const val SALARIES_COLLECTION = "salaries"
        private const val ADMIN_BASE_SALARY = 2000.0
        private const val COACH_BASE_SALARY = 1500.0
        private const val HEAD_COACH_BASE_SALARY = 2500.0
        
        private fun getMonthName(month: Int): String {
            return when (month) {
                0 -> "January"
                1 -> "February"
                2 -> "March"
                3 -> "April"
                4 -> "May"
                5 -> "June"
                6 -> "July"
                7 -> "August"
                8 -> "September"
                9 -> "October"
                10 -> "November"
                11 -> "December"
                else -> "Unknown"
            }
        }
    }
    
    private suspend fun serverMonthKey(): String {
        val date = ServerTime.now()
        val cal = Calendar.getInstance().apply { time = date }
        return String.format("%04d-%02d", cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1)
    }

    suspend fun createSalaryForEmployee(employee: Employee): Boolean {
        return try {
            val month = serverMonthKey()
            val year = month.substring(0, 4).toInt()
            
            // Only create salary if employee has trainees
            val trainees = db.collection("trainees")
                .whereEqualTo("coachId", employee.id)
                .whereEqualTo("status", "active")
                .get()
                .await()
                .toObjects(Trainee::class.java)
            
            // No base salary - only trainee payments count
            val baseSalary = 0.0
            
            val salary = Salary(
                employeeId = employee.id,
                employeeName = employee.name,
                role = employee.role,
                branch = employee.branch,
                month = month,
                year = year,
                baseSalary = baseSalary,
                finalSalary = baseSalary,
                createdAt = Timestamp.now()
            )
            
            db.collection(SALARIES_COLLECTION).add(salary).await()
            Log.d(TAG, "Created salary record for ${employee.name} with base salary: $baseSalary")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error creating salary for employee: ${e.message}")
            false
        }
    }
    
    // Function to recalculate salary when trainee is added/renewed
    suspend fun recalculateSalaryForCoach(coachId: String): Boolean {
        return try {
            val month = serverMonthKey()
            
            Log.d(TAG, "=== RECALCULATING SALARY FOR COACH: $coachId ===")
            
            // Get the coach
            val coachDoc = db.collection("employees").document(coachId).get().await()
            if (!coachDoc.exists()) {
                Log.e(TAG, "Coach with ID $coachId not found")
                return false
            }
            
            val coach = coachDoc.toObject(Employee::class.java)?.copy(id = coachId)
            if (coach == null) {
                Log.e(TAG, "Failed to parse coach data")
                return false
            }
            
            Log.d(TAG, "Found coach: ${coach.name}, Role: ${coach.role}, TotalDays: ${coach.totalDays}")
            
            // Get all trainees for this coach - check both coachId and coachName
            val traineesSnapshot = db.collection("trainees")
                .whereEqualTo("coachId", coachId)
                .get()
                .await()
            
            val trainees = traineesSnapshot.documents.mapNotNull { doc ->
                doc.toObject(Trainee::class.java)?.copy(id = doc.id)
            }.filter { it.status in listOf("active", "academy", "team", "academy and Preparatonal") }
            
            Log.d(TAG, "Found ${trainees.size} active trainees for coach ${coach.name}")
            trainees.forEach { trainee ->
                Log.d(TAG, "Trainee: ${trainee.name}, CoachID: ${trainee.coachId}, CoachName: ${trainee.coachName}, Payment: ${trainee.paymentAmount}")
            }
            
            // Calculate salary with current trainees
            calculateEmployeeSalary(coach, trainees, month)
            
            Log.d(TAG, "=== SALARY RECALCULATION COMPLETED ===")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error recalculating salary for coach: ${e.message}")
            e.printStackTrace()
            false
        }
    }
    
    suspend fun calculateMonthlySalaries(): Boolean {
        return try {
            val month = serverMonthKey()
            
            Log.d(TAG, "Starting salary calculation for month: $month")
            
            // Get all active employees
            val employeesSnapshot = db.collection("employees")
                .whereEqualTo("status", "active")
                .get()
                .await()
            
            val employees = employeesSnapshot.documents.mapNotNull { doc ->
                doc.toObject(Employee::class.java)?.copy(id = doc.id)
            }
            
            Log.d(TAG, "Found ${employees.size} active employees")
            
            // Get all trainees
            val traineesSnapshot = db.collection("trainees")
                .get()
                .await()
            
            val trainees = traineesSnapshot.documents.mapNotNull { doc ->
                doc.toObject(Trainee::class.java)?.copy(id = doc.id)
            }
            
            Log.d(TAG, "Found ${trainees.size} total trainees")
            
            for (employee in employees) {
                calculateEmployeeSalary(employee, trainees, month)
            }
            
            Log.d(TAG, "Salary calculation completed for $month")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating monthly salaries: ${e.message}")
            false
        }
    }
    
    private suspend fun calculateEmployeeSalary(employee: Employee, allTrainees: List<Trainee>, month: String, year: Int = month.substring(0,4).toInt()) {
        try {
            Log.d(TAG, "Calculating salary for ${employee.name} (ID: ${employee.id}) for month $month")
            
            // Handle different employee roles
            when (employee.role.lowercase()) {
                "admin" -> calculateAdminSalary(employee, month, year)
                "coach", "head coach" -> calculateCoachSalary(employee, allTrainees, month, year)
                else -> {
                    Log.d(TAG, "Unknown role for ${employee.name}: ${employee.role}")
                    calculateCoachSalary(employee, allTrainees, month, year) // Default to coach calculation
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating salary for ${employee.name}: ${e.message}")
        }
    }
    
    private suspend fun calculateAdminSalary(employee: Employee, month: String, year: Int) {
        try {
            Log.d(TAG, "Calculating ADMIN salary for ${employee.name}")
            
            // Admin gets fixed salary of 2000
            val baseSalary = ADMIN_BASE_SALARY
            val totalPayments = baseSalary
            
            // Use actual working days from employee record, default to 30 if not set
            val totalWorkingDays = if (employee.totalDays > 0) employee.totalDays else 30
            val presentDays = employee.attendanceDays.values.count { it }
            val absenceDays = if (employee.attendanceDays.isEmpty()) 0 else (totalWorkingDays - presentDays)
            val absencePercentage = if (totalWorkingDays > 0) (absenceDays.toDouble() / totalWorkingDays.toDouble()) * 100 else 0.0
            
            // Deduct from base salary for admin
            val absenceDeduction = (baseSalary * absencePercentage) / 100
            
            Log.d(TAG, "${employee.name} (ADMIN) - Attendance Details:")
            Log.d(TAG, "  Total Working Days: $totalWorkingDays")
            Log.d(TAG, "  Present Days: $presentDays")
            Log.d(TAG, "  Absence Days: $absenceDays")
            Log.d(TAG, "  Absence Percentage: ${String.format("%.1f", absencePercentage)}%")
            Log.d(TAG, "  Base Salary: $baseSalary")
            Log.d(TAG, "  Absence Deduction: $absenceDeduction")
            
            // Create deduction details
            val deductionDetails = mutableListOf<DeductionDetail>()
            if (absenceDeduction > 0) {
                deductionDetails.add(
                    DeductionDetail(
                        type = "ABSENCE",
                        description = "$absenceDays absence days (${String.format("%.1f", absencePercentage)}%)",
                        amount = absenceDeduction,
                        date = Timestamp.now()
                    )
                )
            }
            
            val totalDeduction = deductionDetails.sumOf { it.amount }
            val finalSalary = totalPayments - totalDeduction
            
            Log.d(TAG, "${employee.name} (ADMIN) - Final calculation: Base: $baseSalary, Deductions: $totalDeduction, Final: $finalSalary")
            
            // Check if salary record exists for this month
            val existingSalary = db.collection(SALARIES_COLLECTION)
                .whereEqualTo("employeeId", employee.id)
                .whereEqualTo("month", month)
                .get()
                .await()
            
            val salary = Salary(
                employeeId = employee.id,
                employeeName = employee.name,
                role = employee.role,
                branch = employee.branch,
                month = month,
                year = year,
                baseSalary = baseSalary,
                totalTrainees = 0, // Admin has no trainees
                traineeDetails = emptyList(),
                totalPayments = totalPayments,
                absenceDays = absenceDays,
                totalWorkingDays = totalWorkingDays,
                absencePercentage = absencePercentage,
                deductionAmount = totalDeduction,
                deductionDetails = deductionDetails,
                finalSalary = finalSalary,
                calculatedAt = Timestamp.now()
            )
            
            if (existingSalary.isEmpty) {
                // Create new salary record
                val docRef = db.collection(SALARIES_COLLECTION).add(salary).await()
                Log.d(TAG, "Created new ADMIN salary record for ${employee.name} - Month: $month, DocID: ${docRef.id}")
            } else {
                // Update existing salary record
                val docId = existingSalary.documents[0].id
                db.collection(SALARIES_COLLECTION).document(docId).set(salary.copy(id = docId)).await()
                Log.d(TAG, "Updated ADMIN salary record for ${employee.name} - Month: $month, DocID: $docId")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating admin salary for ${employee.name}: ${e.message}")
        }
    }
    
    private suspend fun calculateCoachSalary(employee: Employee, allTrainees: List<Trainee>, month: String, year: Int) {
        try {
            Log.d(TAG, "Calculating COACH salary for ${employee.name}")
            
            // Get employee's trainees - check both coachId and coachName for better matching
            val employeeTrainees = allTrainees.filter { trainee ->
                (trainee.coachId == employee.id || trainee.coachName == employee.name) && 
                trainee.status in listOf("active", "academy", "team", "academy and Preparatonal")
            }
            Log.d(TAG, "${employee.name} has ${employeeTrainees.size} active trainees")
            
            // Log trainee details for debugging
            employeeTrainees.forEach { trainee ->
                Log.d(TAG, "Trainee: ${trainee.name}, CoachID: ${trainee.coachId}, CoachName: ${trainee.coachName}, Payment: ${trainee.paymentAmount}")
            }
            
            // Calculate total payments from trainees (coach gets 40% of trainee fees)
            val totalTraineeFees = employeeTrainees.sumOf { it.paymentAmount }
            val totalPayments = totalTraineeFees * 0.4 // Coach gets 40% of trainee fees
            Log.d(TAG, "${employee.name} total trainee fees: $totalTraineeFees, coach payment (40%): $totalPayments")
            
            // No fixed base salary - only trainee payments count
            val baseSalary = 0.0
            Log.d(TAG, "${employee.name} base salary: $baseSalary (only trainee payments count)")
            
            // Use actual working days from employee record, default to 30 if not set
            val totalWorkingDays = if (employee.totalDays > 0) employee.totalDays else 30
            val presentDays = employee.attendanceDays.values.count { it }
            val absenceDays = if (employee.attendanceDays.isEmpty()) 0 else (totalWorkingDays - presentDays)
            
            Log.d(TAG, "${employee.name} - Attendance Data Check:")
            Log.d(TAG, "  Attendance Days Map: ${employee.attendanceDays}")
            Log.d(TAG, "  Attendance Days Empty: ${employee.attendanceDays.isEmpty()}")
            Log.d(TAG, "  Attendance Days Size: ${employee.attendanceDays.size}")
            val absencePercentage = if (totalWorkingDays > 0) (absenceDays.toDouble() / totalWorkingDays.toDouble()) * 100 else 0.0
            // Deduct from trainee payments instead of base salary
            val absenceDeduction = (totalPayments * absencePercentage) / 100
            
            Log.d(TAG, "${employee.name} - Attendance Details:")
            Log.d(TAG, "  Total Working Days: $totalWorkingDays")
            Log.d(TAG, "  Present Days: $presentDays")
            Log.d(TAG, "  Absence Days: $absenceDays")
            Log.d(TAG, "  Absence Percentage: ${String.format("%.1f", absencePercentage)}%")
            Log.d(TAG, "  Total Payments: $totalPayments")
            Log.d(TAG, "  Absence Deduction: $absenceDeduction")
            
            // Create trainee details (show 40% of each trainee's fee)
            val traineeDetails = employeeTrainees.map { trainee ->
                TraineeDetail(
                    traineeId = trainee.id,
                    traineeName = trainee.name,
                    monthlyFee = trainee.paymentAmount * 0.4, // Coach gets 40% of trainee fee
                    paymentDate = Timestamp.now()
                )
            }
            
            // Create deduction details
            val deductionDetails = mutableListOf<DeductionDetail>()
            if (absenceDeduction > 0) {
                deductionDetails.add(
                    DeductionDetail(
                        type = "ABSENCE",
                        description = "$absenceDays absence days (${String.format("%.1f", absencePercentage)}%)",
                        amount = absenceDeduction,
                        date = Timestamp.now()
                    )
                )
                Log.d(TAG, "${employee.name} - Added absence deduction: $absenceDeduction")
            } else {
                Log.d(TAG, "${employee.name} - No absence deduction (amount: $absenceDeduction)")
            }
            
            val totalDeduction = deductionDetails.sumOf { it.amount }
            // Final salary is trainee payments minus deductions (no base salary)
            val finalSalary = totalPayments - totalDeduction
            
            Log.d(TAG, "${employee.name} - Final calculation: Base: $baseSalary, Deductions: $totalDeduction, Final: $finalSalary")
            
            // Check if salary record exists for this month
            val existingSalary = db.collection(SALARIES_COLLECTION)
                .whereEqualTo("employeeId", employee.id)
                .whereEqualTo("month", month)
                .get()
                .await()
            
            val salary = Salary(
                employeeId = employee.id,
                employeeName = employee.name,
                role = employee.role,
                branch = employee.branch,
                month = month,
                year = year,
                baseSalary = baseSalary,
                totalTrainees = employeeTrainees.size,
                traineeDetails = traineeDetails,
                totalPayments = totalPayments,
                absenceDays = absenceDays,
                totalWorkingDays = totalWorkingDays,
                absencePercentage = absencePercentage,
                deductionAmount = totalDeduction,
                deductionDetails = deductionDetails,
                finalSalary = finalSalary,
                calculatedAt = Timestamp.now()
            )
            
            if (existingSalary.isEmpty) {
                // Create new salary record
                val docRef = db.collection(SALARIES_COLLECTION).add(salary).await()
                Log.d(TAG, "Created new salary record for ${employee.name} - Month: $month, DocID: ${docRef.id}")
            } else {
                // Update existing salary record
                val docId = existingSalary.documents[0].id
                db.collection(SALARIES_COLLECTION).document(docId).set(salary.copy(id = docId)).await()
                Log.d(TAG, "Updated salary record for ${employee.name} - Month: $month, DocID: $docId")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating coach salary for ${employee.name}: ${e.message}")
        }
    }
    
    suspend fun getSalaryDetails(employeeId: String, month: String): Salary? {
        return try {
            val result = db.collection(SALARIES_COLLECTION)
                .whereEqualTo("employeeId", employeeId)
                .whereEqualTo("month", month)
                .get()
                .await()
            
            if (!result.isEmpty) {
                val salary = result.documents[0].toObject(Salary::class.java)
                salary?.copy(id = result.documents[0].id)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting salary details: ${e.message}")
            null
        }
    }
    
    suspend fun generateMonthlyReport(month: String): List<Salary> {
        return try {
            val result = db.collection(SALARIES_COLLECTION)
                .whereEqualTo("month", month)
                .get()
                .await()
            
            val salaries = result.documents.mapNotNull { doc ->
                doc.toObject(Salary::class.java)?.copy(id = doc.id)
            }
            
            Log.d(TAG, "Generated monthly report for $month: ${salaries.size} salary records")
            salaries.forEach { salary ->
                Log.d(TAG, "Salary: ${salary.employeeName}, Trainees: ${salary.totalTrainees}, Final: ${salary.finalSalary}")
            }
            
            salaries
        } catch (e: Exception) {
            Log.e(TAG, "Error generating monthly report: ${e.message}")
            emptyList()
        }
    }
    
    suspend fun getAllSalaries(): List<Salary> {
        return try {
            val result = db.collection(SALARIES_COLLECTION)
                .orderBy("month", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .get()
                .await()
            
            val salaries = result.documents.mapNotNull { doc ->
                doc.toObject(Salary::class.java)?.copy(id = doc.id)
            }
            
            // Sort in memory: month desc, then employeeName asc
            val sorted = salaries.sortedWith(compareByDescending<Salary> { it.month }.thenBy { it.employeeName })
            
            Log.d(TAG, "Loaded all salaries: ${sorted.size} total records")
            sorted.forEach { salary ->
                Log.d(TAG, "Salary: ${salary.employeeName} - ${salary.month}, Final: ${salary.finalSalary}")
            }
            
            sorted
        } catch (e: Exception) {
            Log.e(TAG, "Error loading all salaries: ${e.message}")
            emptyList()
        }
    }
    
    suspend fun performMonthlyRolloverIfNeeded(): Boolean {
        return try {
            val monthKey = serverMonthKey()
            val dayOfMonth = Calendar.getInstance().get(Calendar.DAY_OF_MONTH)

            // If current month salaries already exist, nothing to do
            val existingCurrent = db.collection(SALARIES_COLLECTION)
                .whereEqualTo("month", monthKey)
                .limit(1)
                .get()
                .await()
            if (!existingCurrent.isEmpty) {
                Log.d(TAG, "Monthly rollover not needed. Salaries already exist for $monthKey")
                return false
            }

            // Prefer to run on or after the 1st. If the app wasn't opened on the 1st,
            // we still proceed the first time it opens in the new month.
            Log.d(TAG, "No salaries found for $monthKey (day=$dayOfMonth). Triggering monthly calculation...")
            val ok = calculateMonthlySalaries()
            clearAllCoachAttendance()
            ok
        } catch (e: Exception) {
            Log.e(TAG, "Error during performMonthlyRolloverIfNeeded: ${e.message}")
            false
        }
    }
    
    suspend fun getAvailableMonths(): List<String> {
        return try {
            val result = db.collection(SALARIES_COLLECTION)
                .orderBy("month", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .get()
                .await()
            result.documents.mapNotNull { it.getString("month") }.distinct()
        } catch (e: Exception) {
            Log.e(TAG, "Error loading available months: ${e.message}")
            emptyList()
        }
    }

    suspend fun getSalariesForMonth(monthKey: String): List<Salary> {
        return try {
            generateMonthlyReport(monthKey)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading salaries for $monthKey: ${e.message}")
            emptyList()
        }
    }
    
    // Debug function to check database state
    suspend fun debugDatabaseState() {
        try {
            Log.d(TAG, "=== DEBUG DATABASE STATE ===")
            
            // Check employees
            val employees = db.collection("employees").get().await()
            Log.d(TAG, "Total employees in DB: ${employees.size()}")
            employees.documents.forEach { doc ->
                val employee = doc.toObject(Employee::class.java)
                Log.d(TAG, "Employee: ${employee?.name}, ID: ${doc.id}, Role: ${employee?.role}, Status: ${employee?.status}")
            }
            
            // Check trainees
            val trainees = db.collection("trainees").get().await()
            Log.d(TAG, "Total trainees in DB: ${trainees.size()}")
            trainees.documents.forEach { doc ->
                val trainee = doc.toObject(Trainee::class.java)
                Log.d(TAG, "Trainee: ${trainee?.name}, CoachID: ${trainee?.coachId}, CoachName: ${trainee?.coachName}, Status: ${trainee?.status}")
            }
            
            // Check salaries
            val salaries = db.collection(SALARIES_COLLECTION).get().await()
            Log.d(TAG, "Total salaries in DB: ${salaries.size()}")
            salaries.documents.forEach { doc ->
                val salary = doc.toObject(Salary::class.java)
                Log.d(TAG, "Salary: ${salary?.employeeName}, Month: ${salary?.month}, Final: ${salary?.finalSalary}, ID: ${doc.id}")
            }
            
            Log.d(TAG, "=== END DEBUG ===")
        } catch (e: Exception) {
            Log.e(TAG, "Error in debugDatabaseState: ${e.message}")
        }
    }

    private suspend fun clearAllCoachAttendance() {
        try {
            val snap = db.collection("employees").whereIn("role", listOf("coach", "admin")).get().await()
            for (doc in snap.documents) {
                db.collection("employees").document(doc.id).update("attendanceDays", emptyMap<String, Boolean>()).await()
            }
        } catch (_: Exception) {}
    }

    // Production code only beyond this point (test helpers removed)
}
