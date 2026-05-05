package com.ceyinfo.cerpstores.ui.verification

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import coil.load
import com.ceyinfo.cerpstores.R
import com.ceyinfo.cerpstores.data.model.CreateVerificationRequest
import com.ceyinfo.cerpstores.data.remote.ApiClient
import com.ceyinfo.cerpstores.databinding.ActivityVerificationCreateBinding
import com.ceyinfo.cerpstores.databinding.ItemVerificationLineAddedBinding
import com.ceyinfo.cerpstores.ui.grn.GrnStatusStyle
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * Create form for a stock verification. Required:
 *   - store_id (passed in)
 *   - verification_date (defaults to today)
 *   - >= 1 line item
 *
 * Submits as DRAFT. Lands on VerificationDetailActivity on success so the
 * user can review the lines + transition (Submit → Approve) from there.
 */
class VerificationCreateActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_STORE_ID = "store_id"
        const val EXTRA_STORE_NAME = "store_name"
    }

    private lateinit var binding: ActivityVerificationCreateBinding

    private lateinit var storeId: String
    private var storeName: String? = null

    private val lines = mutableListOf<AddVerificationItemSheet.LineEntry>()
    private var verificationDateIso: String = today()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT

        binding = ActivityVerificationCreateBinding.inflate(layoutInflater)
        setContentView(binding.root)

        storeId = intent.getStringExtra(EXTRA_STORE_ID).orEmpty()
        storeName = intent.getStringExtra(EXTRA_STORE_NAME)
        if (storeId.isBlank()) {
            // Mid-stream nav glitch — the create form requires a known store.
            finish()
            return
        }

        binding.btnBack.setOnClickListener { finish() }
        binding.tvStore.text = storeName ?: "—"
        binding.tvDate.text = GrnStatusStyle.formatDate(verificationDateIso)

        binding.cardDate.setOnClickListener {
            pickDate(verificationDateIso) { iso ->
                verificationDateIso = iso
                binding.tvDate.text = GrnStatusStyle.formatDate(iso)
            }
        }

        binding.btnAddLine.setOnClickListener {
            AddVerificationItemSheet.show(this) { entry ->
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

    private fun refreshLines() {
        binding.linesContainer.removeAllViews()
        binding.tvLinesEmpty.visibility =
            if (lines.isEmpty()) View.VISIBLE else View.GONE
        binding.tvLinesCount.text = lines.size.toString()

        for ((index, line) in lines.withIndex()) {
            val row = ItemVerificationLineAddedBinding.inflate(
                LayoutInflater.from(this),
                binding.linesContainer,
                false,
            )
            // Tiny thumbnail of the first photo, plus a "+N" badge when
            // there are extras. Session-lifetime signed URLs — fine for
            // the create preview; detail view re-signs from path.
            val photoUrls = line.photoUrls
            if (photoUrls.isNotEmpty()) {
                row.photoThumbWrap.visibility = View.VISIBLE
                row.photoThumb.load(photoUrls.first()) { crossfade(true) }
                if (photoUrls.size > 1) {
                    row.tvPhotoCount.text = "+${photoUrls.size - 1}"
                    row.tvPhotoCount.visibility = View.VISIBLE
                }
            }
            row.tvName.text = line.description
            val meta = listOfNotNull(
                line.material?.sku?.takeIf { it.isNotBlank() },
                line.unitSymbol?.takeIf { it.isNotBlank() },
                if (line.material == null) "Off-catalogue" else null,
            ).joinToString(" · ")
            row.tvMeta.text = meta.ifEmpty { "—" }
            row.tvMeta.visibility = if (meta.isEmpty()) View.GONE else View.VISIBLE

            val unitSym = line.unitSymbol?.takeIf { it.isNotBlank() }?.let { " $it" } ?: ""
            row.tvQty.text = "Qty: ${GrnStatusStyle.formatQuantity(line.request.verifiedQuantity)}$unitSym"

            val tags = listOfNotNull(
                line.request.serialNumber?.takeIf { it.isNotBlank() }?.let { "S/N: $it" },
                line.request.batchNumber?.takeIf { it.isNotBlank() }?.let { "Batch: $it" },
            )
            if (tags.isNotEmpty()) {
                row.tvSerialBatch.text = tags.joinToString(" · ")
                row.tvSerialBatch.visibility = View.VISIBLE
            }

            row.btnRemove.setOnClickListener {
                lines.removeAt(index)
                refreshLines()
            }
            binding.linesContainer.addView(row.root)
        }
    }

    private fun submit() {
        if (verificationDateIso.isBlank()) {
            Toast.makeText(this, R.string.verification_validation_date, Toast.LENGTH_SHORT).show()
            return
        }
        if (lines.isEmpty()) {
            Toast.makeText(this, R.string.verification_validation_lines, Toast.LENGTH_SHORT).show()
            return
        }

        val request = CreateVerificationRequest(
            storeId = storeId,
            verificationDate = verificationDateIso,
            remarks = binding.etRemarks.text?.toString()?.trim()?.ifEmpty { null },
            items = lines.map { it.request },
        )

        binding.btnSave.isEnabled = false
        binding.progressOverlay.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val resp = ApiClient.getService(this@VerificationCreateActivity)
                    .createVerification(request)
                val v = resp.body()?.data
                if (resp.isSuccessful && resp.body()?.success == true && v != null) {
                    Toast.makeText(
                        this@VerificationCreateActivity,
                        R.string.verification_create_success,
                        Toast.LENGTH_SHORT,
                    ).show()
                    startActivity(
                        Intent(this@VerificationCreateActivity, VerificationDetailActivity::class.java)
                            .putExtra(VerificationDetailActivity.EXTRA_ID, v.id)
                            .putExtra(VerificationDetailActivity.EXTRA_NUMBER, v.verificationNumber)
                    )
                    finish()
                } else {
                    val msg = resp.body()?.message ?: getString(R.string.verification_create_failed)
                    Toast.makeText(this@VerificationCreateActivity, msg, Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(
                    this@VerificationCreateActivity,
                    e.message ?: getString(R.string.verification_create_failed),
                    Toast.LENGTH_LONG,
                ).show()
            } finally {
                binding.btnSave.isEnabled = true
                binding.progressOverlay.visibility = View.GONE
            }
        }
    }
}
