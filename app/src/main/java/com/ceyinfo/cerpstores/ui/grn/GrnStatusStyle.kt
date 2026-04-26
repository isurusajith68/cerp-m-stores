package com.ceyinfo.cerpstores.ui.grn

import android.content.Context
import android.graphics.PorterDuff
import android.graphics.drawable.GradientDrawable
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.ceyinfo.cerpstores.R
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Small bag of view-level helpers shared by the GRN list, detail, and
 * (eventually) create flows. Keeping them together avoids each Activity
 * defining its own `formatStatus()` clone.
 *
 * Status codes follow the backend convention (uppercase: DRAFT, SUBMITTED,
 * APPROVED, CANCELLED, REJECTED). Anything unexpected falls through to a
 * neutral grey so the UI never crashes on a future status we haven't
 * mapped yet.
 */
object GrnStatusStyle {

    private data class Tone(val bgHex: String, val fgHex: String, val labelRes: Int)

    private val tones = mapOf(
        "DRAFT"     to Tone("#F1F5F9", "#475569", R.string.grn_status_draft),
        "SUBMITTED" to Tone("#DBEAFE", "#1D4ED8", R.string.grn_status_submitted),
        "APPROVED"  to Tone("#D1FAE5", "#047857", R.string.grn_status_approved),
        "CANCELLED" to Tone("#FEE2E2", "#B91C1C", R.string.grn_status_cancelled),
        "REJECTED"  to Tone("#FEE2E2", "#B91C1C", R.string.grn_status_rejected),
    )

    private val fallback = Tone("#F1F5F9", "#475569", R.string.grn_status_draft)

    /**
     * Paint a TextView as a status pill in one call. Pass the raw status
     * code from the API; we lower-case the label client-side and tint the
     * pill background in place.
     */
    fun applyStatus(tv: TextView, status: String?) {
        val tone = tones[status?.uppercase()] ?: fallback
        tv.text = tv.context.getString(tone.labelRes)
        tv.setTextColor(android.graphics.Color.parseColor(tone.fgHex))
        val bg = (tv.background?.constantState?.newDrawable()?.mutate() as? GradientDrawable)
            ?: GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 999f
            }
        bg.setColor(android.graphics.Color.parseColor(tone.bgHex))
        tv.background = bg
    }

    /** Foreground hex for a status — used for the GRN-detail status dot. */
    fun fgHex(status: String?): String =
        (tones[status?.uppercase()] ?: fallback).fgHex

    // ── Formatting helpers ─────────────────────────────────────────

    private val isoDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
        isLenient = true
    }
    private val displayDate = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

    /** "2026-04-25T..." or "2026-04-25" → "25 Apr 2026". Returns "—" on null/junk. */
    fun formatDate(iso: String?): String {
        if (iso.isNullOrBlank()) return "—"
        return runCatching {
            val parsed = isoDate.parse(iso.substring(0, minOf(10, iso.length)))
            displayDate.format(parsed!!)
        }.getOrElse { "—" }
    }

    /** 12345.6 → "12,345.60" — purely cosmetic, no currency symbol. */
    fun formatAmount(amount: Double): String =
        NumberFormat.getNumberInstance(Locale.getDefault()).apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 2
        }.format(amount)

    fun formatQuantity(qty: Double): String =
        NumberFormat.getNumberInstance(Locale.getDefault()).apply {
            minimumFractionDigits = 0
            maximumFractionDigits = 3
        }.format(qty)
}
