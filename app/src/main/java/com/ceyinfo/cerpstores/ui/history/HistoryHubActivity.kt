package com.ceyinfo.cerpstores.ui.history

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.ceyinfo.cerpstores.R
import com.ceyinfo.cerpstores.databinding.ActivityHistoryHubBinding
import com.ceyinfo.cerpstores.databinding.ItemQuickActionBinding
import com.ceyinfo.cerpstores.ui.common.BottomNav
import com.ceyinfo.cerpstores.util.SessionManager

class HistoryHubActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistoryHubBinding
    private lateinit var session: SessionManager

    private data class HubAction(
        val titleRes: Int,
        val subtitleRes: Int,
        val iconRes: Int,
        val bgTint: String,
        val iconTint: String,
        val entityCode: String?,
        val target: Class<*>,
    )

    private val actions = listOf(
        HubAction(
            R.string.history_hub_all_movements,
            R.string.history_hub_all_movements_desc,
            R.drawable.ic_transactions,
            "#F1F5F9",
            "#475569",
            null,
            HistoryActivity::class.java,
        ),
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        binding = ActivityHistoryHubBinding.inflate(layoutInflater)
        setContentView(binding.root)

        session = SessionManager(this)
        binding.btnBack.setOnClickListener { finish() }
        BottomNav.bind(binding.bottomNav.root, this, BottomNav.Tab.HISTORY)
        renderActions()
    }

    private fun renderActions() {
        binding.actionsContainer.removeAllViews()
        actions
            .filter { it.entityCode == null || session.isEntityAllowed(it.entityCode) }
            .forEach { action ->
                val row = ItemQuickActionBinding.inflate(
                    LayoutInflater.from(this),
                    binding.actionsContainer,
                    false,
                )
                row.tvTitle.setText(action.titleRes)
                row.tvSubtitle.setText(action.subtitleRes)
                row.icon.setImageResource(action.iconRes)
                row.icon.setColorFilter(Color.parseColor(action.iconTint))
                row.iconBg.background.setTint(Color.parseColor(action.bgTint))
                row.root.setOnClickListener {
                    startActivity(Intent(this, action.target))
                }
                binding.actionsContainer.addView(row.root)
            }
    }
}
