package com.ceyinfo.cerpstores.ui.issue

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
import com.ceyinfo.cerpstores.data.model.Issue
import com.ceyinfo.cerpstores.data.remote.ApiClient
import com.ceyinfo.cerpstores.databinding.ActivityIssueListBinding
import com.ceyinfo.cerpstores.databinding.ItemIssueBinding
import com.ceyinfo.cerpstores.ui.common.BottomNav
import com.ceyinfo.cerpstores.ui.common.StorePickerSheet
import com.ceyinfo.cerpstores.util.SessionManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Goods Issue list — paginated, status-filtered, pull-to-refresh.
 * Mirrors GrnListActivity exactly, adapted for the /issues endpoints.
 */
class IssueListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityIssueListBinding

    private var statusFilter: String? = null

    private val items = mutableListOf<Issue>()
    private val adapter = IssuesAdapter(items) { issue ->
        startActivity(
            Intent(this, IssueDetailActivity::class.java)
                .putExtra(IssueDetailActivity.EXTRA_ISSUE_ID, issue.id)
                .putExtra(IssueDetailActivity.EXTRA_ISSUE_NUMBER, issue.issueNumber)
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

        binding = ActivityIssueListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }

        val session = SessionManager(this)
        val canCreate = session.canPerformAction("store_issue", "create")
        binding.btnAdd.visibility = if (canCreate) View.VISIBLE else View.GONE
        binding.btnAdd.setOnClickListener {
            StorePickerSheet.show(
                activity = this,
                title = getString(R.string.action_goods_issue),
                subtitle = getString(R.string.action_goods_issue_desc),
            ) { store ->
                startActivity(
                    Intent(this, IssueCreateActivity::class.java)
                        .putExtra(IssueCreateActivity.EXTRA_STORE_ID, store.storeId)
                        .putExtra(IssueCreateActivity.EXTRA_STORE_NAME, store.name)
                )
            }
        }

        BottomNav.bind(binding.bottomNav.root, this, BottomNav.Tab.HISTORY)

        binding.rvIssues.layoutManager = LinearLayoutManager(this)
        binding.rvIssues.adapter = adapter

        binding.swipeRefresh.setColorSchemeResources(R.color.primary)
        binding.swipeRefresh.setOnRefreshListener { reload() }

        binding.rvIssues.addOnScrollListener(object : RecyclerView.OnScrollListener() {
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
            Filter(null,        getString(R.string.issue_filter_all)),
            Filter("DRAFT",     getString(R.string.issue_status_draft)),
            Filter("SUBMITTED", getString(R.string.issue_status_submitted)),
            Filter("APPROVED",  getString(R.string.issue_status_approved)),
            Filter("CANCELLED", getString(R.string.issue_status_cancelled)),
        )

        val dp = resources.displayMetrics.density
        for (filter in filters) {
            val chip = TextView(this).apply {
                text = filter.label
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
        if (!append && !binding.swipeRefresh.isRefreshing) {
            binding.progress.visibility = View.VISIBLE
        }
        binding.emptyContainer.visibility = View.GONE

        loadJob = lifecycleScope.launch {
            try {
                val resp = ApiClient.getService(this@IssueListActivity).getIssues(
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

    private class IssuesAdapter(
        private val rows: List<Issue>,
        private val onTap: (Issue) -> Unit,
    ) : RecyclerView.Adapter<IssuesAdapter.VH>() {

        class VH(val b: ItemIssueBinding) : RecyclerView.ViewHolder(b.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(ItemIssueBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun getItemCount() = rows.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val issue = rows[position]
            val b = holder.b

            b.tvIssueNumber.text = issue.issueNumber
            IssueStatusStyle.applyStatus(b.tvStatus, issue.status)

            // Department / issued-to — prefer structured name, fall back to freetext
            val deptLabel = issue.departmentName?.takeIf { it.isNotBlank() }
                ?: issue.toDepartment?.takeIf { it.isNotBlank() }
                ?: "—"
            val empLabel = issue.issuedToName?.takeIf { it.isNotBlank() }
                ?: issue.issuedTo?.takeIf { it.isNotBlank() }
            b.tvDepartment.text = if (empLabel != null) "$deptLabel → $empLabel" else deptLabel

            b.tvStore.text = listOfNotNull(
                issue.storeName?.takeIf { it.isNotBlank() },
                issue.businessUnitName?.takeIf { it.isNotBlank() },
            ).joinToString(" · ").ifEmpty { "—" }

            b.tvDate.text = IssueStatusStyle.formatDate(issue.issueDate)
            b.tvTotalQty.text = "Total Qty: ${IssueStatusStyle.formatQuantity(issue.totalQuantity)}"

            b.root.setOnClickListener { onTap(issue) }
        }
    }
}
