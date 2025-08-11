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
    
    override fun onResume() {
        super.onResume()
        // Refresh schedule when fragment becomes visible
        refreshSchedule()
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
        // Button removed - trainees are added automatically from TraineesFragment
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
        val scheduleId = "schedule_$branch"
        val document = firestore.collection("weekly_schedules")
            .document(scheduleId)
            .get()
            .await()
        
        val schedule = if (document.exists()) {
            document.toObject(WeeklySchedule::class.java)?.copy(id = document.id)
        } else {
            // Create new schedule for branch
            WeeklySchedule(
                id = scheduleId,
                branch = branch,
                scheduleData = createEmptySchedule(),
                createdAt = com.google.firebase.Timestamp.now(),
                createdBy = currentUserId
            )
        }
        
        withContext(Dispatchers.Main) {
            currentSchedule = schedule
            android.util.Log.d("WeeklyScheduleFragment", "Loaded schedule for branch: $branch")
            android.util.Log.d("WeeklyScheduleFragment", "Schedule ID: ${schedule?.id}")
            android.util.Log.d("WeeklyScheduleFragment", "Schedule data: ${schedule?.scheduleData}")
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
            android.util.Log.d("WeeklyScheduleFragment", "updateScheduleDisplay called")
            android.util.Log.d("WeeklyScheduleFragment", "Schedule data structure: ${schedule.scheduleData}")
            android.util.Log.d("WeeklyScheduleFragment", "Trainee names map: $traineeNames")
            
            // Log each day's schedule for debugging
            schedule.scheduleData.forEach { (day, daySchedule) ->
                daySchedule.forEach { (time, traineeIds) ->
                    if (traineeIds.isNotEmpty()) {
                        android.util.Log.d("WeeklyScheduleFragment", "Found trainees in $day at $time: $traineeIds")
                        val names = traineeIds.mapNotNull { traineeNames[it] }
                        android.util.Log.d("WeeklyScheduleFragment", "Trainee names: $names")
                    }
                }
            }
            
            scheduleAdapter.updateSchedule(schedule.scheduleData, traineeNames)
        } ?: android.util.Log.w("WeeklyScheduleFragment", "currentSchedule is null")
    }
    
    private fun updateStats() {
        // Stats counters removed - no longer needed
    }
    
    // Add trainee dialog and manual adding removed - all automatic now
    
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
                    
                    // Save to Firestore using consistent document ID
                    val scheduleId = if (schedule.id.isNotEmpty()) schedule.id else "schedule_$selectedBranch"
                    firestore.collection("weekly_schedules")
                        .document(scheduleId)
                        .set(updatedSchedule.copy(id = scheduleId))
                        .await()
                    
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
    
    fun refreshSchedule() {
        lifecycleScope.launch {
            try {
                loadScheduleForBranch(selectedBranch)
                android.util.Log.d("WeeklyScheduleFragment", "Schedule refreshed for branch: $selectedBranch")
            } catch (e: Exception) {
                android.util.Log.e("WeeklyScheduleFragment", "Error refreshing schedule: ${e.message}")
            }
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
