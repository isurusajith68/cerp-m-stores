package com.ceyinfo.cerpstores.ui.grn

import android.app.Activity
import android.app.DatePickerDialog
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import com.ceyinfo.cerpstores.R
import com.ceyinfo.cerpstores.data.model.CreateGrnLineRequest
import com.ceyinfo.cerpstores.data.model.Material
import com.ceyinfo.cerpstores.databinding.SheetAddLineItemBinding
import com.ceyinfo.cerpstores.ui.common.MaterialPickerSheet
import com.google.android.material.bottomsheet.BottomSheetDialog
import java.util.Calendar

/**
 * Bottom sheet for adding/editing a single GRN line item.
 *
 * Held state lives entirely in this sheet — once the user taps "Add to
 * GRN", the callback fires with a `CreateGrnLineRequest` payload (plus
 * a label-only `Material` for display purposes in the parent list).
 *
 * The line total is recomputed live as qty/price change so users can
 * sanity-check before submitting.
 *
 * No "edit existing line" path yet — Pass 2a only supports add. Editing
 * an already-added line is a Pass 2b nicety; today the user has to
 * delete + re-add.
 */
object AddLineItemSheet {

    /** Display-only wrapper so the parent list can show name + meta. */
    data class LineEntry(
        val material: Material,
        val request: CreateGrnLineRequest,
    )

    fun show(activity: Activity, onSave: (LineEntry) -> Unit) {
        val dialog = BottomSheetDialog(activity, R.style.Theme_Stores_BottomSheet)
        val binding = SheetAddLineItemBinding.inflate(LayoutInflater.from(activity))
        dialog.setContentView(binding.root)

        var picked: Material? = null
        var expiryIso: String? = null

        // Material picker → updates the card label and clears any error
        // on the picker's field.
        binding.cardMaterial.setOnClickListener {
            MaterialPickerSheet.show(activity) { material ->
                picked = material
                binding.tvMaterialName.text = material.name
                binding.tvMaterialName.setTextColor(
                    activity.getColor(R.color.on_surface)
                )
                val meta = listOfNotNull(
                    material.sku?.takeIf { it.isNotBlank() },
                    material.baseUnitSymbol?.takeIf { it.isNotBlank() },
                ).joinToString(" · ")
                binding.tvMaterialMeta.text = meta
                binding.tvMaterialMeta.visibility =
                    if (meta.isNotEmpty()) View.VISIBLE else View.GONE
            }
        }

        // Expiry picker — DatePickerDialog → store ISO yyyy-MM-dd.
        binding.cardExpiry.setOnClickListener {
            val cal = Calendar.getInstance()
            DatePickerDialog(
                activity,
                { _, year, month, day ->
                    val iso = "%04d-%02d-%02d".format(year, month + 1, day)
                    expiryIso = iso
                    binding.tvExpiry.text = GrnStatusStyle.formatDate(iso)
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH),
            ).show()
        }

        // Live line-total preview. Recomputed on every keystroke; cheap.
        val recomputeTotal = {
            val q = binding.etQty.text?.toString()?.toDoubleOrNull() ?: 0.0
            val p = binding.etPrice.text?.toString()?.toDoubleOrNull() ?: 0.0
            binding.tvLineTotal.text = GrnStatusStyle.formatAmount(q * p)
        }
        binding.tvLineTotal.text = GrnStatusStyle.formatAmount(0.0)
        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                recomputeTotal()
            }
            override fun afterTextChanged(s: Editable?) {}
        }
        binding.etQty.addTextChangedListener(watcher)
        binding.etPrice.addTextChangedListener(watcher)

        binding.btnSave.setOnClickListener {
            val material = picked
            val qty = binding.etQty.text?.toString()?.toDoubleOrNull() ?: 0.0
            val price = binding.etPrice.text?.toString()?.toDoubleOrNull() ?: 0.0

            if (material == null) {
                Toast.makeText(activity, R.string.line_validation_material, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (qty <= 0.0) {
                binding.tilQty.error = activity.getString(R.string.line_validation_qty)
                return@setOnClickListener
            }
            binding.tilQty.error = null

            val req = CreateGrnLineRequest(
                materialId = material.materialId,
                receivedQuantity = qty,
                unitPrice = price,
                totalAmount = qty * price,
                batchNumber = binding.etBatch.text?.toString()?.trim()?.ifEmpty { null },
                expiryDate = expiryIso,
                remarks = binding.etRemarks.text?.toString()?.trim()?.ifEmpty { null },
            )

            dialog.dismiss()
            onSave(LineEntry(material, req))
        }

        dialog.show()
    }
}
