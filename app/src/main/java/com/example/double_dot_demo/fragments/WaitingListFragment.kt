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
import com.example.double_dot_demo.adapters.WaitingListAdapter
import com.example.double_dot_demo.databinding.FragmentWaitingListBinding
import com.example.double_dot_demo.dialogs.AddWaitingListPlayerDialog
import com.example.double_dot_demo.models.WaitingListPlayer
import com.example.double_dot_demo.utils.LoadingManager
import com.example.double_dot_demo.utils.PerformanceUtils
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await

class WaitingListFragment : Fragment() {
    private var _binding: FragmentWaitingListBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var waitingListAdapter: WaitingListAdapter
    private lateinit var loadingManager: LoadingManager
    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    
    private var allWaitingListPlayers = mutableListOf<WaitingListPlayer>()
    private var filteredWaitingListPlayers = mutableListOf<WaitingListPlayer>()
    private var currentUserRole: String = ""
    private var currentUserId: String = ""
    private var currentUserName: String = ""
    
    private val branches = listOf("All Branches", "Branch A", "Branch B", "Branch C", "Branch D")
    private val statuses = listOf("All Status", "waiting", "contacted", "enrolled", "rejected")
    private val sortOptions = listOf("Earliest Added", "Latest Added", "Name A-Z", "Name Z-A", "Age Low-High", "Age High-Low")
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWaitingListBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        initializeComponents()
        setupRecyclerView()
        setupSpinners()
        setupClickListeners()
        setupSearch()
        loadWaitingListPlayers()
    }
    
    private fun initializeComponents() {
        firestore = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()
        currentUserId = auth.currentUser?.uid ?: ""
        currentUserName = auth.currentUser?.displayName ?: "Unknown User"
        
        // Get user role from arguments or default to coach
        currentUserRole = arguments?.getString("user_role", "coach") ?: "coach"
        
        loadingManager = LoadingManager(binding.loadingOverlay.root)
        
        // Check if user has permission to access waiting list
        if (currentUserRole != "head_coach" && currentUserRole != "admin") {
            showToast("You don't have permission to access the waiting list")
            requireActivity().onBackPressed()
            return
        }
    }
    
    private fun setupRecyclerView() {
        waitingListAdapter = WaitingListAdapter(
            onContactClick = { player -> contactPlayer(player) },
            onEnrollClick = { player -> enrollPlayer(player) },
            onRejectClick = { player -> rejectPlayer(player) },
            onEditClick = { player -> editPlayer(player) },
            onDeleteClick = { player -> deletePlayer(player) }
        )
        
        binding.recyclerViewWaitingList.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = waitingListAdapter
        }
    }
    
    private fun setupSpinners() {
        // Branch filter
        val branchAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, branches)
        branchAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.actvBranch.setAdapter(branchAdapter)
        binding.actvBranch.setText("All Branches", false)
        
        // Status filter
        val statusAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, statuses)
        statusAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.actvStatus.setAdapter(statusAdapter)
        binding.actvStatus.setText("All Status", false)
        
        // Sort options
        val sortAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, sortOptions)
        sortAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.actvSortBy.setAdapter(sortAdapter)
        binding.actvSortBy.setText("Earliest Added", false)
        
        // Set up listeners
        binding.actvBranch.setOnItemClickListener { _, _, position, _ ->
            filterAndSortPlayers()
        }
        
        binding.actvStatus.setOnItemClickListener { _, _, position, _ ->
            filterAndSortPlayers()
        }
        
        binding.actvSortBy.setOnItemClickListener { _, _, position, _ ->
            filterAndSortPlayers()
        }
    }
    
    private fun setupClickListeners() {
        binding.btnAddWaitingListPlayer.setOnClickListener {
            if (binding.btnAddWaitingListPlayer.isEnabled) {
                binding.btnAddWaitingListPlayer.isEnabled = false
                showAddPlayerDialog()
                binding.btnAddWaitingListPlayer.postDelayed({
                    binding.btnAddWaitingListPlayer.isEnabled = true
                }, 1000)
            }
        }
    }
    
    private fun setupSearch() {
        var searchJob: kotlinx.coroutines.Job? = null
        
        binding.etSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                searchJob?.cancel()
                searchJob = lifecycleScope.launch {
                    kotlinx.coroutines.delay(300) // Debounce search
                    filterAndSortPlayers()
                }
            }
        })
    }
    
    private fun loadWaitingListPlayers() {
        lifecycleScope.launch {
            try {
                loadingManager.showLoading()
                
                PerformanceUtils.launchInBackground {
                    val query = firestore.collection("waiting_list")
                        .orderBy("createdAt", Query.Direction.DESCENDING)
                    
                    val snapshot = query.get().await()
                    val players = mutableListOf<WaitingListPlayer>()
                    
                    for (document in snapshot.documents) {
                        val player = document.toObject(WaitingListPlayer::class.java)
                        player?.id = document.id
                        if (player != null) {
                            players.add(player)
                        }
                    }
                    
                    withContext(Dispatchers.Main) {
                        allWaitingListPlayers.clear()
                        allWaitingListPlayers.addAll(players)
                        filterAndSortPlayers()
                        updateStats()
                        loadingManager.hideLoading()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    loadingManager.hideLoading()
                    showToast("Error loading waiting list: ${e.message}")
                }
            }
        }
    }
    
    private fun filterAndSortPlayers() {
        PerformanceUtils.launchInBackground {
            val searchQuery = binding.etSearch.text.toString().trim().lowercase()
            val selectedBranch = binding.actvBranch.text.toString()
            val selectedStatus = binding.actvStatus.text.toString()
            val selectedSort = binding.actvSortBy.text.toString()
            
            val filtered = allWaitingListPlayers.filter { player ->
                val matchesSearch = searchQuery.isEmpty() || 
                    player.name.lowercase().contains(searchQuery) ||
                    player.phoneNumber.contains(searchQuery) ||
                    player.branch.lowercase().contains(searchQuery)
                
                val matchesBranch = selectedBranch == "All Branches" || player.branch == selectedBranch
                val matchesStatus = selectedStatus == "All Status" || player.status == selectedStatus
                
                matchesSearch && matchesBranch && matchesStatus
            }
            
            val sorted = when (selectedSort) {
                "Earliest Added" -> filtered.sortedBy { it.createdAt }
                "Latest Added" -> filtered.sortedByDescending { it.createdAt }
                "Name A-Z" -> filtered.sortedBy { it.name }
                "Name Z-A" -> filtered.sortedByDescending { it.name }
                "Age Low-High" -> filtered.sortedBy { it.age }
                "Age High-Low" -> filtered.sortedByDescending { it.age }
                else -> filtered.sortedBy { it.createdAt }
            }
            
            withContext(Dispatchers.Main) {
                filteredWaitingListPlayers.clear()
                filteredWaitingListPlayers.addAll(sorted)
                waitingListAdapter.submitList(filteredWaitingListPlayers.toList())
                updateStats()
            }
        }
    }
    
    private fun updateStats() {
        val totalCount = allWaitingListPlayers.size
        val waitingCount = allWaitingListPlayers.count { it.status == "waiting" }
        
        binding.tvTotalCount.text = totalCount.toString()
        binding.tvWaitingCount.text = waitingCount.toString()
    }
    
    private fun showAddPlayerDialog() {
        val dialog = AddWaitingListPlayerDialog()
        dialog.setOnPlayerAddedListener { player ->
            addPlayerToFirestore(player)
        }
        dialog.show(childFragmentManager, "AddWaitingListPlayerDialog")
    }
    
    private fun addPlayerToFirestore(player: WaitingListPlayer) {
        lifecycleScope.launch {
            try {
                loadingManager.showLoading()
                
                PerformanceUtils.launchInBackground {
                    val playerData = hashMapOf(
                        "name" to player.name,
                        "age" to player.age,
                        "phoneNumber" to player.phoneNumber,
                        "branch" to player.branch,
                        "addedBy" to currentUserId,
                        "addedByName" to currentUserName,
                        "createdAt" to com.google.firebase.Timestamp.now(),
                        "status" to "waiting"
                    )
                    
                    firestore.collection("waiting_list").add(playerData).await()
                    
                    withContext(Dispatchers.Main) {
                        loadingManager.hideLoading()
                        showToast("Player added to waiting list successfully")
                        loadWaitingListPlayers()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    loadingManager.hideLoading()
                    showToast("Error adding player: ${e.message}")
                }
            }
        }
    }
    
    private fun contactPlayer(player: WaitingListPlayer) {
        updatePlayerStatus(player, "contacted")
    }
    
    private fun enrollPlayer(player: WaitingListPlayer) {
        updatePlayerStatus(player, "enrolled")
    }
    
    private fun rejectPlayer(player: WaitingListPlayer) {
        updatePlayerStatus(player, "rejected")
    }
    
    private fun updatePlayerStatus(player: WaitingListPlayer, newStatus: String) {
        lifecycleScope.launch {
            try {
                PerformanceUtils.launchInBackground {
                    firestore.collection("waiting_list")
                        .document(player.id)
                        .update("status", newStatus)
                        .await()
                    
                    withContext(Dispatchers.Main) {
                        showToast("Player status updated to $newStatus")
                        loadWaitingListPlayers()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showToast("Error updating player status: ${e.message}")
                }
            }
        }
    }
    
    private fun editPlayer(player: WaitingListPlayer) {
        val dialog = AddWaitingListPlayerDialog()
        dialog.setPlayerToEdit(player)
        dialog.setOnPlayerAddedListener { updatedPlayer ->
            updatePlayerInFirestore(updatedPlayer)
        }
        dialog.show(childFragmentManager, "EditWaitingListPlayerDialog")
    }
    
    private fun updatePlayerInFirestore(player: WaitingListPlayer) {
        lifecycleScope.launch {
            try {
                loadingManager.showLoading()
                
                PerformanceUtils.launchInBackground {
                    val playerData = hashMapOf(
                        "name" to player.name,
                        "age" to player.age,
                        "phoneNumber" to player.phoneNumber,
                        "branch" to player.branch
                    )
                    
                    firestore.collection("waiting_list")
                        .document(player.id)
                        .update(playerData as Map<String, Any>)
                        .await()
                    
                    withContext(Dispatchers.Main) {
                        loadingManager.hideLoading()
                        showToast("Player updated successfully")
                        loadWaitingListPlayers()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    loadingManager.hideLoading()
                    showToast("Error updating player: ${e.message}")
                }
            }
        }
    }
    
    private fun deletePlayer(player: WaitingListPlayer) {
        // Show confirmation dialog
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Delete Player")
            .setMessage("Are you sure you want to delete ${player.name} from the waiting list?")
            .setPositiveButton("Delete") { _, _ ->
                performDeletePlayer(player)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun performDeletePlayer(player: WaitingListPlayer) {
        lifecycleScope.launch {
            try {
                loadingManager.showLoading()
                
                PerformanceUtils.launchInBackground {
                    firestore.collection("waiting_list")
                        .document(player.id)
                        .delete()
                        .await()
                    
                    withContext(Dispatchers.Main) {
                        loadingManager.hideLoading()
                        showToast("Player deleted successfully")
                        loadWaitingListPlayers()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    loadingManager.hideLoading()
                    showToast("Error deleting player: ${e.message}")
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
