package com.ceyinfo.cerpstores.ui.transfer

import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
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
import com.ceyinfo.cerpstores.data.model.Transfer
import com.ceyinfo.cerpstores.data.remote.ApiClient
import com.ceyinfo.cerpstores.databinding.ActivityTransferListBinding
import com.ceyinfo.cerpstores.databinding.ItemTransferBinding
import com.ceyinfo.cerpstores.ui.common.BottomNav
import com.ceyinfo.cerpstores.ui.common.StorePickerSheet
import com.ceyinfo.cerpstores.util.SessionManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Stock Transfer list — paginated, status-filtered, pull-to-refresh.
 * Mirrors IssueListActivity / GrnListActivity exactly, adapted for
 * the /transfers endpoints with DRAFT / IN_TRANSIT / RECEIVED statuses.
 */
class TransferListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTransferListBinding

    private var statusFilter: String? = null

    private val items = mutableListOf<Transfer>()
    private val adapter = TransfersAdapter(items) { transfer ->
        startActivity(
            Intent(this, TransferDetailActivity::class.java)
                .putExtra(TransferDetailActivity.EXTRA_TRANSFER_ID, transfer.id)
                .putExtra(TransferDetailActivity.EXTRA_TRANSFER_NUMBER, transfer.transferNumber)
        )
    }

    private var page = 1
    private var totalCount = 0
    private var isLoading = false
    private var loadJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT

        binding = ActivityTransferListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }

        val session = SessionManager(this)
        val canCreate = session.canPerformAction("stock_transfer", "create")
        binding.btnAdd.visibility = if (canCreate) View.VISIBLE else View.GONE
        binding.btnAdd.setOnClickListener {
            StorePickerSheet.show(
                activity = this,
                title = getString(R.string.action_stock_transfer),
                subtitle = getString(R.string.action_stock_transfer_desc),
            ) { store ->
                startActivity(
                    Intent(this, TransferCreateActivity::class.java)
                        .putExtra(TransferCreateActivity.EXTRA_STORE_ID, store.storeId)
                        .putExtra(TransferCreateActivity.EXTRA_STORE_NAME, store.name)
                )
            }
        }

        BottomNav.bind(binding.bottomNav.root, this, BottomNav.Tab.HOME)

        binding.rvTransfers.layoutManager = LinearLayoutManager(this)
        binding.rvTransfers.adapter = adapter

        binding.swipeRefresh.setColorSchemeResources(R.color.primary)
        binding.swipeRefresh.setOnRefreshListener { reload() }

        binding.rvTransfers.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                if (dy <= 0 || isLoading) return
                val lm = rv.layoutManager as? LinearLayoutManager ?: return
                val lastVisible = lm.findLastVisibleItemPosition()
                if (lastVisible >= lm.itemCount - 3 && items.size < totalCount) {
                    loadPage(page + 1, append = true)
                }
            }
        })

        buildFilterChips()
        reload()
    }

    private fun buildFilterChips() {
        binding.filterContainer.removeAllViews()

        data class Filter(val code: String?, val label: String)
        val filters = listOf(
            Filter(null,         getString(R.string.transfer_filter_all)),
            Filter("DRAFT",      getString(R.string.transfer_status_draft)),
            Filter("PENDING",    getString(R.string.transfer_status_pending)),
            Filter("IN_TRANSIT", getString(R.string.transfer_status_in_transit)),
            Filter("RECEIVED",   getString(R.string.transfer_status_received)),
            Filter("CANCELLED",  getString(R.string.transfer_status_cancelled)),
        )

        val dp = resources.displayMetrics.density
        for (filter in filters) {
            val chip = TextView(this).apply {
                text = filter.label
                textSize = 12f
                setPadding(
                    (14 * dp).toInt(), (7 * dp).toInt(),
                    (14 * dp).toInt(), (7 * dp).toInt(),
                )
                background = ContextCompat.getDrawable(context, R.drawable.bg_filter_chip)?.mutate()
                isClickable = true
                isFocusable = true
                val attrs = context.obtainStyledAttributes(
                    intArrayOf(android.R.attr.selectableItemBackground)
                )
                foreground = attrs.getDrawable(0)
                attrs.recycle()
            }
            paintChip(chip, isActive = filter.code == statusFilter)
            chip.setOnClickListener {
                if (statusFilter == filter.code) return@setOnClickListener
                statusFilter = filter.code
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
                val resp = ApiClient.getService(this@TransferListActivity).getTransfers(
                    status = statusFilter,
                    page = targetPage,
                    limit = 20,
                )
                val body = resp.body()
                if (resp.isSuccessful && body?.success == true) {
                    val rows = body.data.orEmpty()
                    if (!append) items.clear()
                    items.addAll(rows)
                    totalCount = body.total ?: items.size
                    page = targetPage
                    adapter.notifyDataSetChanged()
                } else {
                    if (!append) { items.clear(); adapter.notifyDataSetChanged() }
                }
            } catch (_: Exception) {
                if (!append) { items.clear(); adapter.notifyDataSetChanged() }
            } finally {
                isLoading = false
                binding.progress.visibility = View.GONE
                binding.swipeRefresh.isRefreshing = false
                binding.emptyContainer.visibility =
                    if (items.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    // ── Adapter ───────────────────────────────────────────────────

    private class TransfersAdapter(
        private val rows: List<Transfer>,
        private val onTap: (Transfer) -> Unit,
    ) : RecyclerView.Adapter<TransfersAdapter.VH>() {

        class VH(val b: ItemTransferBinding) : RecyclerView.ViewHolder(b.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(ItemTransferBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun getItemCount() = rows.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val transfer = rows[position]
            val b = holder.b

            b.tvTransferNumber.text = transfer.transferNumber
            TransferStatusStyle.applyStatus(b.tvStatus, transfer.status)

            // Route: From Store → To Store
            val fromLabel = transfer.fromStoreName?.takeIf { it.isNotBlank() }
                ?: transfer.fromStoreCode?.takeIf { it.isNotBlank() }
                ?: "—"
            val toLabel = transfer.toStoreName?.takeIf { it.isNotBlank() }
                ?: transfer.toStoreCode?.takeIf { it.isNotBlank() }
                ?: "—"
            b.tvRoute.text = "$fromLabel → $toLabel"

            b.tvDate.text = TransferStatusStyle.formatDate(transfer.transferDate)
            b.tvTotalQty.text = "Total Qty: ${TransferStatusStyle.formatQuantity(transfer.totalQuantity)}"

            b.root.setOnClickListener { onTap(transfer) }
        }
    }
}
