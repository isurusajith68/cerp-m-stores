package com.ceyinfo.cerpstores.ui.history

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
import com.ceyinfo.cerpstores.data.model.StockTransaction
import com.ceyinfo.cerpstores.data.remote.ApiClient
import com.ceyinfo.cerpstores.databinding.ActivityHistoryBinding
import com.ceyinfo.cerpstores.databinding.ItemTransactionBinding
import com.ceyinfo.cerpstores.ui.common.BottomNav
import com.ceyinfo.cerpstores.ui.grn.GrnStatusStyle
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class HistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistoryBinding

    // null = All
    private var txnTypeFilter: String? = null
    private var searchQuery: String = ""
    private var searchDebounceJob: Job? = null

    private val items = mutableListOf<StockTransaction>()
    private val adapter = TxnAdapter(items)

    private var page = 1
    private var totalCount = 0
    private var isLoading = false
    private var loadJob: Job? = null

    // Chip definitions: label res → API txn_type value (null = All)
    private val filterDefs = listOf(
        R.string.history_filter_all to null,
        R.string.history_filter_grn_in to "GRN_IN",
        R.string.history_filter_issue_out to "ISSUE_OUT",
        R.string.history_filter_transfer_in to "TRANSFER_IN",
        R.string.history_filter_transfer_out to "TRANSFER_OUT",
        R.string.history_filter_return to "RETURN",
        R.string.history_filter_adjustment to "ADJUSTMENT_IN",
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT

        binding = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }
        BottomNav.bind(binding.bottomNav.root, this, BottomNav.Tab.HISTORY)

        val layoutManager = LinearLayoutManager(this)
        binding.rvTransactions.layoutManager = layoutManager
        binding.rvTransactions.adapter = adapter

        binding.swipeRefresh.setColorSchemeResources(R.color.primary)
        binding.swipeRefresh.setOnRefreshListener { reload() }

        binding.rvTransactions.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                val lm = rv.layoutManager as LinearLayoutManager
                if (lm.findLastVisibleItemPosition() >= items.size - 5 && !isLoading && items.size < totalCount) {
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
        binding.btnClearSearch.setOnClickListener { binding.etSearch.text?.clear() }

        buildFilterChips()
        reload()
    }

    // ── Filter chips ─────────────────────────────────────────────────

    private fun buildFilterChips() {
        binding.filterContainer.removeAllViews()
        val dp = resources.displayMetrics.density
        for ((labelRes, typeValue) in filterDefs) {
            val isActive = txnTypeFilter == typeValue
            val chip = makeChip(getString(labelRes), isActive) {
                if (txnTypeFilter == typeValue) return@makeChip
                txnTypeFilter = typeValue
                buildFilterChips()
                reload()
            }
            val lp = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { marginEnd = (8 * dp).toInt() }
            binding.filterContainer.addView(chip, lp)
        }
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
        val bg = chip.background as? GradientDrawable
        if (isActive) {
            bg?.setColor(ContextCompat.getColor(this, R.color.primary))
            bg?.setStroke(0, 0)
            chip.setTextColor(ContextCompat.getColor(this, R.color.white))
            chip.paint.isFakeBoldText = true
        } else {
            bg?.setColor(ContextCompat.getColor(this, R.color.white))
            bg?.setStroke(
                (1 * dp).toInt(),
                android.graphics.Color.parseColor("#E2E8F0"),
            )
            chip.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
            chip.paint.isFakeBoldText = false
        }
        chip.setOnClickListener { onTap() }
        return chip
    }

    // ── Loading ──────────────────────────────────────────────────────

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
                val resp = ApiClient.getService(this@HistoryActivity).getTransactions(
                    txnType = txnTypeFilter,
                    search = searchQuery.ifBlank { null },
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
                binding.emptyContainer.visibility =
                    if (items.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    // ── Adapter ──────────────────────────────────────────────────────

    private class TxnAdapter(
        private val rows: List<StockTransaction>,
    ) : RecyclerView.Adapter<TxnAdapter.VH>() {

        private val isoFmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        private val displayFmt = SimpleDateFormat("d MMM yyyy, HH:mm", Locale.US)

        class VH(val b: ItemTransactionBinding) : RecyclerView.ViewHolder(b.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(ItemTransactionBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun getItemCount() = rows.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val tx = rows[position]
            val b = holder.b
            val ctx = b.root.context

            // Type badge
            val (typeLabel, bgColor, textColor) = typeStyle(tx.transactionType)
            b.tvTxnType.text = typeLabel
            b.tvTxnType.background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = 999f
                setColor(android.graphics.Color.parseColor(bgColor))
            }
            b.tvTxnType.setTextColor(android.graphics.Color.parseColor(textColor))

            b.tvTxnNumber.text = tx.transactionNumber?.takeIf { it.isNotBlank() } ?: tx.referenceDocument ?: "—"
            b.tvDate.text = formatDate(tx.createdAt)

            b.tvMaterial.text = tx.materialName?.takeIf { it.isNotBlank() } ?: "—"

            // Store route
            val from = tx.fromStoreName
            val to = tx.toStoreName
            b.tvStoreRoute.text = when {
                from != null && to != null -> "$from → $to"
                from != null -> "From: $from"
                to != null -> "To: $to"
                else -> "—"
            }

            // Batch
            val batch = tx.batchNumber?.takeIf { it.isNotBlank() }
            b.tvBatch.text = if (batch != null) "Batch: $batch" else ""

            // Quantity pill
            val unitSym = tx.unitSymbol?.takeIf { it.isNotBlank() }?.let { " $it" } ?: ""
            val isInflow = isInflow(tx)
            b.tvQuantity.text =
                (if (isInflow) "+" else "-") +
                    GrnStatusStyle.formatQuantity(kotlin.math.abs(tx.quantity)) +
                    unitSym
            b.tvQuantity.setTextColor(
                android.graphics.Color.parseColor(if (isInflow) "#0F766E" else "#BE123C")
            )

            b.tvCreatedBy.text = tx.createdByName?.takeIf { it.isNotBlank() } ?: ""
        }

        // Returns (label, bgHex, textHex)
        private fun typeStyle(type: String): Triple<String, String, String> = when (type) {
            "GRN_IN"        -> Triple("GRN IN",       "#DCFCE7", "#16A34A")
            "ISSUE_OUT"     -> Triple("ISSUE OUT",     "#FEF3C7", "#D97706")
            "TRANSFER_IN"   -> Triple("TRANSFER IN",   "#DBEAFE", "#1D4ED8")
            "TRANSFER_OUT"  -> Triple("TRANSFER OUT",  "#E0E7FF", "#4338CA")
            "RETURN"        -> Triple("RETURN",        "#FCE7F3", "#BE185D")
            "RETURN_IN"     -> Triple("RETURN IN",     "#FCE7F3", "#BE185D")
            "ADJUSTMENT_IN" -> Triple("ADJ IN",        "#F0FDF4", "#15803D")
            "ADJUSTMENT_OUT"-> Triple("ADJ OUT",       "#FFF7ED", "#C2410C")
            "VERIFICATION"  -> Triple("VERIFY",        "#F1F5F9", "#475569")
            else            -> Triple(type,            "#F1F5F9", "#475569")
        }

        private fun isInflow(tx: StockTransaction): Boolean {
            if (tx.transactionType == "VERIFICATION") {
                return tx.toStoreId != null && tx.fromStoreId == null
            }
            return when (tx.transactionType) {
                "GRN_IN",
                "TRANSFER_IN",
                "RETURN_IN",
                "ADJUSTMENT_IN" -> true
                else -> false
            }
        }

        private fun formatDate(raw: String?): String {
            if (raw.isNullOrBlank()) return "—"
            return try {
                val cleaned = raw.substringBefore('.').replace('Z', ' ').trim()
                val date = isoFmt.parse(cleaned) ?: return raw.substringBefore('T').take(10)
                displayFmt.format(date)
            } catch (_: Exception) {
                raw.substringBefore('T').take(10)
            }
        }
    }
}
