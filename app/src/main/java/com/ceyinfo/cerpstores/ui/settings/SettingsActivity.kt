package com.ceyinfo.cerpstores.ui.settings

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.ceyinfo.cerpstores.BuildConfig
import com.ceyinfo.cerpstores.data.remote.ApiClient
import com.ceyinfo.cerpstores.databinding.ActivitySettingsBinding
import com.ceyinfo.cerpstores.ui.common.BottomNav
import com.ceyinfo.cerpstores.ui.login.LoginActivity
import com.ceyinfo.cerpstores.updater.AppUpdater
import com.ceyinfo.cerpstores.updater.UpdateDialog
import com.ceyinfo.cerpstores.util.SessionManager
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var session: SessionManager
    private lateinit var updater: AppUpdater

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT

        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        session = SessionManager(this)
        updater = AppUpdater(this)

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
        binding.rowCheckUpdate.setOnClickListener { checkForUpdate() }
        binding.btnLogout.setOnClickListener { performLogout() }
    }

    /**
     * Force a fresh GitHub Releases check (bypasses the 4h throttle and
     * any "Skip" the user may have set on a prior version).
     */
    private fun checkForUpdate() {
        binding.progressUpdate.visibility = View.VISIBLE
        binding.icUpdateArrow.visibility = View.GONE
        binding.tvUpdateStatus.text = "Checking…"

        lifecycleScope.launch {
            val release = updater.checkForUpdate(forceCheck = true)

            binding.progressUpdate.visibility = View.GONE
            binding.icUpdateArrow.visibility = View.VISIBLE

            if (release != null) {
                val version = release.tagName.removePrefix("v")
                binding.tvUpdateStatus.text = "New version available: v$version"
                UpdateDialog.show(this@SettingsActivity, release, updater)
            } else {
                binding.tvUpdateStatus.text = "You're on the latest version"
                Toast.makeText(this@SettingsActivity, "App is up to date", Toast.LENGTH_SHORT).show()
            }
        }
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
