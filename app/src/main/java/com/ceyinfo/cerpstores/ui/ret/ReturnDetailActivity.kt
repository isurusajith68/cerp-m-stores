package com.ceyinfo.cerpstores.ui.ret

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.ceyinfo.cerpstores.R
import com.ceyinfo.cerpstores.data.model.MaterialReturn
import com.ceyinfo.cerpstores.data.model.ReturnLineItem
import com.ceyinfo.cerpstores.data.model.TransitionRequest
import com.ceyinfo.cerpstores.data.remote.ApiClient
import com.ceyinfo.cerpstores.databinding.ActivityReturnDetailBinding
import com.ceyinfo.cerpstores.databinding.ItemGrnSummaryRowBinding
import com.ceyinfo.cerpstores.databinding.ItemReturnLineBinding
import com.ceyinfo.cerpstores.ui.common.BottomNav
import com.ceyinfo.cerpstores.util.SessionManager
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch

class ReturnDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_RETURN_ID = "return_id"
        const val EXTRA_RETURN_NUMBER = "return_number"
    }

    private lateinit var binding: ActivityReturnDetailBinding
    private lateinit var returnId: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT

        binding = ActivityReturnDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        returnId = intent.getStringExtra(EXTRA_RETURN_ID).orEmpty()
        if (returnId.isBlank()) { finish(); return }

        intent.getStringExtra(EXTRA_RETURN_NUMBER)?.let {
            binding.tvHeaderReturnNumber.text = it
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
                val resp = ApiClient.getService(this@ReturnDetailActivity).getReturn(returnId)
                val ret = resp.body()?.data
                if (resp.isSuccessful && resp.body()?.success == true && ret != null) {
                    render(ret)
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

    private fun render(ret: MaterialReturn) {
        binding.tvHeaderReturnNumber.text = ret.returnNumber
        ReturnStatusStyle.applyStatus(binding.tvStatus, ret.status)
        binding.tvReturnDate.text = ReturnStatusStyle.formatDate(ret.returnDate)

        binding.summaryRows.removeAllViews()
        addSummaryRow(R.string.return_detail_from_store, ret.fromStoreName)

        val typeLabel = when (ret.returnType.lowercase()) {
            "to_supplier" -> "To Supplier"
            "to_store"    -> "To Store"
            else          -> ret.returnType
        }
        addSummaryRow(R.string.return_detail_type, typeLabel)
        addSummaryRow(R.string.return_detail_supplier, ret.supplierName)
        addSummaryRow(R.string.return_detail_to_store, ret.toStoreName)
        addSummaryRow(R.string.return_detail_grn_ref, ret.grnReferenceNumber)

        val totalQty = ret.items?.sumOf { it.returnQuantity } ?: 0.0
        binding.tvTotalQty.text = ReturnStatusStyle.formatQuantity(totalQty)

        buildActions(ret)

        val items = ret.items.orEmpty()
        binding.tvItemsCount.text = items.size.toString()
        binding.itemsContainer.removeAllViews()
        if (items.isEmpty()) {
            binding.tvNoItems.visibility = View.VISIBLE
        } else {
            binding.tvNoItems.visibility = View.GONE
            for (item in items) addLineItem(item)
        }

        if (!ret.remarks.isNullOrBlank()) {
            binding.remarksContainer.visibility = View.VISIBLE
            binding.tvRemarks.text = ret.remarks
        } else {
            binding.remarksContainer.visibility = View.GONE
        }
    }

    private fun buildActions(ret: MaterialReturn) {
        val session = SessionManager(this)
        binding.actionsContainer.removeAllViews()

        val dp = resources.displayMetrics.density
        val lp = android.view.ViewGroup.MarginLayoutParams(
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { marginEnd = (8 * dp).toInt() }

        when (ret.status.uppercase()) {
            "DRAFT" -> {
                if (session.canPerformAction("material_return", "submit")) {
                    addActionButton(getString(R.string.return_action_submit), R.color.primary, lp, "submit", ret)
                }
            }
            "PENDING" -> {
                if (session.canPerformAction("material_return", "approve")) {
                    addActionButton(getString(R.string.return_action_approve), R.color.primary, lp, "approve", ret)
                }
                if (session.canPerformAction("material_return", "cancel")) {
                    addActionButton(getString(R.string.return_action_cancel), R.color.error, lp, "cancel", ret)
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
        ret: MaterialReturn,
    ) {
        val btn = MaterialButton(this).apply {
            text = label
            setBackgroundColor(ContextCompat.getColor(this@ReturnDetailActivity, colorRes))
            setTextColor(ContextCompat.getColor(this@ReturnDetailActivity, R.color.white))
            cornerRadius = (10 * resources.displayMetrics.density).toInt()
            setOnClickListener { performTransition(ret, action) }
        }
        binding.actionsContainer.addView(btn, lp)
    }

    private fun performTransition(ret: MaterialReturn, action: String) {
        lifecycleScope.launch {
            try {
                val resp = ApiClient.getService(this@ReturnDetailActivity)
                    .transitionReturn(ret.id, TransitionRequest(action))
                if (resp.isSuccessful && resp.body()?.success == true) {
                    Toast.makeText(this@ReturnDetailActivity, "Return ${action}d successfully.", Toast.LENGTH_SHORT).show()
                    load()
                } else {
                    val msg = resp.body()?.message ?: "Action failed. Please try again."
                    Toast.makeText(this@ReturnDetailActivity, msg, Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@ReturnDetailActivity, e.message ?: "Action failed.", Toast.LENGTH_LONG).show()
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

    private fun addLineItem(item: ReturnLineItem) {
        val row = ItemReturnLineBinding.inflate(LayoutInflater.from(this), binding.itemsContainer, false)
        row.tvMaterialName.text = item.materialName?.takeIf { it.isNotBlank() } ?: "—"

        val meta = listOfNotNull(
            item.materialSku?.takeIf { it.isNotBlank() },
            item.unitSymbol?.takeIf { it.isNotBlank() },
        ).joinToString(" · ")
        row.tvMaterialMeta.text = meta
        row.tvMaterialMeta.visibility = if (meta.isBlank()) View.GONE else View.VISIBLE

        val unitSym = item.unitSymbol?.takeIf { it.isNotBlank() }?.let { " $it" } ?: ""
        row.tvQty.text = "Qty: ${ReturnStatusStyle.formatQuantity(item.returnQuantity)}$unitSym"

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
