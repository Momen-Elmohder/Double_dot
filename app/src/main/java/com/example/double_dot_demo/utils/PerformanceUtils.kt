package com.example.double_dot_demo.utils

import android.view.View
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean

object PerformanceUtils {
    
    // Debounce click listener to prevent rapid clicks
    fun View.setDebouncedClickListener(
        debounceTime: Long = 500L,
        onClick: () -> Unit
    ) {
        val isClickable = AtomicBoolean(true)
        
        setOnClickListener {
            if (isClickable.getAndSet(false)) {
                onClick()
                CoroutineScope(Dispatchers.Main).launch {
                    delay(debounceTime)
                    isClickable.set(true)
                }
            }
        }
    }
    
    // Disable button temporarily after click
    fun View.disableTemporarily(duration: Long = 1000L) {
        isEnabled = false
        CoroutineScope(Dispatchers.Main).launch {
            delay(duration)
            isEnabled = true
        }
    }
    
    // Safe click listener with error handling
    fun View.setSafeClickListener(onClick: () -> Unit) {
        setOnClickListener {
            try {
                onClick()
            } catch (e: Exception) {
                android.util.Log.e("PerformanceUtils", "Error in click listener: ${e.message}")
            }
        }
    }
    
    // Optimized RecyclerView scroll listener
    fun RecyclerView.setOptimizedScrollListener(
        onScrollStateChanged: (Int) -> Unit = {},
        onScrolled: (Int, Int) -> Unit = { _, _ -> }
    ) {
        addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                onScrollStateChanged(newState)
            }
            
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                onScrolled(dx, dy)
            }
        })
    }
    
    // Clear ImageView resources to prevent memory leaks
    fun ImageView.clearResources() {
        setImageDrawable(null)
        setImageBitmap(null)
    }
    
    // Safe string operations
    fun String?.safeSubstring(startIndex: Int, endIndex: Int): String {
        return try {
            this?.substring(startIndex, endIndex.coerceAtMost(this.length)) ?: ""
        } catch (e: Exception) {
            ""
        }
    }
    
    // Safe number parsing
    fun String?.safeToInt(default: Int = 0): Int {
        return try {
            this?.toInt() ?: default
        } catch (e: NumberFormatException) {
            default
        }
    }
    
    fun String?.safeToDouble(default: Double = 0.0): Double {
        return try {
            this?.toDouble() ?: default
        } catch (e: NumberFormatException) {
            default
        }
    }
    
    // Memory-efficient list operations
    fun <T> List<T>?.safeGet(index: Int): T? {
        return try {
            this?.getOrNull(index)
        } catch (e: IndexOutOfBoundsException) {
            null
        }
    }
    
    // Safe date formatting
    fun formatDateSafely(date: Any?, pattern: String = "dd/MM/yyyy"): String {
        return try {
            when (date) {
                is com.google.firebase.Timestamp -> {
                    val simpleDateFormat = java.text.SimpleDateFormat(pattern, java.util.Locale.getDefault())
                    simpleDateFormat.format(date.toDate())
                }
                is java.util.Date -> {
                    val simpleDateFormat = java.text.SimpleDateFormat(pattern, java.util.Locale.getDefault())
                    simpleDateFormat.format(date)
                }
                else -> "Unknown"
            }
        } catch (e: Exception) {
            "Unknown"
        }
    }
    
    // Coroutine scope for background operations
    fun launchInBackground(block: suspend CoroutineScope.() -> Unit): Job {
        return CoroutineScope(Dispatchers.IO).launch {
            try {
                block()
            } catch (e: Exception) {
                android.util.Log.e("PerformanceUtils", "Background operation failed: ${e.message}")
            }
        }
    }
    
    // Main thread execution
    fun runOnMainThread(block: () -> Unit) {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                block()
            } catch (e: Exception) {
                android.util.Log.e("PerformanceUtils", "Main thread operation failed: ${e.message}")
            }
        }
    }
    
    // Safe view operations
    fun View.safeSetVisibility(visibility: Int) {
        try {
            this.visibility = visibility
        } catch (e: Exception) {
            android.util.Log.e("PerformanceUtils", "Error setting visibility: ${e.message}")
        }
    }
    
    fun View.safeSetText(text: String?) {
        try {
            if (this is android.widget.TextView) {
                this.text = text ?: ""
            }
        } catch (e: Exception) {
            android.util.Log.e("PerformanceUtils", "Error setting text: ${e.message}")
        }
    }
}
