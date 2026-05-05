package com.ceyinfo.cerpstores.ui.issue

import android.graphics.drawable.GradientDrawable
import android.widget.TextView
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * View-level helpers shared by IssueListActivity, IssueDetailActivity, and
 * IssueCreateActivity.  Status colours mirror the GRN palette so the app
 * feels consistent across all transaction types.
 */
object IssueStatusStyle {

    private data class Tone(val bgHex: String, val fgHex: String, val label: String)

    private val tones = mapOf(
        "DRAFT"     to Tone("#F1F5F9", "#475569", "Draft"),
        "SUBMITTED" to Tone("#DBEAFE", "#1D4ED8", "Submitted"),
        "APPROVED"  to Tone("#D1FAE5", "#047857", "Approved"),
        "CANCELLED" to Tone("#FEE2E2", "#B91C1C", "Cancelled"),
        "REJECTED"  to Tone("#FEE2E2", "#B91C1C", "Rejected"),
    )

    private val fallback = Tone("#F1F5F9", "#475569", "Unknown")

    fun applyStatus(tv: TextView, status: String?) {
        val tone = tones[status?.uppercase()] ?: fallback
        tv.text = tone.label
        tv.setTextColor(android.graphics.Color.parseColor(tone.fgHex))
        val bg = (tv.background?.constantState?.newDrawable()?.mutate() as? GradientDrawable)
            ?: GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 999f
            }
        bg.setColor(android.graphics.Color.parseColor(tone.bgHex))
        tv.background = bg
    }

    // ── Formatting helpers ─────────────────────────────────────────

    private val isoDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = true }
    private val displayDate = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

    fun formatDate(iso: String?): String {
        if (iso.isNullOrBlank()) return "—"
        return runCatching {
            val parsed = isoDate.parse(iso.substring(0, minOf(10, iso.length)))
            displayDate.format(parsed!!)
        }.getOrElse { "—" }
    }

    fun formatQuantity(qty: Double): String =
        NumberFormat.getNumberInstance(Locale.getDefault()).apply {
            minimumFractionDigits = 0
            maximumFractionDigits = 3
        }.format(qty)
}
