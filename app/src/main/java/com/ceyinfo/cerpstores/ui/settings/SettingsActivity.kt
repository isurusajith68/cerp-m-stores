package com.ceyinfo.cerpstores.ui.settings

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.ceyinfo.cerpstores.BuildConfig
import com.ceyinfo.cerpstores.data.remote.ApiClient
import com.ceyinfo.cerpstores.databinding.ActivitySettingsBinding
import com.ceyinfo.cerpstores.ui.common.BottomNav
import com.ceyinfo.cerpstores.ui.login.LoginActivity
import com.ceyinfo.cerpstores.util.SessionManager
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var session: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT

        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        session = SessionManager(this)

        BottomNav.bind(binding.bottomNav.root, this, BottomNav.Tab.SETTINGS)

        binding.tvVersion.text = "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
        binding.tvOrganization.text = session.organizationName ?: "—"
        binding.tvEmail.text = session.email ?: "—"
        // Prefer the cross-BU role list (from /store-mobile/my-role) since
        // the user may hold different roles in different BUs. Fall back to
        // the per-BU label from /verify-module when the list is empty.
        binding.tvRole.text = session.getRoleLabels().joinToString(", ").ifEmpty {
            session.roleLabel ?: "—"
        }

        binding.btnBack.setOnClickListener { finish() }

        binding.btnLogout.setOnClickListener { performLogout() }
    }

    private fun performLogout() {
        lifecycleScope.launch {
            try {
                ApiClient.getService(this@SettingsActivity).logout()
            } catch (_: Exception) {
                // Best-effort — clear local state regardless.
            }
            ApiClient.clearSession()
            session.clearSession()
            startActivity(
                Intent(this@SettingsActivity, LoginActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            )
            finish()
        }
    }
}
