package com.ceyinfo.cerpstores.ui.common

import android.app.Activity
import android.content.Intent
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.Toast
import com.ceyinfo.cerpstores.R
import com.ceyinfo.cerpstores.data.model.MyStore
import com.ceyinfo.cerpstores.databinding.ItemQuickActionBinding
import com.ceyinfo.cerpstores.databinding.SheetNewTransactionBinding
import com.ceyinfo.cerpstores.ui.grn.GrnCreateActivity
import com.ceyinfo.cerpstores.util.SessionManager
import com.google.android.material.bottomsheet.BottomSheetDialog

/**
 * Bottom sheet shown when the user taps the center "+" FAB. Lists the four
 * transaction types from the navigation map (GRN, Issue, Transfer, Return).
 *
 * Rows are filtered by ACL — only entities the user is allowed to act on
 * appear, mirroring how the dashboard tiles are gated. If nothing is
 * allowed (rare — owner with no assignments, or all entities blocked) the
 * sheet shows a "no transactions available" empty state instead of an
 * empty list.
 *
 * Tap → store picker → entity create activity. The store picker step is
 * always present so writes carry an explicit `store_id` (the backend
 * derives BU + module instance from it). For now the post-pick action
 * just toasts the chosen store; swap in the entity's create activity as
 * each flow is built.
 */
object NewTransactionSheet {

    private data class Action(
        val titleRes: Int,
        val subtitleRes: Int,
        val iconRes: Int,
        val entityCode: String,
    )

    private val allActions = listOf(
        Action(R.string.action_goods_receipt,  R.string.action_goods_receipt_desc,
            R.drawable.ic_quick_grn,      "grn"),
        Action(R.string.action_goods_issue,    R.string.action_goods_issue_desc,
            R.drawable.ic_quick_issue,    "store_issue"),
        Action(R.string.action_stock_transfer, R.string.action_stock_transfer_desc,
            R.drawable.ic_quick_transfer, "stock_transfer"),
        Action(R.string.action_return,         R.string.action_return_desc,
            R.drawable.ic_quick_return,   "material_return"),
    )

    fun show(activity: Activity) {
        val dialog = BottomSheetDialog(activity, R.style.Theme_Stores_BottomSheet)
        val binding = SheetNewTransactionBinding.inflate(LayoutInflater.from(activity))
        dialog.setContentView(binding.root)

        val session = SessionManager(activity)
        val allowed = allActions.filter {
            session.isEntityAllowed(it.entityCode) &&
                session.canPerformAction(it.entityCode, "create")
        }

        if (allowed.isEmpty()) {
            // Empty state — keep the sheet honest rather than showing rows
            // that 403 on submit. Replace this toast with a styled empty
            // panel inside the sheet if it ends up being a common case.
            Toast.makeText(
                activity,
                "No transactions available for your role.",
                Toast.LENGTH_SHORT,
            ).show()
            return
        }

        val container = binding.actionsContainer as LinearLayout
        for (action in allowed) {
            val row = ItemQuickActionBinding.inflate(
                LayoutInflater.from(activity),
                container,
                false,
            )
            row.tvTitle.setText(action.titleRes)
            row.tvSubtitle.setText(action.subtitleRes)
            row.icon.setImageResource(action.iconRes)
            row.root.setOnClickListener {
                dialog.dismiss()
                StorePickerSheet.show(
                    activity = activity,
                    title = activity.getString(action.titleRes),
                    subtitle = activity.getString(action.subtitleRes),
                ) { store ->
                    routeToCreateActivity(activity, action.entityCode, store)
                }
            }
            container.addView(row.root)
        }

        dialog.show()
    }

    /**
     * Route the picked store + chosen entity to the right create activity.
     *
     * Today only `grn` is wired. The other 4 entities fall through to a
     * "coming soon" toast — once their create activities exist, add a
     * branch here and they're done. Keeping this in one place means the
     * entity-code → activity mapping doesn't get scattered.
     */
    private fun routeToCreateActivity(
        activity: Activity,
        entityCode: String,
        store: MyStore,
    ) {
        when (entityCode) {
            "grn" -> activity.startActivity(
                Intent(activity, GrnCreateActivity::class.java)
                    .putExtra(GrnCreateActivity.EXTRA_STORE_ID, store.storeId)
                    .putExtra(GrnCreateActivity.EXTRA_STORE_NAME, store.name)
            )
            else -> Toast.makeText(
                activity,
                "${activity.getString(R.string.coming_soon)} (${store.name})",
                Toast.LENGTH_SHORT,
            ).show()
        }
    }
}
