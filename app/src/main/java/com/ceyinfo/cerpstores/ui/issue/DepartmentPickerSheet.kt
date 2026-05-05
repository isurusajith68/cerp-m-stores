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
import com.ceyinfo.cerpstores.data.model.Department
import com.ceyinfo.cerpstores.data.remote.ApiClient
import com.ceyinfo.cerpstores.databinding.SheetDepartmentPickerBinding
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Bottom sheet department picker.  Mirrors SupplierPickerSheet but hits
 * `/store-mobile/lookup/departments`.  Debounces search at 300 ms to avoid
 * hammering the API while the user is typing.
 */
object DepartmentPickerSheet {

    fun show(
        activity: Activity,
        scope: LifecycleCoroutineScope,
        onPick: (Department) -> Unit,
    ) {
        val dialog = BottomSheetDialog(activity, R.style.Theme_Stores_BottomSheet)
        val binding = SheetDepartmentPickerBinding.inflate(LayoutInflater.from(activity))
        dialog.setContentView(binding.root)

        val items = mutableListOf<Department>()
        val adapter = DeptAdapter(items) { dept ->
            dialog.dismiss()
            onPick(dept)
        }
        binding.rvDepartments.layoutManager = LinearLayoutManager(activity)
        binding.rvDepartments.adapter = adapter

        var searchJob: Job? = null

        fun load(query: String) {
            binding.progress.visibility = View.VISIBLE
            binding.tvEmpty.visibility = View.GONE
            searchJob?.cancel()
            searchJob = scope.launch {
                delay(300)
                try {
                    val resp = ApiClient.getService(activity)
                        .getDepartments(search = query.ifBlank { null })
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

    private class DeptAdapter(
        private val rows: List<Department>,
        private val onTap: (Department) -> Unit,
    ) : RecyclerView.Adapter<DeptAdapter.VH>() {

        class VH(val tv: TextView) : RecyclerView.ViewHolder(tv)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val tv = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_lookup_row, parent, false) as TextView
            return VH(tv)
        }

        override fun getItemCount() = rows.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val dept = rows[position]
            holder.tv.text = dept.name + if (!dept.code.isNullOrBlank()) "  ·  ${dept.code}" else ""
            holder.tv.setOnClickListener { onTap(dept) }
        }
    }
}
