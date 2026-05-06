package com.ceyinfo.cerpstores.ui.transfer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.ceyinfo.cerpstores.R
import com.ceyinfo.cerpstores.data.model.Transfer
import com.ceyinfo.cerpstores.data.model.TransferLineItem
import com.ceyinfo.cerpstores.data.model.TransitionRequest
import com.ceyinfo.cerpstores.data.remote.ApiClient
import com.ceyinfo.cerpstores.databinding.ActivityTransferDetailBinding
import com.ceyinfo.cerpstores.databinding.ItemGrnSummaryRowBinding
import com.ceyinfo.cerpstores.databinding.ItemTransferLineBinding
import com.ceyinfo.cerpstores.ui.common.BottomNav
import com.ceyinfo.cerpstores.util.SessionManager
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch

/**
 * Stock Transfer detail — read-only header + line items + optional
 * transition buttons based on current status and caller's ACL grants.
 *
 * Transition map (matches the seeded perm_state_transitions for stock_transfer):
 *   DRAFT      → submit  (action: "submit")  → PENDING
 *   DRAFT      → cancel  (action: "cancel")  → CANCELLED
 *   PENDING    → approve (action: "approve") → IN_TRANSIT (deducts source stock)
 *   PENDING    → recall  (action: "recall")  → DRAFT
 *   PENDING    → cancel  (action: "cancel")  → CANCELLED
 *   IN_TRANSIT → receive (action: "receive") → RECEIVED  (adds destination stock)
 *   IN_TRANSIT → cancel  (action: "cancel")  → CANCELLED
 */
class TransferDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_TRANSFER_ID = "transfer_id"
        const val EXTRA_TRANSFER_NUMBER = "transfer_number"
    }

    private lateinit var binding: ActivityTransferDetailBinding
    private lateinit var transferId: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT

        binding = ActivityTransferDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        transferId = intent.getStringExtra(EXTRA_TRANSFER_ID).orEmpty()
        if (transferId.isBlank()) {
            finish()
            return
        }

        // Pre-fill transfer number from extras so the header shows immediately
        intent.getStringExtra(EXTRA_TRANSFER_NUMBER)?.let {
            binding.tvHeaderTransferNumber.text = it
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
                val resp = ApiClient.getService(this@TransferDetailActivity).getTransfer(transferId)
                val transfer = resp.body()?.data
                if (resp.isSuccessful && resp.body()?.success == true && transfer != null) {
                    render(transfer)
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

    private fun render(transfer: Transfer) {
        binding.tvHeaderTransferNumber.text = transfer.transferNumber
        TransferStatusStyle.applyStatus(binding.tvStatus, transfer.status)
        binding.tvTransferDate.text = TransferStatusStyle.formatDate(transfer.transferDate)

        // Summary rows
        binding.summaryRows.removeAllViews()

        val fromValue = listOfNotNull(
            transfer.fromStoreName?.takeIf { it.isNotBlank() },
            transfer.fromStoreCode?.takeIf { it.isNotBlank() },
        ).joinToString(" · ").ifEmpty { null }
        addSummaryRow(R.string.transfer_detail_from_store, fromValue)

        val toValue = listOfNotNull(
            transfer.toStoreName?.takeIf { it.isNotBlank() },
            transfer.toStoreCode?.takeIf { it.isNotBlank() },
        ).joinToString(" · ").ifEmpty { null }
        addSummaryRow(R.string.transfer_detail_to_store, toValue)

        addSummaryRow(
            R.string.transfer_detail_bu,
            transfer.businessUnitName?.takeIf { it.isNotBlank() },
        )

        binding.tvTotalQty.text = TransferStatusStyle.formatQuantity(transfer.totalQuantity)

        // Transition buttons based on status + ACL
        buildActions(transfer)

        // Line items
        val items = transfer.items.orEmpty()
        binding.tvItemsCount.text = items.size.toString()
        binding.itemsContainer.removeAllViews()
        if (items.isEmpty()) {
            binding.tvNoItems.visibility = View.VISIBLE
        } else {
            binding.tvNoItems.visibility = View.GONE
            for (item in items) addLineItem(item)
        }

        // Remarks
        if (!transfer.remarks.isNullOrBlank()) {
            binding.remarksContainer.visibility = View.VISIBLE
            binding.tvRemarks.text = transfer.remarks
        } else {
            binding.remarksContainer.visibility = View.GONE
        }
    }

    private fun buildActions(transfer: Transfer) {
        val session = SessionManager(this)
        binding.actionsContainer.removeAllViews()

        val dp = resources.displayMetrics.density
        val lp = android.view.ViewGroup.MarginLayoutParams(
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { marginEnd = (8 * dp).toInt() }

        when (transfer.status.uppercase()) {
            "DRAFT" -> {
                if (session.canPerformAction("stock_transfer", "submit")) {
                    addActionButton(
                        label = getString(R.string.transfer_action_submit),
                        colorRes = R.color.primary,
                        lp = lp,
                        action = "submit",
                        transfer = transfer,
                    )
                }
                if (session.canPerformAction("stock_transfer", "cancel")) {
                    addActionButton(
                        label = getString(R.string.transfer_action_cancel),
                        colorRes = R.color.error,
                        lp = lp,
                        action = "cancel",
                        transfer = transfer,
                    )
                }
            }
            "PENDING" -> {
                if (session.canPerformAction("stock_transfer", "approve")) {
                    addActionButton(
                        label = getString(R.string.transfer_action_approve),
                        colorRes = R.color.primary,
                        lp = lp,
                        action = "approve",
                        transfer = transfer,
                    )
                }
                if (session.canPerformAction("stock_transfer", "recall")) {
                    addActionButton(
                        label = getString(R.string.transfer_action_recall),
                        colorRes = R.color.text_secondary,
                        lp = lp,
                        action = "recall",
                        transfer = transfer,
                    )
                }
                if (session.canPerformAction("stock_transfer", "cancel")) {
                    addActionButton(
                        label = getString(R.string.transfer_action_cancel),
                        colorRes = R.color.error,
                        lp = lp,
                        action = "cancel",
                        transfer = transfer,
                    )
                }
            }
            "IN_TRANSIT" -> {
                if (session.canPerformAction("stock_transfer", "receive")) {
                    addActionButton(
                        label = getString(R.string.transfer_action_receive),
                        colorRes = R.color.primary,
                        lp = lp,
                        action = "receive",
                        transfer = transfer,
                    )
                }
                if (session.canPerformAction("stock_transfer", "cancel")) {
                    addActionButton(
                        label = getString(R.string.transfer_action_cancel),
                        colorRes = R.color.error,
                        lp = lp,
                        action = "cancel",
                        transfer = transfer,
                    )
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
        transfer: Transfer,
    ) {
        val btn = MaterialButton(this).apply {
            text = label
            setBackgroundColor(ContextCompat.getColor(this@TransferDetailActivity, colorRes))
            setTextColor(ContextCompat.getColor(this@TransferDetailActivity, R.color.white))
            cornerRadius = (10 * resources.displayMetrics.density).toInt()
            setOnClickListener { performTransition(transfer, action) }
        }
        binding.actionsContainer.addView(btn, lp)
    }

    private fun performTransition(transfer: Transfer, action: String) {
        lifecycleScope.launch {
            try {
                val resp = ApiClient.getService(this@TransferDetailActivity)
                    .transitionTransfer(transfer.id, TransitionRequest(action))
                if (resp.isSuccessful && resp.body()?.success == true) {
                    val msg = when (action) {
                        "submit"  -> getString(R.string.transfer_transition_submitted)
                        "approve" -> getString(R.string.transfer_transition_approved)
                        "recall"  -> getString(R.string.transfer_transition_recalled)
                        "receive" -> getString(R.string.transfer_transition_received)
                        "cancel"  -> getString(R.string.transfer_transition_cancelled)
                        else      -> getString(R.string.transfer_transition_updated)
                    }
                    Toast.makeText(this@TransferDetailActivity, msg, Toast.LENGTH_SHORT).show()
                    load()
                } else {
                    val msg = resp.body()?.message ?: getString(R.string.transfer_transition_failed)
                    Toast.makeText(this@TransferDetailActivity, msg, Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(
                    this@TransferDetailActivity,
                    e.message ?: getString(R.string.transfer_transition_failed),
                    Toast.LENGTH_LONG,
                ).show()
            }
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

    private fun addLineItem(item: TransferLineItem) {
        val row = ItemTransferLineBinding.inflate(
            LayoutInflater.from(this),
            binding.itemsContainer,
            false,
        )
        row.tvMaterialName.text = item.materialName?.takeIf { it.isNotBlank() } ?: "—"

        val meta = listOfNotNull(
            item.materialSku?.takeIf { it.isNotBlank() },
            item.unitSymbol?.takeIf { it.isNotBlank() },
        ).joinToString(" · ")
        row.tvMaterialMeta.text = meta
        row.tvMaterialMeta.visibility =
            if (meta.isBlank()) View.GONE else View.VISIBLE

        val unitSym = item.unitSymbol?.takeIf { it.isNotBlank() }?.let { " $it" } ?: ""
        row.tvQty.text = "Qty: ${TransferStatusStyle.formatQuantity(item.transferQuantity)}$unitSym"

        if (!item.batchNumber.isNullOrBlank()) {
            row.tvBatch.text = "Batch: ${item.batchNumber}"
            row.tvBatch.visibility = View.VISIBLE
        } else {
            row.tvBatch.visibility = View.GONE
        }

        binding.itemsContainer.addView(row.root)
    }
}
