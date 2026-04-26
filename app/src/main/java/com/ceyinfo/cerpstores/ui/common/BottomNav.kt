package com.ceyinfo.cerpstores.ui.common

import android.app.Activity
import android.content.Intent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.ceyinfo.cerpstores.R
import com.ceyinfo.cerpstores.ui.dashboard.DashboardActivity
import com.ceyinfo.cerpstores.ui.grn.GrnListActivity
import com.ceyinfo.cerpstores.ui.settings.SettingsActivity

/**
 * Wires the 5-element bottom nav (4 tabs + raised center FAB).
 *
 * - Active tab: orange pill behind the icon, primary tint, bold label.
 * - Re-tapping the active tab is a no-op.
 * - Other tabs route via SINGLE_TOP | CLEAR_TOP so we don't pile activities.
 * - The center FAB always opens the New-Transaction picker, regardless of
 *   which screen is active.
 *
 * History today routes to GrnListActivity (the only entity with a list
 * screen). Once Issues / Transfers / Returns / Adjustments have list
 * activities, swap History's target for a tabbed wrapper that hosts all
 * five. Inventory remains stubbed with a toast until built.
 */
object BottomNav {

    enum class Tab { HOME, INVENTORY, HISTORY, SETTINGS }

    fun bind(root: View, activity: Activity, active: Tab) {
        val activeColor = ContextCompat.getColor(activity, R.color.primary)
        val inactiveColor = ContextCompat.getColor(activity, R.color.text_secondary)

        // Lift the bar above the gesture bar by adding the system inset to the
        // existing bottom padding (12dp from the layout).
        val navBar = root.findViewById<View>(R.id.nav_bar)
        val basePaddingBottom = navBar.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(navBar) { v, insets ->
            val nav = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            v.updatePadding(bottom = basePaddingBottom + nav.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(navBar)

        data class Item(
            val tab: Tab,
            val pillId: Int,
            val iconId: Int,
            val labelId: Int,
            val rowId: Int,
            val target: Class<out Activity>?,
        )

        val items = listOf(
            Item(Tab.HOME, R.id.pill_home, R.id.icon_home, R.id.label_home,
                R.id.nav_home, DashboardActivity::class.java),
            Item(Tab.INVENTORY, R.id.pill_inventory, R.id.icon_inventory, R.id.label_inventory,
                R.id.nav_inventory, null),
            Item(Tab.HISTORY, R.id.pill_history, R.id.icon_history, R.id.label_history,
                R.id.nav_history, GrnListActivity::class.java),
            Item(Tab.SETTINGS, R.id.pill_settings, R.id.icon_settings, R.id.label_settings,
                R.id.nav_settings, SettingsActivity::class.java),
        )

        for (item in items) {
            val pill = root.findViewById<FrameLayout>(item.pillId)
            val icon = root.findViewById<ImageView>(item.iconId)
            val label = root.findViewById<TextView>(item.labelId)
            val row = root.findViewById<View>(item.rowId)

            val isActive = item.tab == active
            pill.setBackgroundResource(if (isActive) R.drawable.bg_nav_pill else 0)
            icon.setColorFilter(if (isActive) activeColor else inactiveColor)
            label.setTextColor(if (isActive) activeColor else inactiveColor)
            label.setTypeface(
                null,
                if (isActive) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL
            )

            row.setOnClickListener {
                if (isActive) return@setOnClickListener
                val target = item.target
                if (target == null) {
                    android.widget.Toast.makeText(
                        activity,
                        "${label.text}: ${activity.getString(R.string.coming_soon)}",
                        android.widget.Toast.LENGTH_SHORT,
                    ).show()
                    return@setOnClickListener
                }
                val intent = Intent(activity, target).addFlags(
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                )
                activity.startActivity(intent)
                activity.overridePendingTransition(0, 0)
                if (active != Tab.HOME) activity.finish()
            }
        }

        // Center FAB → New Transaction picker.
        root.findViewById<View>(R.id.nav_fab).setOnClickListener {
            NewTransactionSheet.show(activity)
        }
    }
}
