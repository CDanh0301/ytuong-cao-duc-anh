package com.example.test2.data.local.prefs

import android.content.Context
import android.content.SharedPreferences

/**
 * Local SharedPreferences data source for Privacy Vault settings and restore exemptions.
 */
class VaultPrefsDataSource(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun isVaultEnabled(): Boolean = prefs.getBoolean(KEY_VAULT_ENABLED, false)

    fun setVaultEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_VAULT_ENABLED, enabled).commit()
    }

    fun getVaultScope(): String = prefs.getString(KEY_VAULT_SCOPE, SCOPE_ALL) ?: SCOPE_ALL

    fun setVaultScope(scope: String) {
        prefs.edit().putString(KEY_VAULT_SCOPE, scope).commit()
    }

    fun getPendingVaultScope(): String? = prefs.getString(KEY_PENDING_VAULT_SCOPE, null)

    fun setPendingVaultScope(scope: String?) {
        val editor = prefs.edit()
        if (scope == null) {
            editor.remove(KEY_PENDING_VAULT_SCOPE)
        } else {
            editor.putString(KEY_PENDING_VAULT_SCOPE, scope)
        }
        editor.commit()
    }

    fun getVaultEnabledSince(): Long = prefs.getLong(KEY_VAULT_ENABLED_SINCE, 0L)

    fun setVaultEnabledSince(timestamp: Long) {
        prefs.edit().putLong(KEY_VAULT_ENABLED_SINCE, timestamp).apply()
    }

    fun hasPromptedConsent(): Boolean = prefs.getBoolean(KEY_HAS_PROMPTED_CONSENT, false)

    fun setPromptedConsent(prompted: Boolean) {
        prefs.edit().putBoolean(KEY_HAS_PROMPTED_CONSENT, prompted).apply()
    }

    fun getRestoredExemptIds(): Set<String> {
        return prefs.getStringSet(KEY_RESTORED_EXEMPT_IDS, null)?.toSet() ?: emptySet()
    }

    fun saveRestoredExemptIds(ids: Set<String>) {
        prefs.edit().putStringSet(KEY_RESTORED_EXEMPT_IDS, HashSet(ids)).commit()
    }

    fun clearRestoredExemptIds() {
        prefs.edit().remove(KEY_RESTORED_EXEMPT_IDS).commit()
    }

    companion object {
        private const val PREF_NAME = "privacy_vault_prefs"
        private const val KEY_VAULT_ENABLED = "is_vault_enabled"
        private const val KEY_HAS_PROMPTED_CONSENT = "has_prompted_consent"
        private const val KEY_VAULT_SCOPE = "vault_scope"
        private const val KEY_VAULT_ENABLED_SINCE = "vault_enabled_since"
        private const val KEY_PENDING_VAULT_SCOPE = "pending_vault_scope"
        private const val KEY_RESTORED_EXEMPT_IDS = "restored_exempt_ids"

        const val SCOPE_ALL = "ALL"
        const val SCOPE_NEW_ONLY = "NEW_ONLY"
    }
}
