package com.ceyinfo.cerpstores.ui.issue

import android.app.Activity
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ceyinfo.cerpstores.R
import com.ceyinfo.cerpstores.data.model.Employee
import com.ceyinfo.cerpstores.data.remote.ApiClient
import com.ceyinfo.cerpstores.databinding.SheetEmployeePickerBinding
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Bottom sheet employee picker.  Optionally pre-filtered by department_id —
 * if the user picked a department first we narrow the list automatically.
 */
object EmployeePickerSheet {

    fun show(
        activity: Activity,
        scope: LifecycleCoroutineScope,
        departmentId: String? = null,
        onPick: (Employee) -> Unit,
    ) {
        val dialog = BottomSheetDialog(activity, R.style.Theme_Stores_BottomSheet)
        val binding = SheetEmployeePickerBinding.inflate(LayoutInflater.from(activity))
        dialog.setContentView(binding.root)

        val items = mutableListOf<Employee>()
        val adapter = EmpAdapter(items) { emp ->
            dialog.dismiss()
            onPick(emp)
        }
        binding.rvEmployees.layoutManager = LinearLayoutManager(activity)
        binding.rvEmployees.adapter = adapter

        var searchJob: Job? = null

        fun load(query: String) {
            binding.progress.visibility = View.VISIBLE
            binding.tvEmpty.visibility = View.GONE
            searchJob?.cancel()
            searchJob = scope.launch {
                delay(300)
                try {
                    val resp = ApiClient.getService(activity).getEmployees(
                        search = query.ifBlank { null },
                        departmentId = departmentId,
                    )
                    val rows = resp.body()?.data.orEmpty()
                    items.clear()
                    items.addAll(rows)
                    adapter.notifyDataSetChanged()
                    binding.tvEmpty.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
                } catch (_: Exception) {
                    binding.tvEmpty.visibility = View.VISIBLE
                } finally {
                    binding.progress.visibility = View.GONE
                }
            }
        }

        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                load(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        load("")
        dialog.show()
    }

    private class EmpAdapter(
        private val rows: List<Employee>,
        private val onTap: (Employee) -> Unit,
    ) : RecyclerView.Adapter<EmpAdapter.VH>() {

        class VH(val tv: TextView) : RecyclerView.ViewHolder(tv)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val tv = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_lookup_row, parent, false) as TextView
            return VH(tv)
        }

        override fun getItemCount() = rows.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val emp = rows[position]
            val meta = listOfNotNull(
                emp.employeeNumber?.takeIf { it.isNotBlank() },
                emp.departmentName?.takeIf { it.isNotBlank() },
            ).joinToString("  ·  ")
            holder.tv.text = if (meta.isNotBlank()) "${emp.name}  ·  $meta" else emp.name
            holder.tv.setOnClickListener { onTap(emp) }
        }
    }
}
