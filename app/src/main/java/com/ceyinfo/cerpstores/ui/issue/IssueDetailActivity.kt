package com.ceyinfo.cerpstores.ui.issue

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.ceyinfo.cerpstores.R
import com.ceyinfo.cerpstores.data.model.Issue
import com.ceyinfo.cerpstores.data.model.IssueLineItem
import com.ceyinfo.cerpstores.data.model.TransitionRequest
import com.ceyinfo.cerpstores.data.remote.ApiClient
import com.ceyinfo.cerpstores.databinding.ActivityIssueDetailBinding
import com.ceyinfo.cerpstores.databinding.ItemGrnSummaryRowBinding
import com.ceyinfo.cerpstores.databinding.ItemIssueLineBinding
import com.ceyinfo.cerpstores.ui.common.BottomNav
import com.ceyinfo.cerpstores.util.SessionManager
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch

/**
 * Goods Issue detail — read-only header + line items + optional transition
 * buttons based on the current status and the caller's ACL grants.
 *
 * Transition map:
 *   DRAFT      → submit   (action: "submit",  canPerformAction("store_issue","submit"))
 *   SUBMITTED  → approve  (action: "approve", canPerformAction("store_issue","approve"))
 *   SUBMITTED  → cancel   (action: "cancel",  canPerformAction("store_issue","cancel"))
 */
class IssueDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_ISSUE_ID = "issue_id"
        const val EXTRA_ISSUE_NUMBER = "issue_number"
    }

    private lateinit var binding: ActivityIssueDetailBinding
    private lateinit var issueId: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT

        binding = ActivityIssueDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        issueId = intent.getStringExtra(EXTRA_ISSUE_ID).orEmpty()
        if (issueId.isBlank()) {
            finish()
            return
        }

        // Pre-fill issue number from extras so the header shows immediately
        // before the detail fetch completes.
        intent.getStringExtra(EXTRA_ISSUE_NUMBER)?.let {
            binding.tvHeaderIssueNumber.text = it
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
                val resp = ApiClient.getService(this@IssueDetailActivity).getIssue(issueId)
                val issue = resp.body()?.data
                if (resp.isSuccessful && resp.body()?.success == true && issue != null) {
                    render(issue)
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

    private fun render(issue: Issue) {
        binding.tvHeaderIssueNumber.text = issue.issueNumber
        IssueStatusStyle.applyStatus(binding.tvStatus, issue.status)
        binding.tvIssueDate.text = IssueStatusStyle.formatDate(issue.issueDate)

        // Summary rows — sparse: only render non-blank fields
        binding.summaryRows.removeAllViews()
        addSummaryRow(R.string.issue_detail_store, listOfNotNull(
            issue.storeName?.takeIf { it.isNotBlank() },
            issue.businessUnitName?.takeIf { it.isNotBlank() },
        ).joinToString(" · ").ifEmpty { null })

        // Department: prefer structured name, fall back to freetext
        val deptValue = issue.departmentName?.takeIf { it.isNotBlank() }
            ?: issue.toDepartment?.takeIf { it.isNotBlank() }
        addSummaryRow(R.string.issue_detail_department, deptValue)

        // Issued-to: prefer structured employee name, fall back to freetext
        val issuedToValue = issue.issuedToName?.takeIf { it.isNotBlank() }
            ?: issue.issuedTo?.takeIf { it.isNotBlank() }
        addSummaryRow(R.string.issue_detail_issued_to, issuedToValue)

        binding.tvTotalQty.text = IssueStatusStyle.formatQuantity(issue.totalQuantity)

        // Transition buttons based on status + ACL
        buildActions(issue)

        // Line items
        val items = issue.items.orEmpty()
        binding.tvItemsCount.text = items.size.toString()
        binding.itemsContainer.removeAllViews()
        if (items.isEmpty()) {
            binding.tvNoItems.visibility = View.VISIBLE
        } else {
            binding.tvNoItems.visibility = View.GONE
            for (item in items) addLineItem(item)
        }

        // Remarks
        if (!issue.remarks.isNullOrBlank()) {
            binding.remarksContainer.visibility = View.VISIBLE
            binding.tvRemarks.text = issue.remarks
        } else {
            binding.remarksContainer.visibility = View.GONE
        }
    }

    private fun buildActions(issue: Issue) {
        val session = SessionManager(this)
        binding.actionsContainer.removeAllViews()

        val dp = resources.displayMetrics.density
        val lp = android.view.ViewGroup.MarginLayoutParams(
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { marginEnd = (8 * dp).toInt() }

        when (issue.status.uppercase()) {
            "DRAFT" -> {
                if (session.canPerformAction("store_issue", "submit")) {
                    addActionButton(
                        label = getString(R.string.issue_action_submit),
                        colorRes = R.color.primary,
                        lp = lp,
                        action = "submit",
                        issue = issue,
                    )
                }
            }
            "SUBMITTED" -> {
                if (session.canPerformAction("store_issue", "approve")) {
                    addActionButton(
                        label = getString(R.string.issue_action_approve),
                        colorRes = R.color.primary,
                        lp = lp,
                        action = "approve",
                        issue = issue,
                    )
                }
                if (session.canPerformAction("store_issue", "cancel")) {
                    addActionButton(
                        label = getString(R.string.issue_action_cancel),
                        colorRes = R.color.error,
                        lp = lp,
                        action = "cancel",
                        issue = issue,
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
        issue: Issue,
    ) {
        val btn = MaterialButton(this).apply {
            text = label
            setBackgroundColor(ContextCompat.getColor(this@IssueDetailActivity, colorRes))
            setTextColor(ContextCompat.getColor(this@IssueDetailActivity, R.color.white))
            cornerRadius = (10 * resources.displayMetrics.density).toInt()
            setOnClickListener { performTransition(issue, action) }
        }
        binding.actionsContainer.addView(btn, lp)
    }

    private fun performTransition(issue: Issue, action: String) {
        lifecycleScope.launch {
            try {
                val resp = ApiClient.getService(this@IssueDetailActivity)
                    .transitionIssue(issue.id, TransitionRequest(action))
                if (resp.isSuccessful && resp.body()?.success == true) {
                    Toast.makeText(
                        this@IssueDetailActivity,
                        "Issue ${action}d successfully.",
                        Toast.LENGTH_SHORT,
                    ).show()
                    // Refresh to show updated status + new available actions
                    load()
                } else {
                    val msg = resp.body()?.message ?: "Action failed. Please try again."
                    Toast.makeText(this@IssueDetailActivity, msg, Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(
                    this@IssueDetailActivity,
                    e.message ?: "Action failed. Please try again.",
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

    private fun addLineItem(item: IssueLineItem) {
        val row = ItemIssueLineBinding.inflate(
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
        row.tvQty.text = "Qty: ${IssueStatusStyle.formatQuantity(item.issuedQuantity)}$unitSym"

        if (!item.batchNumber.isNullOrBlank()) {
            row.tvBatch.text = "Batch: ${item.batchNumber}"
            row.tvBatch.visibility = View.VISIBLE
        } else {
            row.tvBatch.visibility = View.GONE
        }

        binding.itemsContainer.addView(row.root)
    }
}
