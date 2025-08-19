package com.example.double_dot_demo.utils

import android.app.Activity
import android.content.Context
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentTransaction
import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.*

object NavigationUtils {
    
    private const val NAVIGATION_DEBOUNCE_TIME = 300L
    private var lastNavigationTime = 0L
    private var isNavigating = false
    private var navigationJob: Job? = null
    
    /**
     * Safely load a fragment with comprehensive safety checks
     */
    fun safeLoadFragment(
        activity: FragmentActivity?,
        fragment: Fragment,
        containerId: Int,
        addToBackStack: Boolean = true
    ): Boolean {
        if (activity == null || activity.isFinishing || activity.isDestroyed) return false
        if (isNavigating) return false
        
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastNavigationTime < NAVIGATION_DEBOUNCE_TIME) {
            return false
        }
        
        return try {
            isNavigating = true
            lastNavigationTime = currentTime
            
            // Cancel any pending navigation
            navigationJob?.cancel()
            
            val fragmentManager = activity.supportFragmentManager
            
            // Check if fragment manager is in a valid state
            if (fragmentManager.isDestroyed || fragmentManager.isStateSaved) {
                isNavigating = false
                return false
            }
            
            // Execute pending transactions safely
            if (!fragmentManager.isDestroyed && !fragmentManager.isStateSaved) {
                fragmentManager.executePendingTransactions()
            }
            
            // Double-check state after executing transactions
            if (activity.isFinishing || activity.isDestroyed || fragmentManager.isDestroyed) {
                isNavigating = false
                return false
            }
            
            // Create and execute transaction
            val transaction = fragmentManager.beginTransaction()
                .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
                .replace(containerId, fragment)
            
            if (addToBackStack) {
                transaction.addToBackStack(null)
            }
            
            // Commit with appropriate method
            if (fragmentManager.isStateSaved) {
                transaction.commitAllowingStateLoss()
            } else {
                transaction.commit()
            }
            
            // Reset navigation flag after delay
            navigationJob = CoroutineScope(Dispatchers.Main).launch {
                delay(NAVIGATION_DEBOUNCE_TIME)
                isNavigating = false
            }
            
            true
        } catch (e: Exception) {
            android.util.Log.e("NavigationUtils", "Error loading fragment: ${e.message}")
            isNavigating = false
            false
        }
    }
    
    /**
     * Safely show a toast message
     */
    fun safeShowToast(context: Context?, message: String) {
        if (context == null) return
        if (context is Activity && (context.isFinishing || context.isDestroyed)) return
        
        try {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            android.util.Log.e("NavigationUtils", "Error showing toast: ${e.message}")
        }
    }
    
    /**
     * Check if navigation is currently in progress
     */
    fun isNavigationInProgress(): Boolean = isNavigating
    
    /**
     * Cancel any pending navigation operations
     */
    fun cancelNavigation() {
        navigationJob?.cancel()
        isNavigating = false
    }
    
    /**
     * Check if a fragment is in a valid state for UI operations
     */
    fun isFragmentValid(fragment: Fragment?): Boolean {
        return fragment != null && 
               fragment.isAdded && 
               fragment.context != null && 
               !fragment.isDetached && 
               !fragment.isRemoving &&
               fragment.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
    }
    
    /**
     * Check if an activity is in a valid state for UI operations
     */
    fun isActivityValid(activity: Activity?): Boolean {
        return activity != null && 
               !activity.isFinishing && 
               !activity.isDestroyed
    }
}
