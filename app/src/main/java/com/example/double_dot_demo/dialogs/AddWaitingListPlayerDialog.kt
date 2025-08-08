package com.example.double_dot_demo.dialogs

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import com.example.double_dot_demo.R
import com.example.double_dot_demo.databinding.DialogAddWaitingListPlayerBinding
import com.example.double_dot_demo.models.WaitingListPlayer

class AddWaitingListPlayerDialog : DialogFragment() {

    private var _binding: DialogAddWaitingListPlayerBinding? = null
    private val binding get() = _binding!!
    
    private var onPlayerAddedListener: ((WaitingListPlayer) -> Unit)? = null
    private var playerToEdit: WaitingListPlayer? = null
    private var isEditMode = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogAddWaitingListPlayerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupViews()
        setupBranchSpinner()
        
        if (isEditMode) {
            populateFields()
        }
    }

    private fun setupViews() {
        // Set dialog title
        binding.tvDialogTitle.text = if (isEditMode) "Edit Waiting List Player" else "Add Waiting List Player"
        
        // Setup save button
        binding.btnSave.setOnClickListener {
            if (validateInputs()) {
                savePlayer()
            }
        }
        
        // Setup cancel button
        binding.btnCancel.setOnClickListener {
            dismiss()
        }
    }

    private fun setupBranchSpinner() {
        val branches = listOf("المدينة الرياضية", "نادي اليخت", "نادي التوكيلات")
        val branchAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, branches)
        binding.actvBranch.setAdapter(branchAdapter)
        
        if (!isEditMode) {
            binding.actvBranch.setText(branches[0], false)
        }
    }

    private fun populateFields() {
        playerToEdit?.let { player ->
            binding.etName.setText(player.name)
            binding.etAge.setText(player.age.toString())
            binding.etPhone.setText(player.phoneNumber)
            binding.actvBranch.setText(player.branch, false)
        }
    }

    private fun validateInputs(): Boolean {
        val name = binding.etName.text.toString().trim()
        val ageText = binding.etAge.text.toString().trim()
        val phone = binding.etPhone.text.toString().trim()
        val branch = binding.actvBranch.text.toString().trim()

        if (name.isEmpty()) {
            binding.etName.error = "Name is required"
            return false
        }

        if (ageText.isEmpty()) {
            binding.etAge.error = "Age is required"
            return false
        }

        val age = try {
            ageText.toInt()
        } catch (e: NumberFormatException) {
            binding.etAge.error = "Age must be a number"
            return false
        }

        if (age <= 0 || age > 100) {
            binding.etAge.error = "Age must be between 1 and 100"
            return false
        }

        if (phone.isEmpty()) {
            binding.etPhone.error = "Phone number is required"
            return false
        }

        if (branch.isEmpty()) {
            binding.actvBranch.error = "Branch is required"
            return false
        }

        return true
    }

    private fun savePlayer() {
        try {
            val name = binding.etName.text.toString().trim()
            val age = binding.etAge.text.toString().trim().toInt()
            val phone = binding.etPhone.text.toString().trim()
            val branch = binding.actvBranch.text.toString().trim()

            val player = if (isEditMode) {
                playerToEdit?.copy(
                    name = name,
                    age = age,
                    phoneNumber = phone,
                    branch = branch
                ) ?: WaitingListPlayer()
            } else {
                WaitingListPlayer(
                    name = name,
                    age = age,
                    phoneNumber = phone,
                    branch = branch,
                    status = "waiting"
                )
            }

            onPlayerAddedListener?.invoke(player)
            dismiss()

        } catch (e: Exception) {
            android.util.Log.e("AddWaitingListPlayerDialog", "Error saving player: ${e.message}")
            Toast.makeText(requireContext(), "Error saving player: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun setOnPlayerAddedListener(listener: (WaitingListPlayer) -> Unit) {
        onPlayerAddedListener = listener
    }

    fun setPlayerToEdit(player: WaitingListPlayer) {
        playerToEdit = player
        isEditMode = true
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
