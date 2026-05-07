package com.ceyinfo.cerpstores.ui.adjustment

import android.app.Activity
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import com.ceyinfo.cerpstores.R
import com.ceyinfo.cerpstores.data.model.CreateAdjustmentLineRequest
import com.ceyinfo.cerpstores.data.model.Material
import com.ceyinfo.cerpstores.databinding.SheetAddAdjustmentLineBinding
import com.ceyinfo.cerpstores.ui.common.MaterialPickerSheet
import com.google.android.material.bottomsheet.BottomSheetDialog

object AddAdjustmentLineSheet {

    data class LineEntry(
        val material: Material,
        val request: CreateAdjustmentLineRequest,
    )

    fun show(activity: Activity, onSave: (LineEntry) -> Unit) {
        val dialog = BottomSheetDialog(activity, R.style.Theme_Stores_BottomSheet)
        val binding = SheetAddAdjustmentLineBinding.inflate(LayoutInflater.from(activity))
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
            val qty = binding.etQty.text?.toString()?.toDoubleOrNull()

            if (material == null) {
                Toast.makeText(activity, R.string.adjustment_line_validation_material, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (qty == null || qty == 0.0) {
                binding.tilQty.error = activity.getString(R.string.adjustment_line_validation_qty)
                return@setOnClickListener
            }
            binding.tilQty.error = null

            val req = CreateAdjustmentLineRequest(
                materialId = material.materialId,
                adjustedQuantity = qty,
                batchNumber = binding.etBatch.text?.toString()?.trim()?.ifEmpty { null },
                reason = binding.etReason.text?.toString()?.trim()?.ifEmpty { null },
            )

            dialog.dismiss()
            onSave(LineEntry(material, req))
        }

        dialog.show()
    }
}
