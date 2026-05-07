package com.ceyinfo.cerpstores.ui.ret

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
import com.ceyinfo.cerpstores.data.model.CreateReturnRequest
import com.ceyinfo.cerpstores.data.remote.ApiClient
import com.ceyinfo.cerpstores.databinding.ActivityReturnCreateBinding
import com.ceyinfo.cerpstores.databinding.ItemReturnLineAddedBinding
import com.ceyinfo.cerpstores.ui.common.StorePickerSheet
import com.ceyinfo.cerpstores.ui.common.SupplierPickerSheet
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import java.util.Calendar

class ReturnCreateActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_STORE_ID = "store_id"
        const val EXTRA_STORE_NAME = "store_name"
    }

    private lateinit var binding: ActivityReturnCreateBinding

    private lateinit var storeId: String
    private var storeName: String? = null

    private val lines = mutableListOf<AddReturnLineSheet.LineEntry>()
    private var returnDateIso: String = today()
    private var returnType: String = "to_supplier"
    private var pickedSupplierId: String? = null
    private var pickedSupplierName: String? = null
    private var pickedToStoreId: String? = null
    private var pickedToStoreName: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT

        binding = ActivityReturnCreateBinding.inflate(layoutInflater)
        setContentView(binding.root)

        storeId = intent.getStringExtra(EXTRA_STORE_ID).orEmpty()
        storeName = intent.getStringExtra(EXTRA_STORE_NAME)
        if (storeId.isBlank()) { finish(); return }

        binding.btnBack.setOnClickListener { finish() }
        binding.tvStore.text = storeName ?: "—"

        // Date
        binding.tvReturnDate.text = ReturnStatusStyle.formatDate(returnDateIso)
        binding.cardReturnDate.setOnClickListener {
            pickDate(returnDateIso) { iso ->
                returnDateIso = iso
                binding.tvReturnDate.text = ReturnStatusStyle.formatDate(iso)
            }
        }

        // Return type picker
        updateReturnTypeDisplay()
        binding.cardReturnType.setOnClickListener { showReturnTypePicker() }

        // Supplier picker
        binding.cardSupplier.setOnClickListener {
            SupplierPickerSheet.show(this) { supplier ->
                pickedSupplierId = supplier.supplierId
                pickedSupplierName = supplier.supplierName
                binding.tvSupplier.text = supplier.supplierName
                binding.tvSupplier.setTextColor(getColor(R.color.on_surface))
            }
        }

        // To-store picker
        binding.cardToStore.setOnClickListener {
            StorePickerSheet.show(
                activity = this,
                title = getString(R.string.return_field_to_store),
                subtitle = getString(R.string.return_select_to_store),
            ) { store ->
                pickedToStoreId = store.storeId
                pickedToStoreName = store.name
                binding.tvToStore.text = store.name
                binding.tvToStore.setTextColor(getColor(R.color.on_surface))
            }
        }

        // Add line
        binding.btnAddLine.setOnClickListener {
            AddReturnLineSheet.show(this) { entry ->
                lines.add(entry)
                refreshLines()
            }
        }

        binding.btnSave.setOnClickListener { submit() }

        refreshLines()
    }

    private fun showReturnTypePicker() {
        val types = arrayOf("To Supplier", "To Store")
        val values = arrayOf("to_supplier", "to_store")
        val current = values.indexOf(returnType).coerceAtLeast(0)
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.return_field_type))
            .setSingleChoiceItems(types, current) { dialog, which ->
                returnType = values[which]
                updateReturnTypeDisplay()
                dialog.dismiss()
            }
            .show()
    }

    private fun updateReturnTypeDisplay() {
        val label = when (returnType) {
            "to_supplier" -> "To Supplier"
            "to_store"    -> "To Store"
            else          -> returnType
        }
        binding.tvReturnType.text = label
        binding.tvReturnType.setTextColor(getColor(R.color.on_surface))
        binding.cardSupplier.visibility = if (returnType == "to_supplier") View.VISIBLE else View.GONE
        binding.cardToStore.visibility  = if (returnType == "to_store")    View.VISIBLE else View.GONE
        if (returnType != "to_supplier") { pickedSupplierId = null; pickedSupplierName = null }
        if (returnType != "to_store")    { pickedToStoreId = null; pickedToStoreName = null }
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
            val row = ItemReturnLineAddedBinding.inflate(LayoutInflater.from(this), binding.linesContainer, false)
            row.tvName.text = entry.material.name
            val meta = listOfNotNull(
                entry.material.sku?.takeIf { it.isNotBlank() },
                entry.material.baseUnitSymbol?.takeIf { it.isNotBlank() },
            ).joinToString(" · ")
            row.tvMeta.text = meta.ifEmpty { "—" }
            row.tvMeta.visibility = if (meta.isEmpty()) View.GONE else View.VISIBLE
            val unitSym = entry.material.baseUnitSymbol?.let { " $it" }.orEmpty()
            row.tvQty.text = "Qty: ${ReturnStatusStyle.formatQuantity(entry.request.returnQuantity)}$unitSym"
            row.btnRemove.setOnClickListener { lines.removeAt(index); refreshLines() }
            binding.linesContainer.addView(row.root)
        }

        val totalQty = lines.sumOf { it.request.returnQuantity }
        binding.tvTotalQty.text = ReturnStatusStyle.formatQuantity(totalQty)
    }

    private fun submit() {
        if (lines.isEmpty()) {
            Toast.makeText(this, R.string.return_validation_lines, Toast.LENGTH_SHORT).show()
            return
        }

        val request = CreateReturnRequest(
            fromStoreId = storeId,
            toStoreId = pickedToStoreId,
            supplierId = pickedSupplierId,
            returnDate = returnDateIso,
            returnType = returnType,
            remarks = binding.etRemarks.text?.toString()?.trim()?.ifEmpty { null },
            items = lines.map { it.request },
        )

        binding.btnSave.isEnabled = false
        binding.progressOverlay.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val resp = ApiClient.getService(this@ReturnCreateActivity).createReturn(request)
                val ret = resp.body()?.data
                if (resp.isSuccessful && resp.body()?.success == true && ret != null) {
                    Toast.makeText(this@ReturnCreateActivity, R.string.return_create_success, Toast.LENGTH_SHORT).show()
                    startActivity(
                        Intent(this@ReturnCreateActivity, ReturnDetailActivity::class.java)
                            .putExtra(ReturnDetailActivity.EXTRA_RETURN_ID, ret.id)
                            .putExtra(ReturnDetailActivity.EXTRA_RETURN_NUMBER, ret.returnNumber)
                    )
                    finish()
                } else {
                    val msg = resp.body()?.message ?: getString(R.string.return_create_failed)
                    Toast.makeText(this@ReturnCreateActivity, msg, Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@ReturnCreateActivity, e.message ?: getString(R.string.return_create_failed), Toast.LENGTH_LONG).show()
            } finally {
                binding.btnSave.isEnabled = true
                binding.progressOverlay.visibility = View.GONE
            }
        }
    }
}
