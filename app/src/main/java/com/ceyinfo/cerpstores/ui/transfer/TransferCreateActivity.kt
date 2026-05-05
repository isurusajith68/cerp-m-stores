package com.ceyinfo.cerpstores.ui.transfer

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ceyinfo.cerpstores.R
import com.ceyinfo.cerpstores.data.model.CreateTransferRequest
import com.ceyinfo.cerpstores.data.model.StoreInfo
import com.ceyinfo.cerpstores.data.remote.ApiClient
import com.ceyinfo.cerpstores.databinding.ActivityTransferCreateBinding
import com.ceyinfo.cerpstores.databinding.ItemTransferLineAddedBinding
import com.ceyinfo.cerpstores.databinding.SheetStorePickerBinding
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * Stock Transfer create form.
 *
 * Required:
 *  - from_store (passed via intent extras from StorePickerSheet → NewTransactionSheet)
 *  - to_store   (selected here via ToStore picker — must differ from from_store)
 *  - transfer_date (defaults to today)
 *  - At least one line item
 *
 * Optional:
 *  - Remarks
 *
 * On save the user lands on TransferDetailActivity. We finish() so Back
 * from detail returns to the list / dashboard.
 */
class TransferCreateActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_STORE_ID = "store_id"
        const val EXTRA_STORE_NAME = "store_name"
    }

    private lateinit var binding: ActivityTransferCreateBinding

    // From-store comes via intent
    private lateinit var fromStoreId: String
    private var fromStoreName: String? = null

    // To-store is picked in this screen
    private var toStore: StoreInfo? = null

    // Line items
    private val lines = mutableListOf<AddTransferLineSheet.LineEntry>()
    private var transferDateIso: String = today()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT

        binding = ActivityTransferCreateBinding.inflate(layoutInflater)
        setContentView(binding.root)

        fromStoreId = intent.getStringExtra(EXTRA_STORE_ID).orEmpty()
        fromStoreName = intent.getStringExtra(EXTRA_STORE_NAME)
        if (fromStoreId.isBlank()) {
            finish()
            return
        }

        binding.btnBack.setOnClickListener { finish() }

        // Show from-store in header subtitle
        binding.tvFromStore.text = fromStoreName ?: fromStoreId

        // Transfer date — default today
        binding.tvTransferDate.text = TransferStatusStyle.formatDate(transferDateIso)
        binding.cardTransferDate.setOnClickListener {
            pickDate(transferDateIso) { iso ->
                transferDateIso = iso
                binding.tvTransferDate.text = TransferStatusStyle.formatDate(iso)
            }
        }

        // To-store picker
        binding.cardToStore.setOnClickListener {
            showToStorePicker()
        }

        // Add line item
        binding.btnAddLine.setOnClickListener {
            AddTransferLineSheet.show(this) { entry ->
                lines.add(entry)
                refreshLines()
            }
        }

        binding.btnSave.setOnClickListener { submit() }

        refreshLines()
    }

    private fun today(): String {
        val cal = Calendar.getInstance()
        return "%04d-%02d-%02d".format(
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH),
        )
    }

    private fun pickDate(currentIso: String, onPick: (String) -> Unit) {
        val cal = Calendar.getInstance()
        runCatching {
            val parts = currentIso.split("-").map { it.toInt() }
            cal.set(parts[0], parts[1] - 1, parts[2])
        }
        DatePickerDialog(
            this,
            { _, year, month, day ->
                onPick("%04d-%02d-%02d".format(year, month + 1, day))
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH),
        ).show()
    }

    /**
     * Show a bottom sheet listing all stores the user can transfer to.
     * Excludes the currently selected from-store so the user cannot
     * pick the same store for both ends of the transfer.
     */
    private fun showToStorePicker() {
        val dialog = BottomSheetDialog(this, R.style.Theme_Stores_BottomSheet)
        val binding = SheetStorePickerBinding.inflate(LayoutInflater.from(this))
        dialog.setContentView(binding.root)

        binding.tvTitle.text = getString(R.string.transfer_select_to_store_title)
        binding.tvSubtitle.text = getString(R.string.transfer_select_to_store_subtitle)
        binding.progress.visibility = View.VISIBLE
        binding.rvStores.visibility = View.GONE
        binding.emptyContainer.visibility = View.GONE

        dialog.show()

        lifecycleScope.launch {
            try {
                val resp = ApiClient.getService(this@TransferCreateActivity).getStores()
                val stores = resp.body()?.data
                    ?.filter { it.storeId != fromStoreId }
                    .orEmpty()

                binding.progress.visibility = View.GONE

                if (stores.isEmpty()) {
                    binding.emptyContainer.visibility = View.VISIBLE
                    return@launch
                }

                binding.rvStores.visibility = View.VISIBLE
                binding.rvStores.layoutManager = LinearLayoutManager(this@TransferCreateActivity)
                binding.rvStores.adapter = ToStoreAdapter(stores) { store ->
                    dialog.dismiss()
                    toStore = store
                    this@TransferCreateActivity.binding.tvToStore.text = buildStoreLabel(store)
                    this@TransferCreateActivity.binding.tvToStore
                        .setTextColor(getColor(R.color.on_surface))
                }
            } catch (_: Exception) {
                binding.progress.visibility = View.GONE
                binding.emptyContainer.visibility = View.VISIBLE
            }
        }
    }

    private fun buildStoreLabel(store: StoreInfo): String {
        val parts = listOfNotNull(
            store.name.takeIf { it.isNotBlank() },
            store.code?.takeIf { it.isNotBlank() },
            store.businessUnitName?.takeIf { it.isNotBlank() },
        )
        return parts.joinToString(" · ").ifEmpty { "—" }
    }

    /** Rebuild the added-lines list + running total. */
    private fun refreshLines() {
        binding.linesContainer.removeAllViews()
        binding.tvLinesEmpty.visibility =
            if (lines.isEmpty()) View.VISIBLE else View.GONE
        binding.tvLinesCount.text = lines.size.toString()

        for ((index, entry) in lines.withIndex()) {
            val row = ItemTransferLineAddedBinding.inflate(
                LayoutInflater.from(this),
                binding.linesContainer,
                false,
            )
            row.tvName.text = entry.material.name

            val meta = listOfNotNull(
                entry.material.sku?.takeIf { it.isNotBlank() },
                entry.material.baseUnitSymbol?.takeIf { it.isNotBlank() },
            ).joinToString(" · ")
            row.tvMeta.text = meta.ifEmpty { "—" }
            row.tvMeta.visibility = if (meta.isEmpty()) View.GONE else View.VISIBLE

            val unitSym = entry.material.baseUnitSymbol?.let { " $it" }.orEmpty()
            row.tvQty.text =
                "Qty: ${TransferStatusStyle.formatQuantity(entry.request.transferQuantity)}$unitSym"

            row.btnRemove.setOnClickListener {
                lines.removeAt(index)
                refreshLines()
            }
            binding.linesContainer.addView(row.root)
        }

        val totalQty = lines.sumOf { it.request.transferQuantity }
        binding.tvTotalQty.text = TransferStatusStyle.formatQuantity(totalQty)
    }

    private fun submit() {
        val destination = toStore
        if (destination == null) {
            Toast.makeText(this, R.string.transfer_validation_to_store, Toast.LENGTH_SHORT).show()
            return
        }
        if (lines.isEmpty()) {
            Toast.makeText(this, R.string.transfer_validation_lines, Toast.LENGTH_SHORT).show()
            return
        }

        val request = CreateTransferRequest(
            fromStoreId = fromStoreId,
            toStoreId = destination.storeId,
            transferDate = transferDateIso,
            remarks = binding.etRemarks.text?.toString()?.trim()?.ifEmpty { null },
            items = lines.map { it.request },
        )

        binding.btnSave.isEnabled = false
        binding.progressOverlay.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val resp = ApiClient.getService(this@TransferCreateActivity).createTransfer(request)
                val transfer = resp.body()?.data
                if (resp.isSuccessful && resp.body()?.success == true && transfer != null) {
                    Toast.makeText(
                        this@TransferCreateActivity,
                        R.string.transfer_create_success,
                        Toast.LENGTH_SHORT,
                    ).show()
                    startActivity(
                        Intent(this@TransferCreateActivity, TransferDetailActivity::class.java)
                            .putExtra(TransferDetailActivity.EXTRA_TRANSFER_ID, transfer.id)
                            .putExtra(
                                TransferDetailActivity.EXTRA_TRANSFER_NUMBER,
                                transfer.transferNumber,
                            )
                    )
                    finish()
                } else {
                    val msg = resp.body()?.message ?: getString(R.string.transfer_create_failed)
                    Toast.makeText(this@TransferCreateActivity, msg, Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(
                    this@TransferCreateActivity,
                    e.message ?: getString(R.string.transfer_create_failed),
                    Toast.LENGTH_LONG,
                ).show()
            } finally {
                binding.btnSave.isEnabled = true
                binding.progressOverlay.visibility = View.GONE
            }
        }
    }

    // ── To-store adapter (simple flat list) ────────────────────────

    private class ToStoreAdapter(
        private val stores: List<StoreInfo>,
        private val onPick: (StoreInfo) -> Unit,
    ) : RecyclerView.Adapter<ToStoreAdapter.VH>() {

        inner class VH(private val root: android.view.View) : RecyclerView.ViewHolder(root) {
            val tvName: android.widget.TextView = root.findViewById(R.id.tv_store_name)
            val tvMeta: android.widget.TextView = root.findViewById(R.id.tv_store_meta)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_store, parent, false)
            return VH(view)
        }

        override fun getItemCount() = stores.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val store = stores[position]
            holder.tvName.text = store.name
            val parts = listOfNotNull(
                store.code?.takeIf { it.isNotBlank() },
                store.businessUnitName?.takeIf { it.isNotBlank() },
                store.location?.takeIf { it.isNotBlank() },
            )
            holder.tvMeta.text = if (parts.isEmpty()) "—" else parts.joinToString(" · ")
            holder.itemView.setOnClickListener { onPick(store) }
        }
    }
}
