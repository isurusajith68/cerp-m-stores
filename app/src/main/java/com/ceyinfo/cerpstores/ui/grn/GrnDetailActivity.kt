package com.ceyinfo.cerpstores.ui.grn

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.ceyinfo.cerpstores.R
import com.ceyinfo.cerpstores.data.model.Grn
import com.ceyinfo.cerpstores.data.model.GrnLineItem
import com.ceyinfo.cerpstores.data.remote.ApiClient
import com.ceyinfo.cerpstores.databinding.ActivityGrnDetailBinding
import com.ceyinfo.cerpstores.databinding.ItemGrnLineBinding
import com.ceyinfo.cerpstores.databinding.ItemGrnSummaryRowBinding
import com.ceyinfo.cerpstores.ui.common.BottomNav
import kotlinx.coroutines.launch

/**
 * Read-only GRN detail. Pass 1 covers display only; the action-button row
 * lives in XML but stays hidden until Pass 2 wires `PATCH /grns/:id/transition`.
 *
 * The summary card is populated dynamically — fields with no value (e.g.
 * a GRN with no PO link) are skipped rather than rendered as "—" rows,
 * so the card stays compact for sparse records.
 */
class GrnDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_GRN_ID = "grn_id"
        const val EXTRA_GRN_NUMBER = "grn_number"
    }

    private lateinit var binding: ActivityGrnDetailBinding
    private lateinit var grnId: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT

        binding = ActivityGrnDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        grnId = intent.getStringExtra(EXTRA_GRN_ID).orEmpty()
        if (grnId.isBlank()) {
            finish()
            return
        }

        // Pre-fill the header GRN number from the list-screen extra so the
        // user sees something the moment the screen renders, before the
        // detail fetch resolves.
        intent.getStringExtra(EXTRA_GRN_NUMBER)?.let { binding.tvHeaderGrnNumber.text = it }

        binding.btnBack.setOnClickListener { finish() }
        BottomNav.bind(binding.bottomNav.root, this, BottomNav.Tab.HISTORY)

        binding.swipeRefresh.setColorSchemeResources(R.color.primary)
        binding.swipeRefresh.setOnRefreshListener { load() }

        load()
    }

    private fun load() {
        // First load uses the centered spinner; subsequent reloads use
        // the SwipeRefresh indicator so the content doesn't flicker out
        // and back in.
        if (!binding.swipeRefresh.isRefreshing) {
            binding.progress.visibility = View.VISIBLE
            binding.scrollContent.visibility = View.GONE
        }
        binding.tvError.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val resp = ApiClient.getService(this@GrnDetailActivity).getGrn(grnId)
                val grn = resp.body()?.data
                if (resp.isSuccessful && resp.body()?.success == true && grn != null) {
                    render(grn)
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

    private fun render(grn: Grn) {
        binding.tvHeaderGrnNumber.text = grn.grnNumber
        GrnStatusStyle.applyStatus(binding.tvStatus, grn.status)
        binding.tvGrnDate.text = GrnStatusStyle.formatDate(grn.grnDate)

        // Summary rows — only render fields that have a value. Inflated
        // into a LinearLayout so each row's visibility decision is local
        // (cleaner than juggling N XML rows + visibility flags).
        binding.summaryRows.removeAllViews()
        addSummaryRow(R.string.grn_detail_store, listOfNotNull(
            grn.storeName?.takeIf { it.isNotBlank() },
            grn.businessUnitName?.takeIf { it.isNotBlank() },
        ).joinToString(" · "))
        addSummaryRow(R.string.grn_detail_supplier, grn.supplierName)
        addSummaryRow(R.string.grn_detail_po, grn.poNumber ?: grn.poReference)
        addSummaryRow(R.string.grn_detail_invoice, grn.invoiceNumber)
        addSummaryRow(R.string.grn_detail_invoice_date, grn.invoiceDate?.let {
            GrnStatusStyle.formatDate(it)
        })
        addSummaryRow(R.string.grn_detail_receipt_date, grn.receiptDate?.let {
            GrnStatusStyle.formatDate(it)
        })

        binding.tvTotalQty.text = GrnStatusStyle.formatQuantity(grn.totalQuantity)
        binding.tvTotalAmount.text = GrnStatusStyle.formatAmount(grn.totalAmount)

        // Line items
        val items = grn.items.orEmpty()
        binding.tvItemsCount.text = items.size.toString()
        binding.itemsContainer.removeAllViews()
        if (items.isEmpty()) {
            binding.tvNoItems.visibility = View.VISIBLE
        } else {
            binding.tvNoItems.visibility = View.GONE
            for (item in items) addLineItem(item)
        }

        // Remarks
        if (!grn.remarks.isNullOrBlank()) {
            binding.remarksContainer.visibility = View.VISIBLE
            binding.tvRemarks.text = grn.remarks
        } else {
            binding.remarksContainer.visibility = View.GONE
        }
    }

    private fun addSummaryRow(labelRes: Int, value: String?) {
        if (value.isNullOrBlank()) return
        val row = ItemGrnSummaryRowBinding.inflate(
            LayoutInflater.from(this),
            binding.summaryRows,
            false,
        )
        row.tvLabel.setText(labelRes)
        row.tvValue.text = value
        binding.summaryRows.addView(row.root)
    }

    private fun addLineItem(item: GrnLineItem) {
        val row = ItemGrnLineBinding.inflate(
            LayoutInflater.from(this),
            binding.itemsContainer,
            false,
        )
        row.tvMaterialName.text = item.materialName?.takeIf { it.isNotBlank() } ?: "—"

        // Subtitle: SKU only (we don't have the description here)
        row.tvMaterialMeta.text = item.materialSku?.takeIf { it.isNotBlank() } ?: ""
        row.tvMaterialMeta.visibility =
            if (row.tvMaterialMeta.text.isNullOrBlank()) View.GONE else View.VISIBLE

        // Qty: prefer accepted, fall back to received. Show received-only
        // when accepted hasn't been recorded yet (DRAFT GRNs).
        val displayedQty = item.acceptedQuantity ?: item.receivedQuantity
        val unitSym = item.unitSymbol?.takeIf { it.isNotBlank() }?.let { " $it" } ?: ""
        row.tvQty.text = "Qty: ${GrnStatusStyle.formatQuantity(displayedQty)}$unitSym"

        row.tvUnitPrice.text = "Rate: ${GrnStatusStyle.formatAmount(item.unitPrice)}"
        row.tvLineTotal.text = GrnStatusStyle.formatAmount(item.totalAmount)

        // Batch / expiry — only show when present.
        val batchParts = listOfNotNull(
            item.batchNumber?.takeIf { it.isNotBlank() }?.let { "Batch: $it" },
            item.expiryDate?.let { "Exp: ${GrnStatusStyle.formatDate(it)}" },
        )
        if (batchParts.isNotEmpty()) {
            row.tvBatch.text = batchParts.joinToString(" · ")
            row.tvBatch.visibility = View.VISIBLE
        } else {
            row.tvBatch.visibility = View.GONE
        }

        binding.itemsContainer.addView(row.root)
    }
}
