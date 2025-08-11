package com.example.double_dot_demo.fragments

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.double_dot_demo.MainActivity
import com.example.double_dot_demo.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AccountFragment : Fragment() {

    private lateinit var etUsername: TextInputEditText
    private lateinit var tvEmail: TextView
    private lateinit var etPhone: TextInputEditText
    private lateinit var tvRole: TextView
    private lateinit var btnSave: MaterialButton
    private lateinit var btnLogout: MaterialButton

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance() }

    // Realtime state
    private var employeeDocId: String? = null
    private var isInitializing: Boolean = false
    private var pendingSaveJob: Job? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_account, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        etUsername = view.findViewById(R.id.etUsername)
        tvEmail = view.findViewById(R.id.tvEmail)
        etPhone = view.findViewById(R.id.etPhone)
        tvRole = view.findViewById(R.id.tvRole)
        btnSave = view.findViewById(R.id.btnSave)
        btnLogout = view.findViewById(R.id.btnLogout)

        loadAccount()
        setupActions()
        setupRealtimeSavers()
    }

    private fun loadAccount() {
        val user = auth.currentUser ?: return
        val email = user.email ?: "-"
        val uid = user.uid

        isInitializing = true
        tvEmail.text = email
        etUsername.setText(user.displayName ?: "")
        etPhone.setText("")
        tvRole.text = "-"

        // Load role from users collection
        db.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                val role = doc.getString("role") ?: "-"
                val phone = doc.getString("phone") ?: ""
                tvRole.text = role.replace('_', ' ')
                if (phone.isNotEmpty()) etPhone.setText(phone)
            }
            .addOnCompleteListener { isInitializing = false }

        // Try to match an employee by email to get current name/phone and cache doc id
        if (email != "-") {
            db.collection("employees")
                .whereEqualTo("email", email)
                .limit(1)
                .get()
                .addOnSuccessListener { snap ->
                    val doc = snap.documents.firstOrNull()
                    if (doc != null) {
                        employeeDocId = doc.id
                        if (!isInitializing) return@addOnSuccessListener
                        etUsername.setText(doc.getString("name") ?: etUsername.text)
                        val empPhone = doc.getString("phone") ?: ""
                        if (empPhone.isNotEmpty()) etPhone.setText(empPhone)
                    }
                }
                .addOnCompleteListener { isInitializing = false }
        } else {
            isInitializing = false
        }
    }

    private fun setupActions() {
        btnSave.setOnClickListener {
            saveNow()
        }
        btnLogout.setOnClickListener {
            auth.signOut()
            activity?.let {
                val intent = Intent(it, MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                it.startActivity(intent)
                it.finish()
            }
        }
    }

    private fun setupRealtimeSavers() {
        val textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (isInitializing) return
                scheduleDebouncedSave()
            }
        }
        etUsername.addTextChangedListener(textWatcher)
        etPhone.addTextChangedListener(textWatcher)
    }

    private fun scheduleDebouncedSave() {
        pendingSaveJob?.cancel()
        pendingSaveJob = viewLifecycleOwner.lifecycleScope.launch {
            delay(800) // debounce window
            saveProfile(realtime = true)
        }
    }

    private fun saveNow() {
        pendingSaveJob?.cancel()
        viewLifecycleOwner.lifecycleScope.launch { saveProfile(realtime = false) }
    }

    private suspend fun saveProfile(realtime: Boolean) {
        val user = auth.currentUser ?: return
        val email = user.email ?: return
        val uid = user.uid
        val newName = etUsername.text?.toString()?.trim() ?: ""
        val newPhone = etPhone.text?.toString()?.trim() ?: ""
        if (newName.isEmpty()) {
            if (!realtime) {
                android.widget.Toast.makeText(requireContext(), "Name cannot be empty", android.widget.Toast.LENGTH_SHORT).show()
            }
            return
        }

        // Update Firebase Auth displayName
        try {
            user.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(newName).build())
        } catch (_: Exception) {}

        // Update users/{uid}
        val userUpdates = hashMapOf<String, Any>(
            "name" to newName,
            "phone" to newPhone,
            "updatedAt" to com.google.firebase.Timestamp.now()
        )
        db.collection("users").document(uid).set(userUpdates, com.google.firebase.firestore.SetOptions.merge())

        // Update or create employees doc
        val data = hashMapOf<String, Any>(
            "name" to newName,
            "phone" to newPhone,
            "updatedAt" to com.google.firebase.Timestamp.now()
        )

        val onSaved: (Boolean) -> Unit = {
            if (!realtime) {
                android.widget.Toast.makeText(requireContext(), "Saved", android.widget.Toast.LENGTH_SHORT).show()
            }
        }

        val existingId = employeeDocId
        if (!existingId.isNullOrEmpty()) {
            db.collection("employees").document(existingId)
                .set(data, com.google.firebase.firestore.SetOptions.merge())
                .addOnSuccessListener { onSaved(true) }
                .addOnFailureListener { e -> if (!realtime) android.widget.Toast.makeText(requireContext(), "Save failed: ${e.message}", android.widget.Toast.LENGTH_SHORT).show() }
        } else {
            // Try find again by email once; else create
            db.collection("employees").whereEqualTo("email", email).limit(1).get()
                .addOnSuccessListener { snap ->
                    val doc = snap.documents.firstOrNull()
                    if (doc != null) {
                        employeeDocId = doc.id
                        db.collection("employees").document(doc.id)
                            .set(data, com.google.firebase.firestore.SetOptions.merge())
                            .addOnSuccessListener { onSaved(true) }
                            .addOnFailureListener { e -> if (!realtime) android.widget.Toast.makeText(requireContext(), "Save failed: ${e.message}", android.widget.Toast.LENGTH_SHORT).show() }
                    } else {
                        val newDoc = hashMapOf(
                            "name" to newName,
                            "email" to email,
                            "phone" to newPhone,
                            "role" to (tvRole.text?.toString()?.replace(' ', '_') ?: "coach"),
                            "status" to "active",
                            "totalDays" to 0,
                            "attendanceDays" to emptyMap<String, Boolean>(),
                            "createdAt" to com.google.firebase.Timestamp.now(),
                            "updatedAt" to com.google.firebase.Timestamp.now()
                        )
                        db.collection("employees").add(newDoc)
                            .addOnSuccessListener { ref ->
                                employeeDocId = ref.id
                                onSaved(true)
                            }
                            .addOnFailureListener { e -> if (!realtime) android.widget.Toast.makeText(requireContext(), "Save failed: ${e.message}", android.widget.Toast.LENGTH_SHORT).show() }
                    }
                }
                .addOnFailureListener { e -> if (!realtime) android.widget.Toast.makeText(requireContext(), "Save failed: ${e.message}", android.widget.Toast.LENGTH_SHORT).show() }
        }
    }
}
