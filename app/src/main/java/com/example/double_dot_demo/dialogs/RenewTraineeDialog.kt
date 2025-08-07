package com.example.double_dot_demo.dialogs

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.example.double_dot_demo.R
import com.example.double_dot_demo.databinding.DialogRenewTraineeBinding
import com.example.double_dot_demo.models.Trainee
import com.google.firebase.Timestamp

class RenewTraineeDialog(
    private val context: Context,
    private val trainee: Trainee
) : Dialog(context) {

    private lateinit var binding: DialogRenewTraineeBinding
    private var onRenewClickListener: ((Trainee, Int, Int) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DialogRenewTraineeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupViews()
        setupListeners()
    }

    private fun setupViews() {
        binding.tvTraineeName.text = trainee.name
        binding.tvCurrentSessions.text = "Current sessions: ${trainee.remainingSessions}"
        binding.tvCurrentFee.text = "Current fee: $${trainee.monthlyFee}"
        
        // Set default values
        binding.etNewSessions.setText("10")
        binding.etNewFee.setText(trainee.monthlyFee.toString())
    }

    private fun setupListeners() {
        binding.btnCancel.setOnClickListener {
            dismiss()
        }

        binding.btnRenew.setOnClickListener {
            val newSessions = binding.etNewSessions.text.toString().toIntOrNull()
            val newFee = binding.etNewFee.text.toString().toIntOrNull()

            when {
                newSessions == null -> {
                    Toast.makeText(context, "Please enter valid number of sessions", Toast.LENGTH_SHORT).show()
                }
                newSessions <= 0 -> {
                    Toast.makeText(context, "Number of sessions must be greater than 0", Toast.LENGTH_SHORT).show()
                }
                newFee == null -> {
                    Toast.makeText(context, "Please enter valid fee amount", Toast.LENGTH_SHORT).show()
                }
                newFee <= 0 -> {
                    Toast.makeText(context, "Fee amount must be greater than 0", Toast.LENGTH_SHORT).show()
                }
                else -> {
                    onRenewClickListener?.invoke(trainee, newSessions, newFee)
                    dismiss()
                }
            }
        }
    }

    fun setOnRenewClickListener(listener: (Trainee, Int, Int) -> Unit) {
        onRenewClickListener = listener
    }
} 