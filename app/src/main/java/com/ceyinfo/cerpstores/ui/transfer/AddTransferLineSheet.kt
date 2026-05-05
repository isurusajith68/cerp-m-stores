package com.ceyinfo.cerpstores.ui.transfer

import android.app.Activity
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import com.ceyinfo.cerpstores.R
import com.ceyinfo.cerpstores.data.model.CreateTransferLineRequest
import com.ceyinfo.cerpstores.data.model.Material
import com.ceyinfo.cerpstores.databinding.SheetAddTransferLineBinding
import com.ceyinfo.cerpstores.ui.common.MaterialPickerSheet
import com.google.android.material.bottomsheet.BottomSheetDialog

/**
 * Bottom sheet for adding a single stock-transfer line item.
 *
 * Collects: material (required), transfer quantity (required > 0),
 * batch number (optional), remarks (optional).
 *
 * The assembled [LineEntry] is returned via the [onSave] callback.
 * The calling [TransferCreateActivity] builds the full CreateTransferRequest
 * from all collected entries.
 */
object AddTransferLineSheet {

    data class LineEntry(
        val material: Material,
        val request: CreateTransferLineRequest,
    )

    fun show(activity: Activity, onSave: (LineEntry) -> Unit) {
        val dialog = BottomSheetDialog(activity, R.style.Theme_Stores_BottomSheet)
        val binding = SheetAddTransferLineBinding.inflate(LayoutInflater.from(activity))
        dialog.setContentView(binding.root)

        var picked: Material? = null

        binding.cardMaterial.setOnClickListener {
            MaterialPickerSheet.show(activity) { material ->
                picked = material
                binding.tvMaterialName.text = material.name
                binding.tvMaterialName.setTextColor(activity.getColor(R.color.on_surface))
                val meta = listOfNotNull(
                    material.sku?.takeIf { it.isNotBlank() },
                    material.baseUnitSymbol?.takeIf { it.isNotBlank() },
                ).joinToString(" · ")
                binding.tvMaterialMeta.text = meta
                binding.tvMaterialMeta.visibility =
                    if (meta.isNotEmpty()) View.VISIBLE else View.GONE
            }
        }

        binding.btnSave.setOnClickListener {
            val material = picked
            val qty = binding.etQty.text?.toString()?.toDoubleOrNull() ?: 0.0

            if (material == null) {
                Toast.makeText(
                    activity,
                    R.string.transfer_line_validation_material,
                    Toast.LENGTH_SHORT,
                ).show()
                return@setOnClickListener
            }
            if (qty <= 0.0) {
                binding.tilQty.error = activity.getString(R.string.transfer_line_validation_qty)
                return@setOnClickListener
            }
            binding.tilQty.error = null

            val req = CreateTransferLineRequest(
                materialId = material.materialId,
                transferQuantity = qty,
                batchNumber = binding.etBatch.text?.toString()?.trim()?.ifEmpty { null },
                remarks = binding.etRemarks.text?.toString()?.trim()?.ifEmpty { null },
            )

            dialog.dismiss()
            onSave(LineEntry(material, req))
        }

        dialog.show()
    }
}
