package com.ceyinfo.cerpstores.ui.inventory

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ceyinfo.cerpstores.R
import com.ceyinfo.cerpstores.data.model.InventoryRow
import com.ceyinfo.cerpstores.data.model.MyStore
import com.ceyinfo.cerpstores.data.remote.ApiClient
import com.ceyinfo.cerpstores.databinding.ActivityInventoryListBinding
import com.ceyinfo.cerpstores.databinding.ItemInventoryBinding
import com.ceyinfo.cerpstores.ui.common.BottomNav
import com.ceyinfo.cerpstores.ui.common.StorePickerSheet
import com.ceyinfo.cerpstores.ui.grn.GrnStatusStyle
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Inventory tab — read-only stock list across the user's reachable stores.
 *
 * Backed by `GET /store-mobile/inventory` which returns one row per
 * (store, material, batch). Filters: status-style chips for All / Low stock,
 * plus an optional "Store" chip (only shown when the user has 2+ stores).
 *
 * Pagination, P2R, and the empty/loading state-machine all match the GRN
 * list activity verbatim — see GrnListActivity for the comments on each
 * branch. Differences worth noting:
 *   - No "+" header button (read-only view).
 *   - Search box, debounced by 300ms (GRN list relies on filter chips alone).
 *   - Empty-state copy switches between "no matches" and "no stock yet"
 *     so users with an active search aren't told their store is empty.
 */
class InventoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityInventoryListBinding

    /** null = All; true = only low stock. The chip strip toggles between these. */
    private var onlyLowStock: Boolean = false

    /** null = all stores; otherwise filter to this store_id. */
    private var storeFilterId: String? = null
    private var storeFilterName: String? = null

    private var searchQuery: String = ""
    private var searchDebounceJob: Job? = null

    /** Cached list of reachable stores; controls whether the Store chip shows. */
    private var reachableStores: List<MyStore> = emptyList()

    private val items = mutableListOf<InventoryRow>()
    private val adapter = InventoryAdapter(items)

    private var page = 1
    private var totalCount = 0
    private var isLoading = false
    private var loadJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT

        binding = ActivityInventoryListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        BottomNav.bind(binding.bottomNav.root, this, BottomNav.Tab.INVENTORY)

        binding.rvInventory.layoutManager = LinearLayoutManager(this)
        binding.rvInventory.adapter = adapter

        binding.swipeRefresh.setColorSchemeResources(R.color.primary)
        binding.swipeRefresh.setOnRefreshListener { reload() }

        binding.rvInventory.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                if (dy <= 0 || isLoading) return
                val lm = rv.layoutManager as? LinearLayoutManager ?: return
                if (lm.findLastVisibleItemPosition() >= lm.itemCount - 3 && items.size < totalCount) {
                    loadPage(page + 1, append = true)
                }
            }
        })

        // Search — 300ms debounce so we don't fire a request per keystroke.
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val text = s?.toString().orEmpty()
                binding.btnClearSearch.visibility =
                    if (text.isNotBlank()) View.VISIBLE else View.GONE
                searchDebounceJob?.cancel()
                searchDebounceJob = lifecycleScope.launch {
                    delay(300)
                    if (text.trim() != searchQuery) {
                        searchQuery = text.trim()
                        reload()
                    }
                }
            }
        })
        binding.btnClearSearch.setOnClickListener {
            binding.etSearch.text?.clear()
            // afterTextChanged handles the reload via the debouncer.
        }

        buildFilterChips()
        // Fetch reachable stores once so the Store chip renders accurately.
        // We don't block on this — the inventory list loads in parallel.
        loadReachableStores()
        reload()
    }

    // ── Filter strip ────────────────────────────────────────────────

    private fun buildFilterChips() {
        binding.filterContainer.removeAllViews()

        addStatusChip(R.string.inventory_filter_all, isActive = !onlyLowStock) {
            if (!onlyLowStock) return@addStatusChip
            onlyLowStock = false
            buildFilterChips()
            reload()
        }
        addStatusChip(R.string.inventory_filter_low_stock, isActive = onlyLowStock) {
            if (onlyLowStock) return@addStatusChip
            onlyLowStock = true
            buildFilterChips()
            reload()
        }

        // Store filter — only shown when there's actually a choice to make.
        // For single-store users a store chip would be confusing; for users
        // with no reachable stores the API would just return [] anyway.
        if (reachableStores.size >= 2) {
            val label = storeFilterName ?: getString(R.string.inventory_filter_store_all)
            val chip = makeChip(label, isActive = storeFilterId != null) {
                StorePickerSheet.show(
                    activity = this,
                    title = getString(R.string.inventory_filter_store_picker_title),
                ) { picked ->
                    storeFilterId = picked.storeId
                    storeFilterName = picked.name
                    buildFilterChips()
                    reload()
                }
            }
            // Add a × on the right when a store is selected so users can
            // clear back to "all stores" without hunting for the All chip.
            if (storeFilterId != null) {
                chip.setOnLongClickListener {
                    storeFilterId = null
                    storeFilterName = null
                    buildFilterChips()
                    reload()
                    true
                }
            }
            val dp = resources.displayMetrics.density
            val lp = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { marginEnd = (8 * dp).toInt() }
            binding.filterContainer.addView(chip, lp)
        }
    }

    private fun addStatusChip(labelRes: Int, isActive: Boolean, onTap: () -> Unit) {
        val chip = makeChip(getString(labelRes), isActive, onTap)
        val dp = resources.displayMetrics.density
        val lp = ViewGroup.MarginLayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { marginEnd = (8 * dp).toInt() }
        binding.filterContainer.addView(chip, lp)
    }

    private fun makeChip(label: String, isActive: Boolean, onTap: () -> Unit): TextView {
        val dp = resources.displayMetrics.density
        val chip = TextView(this).apply {
            text = label
            textSize = 12f
            setPadding((14 * dp).toInt(), (7 * dp).toInt(), (14 * dp).toInt(), (7 * dp).toInt())
            background = ContextCompat.getDrawable(context, R.drawable.bg_filter_chip)?.mutate()
            isClickable = true
            isFocusable = true
            val attrs = context.obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackground))
            foreground = attrs.getDrawable(0)
            attrs.recycle()
        }
        paintChip(chip, isActive)
        chip.setOnClickListener { onTap() }
        return chip
    }

    private fun paintChip(chip: TextView, isActive: Boolean) {
        val bg = chip.background as? GradientDrawable ?: return
        if (isActive) {
            bg.setColor(ContextCompat.getColor(this, R.color.primary))
            bg.setStroke(0, 0)
            chip.setTextColor(ContextCompat.getColor(this, R.color.white))
            chip.paint.isFakeBoldText = true
        } else {
            bg.setColor(ContextCompat.getColor(this, R.color.white))
            bg.setStroke(
                (1 * resources.displayMetrics.density).toInt(),
                android.graphics.Color.parseColor("#E2E8F0"),
            )
            chip.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
            chip.paint.isFakeBoldText = false
        }
    }

    private fun loadReachableStores() {
        lifecycleScope.launch {
            try {
                val resp = ApiClient.getService(this@InventoryActivity).getMyStores()
                if (resp.isSuccessful && resp.body()?.success == true) {
                    reachableStores = resp.body()?.data.orEmpty()
                    buildFilterChips()
                }
            } catch (_: Exception) {
                // Soft-fail — the inventory list itself doesn't depend on
                // this; the store chip just won't show.
            }
        }
    }

    // ── List loading ────────────────────────────────────────────────

    private fun reload() {
        page = 1
        items.clear()
        adapter.notifyDataSetChanged()
        loadPage(1, append = false)
    }

    private fun loadPage(targetPage: Int, append: Boolean) {
        loadJob?.cancel()
        isLoading = true

        if (!append && !binding.swipeRefresh.isRefreshing) {
            binding.progress.visibility = View.VISIBLE
        }
        binding.emptyContainer.visibility = View.GONE

        loadJob = lifecycleScope.launch {
            try {
                val resp = ApiClient.getService(this@InventoryActivity).getInventory(
                    storeId = storeFilterId,
                    search = searchQuery.ifBlank { null },
                    onlyLow = if (onlyLowStock) true else null,
                    page = targetPage,
                    limit = 30,
                )
                val body = resp.body()
                if (resp.isSuccessful && body?.success == true) {
                    val rows = body.data.orEmpty()
                    if (!append) items.clear()
                    items.addAll(rows)
                    totalCount = body.total ?: items.size
                    page = targetPage
                    adapter.notifyDataSetChanged()
                } else if (!append) {
                    items.clear()
                    adapter.notifyDataSetChanged()
                }
            } catch (_: Exception) {
                if (!append) {
                    items.clear()
                    adapter.notifyDataSetChanged()
                }
            } finally {
                isLoading = false
                binding.progress.visibility = View.GONE
                binding.swipeRefresh.isRefreshing = false
                binding.tvEmpty.text = getString(
                    if (searchQuery.isNotBlank()) R.string.inventory_empty_search
                    else R.string.inventory_empty
                )
                binding.emptyContainer.visibility =
                    if (items.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    // ── Adapter ─────────────────────────────────────────────────────

    private class InventoryAdapter(
        private val rows: List<InventoryRow>,
    ) : RecyclerView.Adapter<InventoryAdapter.VH>() {

        class VH(val b: ItemInventoryBinding) : RecyclerView.ViewHolder(b.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(ItemInventoryBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun getItemCount(): Int = rows.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val row = rows[position]
            val b = holder.b
            val ctx = b.root.context
            val sep = ctx.getString(R.string.inventory_meta_separator)
            val unitSym = row.unitSymbol?.takeIf { it.isNotBlank() }?.let { " $it" } ?: ""

            // Headline name + low-stock badge.
            b.tvName.text = row.materialName?.takeIf { it.isNotBlank() } ?: "—"
            b.tvLowBadge.visibility = if (row.isLowStock) View.VISIBLE else View.GONE

            // Meta line: SKU · Batch (if any) · Min level (if set).
            val metaParts = mutableListOf<String>()
            row.materialSku?.takeIf { it.isNotBlank() }?.let { metaParts.add(it) }
            row.batchNumber?.takeIf { it.isNotBlank() }?.let {
                metaParts.add(ctx.getString(R.string.inventory_batch_label, it))
            }
            row.minStockLevel?.let {
                metaParts.add(ctx.getString(R.string.inventory_qty_min, GrnStatusStyle.formatQuantity(it)))
            }
            b.tvMeta.text = metaParts.joinToString(sep).ifEmpty { "—" }

            // Store · BU. Falls back gracefully when only one is present.
            b.tvStore.text = listOfNotNull(
                row.storeName?.takeIf { it.isNotBlank() },
                row.businessUnitName?.takeIf { it.isNotBlank() },
            ).joinToString(sep).ifEmpty { "—" }

            // Quantity block.
            b.tvAvailable.text = GrnStatusStyle.formatQuantity(row.availableQuantity) + unitSym

            // Tint the headline number red when low — a duplicate signal of
            // the LOW badge but it's the number the user will actually scan.
            val availColor = if (row.isLowStock)
                ContextCompat.getColor(ctx, R.color.error)
            else
                ContextCompat.getColor(ctx, R.color.on_surface)
            b.tvAvailable.setTextColor(availColor)

            // Show "On hand: X" only when it differs from available — no
            // sense duplicating the same number twice.
            if (row.quantityOnHand != row.availableQuantity) {
                b.tvOnHand.visibility = View.VISIBLE
                b.tvOnHand.text = ctx.getString(
                    R.string.inventory_qty_on_hand,
                    GrnStatusStyle.formatQuantity(row.quantityOnHand) + unitSym,
                )
            } else {
                b.tvOnHand.visibility = View.GONE
            }

            if (row.reservedQuantity > 0.0) {
                b.tvReserved.visibility = View.VISIBLE
                b.tvReserved.text = ctx.getString(
                    R.string.inventory_qty_reserved,
                    GrnStatusStyle.formatQuantity(row.reservedQuantity) + unitSym,
                )
            } else {
                b.tvReserved.visibility = View.GONE
            }
        }
    }
}
