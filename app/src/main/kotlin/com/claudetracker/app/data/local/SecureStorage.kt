package com.claudetracker.app.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.claudetracker.app.data.model.Account
import com.claudetracker.app.data.model.Platform
import org.json.JSONArray

class SecureStorage(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "claude_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    // ── Account CRUD ────────────────────────────────────

    fun addAccount(account: Account) {
        val accounts = getAllAccounts().toMutableList()
        // Codex accounts have generated IDs, so keep one current account.
        val existingIndex = when (account.platform) {
            Platform.CODEX -> accounts.indexOfFirst {
                it.platform == Platform.CODEX
            }
            else -> accounts.indexOfFirst { it.id == account.id }
        }
        if (existingIndex >= 0) {
            // Preserve original ID but update credentials
            val existingId = accounts[existingIndex].id
            accounts[existingIndex] = account.copy(id = existingId)
        } else {
            accounts.add(account)
        }
        saveAccounts(accounts)
    }

    fun removeAccount(accountId: String) {
        val accounts = getAllAccounts().filter { it.id != accountId }
        saveAccounts(accounts)
    }

    fun getAllAccounts(): List<Account> {
        val json = prefs.getString(KEY_ACCOUNTS, null) ?: return emptyList()
        return try {
            val array = JSONArray(json)
            (0 until array.length()).mapNotNull { Account.fromJson(array.getJSONObject(it)) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun getAccountsByPlatform(platform: Platform): List<Account> {
        return getAllAccounts().filter { it.platform == platform }
    }

    // ── Token update helpers ────────────────────────────

    /**
     * Update the Codex access token after JWT refresh.
     */
    fun updateCodexAccessToken(accountId: String, newToken: String) {
        val accounts = getAllAccounts().toMutableList()
        val idx = accounts.indexOfFirst { it.id == accountId }
        if (idx >= 0) {
            accounts[idx] = accounts[idx].copy(codexAccessToken = newToken)
            saveAccounts(accounts)
        }
    }

    // ── Private helpers ─────────────────────────────────

    private fun saveAccounts(accounts: List<Account>) {
        val array = JSONArray()
        accounts.forEach { array.put(it.toJson()) }
        // Use commit() (synchronous) to prevent race conditions
        prefs.edit().putString(KEY_ACCOUNTS, array.toString()).commit()
        android.util.Log.d("SecureStorage", "Saved ${accounts.size} accounts")
    }

    // ── Legacy compatibility ────────────────────────────

    fun isLoggedIn(): Boolean = getAllAccounts().isNotEmpty()

    fun getCookie(): String? = getAllAccounts().firstOrNull()?.sessionCookie

    fun getOrgId(): String? = getAllAccounts().firstOrNull()?.orgId

    fun saveCookie(cookie: String, orgId: String) {
        addAccount(
            Account(
                id = orgId,
                platform = Platform.CLAUDE,
                orgId = orgId,
                displayName = "Claude Account",
                sessionCookie = cookie,
                planName = "Unknown"
            )
        )
    }

    fun clearAll() {
        prefs.edit().clear().commit()
    }

    companion object {
        private const val KEY_ACCOUNTS = "accounts_v2"
    }
}
