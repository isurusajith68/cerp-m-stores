package com.ceyinfo.cerpstores.ui.dashboard

import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.ceyinfo.cerpstores.R
import com.ceyinfo.cerpstores.data.remote.ApiClient
import com.ceyinfo.cerpstores.databinding.ActivityDashboardBinding
import com.ceyinfo.cerpstores.ui.common.BottomNav
import com.ceyinfo.cerpstores.ui.grn.GrnListActivity
import com.ceyinfo.cerpstores.ui.inventory.InventoryActivity
import com.ceyinfo.cerpstores.ui.issue.IssueListActivity
import com.ceyinfo.cerpstores.ui.login.LoginActivity
import com.ceyinfo.cerpstores.ui.adjustment.AdjustmentListActivity
import com.ceyinfo.cerpstores.ui.ret.ReturnListActivity
import com.ceyinfo.cerpstores.ui.transfer.TransferListActivity
import com.ceyinfo.cerpstores.ui.verification.VerificationListActivity
import com.ceyinfo.cerpstores.updater.AppUpdater
import com.ceyinfo.cerpstores.updater.UpdateDialog
import com.ceyinfo.cerpstores.util.SessionManager
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.launch

class DashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDashboardBinding
    private lateinit var session: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT

        binding = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        session = SessionManager(this)

        if (!session.isLoggedIn) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        setupHeader()
        buildModuleTiles()
        BottomNav.bind(binding.bottomNav.root, this, BottomNav.Tab.HOME)

        binding.swipeRefresh.setOnRefreshListener {
            refreshPermissionsAndTiles { binding.swipeRefresh.isRefreshing = false }
        }
        binding.swipeRefresh.setColorSchemeResources(R.color.primary)

        checkForUpdates()
    }

    /**
     * Throttled background update check (4h between calls — see AppUpdater).
     * If a newer release is available and the user hasn't skipped it, the
     * UpdateDialog prompts to download. Failures are silent.
     */
    private fun checkForUpdates() {
        lifecycleScope.launch {
            val updater = AppUpdater(this@DashboardActivity)
            val release = updater.checkForUpdate()
            if (release != null) {
                UpdateDialog.show(this@DashboardActivity, release, updater)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-pull permissions on every resume so changes made in the web
        // admin (role assignments, entity blocks) reflect when the user
        // foregrounds the app, without forcing a logout.
        setupHeader()
        refreshPermissionsAndTiles()
    }

    /**
     * Refetches `/store-mobile/my-permissions` + `/store-mobile/my-role`,
     * persists them, and rebuilds the tile grid against the new ACL. The
     * tile rebuild runs twice — once with the cached ACL so the UI feels
     * instant, then again after the network fetch so any changes land.
     *
     * Failures are silent — the cached ACL keeps the dashboard usable
     * while offline. The pull-to-refresh spinner clears via `onDone` so
     * we don't strand the user staring at a forever-spinning indicator.
     */
    private fun refreshPermissionsAndTiles(onDone: (() -> Unit)? = null) {
        buildModuleTiles()
        lifecycleScope.launch {
            try {
                val api = ApiClient.getService(this@DashboardActivity)

                val perms = api.getMyPermissions()
                if (perms.isSuccessful && perms.body()?.success == true) {
                    session.savePermissions(perms.body()?.data)
                }

                val role = api.getMyRole()
                if (role.isSuccessful && role.body()?.success == true) {
                    val data = role.body()?.data
                    session.saveRoleLabels(data?.roleLabels)
                    if (data?.isOwner == true) session.isOwner = true
                }

                buildModuleTiles()
                setupHeader()
            } catch (_: Exception) {
                // Cached ACL still drives the UI; nothing to surface.
            } finally {
                onDone?.invoke()
            }
        }
    }

    private fun setupHeader() {
        binding.tvTitle.text = session.organizationName ?: getString(R.string.app_name)
        binding.tvUserEmail.text = session.employeeName ?: session.email
        binding.tvUserRole.text = session.roleLabel ?: session.email
        binding.tvAvatar.text =
            (session.employeeName ?: session.email)?.firstOrNull()?.uppercase() ?: "U"
    }

    /**
     * Tile spec → ACL entity code. Tiles whose entity is not allowed for
     * the current user are dropped before layout, so the grid collapses
     * naturally (no empty slots, no "coming soon" toasts on actions the
     * user can't perform).
     *
     * `entityCode = null` means the tile is always visible (e.g. Stock,
     * which is just a read-only view of `store_stock_ledger` and isn't
     * an entity in the ACL set).
     */
    private data class TileSpec(
        val titleRes: Int,
        val subtitleRes: Int,
        val iconRes: Int,
        val bgTint: String,
        val iconTint: String,
        val entityCode: String?,
    )

    private val tileSpecs = listOf(
        TileSpec(R.string.module_grn,         R.string.module_grn_desc,
            R.drawable.ic_module_grn,         "#FEF3C7", "#D97706", "grn"),
        TileSpec(R.string.module_issues,      R.string.module_issues_desc,
            R.drawable.ic_module_issues,      "#DBEAFE", "#2563EB", "store_issue"),
        TileSpec(R.string.module_transfers,   R.string.module_transfers_desc,
            R.drawable.ic_module_transfers,   "#EDE9FE", "#7C3AED", "stock_transfer"),
        TileSpec(R.string.module_returns,     R.string.module_returns_desc,
            R.drawable.ic_module_returns,     "#FCE7F3", "#DB2777", "material_return"),
        TileSpec(R.string.module_adjustments, R.string.module_adjustments_desc,
            R.drawable.ic_module_adjustments, "#D1FAE5", "#059669", "stock_adjustment"),
        TileSpec(R.string.module_verification, R.string.module_verification_desc,
            R.drawable.ic_module_verification, "#E0F2FE", "#0284C7", "verification"),
        TileSpec(R.string.module_stock,       R.string.module_stock_desc,
            R.drawable.ic_module_stock,       "#FFE4B8", "#F9972D", null),
    )

    private fun buildModuleTiles() {
        val rows = listOf(binding.row1, binding.row2, binding.row3, binding.row4)
        rows.forEach { it.removeAllViews() }

        val visible = tileSpecs.filter {
            it.entityCode == null || session.isEntityAllowed(it.entityCode)
        }

        // 2-up grid. Spec index 0,1 → row1; 2,3 → row2; 4,5 → row3; 6,7 → row4.
        visible.forEachIndexed { idx, spec ->
            val row = rows[idx / 2]
            row.addView(
                makeTile(
                    title = getString(spec.titleRes),
                    subtitle = getString(spec.subtitleRes),
                    iconRes = spec.iconRes,
                    bgTint = spec.bgTint,
                    iconTint = spec.iconTint,
                ) { onTileTapped(spec) }
            )
        }

        // Hide rows that ended up empty so the grid doesn't leave gaps.
        rows.forEach { it.visibility = if (it.childCount > 0) View.VISIBLE else View.GONE }
    }

    /**
     * Route a tap on a tile to the right list activity. Today only the
     * GRN tile is wired — Issues / Transfers / Returns / Adjustments /
     * Stock fall through to a "coming soon" toast until their list
     * activities exist.
     *
     * Centralising the routing here means the entity-code → activity
     * mapping lives in one place; the same switch will grow as each
     * entity's list screen lands.
     */
    private fun onTileTapped(spec: TileSpec) {
        when (spec.entityCode) {
            "grn" -> startActivity(Intent(this, GrnListActivity::class.java))
            "store_issue" -> startActivity(Intent(this, IssueListActivity::class.java))
            "stock_transfer" -> startActivity(Intent(this, TransferListActivity::class.java))
            "verification"   -> startActivity(Intent(this, VerificationListActivity::class.java))
            "material_return" -> startActivity(Intent(this, ReturnListActivity::class.java))
            "stock_adjustment" -> startActivity(Intent(this, AdjustmentListActivity::class.java))
            null -> startActivity(Intent(this, InventoryActivity::class.java))
            else -> Toast.makeText(
                this,
                "${getString(spec.titleRes)}: ${getString(R.string.coming_soon)}",
                Toast.LENGTH_SHORT,
            ).show()
        }
    }


    private fun makeTile(
        title: String,
        subtitle: String,
        iconRes: Int,
        bgTint: String,
        iconTint: String,
        onClick: () -> Unit,
    ): MaterialCardView {
        val dp = resources.displayMetrics.density

        val card = MaterialCardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = (6 * dp).toInt()
                marginEnd = (6 * dp).toInt()
            }
            radius = 16 * dp
            cardElevation = 0f
            strokeColor = android.graphics.Color.parseColor("#E8EEF4")
            strokeWidth = (1 * dp).toInt()
            isClickable = true
            isFocusable = true
            val attrs = context.obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackground))
            foreground = attrs.getDrawable(0)
            attrs.recycle()
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((18 * dp).toInt(), (18 * dp).toInt(), (18 * dp).toInt(), (18 * dp).toInt())
        }

        val iconFrame = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams((44 * dp).toInt(), (44 * dp).toInt())
        }
        val iconBg = View(this).apply {
            layoutParams = FrameLayout.LayoutParams((44 * dp).toInt(), (44 * dp).toInt())
            background = ContextCompat.getDrawable(context, R.drawable.bg_org_icon)?.mutate()
            (background as? GradientDrawable)?.setColor(android.graphics.Color.parseColor(bgTint))
        }
        val iconView = ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams((22 * dp).toInt(), (22 * dp).toInt()).apply {
                gravity = Gravity.CENTER
            }
            setImageResource(iconRes)
            setColorFilter(android.graphics.Color.parseColor(iconTint))
        }
        iconFrame.addView(iconBg)
        iconFrame.addView(iconView)
        content.addView(iconFrame)

        content.addView(TextView(this).apply {
            text = title
            setTextColor(ContextCompat.getColor(context, R.color.on_surface))
            textSize = 14f
            paint.isFakeBoldText = true
            setPadding(0, (14 * dp).toInt(), 0, 0)
        })

        content.addView(TextView(this).apply {
            text = subtitle
            setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
            textSize = 11f
            setPadding(0, (2 * dp).toInt(), 0, 0)
        })

        card.addView(content)
        card.setOnClickListener { onClick() }
        return card
    }

}
