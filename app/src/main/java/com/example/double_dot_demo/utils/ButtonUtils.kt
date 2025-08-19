package com.example.double_dot_demo.utils

import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import androidx.appcompat.widget.Toolbar
import kotlinx.coroutines.*

object ButtonUtils {
    
    private const val DEFAULT_DEBOUNCE_TIME = 500L
    private val clickJobs = mutableMapOf<View, Job>()
    
    /**
     * Set a debounced click listener on any view
     */
    fun View.setDebouncedClickListener(
        debounceTime: Long = DEFAULT_DEBOUNCE_TIME,
        onClick: () -> Unit
    ) {
        setOnClickListener {
            val existingJob = clickJobs[this]
            if (existingJob?.isActive == true) {
                return@setOnClickListener
            }
            
            val job = CoroutineScope(Dispatchers.Main).launch {
                onClick()
                delay(debounceTime)
                clickJobs.remove(this@setDebouncedClickListener)
            }
            clickJobs[this] = job
        }
    }
    
    /**
     * Temporarily disable a button and re-enable it after a delay
     */
    fun View.disableTemporarily(duration: Long = DEFAULT_DEBOUNCE_TIME) {
        isEnabled = false
        alpha = 0.5f
        
        CoroutineScope(Dispatchers.Main).launch {
            delay(duration)
            if (this@disableTemporarily.isAttachedToWindow) {
                isEnabled = true
                alpha = 1.0f
            }
        }
    }
    
    /**
     * Set a safe click listener that checks lifecycle state
     */
    fun View.setSafeClickListener(onClick: () -> Unit) {
        setOnClickListener {
            if (!isAttachedToWindow) return@setOnClickListener
            
            try {
                onClick()
            } catch (e: Exception) {
                android.util.Log.e("ButtonUtils", "Error in click listener: ${e.message}")
            }
        }
    }
    
    /**
     * Set a debounced click listener for navigation buttons
     */
    fun View.setNavigationClickListener(
        debounceTime: Long = DEFAULT_DEBOUNCE_TIME,
        onNavigationClick: () -> Unit
    ) {
        setOnClickListener {
            if (NavigationUtils.isNavigationInProgress()) return@setOnClickListener
            
            val existingJob = clickJobs[this]
            if (existingJob?.isActive == true) {
                return@setOnClickListener
            }
            
            // Temporarily disable the button
            disableTemporarily(debounceTime)
            
            val job = CoroutineScope(Dispatchers.Main).launch {
                onNavigationClick()
                delay(debounceTime)
                clickJobs.remove(this@setNavigationClickListener)
            }
            clickJobs[this] = job
        }
    }
    
    /**
     * Cancel all pending click operations
     */
    fun cancelAllClicks() {
        clickJobs.values.forEach { it.cancel() }
        clickJobs.clear()
    }
    
    /**
     * Cancel click operations for a specific view
     */
    fun cancelClick(view: View) {
        clickJobs[view]?.cancel()
        clickJobs.remove(view)
    }
}
