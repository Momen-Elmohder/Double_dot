package com.example.double_dot_demo.utils

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.Timestamp
import com.example.double_dot_demo.models.WeeklySchedule
import com.example.double_dot_demo.models.Trainee
import android.util.Log

class ScheduleManager {
    private val db = FirebaseFirestore.getInstance()
    
    companion object {
        private const val TAG = "ScheduleManager"
        private const val SCHEDULES_COLLECTION = "weekly_schedules"
    }
    
    fun addTraineeToSchedule(trainee: Trainee, onComplete: (Boolean) -> Unit) {
        if (trainee.scheduleDays.isEmpty() || trainee.scheduleTime.isEmpty()) {
            Log.w(TAG, "Trainee ${trainee.name} has no schedule information")
            onComplete(true) // Not an error, just no schedule
            return
        }
        
        val scheduleId = "schedule_${trainee.branch}"
        
        // Get or create schedule for the branch
        db.collection(SCHEDULES_COLLECTION)
            .document(scheduleId)
            .get()
            .addOnSuccessListener { document ->
                val currentSchedule = if (document.exists()) {
                    document.toObject(WeeklySchedule::class.java) ?: WeeklySchedule()
                } else {
                    WeeklySchedule(
                        id = scheduleId,
                        branch = trainee.branch,
                        createdAt = Timestamp.now()
                    )
                }
                
                // Add trainee to schedule
                val updatedSchedule = currentSchedule.addTrainee(
                    traineeId = trainee.id,
                    days = trainee.scheduleDays,
                    time = trainee.scheduleTime
                ).copy(
                    updatedAt = Timestamp.now(),
                    updatedBy = "system" // Or get current user ID
                )
                
                // Save updated schedule
                db.collection(SCHEDULES_COLLECTION)
                    .document(scheduleId)
                    .set(updatedSchedule)
                    .addOnSuccessListener {
                        Log.d(TAG, "Trainee ${trainee.name} added to schedule successfully")
                        onComplete(true)
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Error adding trainee to schedule: ${e.message}")
                        onComplete(false)
                    }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error getting schedule: ${e.message}")
                onComplete(false)
            }
    }
    
    fun removeTraineeFromSchedule(trainee: Trainee, onComplete: (Boolean) -> Unit) {
        val scheduleId = "schedule_${trainee.branch}"
        
        db.collection(SCHEDULES_COLLECTION)
            .document(scheduleId)
            .get()
            .addOnSuccessListener { document ->
                if (!document.exists()) {
                    Log.w(TAG, "Schedule for branch ${trainee.branch} does not exist")
                    onComplete(true) // Not an error
                    return@addOnSuccessListener
                }
                
                val currentSchedule = document.toObject(WeeklySchedule::class.java) ?: WeeklySchedule()
                
                // Remove trainee from schedule
                val updatedSchedule = currentSchedule.removeTrainee(trainee.id).copy(
                    updatedAt = Timestamp.now(),
                    updatedBy = "system" // Or get current user ID
                )
                
                // Save updated schedule
                db.collection(SCHEDULES_COLLECTION)
                    .document(scheduleId)
                    .set(updatedSchedule)
                    .addOnSuccessListener {
                        Log.d(TAG, "Trainee ${trainee.name} removed from schedule successfully")
                        onComplete(true)
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Error removing trainee from schedule: ${e.message}")
                        onComplete(false)
                    }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error getting schedule: ${e.message}")
                onComplete(false)
            }
    }
    
    fun updateTraineeInSchedule(
        oldTrainee: Trainee, 
        newTrainee: Trainee, 
        onComplete: (Boolean) -> Unit
    ) {
        // If schedule info hasn't changed, no need to update
        if (oldTrainee.scheduleDays == newTrainee.scheduleDays && 
            oldTrainee.scheduleTime == newTrainee.scheduleTime &&
            oldTrainee.branch == newTrainee.branch) {
            onComplete(true)
            return
        }
        
        // Remove from old schedule first
        removeTraineeFromSchedule(oldTrainee) { removeSuccess ->
            if (removeSuccess) {
                // Add to new schedule
                addTraineeToSchedule(newTrainee, onComplete)
            } else {
                onComplete(false)
            }
        }
    }
    
    fun removeTraineeByStatus(traineeId: String, branch: String, onComplete: (Boolean) -> Unit) {
        val scheduleId = "schedule_$branch"
        
        db.collection(SCHEDULES_COLLECTION)
            .document(scheduleId)
            .get()
            .addOnSuccessListener { document ->
                if (!document.exists()) {
                    onComplete(true) // Not an error
                    return@addOnSuccessListener
                }
                
                val currentSchedule = document.toObject(WeeklySchedule::class.java) ?: WeeklySchedule()
                val updatedSchedule = currentSchedule.removeTrainee(traineeId).copy(
                    updatedAt = Timestamp.now(),
                    updatedBy = "system"
                )
                
                db.collection(SCHEDULES_COLLECTION)
                    .document(scheduleId)
                    .set(updatedSchedule)
                    .addOnSuccessListener {
                        Log.d(TAG, "Trainee $traineeId removed from schedule due to status change")
                        onComplete(true)
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Error removing trainee by status: ${e.message}")
                        onComplete(false)
                    }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error getting schedule: ${e.message}")
                onComplete(false)
            }
    }
}
