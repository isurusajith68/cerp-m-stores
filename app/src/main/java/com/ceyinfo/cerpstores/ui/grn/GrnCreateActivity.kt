package com.ceyinfo.cerpstores.ui.grn

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.ceyinfo.cerpstores.R
import com.ceyinfo.cerpstores.data.model.CreateGrnRequest
import com.ceyinfo.cerpstores.data.model.Supplier
import com.ceyinfo.cerpstores.data.remote.ApiClient
import com.ceyinfo.cerpstores.databinding.ActivityGrnCreateBinding
import com.ceyinfo.cerpstores.databinding.ItemGrnLineAddedBinding
import com.ceyinfo.cerpstores.ui.common.SupplierPickerSheet
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * GRN create form. Required fields:
 *   - store (passed in via extras from StorePickerSheet)
 *   - GRN date (defaults to today)
 *   - Receipt date (defaults to today)
 *   - At least one line item
 *
 * Everything else is optional. The form submits as DRAFT — Pass 2b will
 * add transition buttons on the detail screen for moving DRAFT → SUBMITTED → APPROVED.
 *
 * On successful save we land directly on GrnDetailActivity. We don't
 * `finish()` ourselves here because the back-stack semantics are nicer
 * if the user can hit Back to land on the detail screen rather than
 * being kicked back to the dashboard mid-flow.
 */
class GrnCreateActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_STORE_ID = "store_id"
        const val EXTRA_STORE_NAME = "store_name"
    }

    private lateinit var binding: ActivityGrnCreateBinding

    private lateinit var storeId: String
    private var storeName: String? = null

    // Form state — kept as raw fields rather than a data class so we can
    // mutate piecewise as the user fills things in. Lines hold both the
    // request payload and a display Material so we can show readable
    // names without re-fetching.
    private val lines = mutableListOf<AddLineItemSheet.LineEntry>()
    private var grnDateIso: String = today()
    private var receiptDateIso: String = today()
    private var invoiceDateIso: String? = null
    private var supplier: Supplier? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT

        binding = ActivityGrnCreateBinding.inflate(layoutInflater)
        setContentView(binding.root)

        storeId = intent.getStringExtra(EXTRA_STORE_ID).orEmpty()
        storeName = intent.getStringExtra(EXTRA_STORE_NAME)
        if (storeId.isBlank()) {
            // Mid-stream nav glitch — bail rather than send invalid POST.
            finish()
            return
        }

        binding.btnBack.setOnClickListener { finish() }
        binding.tvStore.text = storeName ?: "—"

        // Header date displays — initialise to today.
        binding.tvGrnDate.text = GrnStatusStyle.formatDate(grnDateIso)
        binding.tvReceiptDate.text = GrnStatusStyle.formatDate(receiptDateIso)

        binding.cardGrnDate.setOnClickListener {
            pickDate(grnDateIso) { iso ->
                grnDateIso = iso
                binding.tvGrnDate.text = GrnStatusStyle.formatDate(iso)
            }
        }
        binding.cardReceiptDate.setOnClickListener {
            pickDate(receiptDateIso) { iso ->
                receiptDateIso = iso
                binding.tvReceiptDate.text = GrnStatusStyle.formatDate(iso)
            }
        }
        binding.cardInvoiceDate.setOnClickListener {
            pickDate(invoiceDateIso ?: today()) { iso ->
                invoiceDateIso = iso
                binding.tvInvoiceDate.text = GrnStatusStyle.formatDate(iso)
            }
        }

        binding.cardSupplier.setOnClickListener {
            SupplierPickerSheet.show(this) { sup ->
                supplier = sup
                binding.tvSupplier.text = sup.supplierName
                binding.tvSupplier.setTextColor(getColor(R.color.on_surface))
            }
        }

        binding.btnAddLine.setOnClickListener {
            AddLineItemSheet.show(this) { entry ->
                lines.add(entry)
                refreshLines()
            }
        }

        binding.btnSave.setOnClickListener { submit() }

        refreshLines()
    }

    /** Today as ISO yyyy-MM-dd. Recomputed once at load — fine for a session. */
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
        // Best-effort parse of the current value so the picker opens at
        // the right month. Falls back to today on bad input.
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

    /** Rebuild the lines list + totals + count. Cheap; called on every mutation. */
    private fun refreshLines() {
        binding.linesContainer.removeAllViews()
        binding.tvLinesEmpty.visibility =
            if (lines.isEmpty()) View.VISIBLE else View.GONE
        binding.tvLinesCount.text = lines.size.toString()

        for ((index, line) in lines.withIndex()) {
            val row = ItemGrnLineAddedBinding.inflate(
                LayoutInflater.from(this),
                binding.linesContainer,
                false,
            )
            row.tvName.text = line.material.name
            val meta = listOfNotNull(
                line.material.sku?.takeIf { it.isNotBlank() },
                line.material.baseUnitSymbol?.takeIf { it.isNotBlank() },
            ).joinToString(" · ")
            row.tvMeta.text = meta.ifEmpty { "—" }
            row.tvMeta.visibility = if (meta.isEmpty()) View.GONE else View.VISIBLE

            val unitSym = line.material.baseUnitSymbol?.let { " $it" }.orEmpty()
            row.tvQty.text = "Qty: ${GrnStatusStyle.formatQuantity(line.request.receivedQuantity)}$unitSym" +
                "  ·  Rate: ${GrnStatusStyle.formatAmount(line.request.unitPrice)}"
            row.tvTotal.text = GrnStatusStyle.formatAmount(
                line.request.totalAmount ?: (line.request.receivedQuantity * line.request.unitPrice)
            )

            // Capture index by value so removal stays correct after list shrinks.
            row.btnRemove.setOnClickListener {
                lines.removeAt(index)
                refreshLines()
            }
            binding.linesContainer.addView(row.root)
        }

        val totalQty = lines.sumOf { it.request.receivedQuantity }
        val totalAmount = lines.sumOf {
            it.request.totalAmount ?: (it.request.receivedQuantity * it.request.unitPrice)
        }
        binding.tvTotalQty.text = GrnStatusStyle.formatQuantity(totalQty)
        binding.tvTotalAmount.text = GrnStatusStyle.formatAmount(totalAmount)
    }

    private fun submit() {
        // Required-field validation. Done client-side only — the backend
        // also enforces these, but a fast client-side block saves a
        // round-trip.
        if (grnDateIso.isBlank() || receiptDateIso.isBlank()) {
            Toast.makeText(this, R.string.grn_validation_dates, Toast.LENGTH_SHORT).show()
            return
        }
        if (lines.isEmpty()) {
            Toast.makeText(this, R.string.grn_validation_lines, Toast.LENGTH_SHORT).show()
            return
        }

        val request = CreateGrnRequest(
            storeId = storeId,
            grnDate = grnDateIso,
            receiptDate = receiptDateIso,
            supplierId = supplier?.supplierId,
            poReference = binding.etPoReference.text?.toString()?.trim()?.ifEmpty { null },
            invoiceNumber = binding.etInvoiceNumber.text?.toString()?.trim()?.ifEmpty { null },
            invoiceDate = invoiceDateIso,
            remarks = binding.etRemarks.text?.toString()?.trim()?.ifEmpty { null },
            items = lines.map { it.request },
        )

        binding.btnSave.isEnabled = false
        binding.progressOverlay.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val resp = ApiClient.getService(this@GrnCreateActivity).createGrn(request)
                val grn = resp.body()?.data
                if (resp.isSuccessful && resp.body()?.success == true && grn != null) {
                    Toast.makeText(
                        this@GrnCreateActivity,
                        R.string.grn_create_success,
                        Toast.LENGTH_SHORT,
                    ).show()
                    // Land on detail. Finish so Back from detail returns
                    // the user to the dashboard / list, not the form.
                    startActivity(
                        Intent(this@GrnCreateActivity, GrnDetailActivity::class.java)
                            .putExtra(GrnDetailActivity.EXTRA_GRN_ID, grn.id)
                            .putExtra(GrnDetailActivity.EXTRA_GRN_NUMBER, grn.grnNumber)
                    )
                    finish()
                } else {
                    val msg = resp.body()?.message ?: getString(R.string.grn_create_failed)
                    Toast.makeText(this@GrnCreateActivity, msg, Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(
                    this@GrnCreateActivity,
                    e.message ?: getString(R.string.grn_create_failed),
                    Toast.LENGTH_LONG,
                ).show()
            } finally {
                binding.btnSave.isEnabled = true
                binding.progressOverlay.visibility = View.GONE
            }
        }
    }
}
