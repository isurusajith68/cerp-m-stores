package com.ceyinfo.cerpstores.ui.common

import android.app.Activity
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ceyinfo.cerpstores.R
import com.ceyinfo.cerpstores.data.model.Supplier
import com.ceyinfo.cerpstores.data.remote.ApiClient
import com.ceyinfo.cerpstores.databinding.ItemLookupRowBinding
import com.ceyinfo.cerpstores.databinding.SheetMaterialPickerBinding
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Searchable supplier picker. Mirrors MaterialPickerSheet — same shell
 * layout (sheet_material_picker.xml), different endpoint + data class.
 *
 * Suppliers are typically small in count (dozens, not thousands), so we
 * don't paginate — `/store-mobile/lookup/suppliers` returns the full
 * active+approved set sorted by name. Search is still server-side so
 * the picker stays consistent with the material flow.
 */
object SupplierPickerSheet {

    fun show(activity: Activity, onPick: (Supplier) -> Unit) {
        val dialog = BottomSheetDialog(activity, R.style.Theme_Stores_BottomSheet)
        val binding = SheetMaterialPickerBinding.inflate(LayoutInflater.from(activity))
        dialog.setContentView(binding.root)

        binding.tvTitle.setText(R.string.supplier_picker_title)
        binding.etSearch.hint = activity.getString(R.string.supplier_picker_search)
        binding.tvEmpty.setText(R.string.supplier_picker_empty)
        binding.rvItems.layoutManager = LinearLayoutManager(activity)

        val items = mutableListOf<Supplier>()
        val adapter = LookupAdapter(items) { supplier ->
            dialog.dismiss()
            onPick(supplier)
        }
        binding.rvItems.adapter = adapter

        val scope = (activity as? LifecycleOwner)?.lifecycleScope
            ?: run { renderError(binding); dialog.show(); return }

        var searchJob: Job? = null
        fun fetch(query: String) {
            searchJob?.cancel()
            searchJob = scope.launch {
                delay(300)
                binding.progress.visibility = View.VISIBLE
                binding.tvEmpty.visibility = View.GONE
                try {
                    val resp = ApiClient.getService(activity)
                        .getSuppliers(search = query.takeIf { it.isNotBlank() })
                    if (resp.isSuccessful && resp.body()?.success == true) {
                        val rows = resp.body()?.data.orEmpty()
                        items.clear()
                        items.addAll(rows)
                        adapter.notifyDataSetChanged()
                        binding.tvEmpty.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
                    }
                } catch (_: Exception) {
                    items.clear()
                    adapter.notifyDataSetChanged()
                    renderError(binding)
                } finally {
                    binding.progress.visibility = View.GONE
                }
            }
        }

        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                fetch(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        fetch("")
        dialog.show()
    }

    private fun renderError(binding: SheetMaterialPickerBinding) {
        binding.progress.visibility = View.GONE
        binding.tvEmpty.visibility = View.VISIBLE
    }

    private class LookupAdapter(
        private val rows: MutableList<Supplier>,
        private val onTap: (Supplier) -> Unit,
    ) : RecyclerView.Adapter<LookupAdapter.VH>() {

        class VH(val b: ItemLookupRowBinding) : RecyclerView.ViewHolder(b.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(ItemLookupRowBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun getItemCount() = rows.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val sup = rows[position]
            holder.b.tvTitle.text = sup.supplierName
            holder.b.tvSubtitle.text = sup.supplierCode?.takeIf { it.isNotBlank() } ?: "—"
            holder.b.root.setOnClickListener { onTap(sup) }
        }
    }
}
