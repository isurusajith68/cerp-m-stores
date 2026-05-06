package com.ceyinfo.cerpstores.ui.verification

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
import com.ceyinfo.cerpstores.data.model.Verification
import com.ceyinfo.cerpstores.data.remote.ApiClient
import com.ceyinfo.cerpstores.databinding.ActivityVerificationListBinding
import com.ceyinfo.cerpstores.databinding.ItemVerificationBinding
import com.ceyinfo.cerpstores.ui.common.BottomNav
import com.ceyinfo.cerpstores.ui.common.StorePickerSheet
import com.ceyinfo.cerpstores.ui.grn.GrnStatusStyle
import com.ceyinfo.cerpstores.util.SessionManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * List screen for stock verifications. Pattern is intentionally identical
 * to GrnListActivity — same status chips, same infinite-scroll, same P2R,
 * same "+" header button gated by ACL. The only deltas are the data shape
 * and the absence of supplier / amount columns.
 *
 * The "+" button always opens StorePickerSheet first so the create form
 * starts with a known store_id (the backend derives BU + module instance
 * from it, mirroring the GRN flow).
 */
class VerificationListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVerificationListBinding

    private var statusFilter: String? = null

    private val items = mutableListOf<Verification>()
    private val adapter = VerificationsAdapter(items) { v ->
        startActivity(
            Intent(this, VerificationDetailActivity::class.java)
                .putExtra(VerificationDetailActivity.EXTRA_ID, v.id)
                .putExtra(VerificationDetailActivity.EXTRA_NUMBER, v.verificationNumber)
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

        binding = ActivityVerificationListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }

        val session = SessionManager(this)
        val canCreate = session.canPerformAction("verification", "create")
        binding.btnAdd.visibility = if (canCreate) View.VISIBLE else View.GONE
        binding.btnAdd.setOnClickListener {
            StorePickerSheet.show(
                activity = this,
                title = getString(R.string.action_verification),
                subtitle = getString(R.string.action_verification_desc),
            ) { store ->
                startActivity(
                    Intent(this, VerificationCreateActivity::class.java)
                        .putExtra(VerificationCreateActivity.EXTRA_STORE_ID, store.storeId)
                        .putExtra(VerificationCreateActivity.EXTRA_STORE_NAME, store.name)
                )
            }
        }

        BottomNav.bind(binding.bottomNav.root, this, BottomNav.Tab.HOME)

        binding.rvVerifications.layoutManager = LinearLayoutManager(this)
        binding.rvVerifications.adapter = adapter

        binding.swipeRefresh.setColorSchemeResources(R.color.primary)
        binding.swipeRefresh.setOnRefreshListener { reload() }

        binding.rvVerifications.addOnScrollListener(object : RecyclerView.OnScrollListener() {
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

    /** When returning from create/detail, refresh so transitions reflect. */
    override fun onResume() {
        super.onResume()
        if (items.isNotEmpty()) reload()
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

        if (!append && !binding.swipeRefresh.isRefreshing) {
            binding.progress.visibility = View.VISIBLE
        }
        binding.emptyContainer.visibility = View.GONE

        loadJob = lifecycleScope.launch {
            try {
                val resp = ApiClient.getService(this@VerificationListActivity).getVerifications(
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

    // ── Adapter ────────────────────────────────────────────────────

    private class VerificationsAdapter(
        private val rows: List<Verification>,
        private val onTap: (Verification) -> Unit,
    ) : RecyclerView.Adapter<VerificationsAdapter.VH>() {

        class VH(val b: ItemVerificationBinding) : RecyclerView.ViewHolder(b.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(ItemVerificationBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun getItemCount() = rows.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val v = rows[position]
            val b = holder.b

            b.tvNumber.text = v.verificationNumber
            GrnStatusStyle.applyStatus(b.tvStatus, v.status)

            b.tvStore.text = listOfNotNull(
                v.storeName?.takeIf { it.isNotBlank() },
                v.businessUnitName?.takeIf { it.isNotBlank() },
            ).joinToString(" · ").ifEmpty { "—" }

            val count = v.itemsCount ?: v.items?.size ?: 0
            b.tvLines.text = "$count item${if (count == 1) "" else "s"}"
            b.tvDate.text = GrnStatusStyle.formatDate(v.verificationDate ?: v.createdAt)

            b.root.setOnClickListener { onTap(v) }
        }
    }
}
