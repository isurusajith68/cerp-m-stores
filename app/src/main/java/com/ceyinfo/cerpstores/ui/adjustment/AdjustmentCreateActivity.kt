package com.ceyinfo.cerpstores.ui.adjustment

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
import com.ceyinfo.cerpstores.data.model.CreateAdjustmentRequest
import com.ceyinfo.cerpstores.data.remote.ApiClient
import com.ceyinfo.cerpstores.databinding.ActivityAdjustmentCreateBinding
import com.ceyinfo.cerpstores.databinding.ItemAdjustmentLineAddedBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import java.util.Calendar
import kotlin.math.abs

class AdjustmentCreateActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_STORE_ID = "store_id"
        const val EXTRA_STORE_NAME = "store_name"
    }

    private lateinit var binding: ActivityAdjustmentCreateBinding

    private lateinit var storeId: String
    private var storeName: String? = null

    private val lines = mutableListOf<AddAdjustmentLineSheet.LineEntry>()
    private var adjustmentDateIso: String = today()
    private var adjustmentType: String = "count_adjustment"

    private val adjustmentTypes = arrayOf(
        "Count Adjustment",
        "Damage Write-off",
        "Expiry Write-off",
        "Transfer In",
        "Transfer Out",
    )
    private val adjustmentTypeValues = arrayOf(
        "count_adjustment",
        "damage",
        "expiry",
        "transfer_in",
        "transfer_out",
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT

        binding = ActivityAdjustmentCreateBinding.inflate(layoutInflater)
        setContentView(binding.root)

        storeId = intent.getStringExtra(EXTRA_STORE_ID).orEmpty()
        storeName = intent.getStringExtra(EXTRA_STORE_NAME)
        if (storeId.isBlank()) { finish(); return }

        binding.btnBack.setOnClickListener { finish() }
        binding.tvStore.text = storeName ?: "—"

        // Date
        binding.tvAdjustmentDate.text = AdjustmentStatusStyle.formatDate(adjustmentDateIso)
        binding.cardAdjustmentDate.setOnClickListener {
            pickDate(adjustmentDateIso) { iso ->
                adjustmentDateIso = iso
                binding.tvAdjustmentDate.text = AdjustmentStatusStyle.formatDate(iso)
            }
        }

        // Type picker
        updateTypeDisplay()
        binding.cardAdjustmentType.setOnClickListener { showTypePicker() }

        // Add line
        binding.btnAddLine.setOnClickListener {
            AddAdjustmentLineSheet.show(this) { entry ->
                lines.add(entry)
                refreshLines()
            }
        }

        binding.btnSave.setOnClickListener { submit() }

        refreshLines()
    }

    private fun showTypePicker() {
        val current = adjustmentTypeValues.indexOf(adjustmentType).coerceAtLeast(0)
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.adjustment_field_type))
            .setSingleChoiceItems(adjustmentTypes, current) { dialog, which ->
                adjustmentType = adjustmentTypeValues[which]
                updateTypeDisplay()
                dialog.dismiss()
            }
            .show()
    }

    private fun updateTypeDisplay() {
        val idx = adjustmentTypeValues.indexOf(adjustmentType)
        val label = if (idx >= 0) adjustmentTypes[idx] else adjustmentType
        binding.tvAdjustmentType.text = label
        binding.tvAdjustmentType.setTextColor(getColor(R.color.on_surface))
    }

    private fun today(): String {
        val cal = Calendar.getInstance()
        return "%04d-%02d-%02d".format(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))
    }

    private fun pickDate(currentIso: String, onPick: (String) -> Unit) {
        val cal = Calendar.getInstance()
        runCatching {
            val parts = currentIso.split("-").map { it.toInt() }
            cal.set(parts[0], parts[1] - 1, parts[2])
        }
        DatePickerDialog(this, { _, year, month, day ->
            onPick("%04d-%02d-%02d".format(year, month + 1, day))
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun refreshLines() {
        binding.linesContainer.removeAllViews()
        binding.tvLinesEmpty.visibility = if (lines.isEmpty()) View.VISIBLE else View.GONE
        binding.tvLinesCount.text = lines.size.toString()

        for ((index, entry) in lines.withIndex()) {
            val row = ItemAdjustmentLineAddedBinding.inflate(LayoutInflater.from(this), binding.linesContainer, false)
            row.tvName.text = entry.material.name
            val meta = listOfNotNull(
                entry.material.sku?.takeIf { it.isNotBlank() },
                entry.material.baseUnitSymbol?.takeIf { it.isNotBlank() },
            ).joinToString(" · ")
            row.tvMeta.text = meta.ifEmpty { "—" }
            row.tvMeta.visibility = if (meta.isEmpty()) View.GONE else View.VISIBLE
            val unitSym = entry.material.baseUnitSymbol?.let { " $it" }.orEmpty()
            val sign = if (entry.request.adjustedQuantity >= 0) "+" else ""
            row.tvQty.text = "Qty: $sign${AdjustmentStatusStyle.formatQuantity(entry.request.adjustedQuantity)}$unitSym"
            row.btnRemove.setOnClickListener { lines.removeAt(index); refreshLines() }
            binding.linesContainer.addView(row.root)
        }

        val totalQty = lines.sumOf { abs(it.request.adjustedQuantity) }
        binding.tvTotalQty.text = AdjustmentStatusStyle.formatQuantity(totalQty)
    }

    private fun submit() {
        if (lines.isEmpty()) {
            Toast.makeText(this, R.string.adjustment_validation_lines, Toast.LENGTH_SHORT).show()
            return
        }

        val request = CreateAdjustmentRequest(
            storeId = storeId,
            adjustmentDate = adjustmentDateIso,
            adjustmentType = adjustmentType,
            remarks = binding.etRemarks.text?.toString()?.trim()?.ifEmpty { null },
            items = lines.map { it.request },
        )

        binding.btnSave.isEnabled = false
        binding.progressOverlay.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val resp = ApiClient.getService(this@AdjustmentCreateActivity).createAdjustment(request)
                val adj = resp.body()?.data
                if (resp.isSuccessful && resp.body()?.success == true && adj != null) {
                    Toast.makeText(this@AdjustmentCreateActivity, R.string.adjustment_create_success, Toast.LENGTH_SHORT).show()
                    startActivity(
                        Intent(this@AdjustmentCreateActivity, AdjustmentDetailActivity::class.java)
                            .putExtra(AdjustmentDetailActivity.EXTRA_ADJUSTMENT_ID, adj.id)
                            .putExtra(AdjustmentDetailActivity.EXTRA_ADJUSTMENT_NUMBER, adj.adjustmentNumber)
                    )
                    finish()
                } else {
                    val msg = resp.body()?.message ?: getString(R.string.adjustment_create_failed)
                    Toast.makeText(this@AdjustmentCreateActivity, msg, Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@AdjustmentCreateActivity, e.message ?: getString(R.string.adjustment_create_failed), Toast.LENGTH_LONG).show()
            } finally {
                binding.btnSave.isEnabled = true
                binding.progressOverlay.visibility = View.GONE
            }
        }
    }
}
