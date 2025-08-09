package com.example.double_dot_demo.models

import com.google.firebase.Timestamp

data class WeeklySchedule(
    var id: String = "",
    var branch: String = "",
    var scheduleData: Map<String, Map<String, List<String>>> = emptyMap(), // day -> time -> traineeIds
    var createdAt: Timestamp? = null,
    var updatedAt: Timestamp? = null,
    var createdBy: String = "",
    var updatedBy: String = ""
) {
    // Required empty constructor for Firestore
    constructor() : this("", "", emptyMap(), null, null, "", "")
    
    companion object {
        val DAYS_OF_WEEK = listOf(
            "الأحد", "الإثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجمعة", "السبت"
        )
        
        val TIME_SLOTS = listOf("٤", "٥", "٦", "٧", "٨", "٩", "١٠")
        
        val TIME_SLOTS_ENGLISH = listOf("4", "5", "6", "7", "8", "9", "10")
        
        fun getDayIndex(day: String): Int {
            return DAYS_OF_WEEK.indexOf(day)
        }
        
        fun getTimeIndex(time: String): Int {
            return TIME_SLOTS.indexOf(time)
        }
        
        fun getEnglishTime(arabicTime: String): String {
            val index = TIME_SLOTS.indexOf(arabicTime)
            return if (index >= 0) TIME_SLOTS_ENGLISH[index] else arabicTime
        }
        
        fun getArabicTime(englishTime: String): String {
            val index = TIME_SLOTS_ENGLISH.indexOf(englishTime)
            return if (index >= 0) TIME_SLOTS[index] else englishTime
        }
    }
    
    // Helper functions for trainee management
    fun addTrainee(traineeId: String, days: List<String>, time: String): WeeklySchedule {
        val mutableSchedule = scheduleData.toMutableMap()
        
        for (day in days) {
            if (day in DAYS_OF_WEEK && time in TIME_SLOTS) {
                val daySchedule = mutableSchedule[day]?.toMutableMap() ?: mutableMapOf()
                val timeTrainees = daySchedule[time]?.toMutableList() ?: mutableListOf()
                
                if (!timeTrainees.contains(traineeId)) {
                    timeTrainees.add(traineeId)
                    daySchedule[time] = timeTrainees
                    mutableSchedule[day] = daySchedule
                }
            }
        }
        
        return this.copy(scheduleData = mutableSchedule)
    }
    
    fun removeTrainee(traineeId: String): WeeklySchedule {
        val mutableSchedule = scheduleData.toMutableMap()
        
        for ((day, daySchedule) in mutableSchedule) {
            val mutableDaySchedule = daySchedule.toMutableMap()
            for ((time, trainees) in mutableDaySchedule) {
                val updatedTrainees = trainees.filter { it != traineeId }
                mutableDaySchedule[time] = updatedTrainees
            }
            mutableSchedule[day] = mutableDaySchedule
        }
        
        return this.copy(scheduleData = mutableSchedule)
    }
    
    fun updateTrainee(traineeId: String, newDays: List<String>, newTime: String): WeeklySchedule {
        return this.removeTrainee(traineeId).addTrainee(traineeId, newDays, newTime)
    }
}

data class ScheduleCell(
    val day: String,
    val time: String,
    val traineeIds: List<String> = emptyList(),
    val traineeNames: List<String> = emptyList()
) {
    val isEmpty: Boolean
        get() = traineeIds.isEmpty()
    
    val traineeCount: Int
        get() = traineeIds.size
}

