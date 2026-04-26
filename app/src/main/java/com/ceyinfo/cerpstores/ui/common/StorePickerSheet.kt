package com.ceyinfo.cerpstores.ui.common

import android.app.Activity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ceyinfo.cerpstores.R
import com.ceyinfo.cerpstores.data.model.MyStore
import com.ceyinfo.cerpstores.data.remote.ApiClient
import com.ceyinfo.cerpstores.databinding.ItemStoreBinding
import com.ceyinfo.cerpstores.databinding.ItemStoreBuHeaderBinding
import com.ceyinfo.cerpstores.databinding.SheetStorePickerBinding
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.launch

/**
 * Reusable bottom sheet that lists every store the current user can write
 * to (via `/store-mobile/stores/my-stores`) grouped by business unit.
 *
 * Caller supplies a callback that receives the chosen `MyStore`. The sheet
 * dismisses itself before invoking the callback so the caller can navigate
 * straight away.
 *
 * Usage:
 * ```
 * StorePickerSheet.show(activity, title = "Goods Receipt — pick a store") { store ->
 *     startActivity(Intent(this, GrnCreateActivity::class.java).apply {
 *         putExtra("store_id", store.storeId)
 *         putExtra("store_name", store.name)
 *     })
 * }
 * ```
 *
 * Three render states swap on the same root: Loading (spinner), Empty
 * (rose-coloured help card), Loaded (grouped list). The empty state's
 * copy is intentionally specific so the user knows the fix is admin-side.
 */
object StorePickerSheet {

    /** Sealed-style row model for the flat adapter. */
    private sealed class Row {
        data class BuHeader(val name: String) : Row()
        data class StoreRow(val store: MyStore) : Row()
    }

    fun show(
        activity: Activity,
        title: String? = null,
        subtitle: String? = null,
        onPick: (MyStore) -> Unit,
    ) {
        val dialog = BottomSheetDialog(activity, R.style.Theme_Stores_BottomSheet)
        val binding = SheetStorePickerBinding.inflate(LayoutInflater.from(activity))
        dialog.setContentView(binding.root)

        title?.let { binding.tvTitle.text = it }
        subtitle?.let { binding.tvSubtitle.text = it }

        binding.rvStores.layoutManager = LinearLayoutManager(activity)

        // Loading state
        binding.progress.visibility = View.VISIBLE
        binding.rvStores.visibility = View.GONE
        binding.emptyContainer.visibility = View.GONE

        dialog.show()

        // Coroutine scope: prefer the host activity's lifecycle so the
        // fetch cancels if the activity is destroyed mid-request.
        val scope = (activity as? LifecycleOwner)?.lifecycleScope
        if (scope == null) {
            // Defensive — every Activity is a LifecycleOwner since AppCompat
            // 1.0, but keep the sheet usable even if a future caller passes
            // a plain Activity.
            renderError(binding)
            return
        }

        scope.launch {
            try {
                val resp = ApiClient.getService(activity).getMyStores()
                if (!resp.isSuccessful || resp.body()?.success != true) {
                    renderError(binding)
                    return@launch
                }

                val stores = resp.body()?.data.orEmpty()
                if (stores.isEmpty()) {
                    renderError(binding)
                    return@launch
                }

                renderList(binding, stores) { store ->
                    dialog.dismiss()
                    onPick(store)
                }
            } catch (_: Exception) {
                renderError(binding)
            }
        }
    }

    private fun renderError(binding: SheetStorePickerBinding) {
        binding.progress.visibility = View.GONE
        binding.rvStores.visibility = View.GONE
        binding.emptyContainer.visibility = View.VISIBLE
    }

    private fun renderList(
        binding: SheetStorePickerBinding,
        stores: List<MyStore>,
        onTap: (MyStore) -> Unit,
    ) {
        binding.progress.visibility = View.GONE
        binding.emptyContainer.visibility = View.GONE
        binding.rvStores.visibility = View.VISIBLE

        // Flatten to header rows + store rows. `null` BU name is bucketed
        // under "Other" so unbound stores still appear (rare but possible).
        val rows = mutableListOf<Row>()
        val grouped = stores.groupBy { it.businessUnitName ?: "Other" }
        for ((bu, list) in grouped.toSortedMap()) {
            rows.add(Row.BuHeader(bu))
            list.sortedBy { it.name }.forEach { rows.add(Row.StoreRow(it)) }
        }

        binding.rvStores.adapter = StoresAdapter(rows, onTap)
    }

    // ── Adapter ────────────────────────────────────────────────────

    private class StoresAdapter(
        private val rows: List<Row>,
        private val onTap: (MyStore) -> Unit,
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        companion object {
            const val TYPE_HEADER = 0
            const val TYPE_STORE = 1
        }

        override fun getItemCount() = rows.size

        override fun getItemViewType(position: Int) = when (rows[position]) {
            is Row.BuHeader -> TYPE_HEADER
            is Row.StoreRow -> TYPE_STORE
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            return if (viewType == TYPE_HEADER) {
                HeaderVH(ItemStoreBuHeaderBinding.inflate(inflater, parent, false))
            } else {
                StoreVH(ItemStoreBinding.inflate(inflater, parent, false))
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val row = rows[position]) {
                is Row.BuHeader -> (holder as HeaderVH).bind(row)
                is Row.StoreRow -> (holder as StoreVH).bind(row.store, onTap)
            }
        }

        class HeaderVH(private val b: ItemStoreBuHeaderBinding) : RecyclerView.ViewHolder(b.root) {
            fun bind(row: Row.BuHeader) {
                b.tvBuName.text = row.name
            }
        }

        class StoreVH(private val b: ItemStoreBinding) : RecyclerView.ViewHolder(b.root) {
            fun bind(store: MyStore, onTap: (MyStore) -> Unit) {
                b.tvStoreName.text = store.name
                // Subtitle prefers code + location; falls back to type.
                val parts = listOfNotNull(
                    store.code?.takeIf { it.isNotBlank() },
                    store.location?.takeIf { it.isNotBlank() },
                    store.type?.takeIf { it.isNotBlank() },
                )
                b.tvStoreMeta.text =
                    if (parts.isEmpty()) "—" else parts.joinToString(" · ")
                b.root.setOnClickListener { onTap(store) }
            }
        }
    }
}
