package com.claudetracker.app.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.claudetracker.app.data.model.Account
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

    fun addAccount(account: Account) {
        val accounts = getAllAccounts().toMutableList()
        val existingIndex = accounts.indexOfFirst { it.orgId == account.orgId }
        if (existingIndex >= 0) {
            accounts[existingIndex] = account
        } else {
            accounts.add(account)
        }
        saveAccounts(accounts)
    }

    fun removeAccount(orgId: String) {
        val accounts = getAllAccounts().filter { it.orgId != orgId }
        saveAccounts(accounts)
    }

    fun getAllAccounts(): List<Account> {
        val json = prefs.getString(KEY_ACCOUNTS, null) ?: return emptyList()
        return try {
            val array = JSONArray(json)
            (0 until array.length()).map { Account.fromJson(array.getJSONObject(it)) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveAccounts(accounts: List<Account>) {
        val array = JSONArray()
        accounts.forEach { array.put(it.toJson()) }
        // Use commit() (synchronous) not apply() — prevents race condition where
        // the status screen reads accounts before they are written to disk.
        prefs.edit().putString(KEY_ACCOUNTS, array.toString()).commit()
        android.util.Log.d("SecureStorage", "Saved ${accounts.size} accounts. Read-back: ${getAllAccounts().size}")
    }

    // Legacy compatibility — check if any account exists
    fun isLoggedIn(): Boolean = getAllAccounts().isNotEmpty()

    // Legacy compatibility — get first account's cookie
    fun getCookie(): String? = getAllAccounts().firstOrNull()?.sessionCookie

    // Legacy compatibility — get first account's org ID
    fun getOrgId(): String? = getAllAccounts().firstOrNull()?.orgId

    // Legacy compatibility
    fun saveCookie(cookie: String, orgId: String) {
        addAccount(Account(orgId = orgId, displayName = "Claude Account", sessionCookie = cookie, planName = "Unknown"))
    }

    fun clearAll() {
        prefs.edit().clear().commit()
    }

    companion object {
        private const val KEY_ACCOUNTS = "accounts_v2"
    }
}
