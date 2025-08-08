package com.example.double_dot_demo.utils

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.widget.ImageView
import android.widget.TextView
import com.example.double_dot_demo.R
import kotlinx.coroutines.*

class LoadingManager(private val loadingOverlay: View) {
    
    private var isShowing = false
    private var currentJob: Job? = null
    
    private val squashBallAnimation: ImageView? = loadingOverlay.findViewById(R.id.squashBallAnimation)
    private val loadingText: TextView? = loadingOverlay.findViewById(R.id.loadingText)
    
    fun showLoading(message: String = "Loading...") {
        if (isShowing) return
        
        try {
            isShowing = true
            loadingText?.text = message
            loadingOverlay.visibility = View.VISIBLE
            
            // Start squash ball animation
            startSquashBallAnimation()
            
        } catch (e: Exception) {
            android.util.Log.e("LoadingManager", "Error showing loading: ${e.message}")
        }
    }
    
    fun hideLoading() {
        if (!isShowing) return
        
        try {
            isShowing = false
            loadingOverlay.visibility = View.GONE
            
            // Stop animations
            stopSquashBallAnimation()
            
        } catch (e: Exception) {
            android.util.Log.e("LoadingManager", "Error hiding loading: ${e.message}")
        }
    }
    
    private fun startSquashBallAnimation() {
        squashBallAnimation?.let { ball ->
            try {
                // Rotation animation
                val rotationAnimator = ObjectAnimator.ofFloat(ball, "rotation", 0f, 360f).apply {
                    duration = 2000
                    repeatCount = ObjectAnimator.INFINITE
                    interpolator = LinearInterpolator()
                }
                
                // Scale animation for bounce effect
                val scaleXAnimator = ObjectAnimator.ofFloat(ball, "scaleX", 1f, 0.8f, 1f).apply {
                    duration = 1000
                    repeatCount = ObjectAnimator.INFINITE
                    interpolator = AccelerateDecelerateInterpolator()
                }
                
                val scaleYAnimator = ObjectAnimator.ofFloat(ball, "scaleY", 1f, 0.8f, 1f).apply {
                    duration = 1000
                    repeatCount = ObjectAnimator.INFINITE
                    interpolator = AccelerateDecelerateInterpolator()
                }
                
                // Combine animations
                val animatorSet = AnimatorSet().apply {
                    playTogether(rotationAnimator, scaleXAnimator, scaleYAnimator)
                }
                
                animatorSet.start()
                
            } catch (e: Exception) {
                android.util.Log.e("LoadingManager", "Error starting squash ball animation: ${e.message}")
            }
        }
    }
    
    private fun stopSquashBallAnimation() {
        squashBallAnimation?.let { ball ->
            try {
                ball.clearAnimation()
            } catch (e: Exception) {
                android.util.Log.e("LoadingManager", "Error stopping squash ball animation: ${e.message}")
            }
        }
    }
    
    // Auto-hide loading after a timeout
    fun showLoadingWithTimeout(message: String = "Loading...", timeoutMs: Long = 10000L) {
        showLoading(message)
        
        currentJob?.cancel()
        currentJob = CoroutineScope(Dispatchers.Main).launch {
            delay(timeoutMs)
            hideLoading()
        }
    }
    
    // Show loading for a specific operation
    suspend fun <T> withLoading(
        message: String = "Loading...",
        operation: suspend () -> T
    ): T {
        return try {
            showLoading(message)
            operation()
        } finally {
            hideLoading()
        }
    }
    
    fun cleanup() {
        currentJob?.cancel()
        hideLoading()
    }
}
