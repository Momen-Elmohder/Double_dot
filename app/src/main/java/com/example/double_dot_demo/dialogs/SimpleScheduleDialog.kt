package com.example.double_dot_demo.dialogs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import com.example.double_dot_demo.databinding.DialogEditScheduleCellBinding
import com.example.double_dot_demo.models.Trainee

class SimpleScheduleDialog : DialogFragment() {
    private var _binding: DialogEditScheduleCellBinding? = null
    private val binding get() = _binding!!
    
    private var currentTraineeIds: List<String> = emptyList()
    private var allTrainees: List<Trainee> = emptyList()
    private var selectedTraineeId: String? = null
    private var day: String = ""
    private var timeSlot: String = ""
    private var branch: String = ""
    
    private var onSaveListener: ((String, String, List<String>) -> Unit)? = null
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogEditScheduleCellBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupViews()
        setupTraineeSpinner()
        setupClickListeners()
    }
    
    private fun setupViews() {
        binding.tvDayTime.text = "$day - $timeSlot"
    }
    
    private fun setupTraineeSpinner() {
        // Filter trainees by branch
        val branchTrainees = allTrainees.filter { it.branch == branch }
        val traineeNames = branchTrainees.map { it.name }
        
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, traineeNames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.actvTrainee.setAdapter(adapter)
        
        binding.actvTrainee.setOnItemClickListener { _, _, position, _ ->
            selectedTraineeId = branchTrainees[position].id
        }
    }
    
    private fun setupClickListeners() {
        binding.btnCancel.setOnClickListener {
            dismiss()
        }
        
        binding.btnSave.setOnClickListener {
            saveChanges()
        }
    }
    
    private fun saveChanges() {
        selectedTraineeId?.let { newTraineeId ->
            val updatedIds = currentTraineeIds.toMutableList()
            if (!updatedIds.contains(newTraineeId)) {
                updatedIds.add(newTraineeId)
            }
            onSaveListener?.invoke(day, timeSlot, updatedIds)
        } ?: run {
            onSaveListener?.invoke(day, timeSlot, currentTraineeIds)
        }
        dismiss()
    }
    
    fun setData(
        day: String,
        timeSlot: String,
        branch: String,
        currentTraineeIds: List<String>,
        allTrainees: List<Trainee>
    ) {
        this.day = day
        this.timeSlot = timeSlot
        this.branch = branch
        this.currentTraineeIds = currentTraineeIds
        this.allTrainees = allTrainees
    }
    
    fun setOnSaveListener(listener: (String, String, List<String>) -> Unit) {
        onSaveListener = listener
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

