package com.ceyinfo.cerpstores.ui.adjustment

import android.graphics.drawable.GradientDrawable
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

object AdjustmentStatusStyle {

    private val isoFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    private val displayFmt = SimpleDateFormat("d MMM yyyy", Locale.US)

    fun applyStatus(view: TextView, status: String) {
        val (bgColor, textColor, label) = when (status.uppercase()) {
            "DRAFT"     -> Triple("#F1F5F9", "#475569", "DRAFT")
            "PENDING"   -> Triple("#DBEAFE", "#1D4ED8", "SUBMITTED")
            "APPROVED"  -> Triple("#DCFCE7", "#16A34A", "APPROVED")
            "CANCELLED" -> Triple("#FEF2F2", "#DC2626", "CANCELLED")
            else        -> Triple("#F1F5F9", "#475569", status.uppercase())
        }
        val bg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 999f
            setColor(android.graphics.Color.parseColor(bgColor))
        }
        view.background = bg
        view.setTextColor(android.graphics.Color.parseColor(textColor))
        view.text = label
    }

    fun formatDate(raw: String?): String {
        if (raw.isNullOrBlank()) return "—"
        return try {
            val datePart = raw.substringBefore('T').take(10)
            val date = isoFmt.parse(datePart) ?: return raw.substringBefore('T').take(10)
            displayFmt.format(date)
        } catch (_: Exception) {
            raw.substringBefore('T').take(10)
        }
    }

    fun formatQuantity(qty: Double): String =
        if (qty == qty.toLong().toDouble()) qty.toLong().toString()
        else "%.3f".format(qty).trimEnd('0').trimEnd('.')
}
