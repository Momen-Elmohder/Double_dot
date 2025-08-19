package com.example.double_dot_demo.utils

import android.util.Log
import com.example.double_dot_demo.models.Salary
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

object MonthFormatMigration {
    private const val TAG = "MonthFormatMigration"
    private val db = FirebaseFirestore.getInstance()
    private val monthFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
    private val oldMonthFormat = SimpleDateFormat("yyyy-MM", Locale.getDefault())
    
    /**
     * Migrate all salary records from old format (YYYY-MM) to new format (MMMM yyyy)
     * and merge duplicate entries for the same month
     */
    suspend fun migrateSalaryMonthFormats(): Boolean {
        return try {
            Log.d(TAG, "Starting salary month format migration...")
            
            // Get all salary records
            val allSalaries = db.collection("salaries").get().await()
            val salaryDocs = allSalaries.documents
            
            Log.d(TAG, "Found ${salaryDocs.size} salary records to process")
            
            // Group salaries by employee and month (normalized)
            val groupedSalaries = mutableMapOf<String, MutableList<Pair<String, Salary>>>()
            
            salaryDocs.forEach { doc ->
                val salary = doc.toObject(Salary::class.java)?.copy(id = doc.id)
                if (salary != null) {
                    val normalizedMonth = normalizeMonthFormat(salary.month)
                    val key = "${salary.employeeId}_$normalizedMonth"
                    
                    if (!groupedSalaries.containsKey(key)) {
                        groupedSalaries[key] = mutableListOf()
                    }
                    groupedSalaries[key]?.add(doc.id to salary)
                }
            }
            
            Log.d(TAG, "Grouped into ${groupedSalaries.size} unique employee-month combinations")
            
            var migratedCount = 0
            var mergedCount = 0
            
            // Process each group
            groupedSalaries.forEach { (key, salaryPairs) ->
                if (salaryPairs.size == 1) {
                    // Single record - just migrate format if needed
                    val (docId, salary) = salaryPairs[0]
                    val normalizedMonth = normalizeMonthFormat(salary.month)
                    
                    if (salary.month != normalizedMonth) {
                        // Update to new format
                        db.collection("salaries").document(docId)
                            .update("month", normalizedMonth)
                            .await()
                        migratedCount++
                        Log.d(TAG, "Migrated salary for ${salary.employeeName}: ${salary.month} -> $normalizedMonth")
                    }
                } else {
                    // Multiple records for same employee-month - merge them
                    Log.d(TAG, "Found ${salaryPairs.size} duplicate records for key: $key")
                    
                    // Sort by creation date to keep the most recent
                    val sortedPairs = salaryPairs.sortedByDescending { it.second.createdAt.toDate() }
                    val primarySalary = sortedPairs[0].second
                    val primaryDocId = sortedPairs[0].first
                    
                    // Merge data from all records
                    val mergedSalary = mergeSalaryRecords(sortedPairs.map { it.second })
                    val normalizedMonth = normalizeMonthFormat(mergedSalary.month)
                    
                    // Update the primary record with merged data and new format
                    db.collection("salaries").document(primaryDocId)
                        .set(mergedSalary.copy(id = primaryDocId, month = normalizedMonth))
                        .await()
                    
                    // Delete the duplicate records
                    for (i in 1 until sortedPairs.size) {
                        db.collection("salaries").document(sortedPairs[i].first).delete().await()
                    }
                    
                    mergedCount++
                    Log.d(TAG, "Merged ${salaryPairs.size} records for ${primarySalary.employeeName} in $normalizedMonth")
                }
            }
            
            Log.d(TAG, "Migration completed: $migratedCount records migrated, $mergedCount groups merged")
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during salary month format migration: ${e.message}")
            e.printStackTrace()
            false
        }
    }
    
    /**
     * Normalize month format to "MMMM yyyy"
     */
    private fun normalizeMonthFormat(monthString: String): String {
        return try {
            // Try to parse as old format first (YYYY-MM)
            if (monthString.matches(Regex("\\d{4}-\\d{2}"))) {
                val date = oldMonthFormat.parse(monthString)
                return monthFormat.format(date!!)
            }
            
            // Try to parse as new format (MMMM yyyy)
            if (monthString.matches(Regex("[A-Za-z]+ \\d{4}"))) {
                val date = monthFormat.parse(monthString)
                return monthFormat.format(date!!)
            }
            
            // If we can't parse it, return as is
            monthString
        } catch (e: Exception) {
            Log.w(TAG, "Could not normalize month format: $monthString")
            monthString
        }
    }
    
    /**
     * Merge multiple salary records for the same employee and month
     */
    private fun mergeSalaryRecords(salaries: List<Salary>): Salary {
        if (salaries.isEmpty()) return Salary()
        if (salaries.size == 1) return salaries[0]
        
        val primary = salaries[0]
        
        // Merge trainee details
        val allTraineeDetails = salaries.flatMap { it.traineeDetails }
        val uniqueTraineeDetails = allTraineeDetails.groupBy { it.traineeId }
            .mapValues { it.value.maxByOrNull { detail -> detail.paymentDate?.toDate() ?: Date(0) } }
            .values.filterNotNull()
        
        // Merge deduction details
        val allDeductionDetails = salaries.flatMap { it.deductionDetails }
        
        // Sum up numerical values
        val totalTrainees = salaries.sumOf { it.totalTrainees }
        val totalPayments = salaries.sumOf { it.totalPayments }
        val absenceDays = salaries.sumOf { it.absenceDays }
        val totalWorkingDays = salaries.maxOfOrNull { it.totalWorkingDays } ?: 0
        val absencePercentage = if (totalWorkingDays > 0) (absenceDays.toDouble() / totalWorkingDays.toDouble()) * 100 else 0.0
        val deductionAmount = allDeductionDetails.sumOf { it.amount }
        val finalSalary = salaries.sumOf { it.finalSalary }
        
        return primary.copy(
            totalTrainees = totalTrainees,
            traineeDetails = uniqueTraineeDetails,
            totalPayments = totalPayments,
            absenceDays = absenceDays,
            totalWorkingDays = totalWorkingDays,
            absencePercentage = absencePercentage,
            deductionAmount = deductionAmount,
            deductionDetails = allDeductionDetails,
            finalSalary = finalSalary,
            updatedAt = com.google.firebase.Timestamp.now()
        )
    }
    
    /**
     * Check if migration is needed by looking for old format records
     */
    suspend fun isMigrationNeeded(): Boolean {
        return try {
            val allSalaries = db.collection("salaries").get().await()
            val hasOldFormat = allSalaries.documents.any { doc ->
                val month = doc.getString("month") ?: ""
                month.matches(Regex("\\d{4}-\\d{2}"))
            }
            
            Log.d(TAG, "Migration needed: $hasOldFormat")
            hasOldFormat
        } catch (e: Exception) {
            Log.e(TAG, "Error checking if migration is needed: ${e.message}")
            false
        }
    }
    
    /**
     * Test function to verify month format normalization
     */
    fun testMonthFormatNormalization() {
        val testCases = listOf(
            "2024-01" to "January 2024",
            "2024-12" to "December 2024",
            "January 2024" to "January 2024",
            "December 2024" to "December 2024",
            "invalid" to "invalid"
        )
        
        testCases.forEach { (input, expected) ->
            val result = normalizeMonthFormat(input)
            val success = result == expected
            Log.d(TAG, "Test: '$input' -> '$result' (expected: '$expected') - ${if (success) "PASS" else "FAIL"}")
        }
    }
}
