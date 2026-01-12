package com.example.double_dot_demo.utils

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

class MonthlyResetManager {

    private val db = FirebaseFirestore.getInstance()
    private val monthFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())

    fun resetTraineesIfNewMonth(onDone: (() -> Unit)? = null) {
        val currentMonth = monthFormat.format(Date())

        db.collection("meta")
            .document("system")
            .get()
            .addOnSuccessListener { doc ->
                val lastMonth = doc.getString("lastResetMonth")

                if (lastMonth == currentMonth) {
                    // Already reset this month
                    onDone?.invoke()
                    return@addOnSuccessListener
                }

                performReset(currentMonth, onDone)
            }
    }

    private fun performReset(currentMonth: String, onDone: (() -> Unit)?) {
        db.collection("trainees")
            .get()
            .addOnSuccessListener { snapshot ->
                val batch = db.batch()

                snapshot.documents.forEach { doc ->
                    batch.update(doc.reference, mapOf(
                        "paymentAmount" to 0.0,
                        "isPaid" to false,
                        "attendanceSessions" to hashMapOf<String, Any>(),
                        "updatedAt" to Timestamp.now()
                    ))
                }

                // Save month marker
                val metaRef = db.collection("meta").document("system")
                batch.set(metaRef, mapOf(
                    "lastResetMonth" to currentMonth,
                    "updatedAt" to Timestamp.now()
                ))

                batch.commit().addOnSuccessListener {
                    onDone?.invoke()
                }
            }
    }
}