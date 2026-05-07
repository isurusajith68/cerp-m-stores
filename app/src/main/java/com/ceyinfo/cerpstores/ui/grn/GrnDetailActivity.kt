package com.ceyinfo.cerpstores.ui.grn

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.ceyinfo.cerpstores.R
import com.ceyinfo.cerpstores.data.model.Grn
import com.ceyinfo.cerpstores.data.model.GrnLineItem
import com.ceyinfo.cerpstores.data.model.TransitionRequest
import com.ceyinfo.cerpstores.data.remote.ApiClient
import com.ceyinfo.cerpstores.databinding.ActivityGrnDetailBinding
import com.ceyinfo.cerpstores.databinding.ItemGrnLineBinding
import com.ceyinfo.cerpstores.databinding.ItemGrnSummaryRowBinding
import com.ceyinfo.cerpstores.ui.common.BottomNav
import com.ceyinfo.cerpstores.util.SessionManager
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch

/**
 * GRN detail with full state-machine transition buttons (Pass 2).
 *
 * Button visibility is driven by two guards:
 *   1. Status — which actions are valid for the current state
 *      (DRAFT→submit/cancel, SUBMITTED→approve/recall/cancel, terminal otherwise)
 *   2. ACL   — `session.canPerformAction("grn", actionCode)` filtering against
 *      the `/my-permissions` cache loaded at login.
 *
 * Destructive transitions (approve, recall, cancel) show an AlertDialog before
 * firing so taps during scrolling don't accidentally commit financials.
 * On success the detail re-fetches so the status badge and button row both
 * refresh. On error a Toast shows the backend message and the buttons restore.
 */
class GrnDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_GRN_ID = "grn_id"
        const val EXTRA_GRN_NUMBER = "grn_number"
    }

    private lateinit var binding: ActivityGrnDetailBinding
    private lateinit var grnId: String
    private lateinit var session: SessionManager

    /** Last successfully loaded GRN — drives button rebuild after errors. */
    private var current: Grn? = null

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

        session = SessionManager(this)
        binding.btnBack.setOnClickListener { finish() }
        BottomNav.bind(binding.bottomNav.root, this, BottomNav.Tab.HOME)

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
                    current = grn
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

        // Action buttons — built last so they reflect the freshly loaded status.
        renderActionButtons(grn)

        // Remarks
        if (!grn.remarks.isNullOrBlank()) {
            binding.remarksContainer.visibility = View.VISIBLE
            binding.tvRemarks.text = grn.remarks
        } else {
            binding.remarksContainer.visibility = View.GONE
        }
    }

    // ── Transition buttons ────────────────────────────────────────────────────

    /**
     * Build the action button row from the current status × ACL grants.
     *
     * The available-actions map mirrors the backend state machine defined in
     * `seed-store-acl.ts` so we never show a button that would 400:
     *
     *   DRAFT      → submit (primary), cancel (secondary)
     *   SUBMITTED  → approve (primary), recall (secondary), cancel (secondary)
     *   APPROVED   → terminal (no buttons)
     *   CANCELLED  → terminal (no buttons)
     */
    private fun renderActionButtons(grn: Grn) {
        binding.actionsContainer.removeAllViews()

        data class ActionSpec(
            val code: String,
            val labelRes: Int,
            val isPrimary: Boolean,
        )

        val available: List<ActionSpec> = when (grn.status.uppercase()) {
            "DRAFT" -> listOf(
                ActionSpec("submit", R.string.grn_action_submit, isPrimary = true),
                ActionSpec("cancel", R.string.grn_action_cancel, isPrimary = false),
            )
            "PENDING" -> listOf(
                ActionSpec("approve", R.string.grn_action_approve, isPrimary = true),
                ActionSpec("recall",  R.string.grn_action_recall,  isPrimary = false),
                ActionSpec("cancel",  R.string.grn_action_cancel,  isPrimary = false),
            )
            else -> emptyList() // APPROVED + CANCELLED are terminal states
        }

        // Filter against allowed_actions from /my-permissions cache. Owners
        // pass through automatically (canPerformAction returns true for owners).
        val allowed = available.filter {
            session.canPerformAction("grn", it.code)
        }

        if (allowed.isEmpty()) {
            binding.actionsContainer.visibility = View.GONE
            return
        }

        binding.actionsContainer.visibility = View.VISIBLE
        binding.actionsContainer.orientation = LinearLayout.HORIZONTAL

        val dp = resources.displayMetrics.density
        for ((idx, spec) in allowed.withIndex()) {
            val btn = MaterialButton(this).apply {
                text = getString(spec.labelRes)
                textSize = 13f
                isAllCaps = false
                cornerRadius = (12 * dp).toInt()
                if (!spec.isPrimary) {
                    // Outlined style for secondary actions (recall, cancel) so
                    // the primary (Submit / Approve) stays visually anchored.
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    setTextColor(getColor(R.color.on_surface))
                    strokeWidth = (1 * dp).toInt()
                    strokeColor = android.content.res.ColorStateList.valueOf(
                        android.graphics.Color.parseColor("#E2E8F0")
                    )
                }
            }
            val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            if (idx > 0) lp.marginStart = (8 * dp).toInt()
            btn.layoutParams = lp
            btn.setOnClickListener { maybeConfirmThenTransition(spec.code) }
            binding.actionsContainer.addView(btn)
        }
    }

    /**
     * Show a confirmation dialog for destructive/irreversible actions, then
     * call [performTransition]. Non-destructive actions (submit) fire directly.
     */
    private fun maybeConfirmThenTransition(action: String) {
        val (titleRes, messageRes) = when (action) {
            "approve" -> R.string.grn_confirm_approve_title to R.string.grn_confirm_approve_message
            "recall"  -> R.string.grn_confirm_recall_title  to R.string.grn_confirm_recall_message
            "cancel"  -> R.string.grn_confirm_cancel_title  to R.string.grn_confirm_cancel_message
            else -> {
                // submit and any unrecognised action fire immediately
                performTransition(action)
                return
            }
        }

        AlertDialog.Builder(this)
            .setTitle(titleRes)
            .setMessage(messageRes)
            .setPositiveButton(R.string.grn_confirm_positive) { _, _ -> performTransition(action) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /**
     * Fire the transition API call.
     *
     * While in-flight the button row is hidden to prevent double-taps.
     * On success the full detail re-fetches so the status badge, button row,
     * and (for APPROVED) any server-side derived fields all refresh together.
     * On failure the buttons restore from [current] so the user can retry.
     */
    private fun performTransition(action: String) {
        binding.actionsContainer.visibility = View.GONE
        lifecycleScope.launch {
            try {
                val resp = ApiClient.getService(this@GrnDetailActivity)
                    .transitionGrn(grnId, TransitionRequest(action))
                val body = resp.body()
                if (resp.isSuccessful && body?.success == true) {
                    Toast.makeText(
                        this@GrnDetailActivity,
                        getString(R.string.grn_transition_success),
                        Toast.LENGTH_SHORT,
                    ).show()
                    load() // full re-fetch picks up new status + any server side-effects
                } else {
                    val msg = body?.message ?: getString(R.string.grn_transition_failed)
                    Toast.makeText(this@GrnDetailActivity, msg, Toast.LENGTH_LONG).show()
                    current?.let { renderActionButtons(it) }
                }
            } catch (e: Exception) {
                Toast.makeText(
                    this@GrnDetailActivity,
                    e.message ?: getString(R.string.grn_transition_failed),
                    Toast.LENGTH_LONG,
                ).show()
                current?.let { renderActionButtons(it) }
            }
        }
    }

    // ── View helpers ──────────────────────────────────────────────────────────

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
