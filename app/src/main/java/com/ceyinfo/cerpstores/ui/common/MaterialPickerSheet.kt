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
import com.ceyinfo.cerpstores.data.model.Material
import com.ceyinfo.cerpstores.data.remote.ApiClient
import com.ceyinfo.cerpstores.databinding.ItemLookupRowBinding
import com.ceyinfo.cerpstores.databinding.SheetMaterialPickerBinding
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Searchable material picker. Hits `/store-mobile/lookup/materials` with
 * a 300ms debounce so we don't flood the API on every keystroke.
 *
 * Usage:
 * ```
 * MaterialPickerSheet.show(activity) { material ->
 *     // material has .materialId, .name, .sku, .baseUnitSymbol
 * }
 * ```
 *
 * Three render states:
 *   - Loading: spinner over the list
 *   - Empty:   "no materials match" message
 *   - Loaded:  scrollable list, tap to pick
 *
 * Search runs on the wire (server-side), not client-side, so the picker
 * stays usable for tenants with thousands of materials. We never paginate
 * past page 1 — first 50 hits are enough; users can refine via search.
 */
object MaterialPickerSheet {

    fun show(activity: Activity, onPick: (Material) -> Unit) {
        val dialog = BottomSheetDialog(activity, R.style.Theme_Stores_BottomSheet)
        val binding = SheetMaterialPickerBinding.inflate(LayoutInflater.from(activity))
        dialog.setContentView(binding.root)

        binding.tvTitle.setText(R.string.material_picker_title)
        binding.etSearch.hint = activity.getString(R.string.material_picker_search)
        binding.tvEmpty.setText(R.string.material_picker_empty)
        binding.rvItems.layoutManager = LinearLayoutManager(activity)

        val items = mutableListOf<Material>()
        val adapter = LookupAdapter(items) { material ->
            dialog.dismiss()
            onPick(material)
        }
        binding.rvItems.adapter = adapter

        val scope = (activity as? LifecycleOwner)?.lifecycleScope
            ?: run { renderError(binding); dialog.show(); return }

        // Debounced search — re-fire after 300ms of idle typing. The
        // previous job is cancelled if the user keeps typing, so only
        // the most recent query hits the server.
        var searchJob: Job? = null
        fun fetch(query: String) {
            searchJob?.cancel()
            searchJob = scope.launch {
                delay(300)
                binding.progress.visibility = View.VISIBLE
                binding.tvEmpty.visibility = View.GONE
                try {
                    val resp = ApiClient.getService(activity)
                        .getMaterials(search = query.takeIf { it.isNotBlank() })
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

        // Initial fetch
        fetch("")
        dialog.show()
    }

    private fun renderError(binding: SheetMaterialPickerBinding) {
        binding.progress.visibility = View.GONE
        binding.tvEmpty.visibility = View.VISIBLE
    }

    // ── Adapter (also used by SupplierPickerSheet via the MaterialEntry
    //    factory) ──────────────────────────────────────────────────────

    private class LookupAdapter(
        private val rows: MutableList<Material>,
        private val onTap: (Material) -> Unit,
    ) : RecyclerView.Adapter<LookupAdapter.VH>() {

        class VH(val b: ItemLookupRowBinding) : RecyclerView.ViewHolder(b.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(ItemLookupRowBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun getItemCount() = rows.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val mat = rows[position]
            holder.b.tvTitle.text = mat.name
            // Subtitle: SKU + unit symbol when present, falls back to "—".
            val parts = listOfNotNull(
                mat.sku?.takeIf { it.isNotBlank() },
                mat.baseUnitSymbol?.takeIf { it.isNotBlank() },
            )
            holder.b.tvSubtitle.text = parts.joinToString(" · ").ifEmpty { "—" }
            holder.b.root.setOnClickListener { onTap(mat) }
        }
    }
}
