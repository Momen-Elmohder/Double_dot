package com.example.double_dot_demo.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.double_dot_demo.R
import com.example.double_dot_demo.adapters.WeeklyScheduleAdapter
import com.example.double_dot_demo.databinding.FragmentWeeklyScheduleBinding
import com.example.double_dot_demo.dialogs.SimpleScheduleDialog
import com.example.double_dot_demo.models.Trainee
import com.example.double_dot_demo.models.WeeklySchedule
import com.example.double_dot_demo.utils.LoadingManager
import com.example.double_dot_demo.utils.PerformanceUtils
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await

class WeeklyScheduleFragment : Fragment() {
    private var _binding: FragmentWeeklyScheduleBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var scheduleAdapter: WeeklyScheduleAdapter
    private lateinit var loadingManager: LoadingManager
    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    
    private var currentUserRole: String = ""
    private var currentUserId: String = ""
    private var selectedBranch: String = ""
    private var allTrainees: List<Trainee> = emptyList()
    private var currentSchedule: WeeklySchedule? = null
    private var traineeNames: Map<String, String> = emptyMap()
    
    private val branches = listOf("نادي التوكيلات", "نادي اليخت", "المدينة الرياضية")
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWeeklyScheduleBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        initializeComponents()
        setupRecyclerView()
        setupBranchSelection()
        setupClickListeners()
        loadData()
    }
    
    private fun initializeComponents() {
        firestore = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()
        currentUserId = auth.currentUser?.uid ?: ""
        
        // Get user role from arguments or default to coach
        currentUserRole = arguments?.getString("user_role", "coach") ?: "coach"
        
        loadingManager = LoadingManager(binding.loadingOverlay.root)
        
        // Check if user has permission to access weekly schedule
        if (currentUserRole != "head_coach" && currentUserRole != "admin") {
            showToast("You don't have permission to access the weekly schedule")
            requireActivity().onBackPressed()
            return
        }
    }
    
    private fun setupRecyclerView() {
        scheduleAdapter = WeeklyScheduleAdapter { day, timeSlot, traineeIds ->
            showEditCellDialog(day, timeSlot, traineeIds)
        }
        
        binding.recyclerViewSchedule.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = scheduleAdapter
        }
    }
    
    private fun setupBranchSelection() {
        val branchAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, branches)
        branchAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.actvBranch.setAdapter(branchAdapter)
        
        binding.actvBranch.setOnItemClickListener { _, _, position, _ ->
            selectedBranch = branches[position]
            lifecycleScope.launch {
                loadScheduleForBranch(selectedBranch)
            }
        }
        
        // Set default branch
        if (branches.isNotEmpty()) {
            binding.actvBranch.setText(branches[0], false)
            selectedBranch = branches[0]
        }
    }
    
    private fun setupClickListeners() {
        binding.btnAddTrainee.setOnClickListener {
            if (binding.btnAddTrainee.isEnabled) {
                binding.btnAddTrainee.isEnabled = false
                showAddTraineeDialog()
                binding.btnAddTrainee.postDelayed({
                    binding.btnAddTrainee.isEnabled = true
                }, 1000)
            }
        }
    }
    
    private fun loadData() {
        lifecycleScope.launch {
            try {
                loadingManager.showLoading()
                
                // Load trainees and schedule in parallel
                val traineesJob = launch { loadTrainees() }
                val scheduleJob = launch { loadScheduleForBranch(selectedBranch) }
                
                traineesJob.join()
                scheduleJob.join()
                
                loadingManager.hideLoading()
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    loadingManager.hideLoading()
                    showToast("Error loading data: ${e.message}")
                }
            }
        }
    }
    
    private suspend fun loadTrainees() {
        val snapshot = firestore.collection("trainees").get().await()
        val trainees = mutableListOf<Trainee>()
        
        for (document in snapshot.documents) {
            val trainee = document.toObject(Trainee::class.java)
            trainee?.id = document.id
            if (trainee != null) {
                trainees.add(trainee)
            }
        }
        
        withContext(Dispatchers.Main) {
            allTrainees = trainees
            traineeNames = trainees.associate { it.id to it.name }
        }
    }
    
    private suspend fun loadScheduleForBranch(branch: String) {
        val document = firestore.collection("weekly_schedules")
            .whereEqualTo("branch", branch)
            .get()
            .await()
        
        val schedule = if (!document.isEmpty) {
            document.documents[0].toObject(WeeklySchedule::class.java)?.apply {
                id = document.documents[0].id
            }
        } else {
            // Create new schedule for branch
            WeeklySchedule(
                branch = branch,
                scheduleData = createEmptySchedule(),
                createdAt = com.google.firebase.Timestamp.now(),
                createdBy = currentUserId
            )
        }
        
        withContext(Dispatchers.Main) {
            currentSchedule = schedule
            updateScheduleDisplay()
            updateStats()
        }
    }
    
    private fun createEmptySchedule(): Map<String, Map<String, List<String>>> {
        val schedule = mutableMapOf<String, MutableMap<String, List<String>>>()
        
        WeeklySchedule.DAYS_OF_WEEK.forEach { day ->
            schedule[day] = mutableMapOf()
            WeeklySchedule.TIME_SLOTS.forEach { time ->
                schedule[day]!![time] = emptyList()
            }
        }
        
        return schedule
    }
    
    private fun updateScheduleDisplay() {
        currentSchedule?.let { schedule ->
            scheduleAdapter.updateSchedule(schedule.scheduleData, traineeNames)
        }
    }
    
    private fun updateStats() {
        currentSchedule?.let { schedule ->
            var totalSlots = 0
            var totalTrainees = 0
            
            for (daySchedule in schedule.scheduleData.values) {
                for (traineeIds in daySchedule.values) {
                    if (traineeIds.isNotEmpty()) {
                        totalSlots++
                        totalTrainees += traineeIds.size
                    }
                }
            }
            
            binding.tvTotalSlots.text = totalSlots.toString()
            binding.tvTotalTrainees.text = totalTrainees.toString()
        }
    }
    
    private fun showAddTraineeDialog() {
        // TODO: Implement AddTraineeDialog or use existing AddTraineeDialog
        showToast("Add trainee functionality will be implemented")
    }
    
    private fun addTraineeToSchedule(trainee: Trainee) {
        // This would typically open a dialog to select days and times
        // For now, we'll add to a default slot
        showToast("Trainee added. Please assign to specific time slots.")
    }
    
    private fun showEditCellDialog(day: String, timeSlot: String, traineeIds: List<String>) {
        val dialog = SimpleScheduleDialog()
        dialog.setData(day, timeSlot, selectedBranch, traineeIds, allTrainees)
        dialog.setOnSaveListener { updatedDay, updatedTimeSlot, updatedTraineeIds ->
            updateScheduleCell(updatedDay, updatedTimeSlot, updatedTraineeIds)
        }
        dialog.show(childFragmentManager, "SimpleScheduleDialog")
    }
    
    private fun updateScheduleCell(day: String, timeSlot: String, traineeIds: List<String>) {
        lifecycleScope.launch {
            try {
                loadingManager.showLoading()
                
                currentSchedule?.let { schedule ->
                    val updatedScheduleData = schedule.scheduleData.toMutableMap()
                    val updatedDaySchedule = updatedScheduleData[day]?.toMutableMap() ?: mutableMapOf()
                    updatedDaySchedule[timeSlot] = traineeIds
                    updatedScheduleData[day] = updatedDaySchedule
                    
                    val updatedSchedule = schedule.copy(
                        scheduleData = updatedScheduleData,
                        updatedAt = com.google.firebase.Timestamp.now(),
                        updatedBy = currentUserId
                    )
                    
                    // Save to Firestore
                    if (schedule.id.isNotEmpty()) {
                        firestore.collection("weekly_schedules")
                            .document(schedule.id)
                            .set(updatedSchedule)
                            .await()
                    } else {
                        firestore.collection("weekly_schedules")
                            .add(updatedSchedule)
                            .await()
                    }
                    
                    withContext(Dispatchers.Main) {
                        currentSchedule = updatedSchedule
                        updateScheduleDisplay()
                        updateStats()
                        loadingManager.hideLoading()
                        showToast("Schedule updated successfully")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    loadingManager.hideLoading()
                    showToast("Error updating schedule: ${e.message}")
                }
            }
        }
    }
    
    private fun showToast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
