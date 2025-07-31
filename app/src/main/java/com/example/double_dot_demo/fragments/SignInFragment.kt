package com.example.double_dot_demo.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.double_dot_demo.MainActivity
import com.example.double_dot_demo.R
import com.example.double_dot_demo.databinding.FragmentSigninBinding
import com.example.double_dot_demo.viewmodel.SignInViewModel

class SignInFragment : Fragment() {
    
    private var _binding: FragmentSigninBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: SignInViewModel
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSigninBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Initialize ViewModel
        viewModel = ViewModelProvider(requireActivity())[SignInViewModel::class.java]
        
        setupViews()
        observeViewModel()
    }
    
    private fun setupViews() {
        binding.btnSignIn.setOnClickListener {
            performSignIn()
        }
        
        binding.tvForgotPassword.setOnClickListener {
            // TODO: Implement forgot password functionality
            Toast.makeText(requireContext(), "Forgot password feature coming soon", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun observeViewModel() {
        viewModel.signInState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is SignInViewModel.SignInState.Loading -> {
                    showLoading(true)
                    android.util.Log.d("SignInFragment", "Loading state")
                }
                is SignInViewModel.SignInState.Success -> {
                    showLoading(false)
                    android.util.Log.d("SignInFragment", "Success state with role: ${state.userRole}")
                    navigateToDashboard(state.userRole)
                }
                is SignInViewModel.SignInState.Error -> {
                    showLoading(false)
                    android.util.Log.e("SignInFragment", "Error state: ${state.message}")
                    showError(state.message)
                }
            }
        }
    }
    
    private fun performSignIn() {
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()
        
        if (validateInput(email, password)) {
            // Default role - will be determined by user data in Firestore
            viewModel.signIn(email, password, "user")
        }
    }
    
    private fun validateInput(email: String, password: String): Boolean {
        if (email.isEmpty()) {
            binding.tilEmail.error = "Email is required"
            return false
        }
        
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.tilEmail.error = "Please enter a valid email"
            return false
        }
        
        if (password.isEmpty()) {
            binding.tilPassword.error = "Password is required"
            return false
        }
        
        if (password.length < 6) {
            binding.tilPassword.error = "Password must be at least 6 characters"
            return false
        }
        
        // Clear errors
        binding.tilEmail.error = null
        binding.tilPassword.error = null
        
        return true
    }
    
    private fun showLoading(show: Boolean) {
        binding.progressIndicator.visibility = if (show) View.VISIBLE else View.GONE
        binding.btnSignIn.isEnabled = !show
    }
    
    private fun showError(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
    }
    
    private fun navigateToDashboard(userRole: String) {
        // This will be handled by the MainActivity
        (activity as? MainActivity)?.navigateToDashboard(userRole)
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
    
    companion object {
        fun newInstance(): SignInFragment {
            return SignInFragment()
        }
    }
} 