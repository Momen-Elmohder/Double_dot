package com.example.double_dot_demo.utils

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object ServerTime {
    private val firestore by lazy { FirebaseFirestore.getInstance() }

    suspend fun now(): Date {
        val docRef = firestore.collection("_meta").document("server_time")
        // Write server timestamp and read it back
        docRef.set(mapOf("ts" to FieldValue.serverTimestamp())).await()
        val snap = docRef.get().await()
        val ts = snap.getTimestamp("ts") ?: Timestamp.now()
        return ts.toDate()
    }

    suspend fun monthKey(): String {
        val date = now()
        val cal = Calendar.getInstance()
        cal.time = date
        return String.format("%04d-%02d", cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1)
    }

    suspend fun format(pattern: String): String {
        val sdf = SimpleDateFormat(pattern, Locale.getDefault())
        return sdf.format(now())
    }
}

