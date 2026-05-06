package com.ceyinfo.cerpstores.ui.inventory

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class InventoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityInventoryListBinding

    private var onlyLowStock: Boolean = false
    private var storeFilterId: String? = null
    private var storeFilterName: String? = null

    private var searchQuery: String = ""
    private var searchDebounceJob: Job? = null

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

        binding.btnBack.setOnClickListener { finish() }
        BottomNav.bind(binding.bottomNav.root, this, BottomNav.Tab.INVENTORY)

        val layoutManager = LinearLayoutManager(this)
        binding.rvInventory.layoutManager = layoutManager
        binding.rvInventory.adapter = adapter

        binding.swipeRefresh.setColorSchemeResources(R.color.primary)
        binding.swipeRefresh.setOnRefreshListener { reload() }

        // Load next page when within 5 items of the end of the list
        binding.rvInventory.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                val lm = rv.layoutManager as LinearLayoutManager
                val lastVisible = lm.findLastVisibleItemPosition()
                if (lastVisible >= items.size - 5 && !isLoading && items.size < totalCount) {
                    loadPage(page + 1, append = true)
                }
            }
        })

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
        }

        buildFilterChips()
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
            val dp = resources.displayMetrics.density
            val lp = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { marginEnd = (8 * dp).toInt() }
            binding.filterContainer.addView(chip, lp)
        }

        if (hasActiveFilters()) {
            addStatusChip(R.string.inventory_filter_clear, isActive = false) {
                clearFilters()
            }
        }
    }

    private fun hasActiveFilters(): Boolean =
        onlyLowStock || storeFilterId != null || searchQuery.isNotBlank() || binding.etSearch.text?.isNotBlank() == true

    private fun clearFilters() {
        onlyLowStock = false
        storeFilterId = null
        storeFilterName = null
        searchQuery = ""
        binding.etSearch.text?.clear()
        buildFilterChips()
        reload()
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

    private fun makeChip(label: String, isActive: Boolean, onTap: () -> Unit): android.widget.TextView {
        val dp = resources.displayMetrics.density
        val chip = android.widget.TextView(this).apply {
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

    private fun paintChip(chip: android.widget.TextView, isActive: Boolean) {
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
            } catch (_: Exception) { /* soft-fail */ }
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

        private val isoFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        private val displayFmt = SimpleDateFormat("d MMM yyyy", Locale.US)

        class VH(val b: ItemInventoryBinding) : RecyclerView.ViewHolder(b.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(ItemInventoryBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun getItemCount(): Int = rows.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val row = rows[position]
            val b = holder.b
            val ctx = b.root.context
            val unitSym = row.unitSymbol?.takeIf { it.isNotBlank() }?.let { " $it" } ?: ""

            b.tvMaterial.text = row.materialName?.takeIf { it.isNotBlank() } ?: "—"

            b.tvStore.text = row.storeName?.takeIf { it.isNotBlank() } ?: "—"

            val sku = row.materialSku?.takeIf { it.isNotBlank() }
            if (sku != null) {
                b.tvSku.visibility = View.VISIBLE
                b.tvSku.text = sku.uppercase()
            } else {
                b.tvSku.visibility = View.GONE
            }

            val batch = row.batchNumber?.takeIf { it.isNotBlank() }
            b.tvBatch.text = if (batch != null) "Batch: $batch" else ""

            b.tvAvailable.text = GrnStatusStyle.formatQuantity(row.availableQuantity) + unitSym

            if (row.isLowStock) {
                b.tvStatus.text = ctx.getString(R.string.inventory_status_low_stock)
                b.tvStatus.background = ContextCompat.getDrawable(ctx, R.drawable.bg_status_low)
                b.tvStatus.setTextColor(android.graphics.Color.parseColor("#D97706"))
            } else {
                b.tvStatus.text = ctx.getString(R.string.inventory_status_ok)
                b.tvStatus.background = ContextCompat.getDrawable(ctx, R.drawable.bg_status_ok)
                b.tvStatus.setTextColor(android.graphics.Color.parseColor("#16A34A"))
            }

            b.tvLastTxn.text = formatDate(row.lastTransactionDate)
        }

        private fun formatDate(raw: String?): String {
            if (raw.isNullOrBlank()) return "—"
            return try {
                val datePart = raw.substringBefore('T').take(10)
                val date = isoFmt.parse(datePart) ?: return "—"
                displayFmt.format(date)
            } catch (_: Exception) {
                "—"
            }
        }
    }
}
