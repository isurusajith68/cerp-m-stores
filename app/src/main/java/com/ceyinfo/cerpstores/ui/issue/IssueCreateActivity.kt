package com.ceyinfo.cerpstores.ui.issue

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
import com.ceyinfo.cerpstores.data.model.CreateIssueRequest
import com.ceyinfo.cerpstores.data.model.Department
import com.ceyinfo.cerpstores.data.model.Employee
import com.ceyinfo.cerpstores.data.remote.ApiClient
import com.ceyinfo.cerpstores.databinding.ActivityIssueCreateBinding
import com.ceyinfo.cerpstores.databinding.ItemIssueLineAddedBinding
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * Goods Issue create form. Required fields:
 *   - store (passed via extras from StorePickerSheet → NewTransactionSheet)
 *   - Issue date (defaults to today)
 *   - At least one line item
 *
 * Optional fields:
 *   - Department (structured pick from HR)
 *   - Issued-to employee (cascades from selected department if present)
 *   - Remarks
 *
 * On successful save the user lands on IssueDetailActivity. We finish()
 * here so Back from detail returns to the dashboard/list.
 */
class IssueCreateActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_STORE_ID = "store_id"
        const val EXTRA_STORE_NAME = "store_name"
    }

    private lateinit var binding: ActivityIssueCreateBinding

    private lateinit var storeId: String
    private var storeName: String? = null

    // Form state
    private val lines = mutableListOf<AddIssueLineSheet.LineEntry>()
    private var issueDateIso: String = today()
    private var pickedDepartment: Department? = null
    private var pickedEmployee: Employee? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT

        binding = ActivityIssueCreateBinding.inflate(layoutInflater)
        setContentView(binding.root)

        storeId = intent.getStringExtra(EXTRA_STORE_ID).orEmpty()
        storeName = intent.getStringExtra(EXTRA_STORE_NAME)
        if (storeId.isBlank()) {
            finish()
            return
        }

        binding.btnBack.setOnClickListener { finish() }
        binding.tvStore.text = storeName ?: "—"

        // Issue date — default to today
        binding.tvIssueDate.text = IssueStatusStyle.formatDate(issueDateIso)
        binding.cardIssueDate.setOnClickListener {
            pickDate(issueDateIso) { iso ->
                issueDateIso = iso
                binding.tvIssueDate.text = IssueStatusStyle.formatDate(iso)
            }
        }

        // Department picker
        binding.cardDepartment.setOnClickListener {
            DepartmentPickerSheet.show(this, lifecycleScope) { dept ->
                pickedDepartment = dept
                pickedEmployee = null  // reset employee when department changes
                binding.tvDepartment.text = dept.name
                binding.tvDepartment.setTextColor(getColor(R.color.on_surface))
                binding.tvIssuedTo.text = getString(R.string.issue_select_employee)
                binding.tvIssuedTo.setTextColor(getColor(R.color.text_secondary))
            }
        }

        // Employee picker — optionally pre-filtered by selected department
        binding.cardIssuedTo.setOnClickListener {
            EmployeePickerSheet.show(
                activity = this,
                scope = lifecycleScope,
                departmentId = pickedDepartment?.departmentId,
            ) { emp ->
                pickedEmployee = emp
                binding.tvIssuedTo.text = emp.name
                binding.tvIssuedTo.setTextColor(getColor(R.color.on_surface))
            }
        }

        // Add line item
        binding.btnAddLine.setOnClickListener {
            AddIssueLineSheet.show(this) { entry ->
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

    /** Rebuild the added-lines list + running total. */
    private fun refreshLines() {
        binding.linesContainer.removeAllViews()
        binding.tvLinesEmpty.visibility =
            if (lines.isEmpty()) View.VISIBLE else View.GONE
        binding.tvLinesCount.text = lines.size.toString()

        for ((index, entry) in lines.withIndex()) {
            val row = ItemIssueLineAddedBinding.inflate(
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
            row.tvQty.text = "Qty: ${IssueStatusStyle.formatQuantity(entry.request.issuedQuantity)}$unitSym"

            row.btnRemove.setOnClickListener {
                lines.removeAt(index)
                refreshLines()
            }
            binding.linesContainer.addView(row.root)
        }

        val totalQty = lines.sumOf { it.request.issuedQuantity }
        binding.tvTotalQty.text = IssueStatusStyle.formatQuantity(totalQty)
    }

    private fun submit() {
        if (lines.isEmpty()) {
            Toast.makeText(this, R.string.issue_validation_lines, Toast.LENGTH_SHORT).show()
            return
        }

        val request = CreateIssueRequest(
            storeId = storeId,
            issueDate = issueDateIso,
            departmentId = pickedDepartment?.departmentId,
            issuedToId = pickedEmployee?.employeeId,
            remarks = binding.etRemarks.text?.toString()?.trim()?.ifEmpty { null },
            items = lines.map { it.request },
        )

        binding.btnSave.isEnabled = false
        binding.progressOverlay.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val resp = ApiClient.getService(this@IssueCreateActivity).createIssue(request)
                val issue = resp.body()?.data
                if (resp.isSuccessful && resp.body()?.success == true && issue != null) {
                    Toast.makeText(
                        this@IssueCreateActivity,
                        R.string.issue_create_success,
                        Toast.LENGTH_SHORT,
                    ).show()
                    startActivity(
                        Intent(this@IssueCreateActivity, IssueDetailActivity::class.java)
                            .putExtra(IssueDetailActivity.EXTRA_ISSUE_ID, issue.id)
                            .putExtra(IssueDetailActivity.EXTRA_ISSUE_NUMBER, issue.issueNumber)
                    )
                    finish()
                } else {
                    val msg = resp.body()?.message ?: getString(R.string.issue_create_failed)
                    Toast.makeText(this@IssueCreateActivity, msg, Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(
                    this@IssueCreateActivity,
                    e.message ?: getString(R.string.issue_create_failed),
                    Toast.LENGTH_LONG,
                ).show()
            } finally {
                binding.btnSave.isEnabled = true
                binding.progressOverlay.visibility = View.GONE
            }
        }
    }
}
