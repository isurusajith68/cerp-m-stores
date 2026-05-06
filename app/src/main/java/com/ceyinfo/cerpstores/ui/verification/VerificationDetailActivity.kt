package com.ceyinfo.cerpstores.ui.verification

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import coil.load
import com.ceyinfo.cerpstores.R
import com.ceyinfo.cerpstores.data.model.TransitionRequest
import com.ceyinfo.cerpstores.data.model.Verification
import com.ceyinfo.cerpstores.data.model.VerificationItem
import com.ceyinfo.cerpstores.data.remote.ApiClient
import com.ceyinfo.cerpstores.databinding.ActivityVerificationDetailBinding
import com.ceyinfo.cerpstores.databinding.ItemGrnSummaryRowBinding
import com.ceyinfo.cerpstores.databinding.ItemVerificationLineBinding
import com.ceyinfo.cerpstores.ui.common.BottomNav
import com.ceyinfo.cerpstores.ui.grn.GrnStatusStyle
import com.ceyinfo.cerpstores.util.SessionManager
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch

/**
 * Detail screen with state-machine transition buttons wired from day one.
 *
 * Buttons appear when:
 *   - the corresponding action is in `allowedActions` for the verification
 *     entity (server-side ACL — set via Permissions page in cerp-user)
 *   - AND the action is valid for the current status (DRAFT → submit/cancel,
 *     SUBMITTED → approve/recall/cancel, APPROVED/CANCELLED → none).
 *
 * Tapping fires PATCH /verifications/:id/transition with the action code,
 * then re-fetches the detail to pick up the new status (and on APPROVE,
 * the variance snapshots the backend writes per line item).
 */
class VerificationDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_ID = "verification_id"
        const val EXTRA_NUMBER = "verification_number"
    }

    private lateinit var binding: ActivityVerificationDetailBinding
    private lateinit var verificationId: String
    private lateinit var session: SessionManager

    /** Last loaded payload — needed to drive the action button rebuild. */
    private var current: Verification? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT

        binding = ActivityVerificationDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        verificationId = intent.getStringExtra(EXTRA_ID).orEmpty()
        if (verificationId.isBlank()) {
            finish()
            return
        }

        intent.getStringExtra(EXTRA_NUMBER)?.let { binding.tvHeaderNumber.text = it }

        session = SessionManager(this)
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
                val resp = ApiClient.getService(this@VerificationDetailActivity)
                    .getVerification(verificationId)
                val v = resp.body()?.data
                if (resp.isSuccessful && resp.body()?.success == true && v != null) {
                    current = v
                    render(v)
                    binding.scrollContent.visibility = View.VISIBLE
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

    private fun render(v: Verification) {
        binding.tvHeaderNumber.text = v.verificationNumber
        GrnStatusStyle.applyStatus(binding.tvStatus, v.status)
        binding.tvDate.text = GrnStatusStyle.formatDate(v.verificationDate)

        binding.summaryRows.removeAllViews()
        addSummaryRow(R.string.verification_detail_store, listOfNotNull(
            v.storeName?.takeIf { it.isNotBlank() },
            v.businessUnitName?.takeIf { it.isNotBlank() },
        ).joinToString(" · "))
        addSummaryRow(R.string.verification_detail_date, v.verificationDate?.let {
            GrnStatusStyle.formatDate(it)
        })
        val lineCount = v.items?.size ?: v.itemsCount ?: 0
        addSummaryRow(R.string.verification_detail_total_lines, lineCount.toString())

        renderActionButtons(v)
        renderItems(v.items.orEmpty())

        if (!v.remarks.isNullOrBlank()) {
            binding.remarksContainer.visibility = View.VISIBLE
            binding.tvRemarks.text = v.remarks
        } else {
            binding.remarksContainer.visibility = View.GONE
        }
    }

    /**
     * Build the action button row from the current status × ACL grants.
     *
     * The button-per-action map below intentionally hard-codes which actions
     * make sense from each status — relying purely on the backend's
     * resolveTransition would mean the UI shows buttons that 400. Cheaper
     * to mirror the state machine here than to round-trip every option.
     */
    private fun renderActionButtons(v: Verification) {
        binding.actionsContainer.removeAllViews()

        data class ActionSpec(
            val code: String,
            val labelRes: Int,
            val isPrimary: Boolean,
        )

        val available: List<ActionSpec> = when (v.status.uppercase()) {
            "DRAFT" -> listOf(
                ActionSpec("submit", R.string.verification_action_submit, isPrimary = true),
                ActionSpec("cancel", R.string.verification_action_cancel, isPrimary = false),
            )
            "SUBMITTED" -> listOf(
                ActionSpec("approve", R.string.verification_action_approve, isPrimary = true),
                ActionSpec("recall",  R.string.verification_action_recall,  isPrimary = false),
                ActionSpec("cancel",  R.string.verification_action_cancel,  isPrimary = false),
            )
            else -> emptyList() // APPROVED + CANCELLED are terminal
        }

        // Filter against allowed_actions from /my-permissions cache. Owners
        // pass through (canPerformAction returns true for owner regardless).
        val allowed = available.filter {
            session.canPerformAction("verification", it.code)
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
                    // Secondary buttons render outlined to keep the primary
                    // (Submit / Approve) visually anchored as the default.
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
            btn.setOnClickListener { performTransition(spec.code) }
            binding.actionsContainer.addView(btn)
        }
    }

    private fun performTransition(action: String) {
        // Block re-tap while in flight by hiding the action row. Detail
        // re-renders on response so visibility comes back via render().
        binding.actionsContainer.visibility = View.GONE
        lifecycleScope.launch {
            try {
                val resp = ApiClient.getService(this@VerificationDetailActivity)
                    .transitionVerification(verificationId, TransitionRequest(action))
                val body = resp.body()
                if (resp.isSuccessful && body?.success == true) {
                    load()
                } else {
                    val msg = body?.message ?: getString(R.string.error_loading)
                    Toast.makeText(this@VerificationDetailActivity, msg, Toast.LENGTH_LONG).show()
                    current?.let { renderActionButtons(it) }
                }
            } catch (e: Exception) {
                Toast.makeText(
                    this@VerificationDetailActivity,
                    e.message ?: getString(R.string.error_loading),
                    Toast.LENGTH_LONG,
                ).show()
                current?.let { renderActionButtons(it) }
            }
        }
    }

    private fun renderItems(items: List<VerificationItem>) {
        binding.tvItemsCount.text = items.size.toString()
        binding.itemsContainer.removeAllViews()
        if (items.isEmpty()) {
            binding.tvNoItems.visibility = View.VISIBLE
            return
        }
        binding.tvNoItems.visibility = View.GONE
        for (item in items) addLineItem(item)
    }

    private fun addLineItem(item: VerificationItem) {
        val row = ItemVerificationLineBinding.inflate(
            LayoutInflater.from(this),
            binding.itemsContainer,
            false,
        )
        // Photo strip — backend re-signs every URL on each detail fetch
        // so they're always fresh. The strip stays gone when no photos.
        // Removing the per-tile × interaction here: detail is read-only,
        // remove only happens on the create form.
        val urls = item.photoUrls.filter { it.isNotBlank() }
        if (urls.isNotEmpty()) {
            row.photoScroll.visibility = View.VISIBLE
            row.photoStrip.removeAllViews()
            for (url in urls) {
                val tile = com.ceyinfo.cerpstores.databinding.ItemVerificationPhotoTileBinding.inflate(
                    LayoutInflater.from(this),
                    row.photoStrip,
                    false,
                )
                // Larger detail thumbs — bump from 96dp default to 140dp.
                val dp = resources.displayMetrics.density
                tile.root.layoutParams = tile.root.layoutParams.apply {
                    width = (140 * dp).toInt()
                    height = (140 * dp).toInt()
                }
                tile.btnRemove.visibility = View.GONE
                tile.photoThumb.load(url) { crossfade(true) }
                row.photoStrip.addView(tile.root)
            }
        }

        // Description always present; material name (when bound to catalogue)
        // already overrode it on the backend, so just show description as-is.
        row.tvDescription.text = item.description.ifBlank {
            item.materialName ?: "—"
        }

        // Meta strip: SKU + free-text indicator
        val metaParts = mutableListOf<String>()
        item.materialSku?.takeIf { it.isNotBlank() }?.let { metaParts.add(it) }
        if (item.materialId == null) metaParts.add("Off-catalogue")
        if (metaParts.isNotEmpty()) {
            row.tvMeta.text = metaParts.joinToString(" · ")
            row.tvMeta.visibility = View.VISIBLE
        }

        val unitSym = item.unitSymbol?.takeIf { it.isNotBlank() }?.let { " $it" } ?: ""
        row.tvQty.text = "Qty: ${GrnStatusStyle.formatQuantity(item.verifiedQuantity)}$unitSym"

        // Variance pill — only painted post-APPROVE when delta_quantity is
        // populated. Positive delta = surplus (green), negative = shortage
        // (red), zero = match (grey).
        val delta = item.deltaQuantity
        if (delta != null) {
            val sign = when {
                delta > 0 -> "+"
                delta < 0 -> "−"
                else -> ""
            }
            row.tvVariance.text = "Δ $sign${GrnStatusStyle.formatQuantity(kotlin.math.abs(delta))}"
            row.tvVariance.setTextColor(when {
                delta > 0 -> getColor(R.color.success)
                delta < 0 -> getColor(R.color.error)
                else -> getColor(R.color.text_secondary)
            })
            row.tvVariance.visibility = View.VISIBLE
        }

        val tags = listOfNotNull(
            item.serialNumber?.takeIf { it.isNotBlank() }?.let { "S/N: $it" },
            item.batchNumber?.takeIf { it.isNotBlank() }?.let { "Batch: $it" },
        )
        if (tags.isNotEmpty()) {
            row.tvSerialBatch.text = tags.joinToString(" · ")
            row.tvSerialBatch.visibility = View.VISIBLE
        }

        if (!item.remarks.isNullOrBlank()) {
            row.tvRemarks.text = item.remarks
            row.tvRemarks.visibility = View.VISIBLE
        }

        binding.itemsContainer.addView(row.root)
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
}
