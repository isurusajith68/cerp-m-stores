package com.ceyinfo.cerpstores.ui.transfer

import android.graphics.drawable.GradientDrawable
import android.widget.TextView
import com.ceyinfo.cerpstores.R
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Visual helpers for the Stock Transfer screens — status pill colours,
 * date/quantity formatting. Mirror of GrnStatusStyle but with transfer-specific
 * status codes (DRAFT, PENDING, IN_TRANSIT, RECEIVED, CANCELLED).
 */
object TransferStatusStyle {

    private data class Tone(val bgHex: String, val fgHex: String, val labelRes: Int)

    private val tones = mapOf(
        "DRAFT"      to Tone("#F1F5F9", "#475569", R.string.transfer_status_draft),
        "PENDING"    to Tone("#DBEAFE", "#1D4ED8", R.string.transfer_status_pending),
        "IN_TRANSIT" to Tone("#FEF3C7", "#92400E", R.string.transfer_status_in_transit),
        "RECEIVED"   to Tone("#D1FAE5", "#047857", R.string.transfer_status_received),
        "CANCELLED"  to Tone("#FEE2E2", "#B91C1C", R.string.transfer_status_cancelled),
        "REJECTED"   to Tone("#FEE2E2", "#B91C1C", R.string.transfer_status_cancelled),
    )

    private val fallback = Tone("#F1F5F9", "#475569", R.string.transfer_status_draft)

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

    private val isoFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = true }
    private val displayFmt = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

    fun formatDate(iso: String?): String {
        if (iso.isNullOrBlank()) return "—"
        return runCatching {
            displayFmt.format(isoFmt.parse(iso.substring(0, minOf(10, iso.length)))!!)
        }.getOrElse { "—" }
    }

    fun formatQuantity(qty: Double): String =
        NumberFormat.getNumberInstance(Locale.getDefault()).apply {
            minimumFractionDigits = 0
            maximumFractionDigits = 3
        }.format(qty)
}
