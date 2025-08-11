package com.example.double_dot_demo.utils

import android.view.View

enum class Role(val key: String) {
    HEAD_COACH("head_coach"),
    COACH("coach"),
    ADMIN("admin");

    companion object {
        fun from(value: String?): Role {
            return when (value?.lowercase()?.replace(" ", "_")) {
                "head_coach" -> HEAD_COACH
                "coach" -> COACH
                "admin" -> ADMIN
                else -> COACH
            }
        }
    }
}

object Permissions {
    // Capability flags per role
    private val canViewExpenses = setOf(Role.HEAD_COACH)
    private val canEditExpenses = setOf(Role.HEAD_COACH)

    private val canViewSalaries = setOf(Role.HEAD_COACH, Role.ADMIN)
    private val canEditSalaries = setOf(Role.HEAD_COACH, Role.ADMIN)

    private val canViewEmployees = setOf(Role.HEAD_COACH, Role.ADMIN)
    private val canEditEmployees = setOf(Role.HEAD_COACH, Role.ADMIN)
    private val canViewEmployeeAttendance = setOf(Role.HEAD_COACH, Role.ADMIN)

    private val canViewWaitingList = setOf(Role.HEAD_COACH, Role.ADMIN)

    private val canViewTraineeDetailsFull = setOf(Role.HEAD_COACH, Role.ADMIN)

    fun canAccessExpenses(role: Role) = role in canViewExpenses
    fun canEditExpenses(role: Role) = role in canEditExpenses

    fun canAccessSalaries(role: Role) = role in canViewSalaries
    fun canEditSalaries(role: Role) = role in canEditSalaries

    fun canAccessEmployees(role: Role) = role in canViewEmployees
    fun canEditEmployees(role: Role) = role in canEditEmployees
    fun canAccessEmployeeAttendance(role: Role) = role in canViewEmployeeAttendance

    fun canAccessWaitingList(role: Role) = role in canViewWaitingList

    fun canViewTraineeFull(role: Role) = role in canViewTraineeDetailsFull

    // UI helper: show view only if allowed
    fun setVisibleIfAllowed(view: View?, allowed: Boolean) {
        view?.visibility = if (allowed) View.VISIBLE else View.GONE
    }
}

