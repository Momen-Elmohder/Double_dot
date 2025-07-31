package com.example.double_dot_demo.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.double_dot_demo.R
import com.example.double_dot_demo.models.Employee
import java.text.SimpleDateFormat
import java.util.*

class EmployeeAdapter(
    private val employees: List<Employee>,
    private val onEditClick: (Employee) -> Unit,
    private val onDeleteClick: (Employee) -> Unit
) : RecyclerView.Adapter<EmployeeAdapter.EmployeeViewHolder>() {

    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    class EmployeeViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivAvatar: ImageView = itemView.findViewById(R.id.ivAvatar)
        val tvName: TextView = itemView.findViewById(R.id.tvName)
        val tvDetails: TextView = itemView.findViewById(R.id.tvDetails)
        val tvStatus: TextView = itemView.findViewById(R.id.tvStatus)
        val btnEdit: ImageView = itemView.findViewById(R.id.btnEdit)
        val btnDelete: ImageView = itemView.findViewById(R.id.btnDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EmployeeViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_employee, parent, false)
        return EmployeeViewHolder(view)
    }

    override fun onBindViewHolder(holder: EmployeeViewHolder, position: Int) {
        val employee = employees[position]
        
        holder.tvName.text = employee.name
        holder.tvDetails.text = buildDetailsText(employee)
        holder.tvStatus.text = employee.status.replaceFirstChar { it.uppercase() }
        
        // Set status color
        val statusColor = if (employee.status == "active") {
            holder.itemView.context.getColor(R.color.success_light)
        } else {
            holder.itemView.context.getColor(R.color.error_light)
        }
        holder.tvStatus.setTextColor(statusColor)

        holder.btnEdit.setOnClickListener {
            onEditClick(employee)
        }

        holder.btnDelete.setOnClickListener {
            onDeleteClick(employee)
        }
    }

    override fun getItemCount(): Int = employees.size

    private fun buildDetailsText(employee: Employee): String {
        val details = mutableListOf<String>()
        
        details.add("Role: ${employee.role.replace("_", " ").replaceFirstChar { it.uppercase() }}")
        details.add("Email: ${employee.email}")
        details.add("Phone: ${employee.phone}")
        details.add("Salary: $${employee.salary}")
        
        employee.hireDate?.let {
            details.add("Hired: ${dateFormat.format(it.toDate())}")
        }
        
        return details.joinToString(" • ")
    }
} 