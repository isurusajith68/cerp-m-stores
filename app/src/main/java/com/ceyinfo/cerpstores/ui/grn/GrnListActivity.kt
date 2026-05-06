package com.ceyinfo.cerpstores.ui.grn

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
import com.ceyinfo.cerpstores.data.model.Grn
import com.ceyinfo.cerpstores.data.remote.ApiClient
import com.ceyinfo.cerpstores.databinding.ActivityGrnListBinding
import com.ceyinfo.cerpstores.databinding.ItemGrnBinding
import com.ceyinfo.cerpstores.ui.common.BottomNav
import com.ceyinfo.cerpstores.ui.common.StorePickerSheet
import com.ceyinfo.cerpstores.util.SessionManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * GRN list — paginated, status-filtered, pull-to-refresh.
 *
 * Pagination is page-based (1, 2, 3 …) using the API's `total/page/limit`
 * envelope; we keep loading more when the user scrolls within ~3 rows of
 * the end. State machine:
 *
 *   - First load          → spinner overlay, list hidden
 *   - Reload (filter, P2R) → list dimmed via RecyclerView swap, spinner
 *                            short-circuited (P2R indicator shows instead)
 *   - Load-more           → silent (the list footer renders no spinner —
 *                            users barely notice infinite scroll, which is
 *                            the desired feel)
 *   - Empty               → empty card, list hidden, no spinner
 *
 * Wired to the Home tab in the bottom nav because GRNs are opened from the
 * dashboard shortcut set. The History tab is reserved for stock movements.
 */
class GrnListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGrnListBinding

    /** null = "All" filter; otherwise one of DRAFT/SUBMITTED/APPROVED/CANCELLED. */
    private var statusFilter: String? = null

    private val items = mutableListOf<Grn>()
    private val adapter = GrnsAdapter(items) { grn ->
        startActivity(
            Intent(this, GrnDetailActivity::class.java)
                .putExtra(GrnDetailActivity.EXTRA_GRN_ID, grn.id)
                .putExtra(GrnDetailActivity.EXTRA_GRN_NUMBER, grn.grnNumber)
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

        binding = ActivityGrnListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }

        // Header "+" — only visible to users who can create GRNs. Tapping
        // opens the same store-picker → GrnCreateActivity flow as the
        // dashboard's "+" FAB → "Goods Receipt" path; we just skip the
        // "pick an entity type" step since this list is GRN-specific.
        val session = SessionManager(this)
        val canCreate = session.canPerformAction("grn", "create")
        binding.btnAdd.visibility = if (canCreate) View.VISIBLE else View.GONE
        binding.btnAdd.setOnClickListener {
            StorePickerSheet.show(
                activity = this,
                title = getString(R.string.action_goods_receipt),
                subtitle = getString(R.string.action_goods_receipt_desc),
            ) { store ->
                startActivity(
                    Intent(this, GrnCreateActivity::class.java)
                        .putExtra(GrnCreateActivity.EXTRA_STORE_ID, store.storeId)
                        .putExtra(GrnCreateActivity.EXTRA_STORE_NAME, store.name)
                )
            }
        }

        BottomNav.bind(binding.bottomNav.root, this, BottomNav.Tab.HOME)

        binding.rvGrns.layoutManager = LinearLayoutManager(this)
        binding.rvGrns.adapter = adapter

        binding.swipeRefresh.setColorSchemeResources(R.color.primary)
        binding.swipeRefresh.setOnRefreshListener { reload() }

        // Infinite-scroll pagination — fetch the next page once the user
        // scrolls within 3 rows of the end. Cheap to compute on every
        // scroll event; no need to throttle.
        binding.rvGrns.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                if (dy <= 0 || isLoading) return
                val lm = rv.layoutManager as? LinearLayoutManager ?: return
                val lastVisible = lm.findLastVisibleItemPosition()
                val total = lm.itemCount
                if (lastVisible >= total - 3 && items.size < totalCount) {
                    loadPage(page + 1, append = true)
                }
            }
        })

        buildFilterChips()
        reload()
    }

    private fun buildFilterChips() {
        binding.filterContainer.removeAllViews()

        data class Filter(val code: String?, val labelRes: Int)
        val filters = listOf(
            Filter(null,        R.string.grn_filter_all),
            Filter("DRAFT",     R.string.grn_status_draft),
            Filter("SUBMITTED", R.string.grn_status_submitted),
            Filter("APPROVED",  R.string.grn_status_approved),
            Filter("CANCELLED", R.string.grn_status_cancelled),
        )

        val dp = resources.displayMetrics.density
        for (filter in filters) {
            val chip = TextView(this).apply {
                text = getString(filter.labelRes)
                textSize = 12f
                setPadding((14 * dp).toInt(), (7 * dp).toInt(), (14 * dp).toInt(), (7 * dp).toInt())
                background = ContextCompat.getDrawable(context, R.drawable.bg_filter_chip)?.mutate()
                isClickable = true
                isFocusable = true
                val attrs = context.obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackground))
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

        // Show the centered spinner only on first load; pull-to-refresh has
        // its own indicator, and load-more should be invisible.
        if (!append && !binding.swipeRefresh.isRefreshing) {
            binding.progress.visibility = View.VISIBLE
        }
        binding.emptyContainer.visibility = View.GONE

        loadJob = lifecycleScope.launch {
            try {
                val resp = ApiClient.getService(this@GrnListActivity).getGrns(
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
                    // Server-side error — surface as empty state with a
                    // help message rather than a toast, since toasts
                    // disappear before users can read them.
                    if (!append) {
                        items.clear()
                        adapter.notifyDataSetChanged()
                    }
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

    // ── Adapter ────────────────────────────────────────────────────

    private class GrnsAdapter(
        private val rows: List<Grn>,
        private val onTap: (Grn) -> Unit,
    ) : RecyclerView.Adapter<GrnsAdapter.VH>() {

        class VH(val b: ItemGrnBinding) : RecyclerView.ViewHolder(b.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(ItemGrnBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun getItemCount() = rows.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val grn = rows[position]
            val b = holder.b
            val ctx = b.root.context

            b.tvGrnNumber.text = grn.grnNumber
            GrnStatusStyle.applyStatus(b.tvStatus, grn.status)

            b.tvSupplier.text = grn.supplierName?.takeIf { it.isNotBlank() }
                ?: grn.poNumber?.let { "PO: $it" }
                ?: "—"

            // "Store · BU"  — falls back gracefully when one is missing.
            b.tvStore.text = listOfNotNull(
                grn.storeName?.takeIf { it.isNotBlank() },
                grn.businessUnitName?.takeIf { it.isNotBlank() },
            ).joinToString(" · ").ifEmpty { "—" }

            b.tvDate.text = GrnStatusStyle.formatDate(grn.grnDate ?: grn.receiptDate)

            b.tvTotalQty.text = ctx.getString(
                R.string.grn_detail_total_qty
            ) + ": " + GrnStatusStyle.formatQuantity(grn.totalQuantity)

            b.tvTotalAmount.text = GrnStatusStyle.formatAmount(grn.totalAmount)

            b.root.setOnClickListener { onTap(grn) }
        }
    }
}
