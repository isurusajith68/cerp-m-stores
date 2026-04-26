package com.ceyinfo.cerpstores.util

import android.content.Context
import android.content.SharedPreferences
import com.ceyinfo.cerpstores.data.model.EntityPermissions
import com.ceyinfo.cerpstores.data.model.MyPermissionsData
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class SessionManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("stores_session", Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_EMAIL = "email"
        private const val KEY_ORG_ID = "organization_id"
        private const val KEY_ORG_NAME = "organization_name"
        private const val KEY_IS_OWNER = "is_owner"
        private const val KEY_EMPLOYEE_NAME = "employee_name"
        private const val KEY_ROLE_LABEL = "role_label"
        private const val KEY_ROLE_LABELS = "role_labels"
        private const val KEY_PERMISSIONS = "my_permissions"
    }

    var isLoggedIn: Boolean
        get() = prefs.getBoolean(KEY_IS_LOGGED_IN, false)
        set(value) = prefs.edit().putBoolean(KEY_IS_LOGGED_IN, value).apply()

    var userId: String?
        get() = prefs.getString(KEY_USER_ID, null)
        set(value) = prefs.edit().putString(KEY_USER_ID, value).apply()

    var email: String?
        get() = prefs.getString(KEY_EMAIL, null)
        set(value) = prefs.edit().putString(KEY_EMAIL, value).apply()

    var organizationId: String?
        get() = prefs.getString(KEY_ORG_ID, null)
        set(value) = prefs.edit().putString(KEY_ORG_ID, value).apply()

    var organizationName: String?
        get() = prefs.getString(KEY_ORG_NAME, null)
        set(value) = prefs.edit().putString(KEY_ORG_NAME, value).apply()

    var isOwner: Boolean
        get() = prefs.getBoolean(KEY_IS_OWNER, false)
        set(value) = prefs.edit().putBoolean(KEY_IS_OWNER, value).apply()

    var employeeName: String?
        get() = prefs.getString(KEY_EMPLOYEE_NAME, null)
        set(value) = prefs.edit().putString(KEY_EMPLOYEE_NAME, value).apply()

    var roleLabel: String?
        get() = prefs.getString(KEY_ROLE_LABEL, null)
        set(value) = prefs.edit().putString(KEY_ROLE_LABEL, value).apply()

    // ── Multi-role labels (across all BUs) ─────────────────────────

    /** Persist the cross-BU role label list returned by `/store-mobile/my-role`. */
    fun saveRoleLabels(labels: List<String>?) {
        if (labels == null) prefs.edit().remove(KEY_ROLE_LABELS).apply()
        else prefs.edit().putString(KEY_ROLE_LABELS, gson.toJson(labels)).apply()
    }

    fun getRoleLabels(): List<String> {
        val json = prefs.getString(KEY_ROLE_LABELS, null) ?: return emptyList()
        val type = object : TypeToken<List<String>>() {}.type
        return runCatching { gson.fromJson<List<String>>(json, type) }.getOrDefault(emptyList())
    }

    // ── Merged ACL permissions across all BUs/roles ────────────────

    /**
     * Persist the `/store-mobile/my-permissions` payload. Called from
     * LoginActivity on every launch so permissions stay fresh across role
     * changes done in the web admin while the app was backgrounded.
     */
    fun savePermissions(perms: MyPermissionsData?) {
        if (perms == null) prefs.edit().remove(KEY_PERMISSIONS).apply()
        else prefs.edit().putString(KEY_PERMISSIONS, gson.toJson(perms)).apply()
    }

    fun getPermissions(): MyPermissionsData? {
        val json = prefs.getString(KEY_PERMISSIONS, null) ?: return null
        return runCatching { gson.fromJson(json, MyPermissionsData::class.java) }.getOrNull()
    }

    /** Per-entity gating without forcing every caller to null-check the map. */
    fun entityPermissions(entityCode: String): EntityPermissions? =
        getPermissions()?.entities?.get(entityCode)

    /**
     * True when the user can reach this entity at all (owner or unblocked
     * role with at least one permission row). Drives tile visibility.
     */
    fun isEntityAllowed(entityCode: String): Boolean {
        val perms = getPermissions() ?: return false
        if (perms.isOwner) return true
        return perms.entities[entityCode]?.allowed == true
    }

    /**
     * Action-level gating. Mirrors cash-book's fail-open rule: if no ACL
     * has been seeded for this action, default to allowed (server is the
     * source of truth — better to surface a button + 403 than to silently
     * hide a feature because the seed is incomplete).
     */
    fun canPerformAction(entityCode: String, actionCode: String): Boolean {
        val perms = getPermissions() ?: return true
        if (perms.isOwner) return true
        val ent = perms.entities[entityCode] ?: return true
        if (!ent.allowed) return false
        if (ent.allowedActions.contains(actionCode)) return true
        return ent.permissions.none {
            it.accessType == "action" &&
                it.targetCode == actionCode &&
                it.permission == "disabled"
        }
    }

    fun clearSession() {
        prefs.edit().clear().apply()
    }
}
