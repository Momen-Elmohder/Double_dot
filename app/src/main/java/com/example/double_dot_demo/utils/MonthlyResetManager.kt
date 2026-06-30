package com.example.double_dot_demo.utils

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

class MonthlyResetManager {

    private val db = FirebaseFirestore.getInstance()
    private val monthFormat = SimpleDateFormat("MMMM yyyy", Locale.ENGLISH)
    fun resetTraineesIfNewMonth(onDone: (() -> Unit)? = null) {
        android.util.Log.e("RESET", "resetTraineesIfNewMonth CALLED")
        val currentMonth = monthFormat.format(Date())

        db.collection("meta")
            .document("billing")
            .get()
            .addOnSuccessListener { doc ->
                android.util.Log.e(
                    "RESET",

                    "lastTraineeResetMonth = ${doc.getString("lastTraineeResetMonth")}"

                )

                val lastMonth = doc.getString("lastTraineeResetMonth")

                if (lastMonth == currentMonth) {
                    // Already reset this month
                    onDone?.invoke()
                    return@addOnSuccessListener
                }

                performReset(currentMonth, onDone)
            }
    }

    private fun performReset(currentMonth: String, onDone: (() -> Unit)?) {
        android.util.Log.e("RESET", "performReset CALLED")
        db.collection("trainees")
            .get()
            .addOnSuccessListener { snapshot ->
                val batch = db.batch()
                archiveCurrentMonthSalaries()
                snapshot.documents.forEach { doc ->
                    batch.update(
                        doc.reference, mapOf(
                            "paymentAmount" to 0.0,
                            "isPaid" to false,
                            "attendanceSessions" to hashMapOf<String, Any>(),
                            "updatedAt" to Timestamp.now()
                        )
                    )
                }

                // Save month marker
                val metaRef = db.collection("meta").document("billing")
                batch.set(
                    metaRef, mapOf(
                        "lastTraineeResetMonth" to currentMonth,
                        "updatedAt" to Timestamp.now()
                    )
                )

                batch.commit().addOnSuccessListener {
                    onDone?.invoke()
                }
            }
    }

    private fun archiveCurrentMonthSalaries() {
        android.util.Log.e("RESET", "archiveCurrentMonthSalaries CALLED")

        val currentMonth =
            SimpleDateFormat(
                "MMMM yyyy",
                Locale.ENGLISH
            ).format(Date())

        FirebaseFirestore.getInstance()
            .collection("salaries")
            .whereEqualTo("month", currentMonth)
            .get()
            .addOnSuccessListener { docs ->

                android.util.Log.e(
                    "RESET",
                    "Salary docs count = ${docs.documents.size}"
                )

                docs.forEach { doc ->

                    FirebaseFirestore.getInstance()
                        .collection("salary_archive")
                        .document(currentMonth)
                        .collection("employees")
                        .document(doc.id)
                        .set(doc.data)
                        .addOnSuccessListener {
                            android.util.Log.e(
                                "RESET",
                                "Saved archive for ${doc.id}"
                            )
                        }
                        .addOnFailureListener {
                            android.util.Log.e(
                                "RESET",
                                "FAILED: ${it.message}"
                            )
                        }
                }
            }
    }
}
