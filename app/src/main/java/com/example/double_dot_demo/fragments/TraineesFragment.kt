package com.example.double_dot_demo.fragments

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.double_dot_demo.R
import com.example.double_dot_demo.adapters.TraineeAdapter
import com.example.double_dot_demo.databinding.FragmentTraineesBinding
import com.example.double_dot_demo.dialogs.AddTraineeDialog
import com.example.double_dot_demo.models.Trainee
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

class TraineesFragment : Fragment() {

    private var _binding: FragmentTraineesBinding? = null
    private val binding get() = _binding!!
    private lateinit var firestore: FirebaseFirestore
    private lateinit var traineeAdapter: TraineeAdapter
    private val trainees = mutableListOf<Trainee>()
    private val filteredTrainees = mutableListOf<Trainee>()
    private val coaches = mutableListOf<String>()
    
    // Search and sort variables
    private var searchQuery: String = ""
    private var sortBy: String = "name"
    private var sortOrder: String = "asc"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTraineesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        firestore = FirebaseFirestore.getInstance()
        setupRecyclerView()
        setupAddButton()
        setupSearchAndSort()
        loadCoaches()
        loadTrainees()
    }

    private fun setupRecyclerView() {
        traineeAdapter = TraineeAdapter(
            trainees = filteredTrainees,
            onEditClick = { trainee -> showEditTraineeDialog(trainee) },
            onDeleteClick = { trainee -> deleteTrainee(trainee) },
            onTogglePayment = { trainee -> togglePaymentStatus(trainee) }
        )

        binding.recyclerViewTrainees.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = traineeAdapter
        }
    }

    private fun setupAddButton() {
        binding.btnAddTrainee.setOnClickListener {
            showAddTraineeDialog()
        }
    }

    private fun setupSearchAndSort() {
        // Setup search functionality
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                searchQuery = s.toString().trim()
                filterAndSortTrainees()
            }
        })

        // Setup sort by dropdown
        val sortByOptions = listOf("Name", "Age", "Coach", "Starting Date", "Monthly Fee", "Payment Status")
        val sortByAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, sortByOptions)
        binding.actvSortBy.setAdapter(sortByAdapter)
        binding.actvSortBy.setText("Name", false)

        binding.actvSortBy.setOnItemClickListener { _, _, position, _ ->
            sortBy = when (position) {
                0 -> "name"
                1 -> "age"
                2 -> "coachName"
                3 -> "startingDate"
                4 -> "monthlyFee"
                5 -> "isPaid"
                else -> "name"
            }
            filterAndSortTrainees()
        }

        // Setup sort order dropdown
        val sortOrderOptions = listOf("Ascending", "Descending")
        val sortOrderAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, sortOrderOptions)
        binding.actvSortOrder.setAdapter(sortOrderAdapter)
        binding.actvSortOrder.setText("Ascending", false)

        binding.actvSortOrder.setOnItemClickListener { _, _, position, _ ->
            sortOrder = if (position == 0) "asc" else "desc"
            filterAndSortTrainees()
        }
    }

    private fun filterAndSortTrainees() {
        // Filter trainees based on search query
        filteredTrainees.clear()
        
        trainees.forEach { trainee ->
            val matchesSearch = searchQuery.isEmpty() || 
                trainee.name.contains(searchQuery, ignoreCase = true) ||
                trainee.coachName.contains(searchQuery, ignoreCase = true) ||
                trainee.status.contains(searchQuery, ignoreCase = true) ||
                trainee.age.toString().contains(searchQuery) ||
                trainee.monthlyFee.toString().contains(searchQuery)
            
            if (matchesSearch) {
                filteredTrainees.add(trainee)
            }
        }

        // Sort filtered trainees
        filteredTrainees.sortWith { trainee1, trainee2 ->
            val comparison = when (sortBy) {
                "name" -> trainee1.name.compareTo(trainee2.name, ignoreCase = true)
                "age" -> trainee1.age.compareTo(trainee2.age)
                "coachName" -> trainee1.coachName.compareTo(trainee2.coachName, ignoreCase = true)
                "startingDate" -> {
                    val date1 = trainee1.startingDate?.toDate() ?: Date(0)
                    val date2 = trainee2.startingDate?.toDate() ?: Date(0)
                    date1.compareTo(date2)
                }
                "monthlyFee" -> trainee1.monthlyFee.compareTo(trainee2.monthlyFee)
                "isPaid" -> trainee1.isPaid.compareTo(trainee2.isPaid)
                else -> trainee1.name.compareTo(trainee2.name, ignoreCase = true)
            }
            
            if (sortOrder == "desc") -comparison else comparison
        }

        updateStats()
        traineeAdapter.notifyDataSetChanged()
    }

    private fun loadCoaches() {
        firestore.collection("employees")
            .whereEqualTo("role", "coach")
            .whereEqualTo("status", "active")
            .get()
            .addOnSuccessListener { documents ->
                coaches.clear()
                for (document in documents) {
                    val name = document.getString("name") ?: ""
                    if (name.isNotEmpty()) {
                        coaches.add(name)
                    }
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Error loading coaches: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun loadTrainees() {
        firestore.collection("trainees")
            .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Toast.makeText(requireContext(), "Error loading trainees: ${e.message}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                trainees.clear()
                if (snapshot != null) {
                    for (document in snapshot) {
                        val trainee = document.toObject(Trainee::class.java)
                        trainee?.let {
                            it.id = document.id
                            trainees.add(it)
                        }
                    }
                }

                filterAndSortTrainees()
            }
    }

    private fun updateStats() {
        val total = filteredTrainees.size
        val active = filteredTrainees.count { it.status == "active" }
        
        binding.tvTotalCount.text = total.toString()
        binding.tvActiveCount.text = active.toString()
    }

    private fun showAddTraineeDialog() {
        if (coaches.isEmpty()) {
            Toast.makeText(requireContext(), "No coaches available. Please add coaches first.", Toast.LENGTH_LONG).show()
            return
        }

        val dialog = AddTraineeDialog(requireContext(), coaches)
        dialog.setOnSaveClickListener { trainee ->
            addTrainee(trainee)
        }
        dialog.show()
    }

    private fun showEditTraineeDialog(trainee: Trainee) {
        val dialog = AddTraineeDialog(requireContext(), coaches, trainee)
        dialog.setOnSaveClickListener { updatedTrainee ->
            updateTrainee(updatedTrainee)
        }
        dialog.show()
    }

    private fun addTrainee(trainee: Trainee) {
        val traineeData = hashMapOf(
            "name" to trainee.name,
            "age" to trainee.age,
            "startingDate" to trainee.startingDate,
            "endingDate" to trainee.endingDate,
            "coachName" to trainee.coachName,
            "monthlyFee" to trainee.monthlyFee,
            "isPaid" to trainee.isPaid,
            "status" to trainee.status,
            "createdAt" to Timestamp.now(),
            "updatedAt" to Timestamp.now()
        )

        firestore.collection("trainees")
            .add(traineeData)
            .addOnSuccessListener { documentReference ->
                Toast.makeText(requireContext(), "Trainee added successfully", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Error adding trainee: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun updateTrainee(trainee: Trainee) {
        val traineeData = hashMapOf(
            "name" to trainee.name,
            "age" to trainee.age,
            "startingDate" to trainee.startingDate,
            "endingDate" to trainee.endingDate,
            "coachName" to trainee.coachName,
            "monthlyFee" to trainee.monthlyFee,
            "isPaid" to trainee.isPaid,
            "status" to trainee.status,
            "updatedAt" to Timestamp.now()
        )

        firestore.collection("trainees").document(trainee.id)
            .update(traineeData as Map<String, Any>)
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "Trainee updated successfully", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Error updating trainee: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun deleteTrainee(trainee: Trainee) {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Delete Trainee")
            .setMessage("Are you sure you want to delete ${trainee.name}? This action cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                firestore.collection("trainees").document(trainee.id)
                    .delete()
                    .addOnSuccessListener {
                        Toast.makeText(requireContext(), "Trainee deleted successfully", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(requireContext(), "Error deleting trainee: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun togglePaymentStatus(trainee: Trainee) {
        val newPaymentStatus = !trainee.isPaid
        firestore.collection("trainees").document(trainee.id)
            .update("isPaid", newPaymentStatus, "updatedAt", Timestamp.now())
            .addOnSuccessListener {
                val message = if (newPaymentStatus) "Payment marked as paid" else "Payment marked as unpaid"
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Error updating payment status: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(): TraineesFragment {
            return TraineesFragment()
        }
    }
} 