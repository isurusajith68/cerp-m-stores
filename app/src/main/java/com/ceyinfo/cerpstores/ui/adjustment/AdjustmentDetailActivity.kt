package com.ceyinfo.cerpstores.ui.adjustment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.ceyinfo.cerpstores.R
import com.ceyinfo.cerpstores.data.model.AdjustmentLineItem
import com.ceyinfo.cerpstores.data.model.StockAdjustment
import com.ceyinfo.cerpstores.data.model.TransitionRequest
import com.ceyinfo.cerpstores.data.remote.ApiClient
import com.ceyinfo.cerpstores.databinding.ActivityAdjustmentDetailBinding
import com.ceyinfo.cerpstores.databinding.ItemAdjustmentLineBinding
import com.ceyinfo.cerpstores.databinding.ItemGrnSummaryRowBinding
import com.ceyinfo.cerpstores.ui.common.BottomNav
import com.ceyinfo.cerpstores.util.SessionManager
import com.google.android.material.button.MaterialButton
import kotlin.math.abs
import kotlinx.coroutines.launch

class AdjustmentDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_ADJUSTMENT_ID = "adjustment_id"
        const val EXTRA_ADJUSTMENT_NUMBER = "adjustment_number"
    }

    private lateinit var binding: ActivityAdjustmentDetailBinding
    private lateinit var adjustmentId: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT

        binding = ActivityAdjustmentDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adjustmentId = intent.getStringExtra(EXTRA_ADJUSTMENT_ID).orEmpty()
        if (adjustmentId.isBlank()) { finish(); return }

        intent.getStringExtra(EXTRA_ADJUSTMENT_NUMBER)?.let {
            binding.tvHeaderAdjustmentNumber.text = it
        }

        binding.btnBack.setOnClickListener { finish() }
        BottomNav.bind(binding.bottomNav.root, this, BottomNav.Tab.HOME)

        binding.swipeRefresh.setColorSchemeResources(R.color.primary)
        binding.swipeRefresh.setOnRefreshListener { load() }

        load()
    }

    private fun load() {
        if (!binding.swipeRefresh.isRefreshing) {
            binding.progress.visibility = View.VISIBLE
            binding.scrollContent.visibility = View.GONE
        }
        binding.tvError.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val resp = ApiClient.getService(this@AdjustmentDetailActivity).getAdjustment(adjustmentId)
                val adj = resp.body()?.data
                if (resp.isSuccessful && resp.body()?.success == true && adj != null) {
                    render(adj)
                    binding.scrollContent.visibility = View.VISIBLE
                    binding.tvError.visibility = View.GONE
                } else {
                    binding.scrollContent.visibility = View.GONE
                    binding.tvError.visibility = View.VISIBLE
                }
            } catch (_: Exception) {
                binding.scrollContent.visibility = View.GONE
                binding.tvError.visibility = View.VISIBLE
            } finally {
                binding.progress.visibility = View.GONE
                binding.swipeRefresh.isRefreshing = false
            }
        }
    }

    private fun render(adj: StockAdjustment) {
        binding.tvHeaderAdjustmentNumber.text = adj.adjustmentNumber
        AdjustmentStatusStyle.applyStatus(binding.tvStatus, adj.status)
        binding.tvAdjustmentDate.text = AdjustmentStatusStyle.formatDate(adj.adjustmentDate)

        binding.summaryRows.removeAllViews()
        addSummaryRow(R.string.adjustment_detail_store, adj.storeName)

        val typeLabel = adj.adjustmentType.replace('_', ' ').replaceFirstChar { it.uppercase() }
        addSummaryRow(R.string.adjustment_detail_type, typeLabel)

        val totalQty = adj.items?.sumOf { it.adjustedQuantity } ?: 0.0
        binding.tvTotalQty.text = AdjustmentStatusStyle.formatQuantity(kotlin.math.abs(totalQty))

        buildActions(adj)

        val items = adj.items.orEmpty()
        binding.tvItemsCount.text = items.size.toString()
        binding.itemsContainer.removeAllViews()
        if (items.isEmpty()) {
            binding.tvNoItems.visibility = View.VISIBLE
        } else {
            binding.tvNoItems.visibility = View.GONE
            for (item in items) addLineItem(item)
        }

        if (!adj.remarks.isNullOrBlank()) {
            binding.remarksContainer.visibility = View.VISIBLE
            binding.tvRemarks.text = adj.remarks
        } else {
            binding.remarksContainer.visibility = View.GONE
        }
    }

    private fun buildActions(adj: StockAdjustment) {
        val session = SessionManager(this)
        binding.actionsContainer.removeAllViews()

        val dp = resources.displayMetrics.density
        val lp = android.view.ViewGroup.MarginLayoutParams(
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { marginEnd = (8 * dp).toInt() }

        when (adj.status.uppercase()) {
            "DRAFT" -> {
                if (session.canPerformAction("stock_adjustment", "submit")) {
                    addActionButton(getString(R.string.adjustment_action_submit), R.color.primary, lp, "submit", adj)
                }
            }
            "PENDING" -> {
                if (session.canPerformAction("stock_adjustment", "approve")) {
                    addActionButton(getString(R.string.adjustment_action_approve), R.color.primary, lp, "approve", adj)
                }
                if (session.canPerformAction("stock_adjustment", "cancel")) {
                    addActionButton(getString(R.string.adjustment_action_cancel), R.color.error, lp, "cancel", adj)
                }
            }
        }

        binding.actionsContainer.visibility =
            if (binding.actionsContainer.childCount > 0) View.VISIBLE else View.GONE
    }

    private fun addActionButton(
        label: String,
        colorRes: Int,
        lp: android.view.ViewGroup.MarginLayoutParams,
        action: String,
        adj: StockAdjustment,
    ) {
        val btn = MaterialButton(this).apply {
            text = label
            setBackgroundColor(ContextCompat.getColor(this@AdjustmentDetailActivity, colorRes))
            setTextColor(ContextCompat.getColor(this@AdjustmentDetailActivity, R.color.white))
            cornerRadius = (10 * resources.displayMetrics.density).toInt()
            setOnClickListener { performTransition(adj, action) }
        }
        binding.actionsContainer.addView(btn, lp)
    }

    private fun performTransition(adj: StockAdjustment, action: String) {
        lifecycleScope.launch {
            try {
                val resp = ApiClient.getService(this@AdjustmentDetailActivity)
                    .transitionAdjustment(adj.id, TransitionRequest(action))
                if (resp.isSuccessful && resp.body()?.success == true) {
                    Toast.makeText(this@AdjustmentDetailActivity, "Adjustment ${action}d successfully.", Toast.LENGTH_SHORT).show()
                    load()
                } else {
                    val msg = resp.body()?.message ?: "Action failed. Please try again."
                    Toast.makeText(this@AdjustmentDetailActivity, msg, Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@AdjustmentDetailActivity, e.message ?: "Action failed.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun addSummaryRow(labelRes: Int, value: String?) {
        if (value.isNullOrBlank()) return
        val row = ItemGrnSummaryRowBinding.inflate(LayoutInflater.from(this), binding.summaryRows, false)
        row.tvLabel.setText(labelRes)
        row.tvValue.text = value
        binding.summaryRows.addView(row.root)
    }

    private fun addLineItem(item: AdjustmentLineItem) {
        val row = ItemAdjustmentLineBinding.inflate(LayoutInflater.from(this), binding.itemsContainer, false)
        row.tvMaterialName.text = item.materialName?.takeIf { it.isNotBlank() } ?: "—"

        val meta = listOfNotNull(
            item.materialSku?.takeIf { it.isNotBlank() },
            item.unitSymbol?.takeIf { it.isNotBlank() },
        ).joinToString(" · ")
        row.tvMaterialMeta.text = meta
        row.tvMaterialMeta.visibility = if (meta.isBlank()) View.GONE else View.VISIBLE

        val unitSym = item.unitSymbol?.takeIf { it.isNotBlank() }?.let { " $it" } ?: ""
        val sign = if (item.adjustedQuantity >= 0) "+" else ""
        row.tvQty.text = "$sign${AdjustmentStatusStyle.formatQuantity(item.adjustedQuantity)}$unitSym"

        if (!item.batchNumber.isNullOrBlank()) {
            row.tvBatch.text = "Batch: ${item.batchNumber}"
            row.tvBatch.visibility = View.VISIBLE
        } else {
            row.tvBatch.visibility = View.GONE
        }

        if (!item.reason.isNullOrBlank()) {
            row.tvReason.text = "Reason: ${item.reason}"
            row.tvReason.visibility = View.VISIBLE
        } else {
            row.tvReason.visibility = View.GONE
        }

        binding.itemsContainer.addView(row.root)
    }
}
