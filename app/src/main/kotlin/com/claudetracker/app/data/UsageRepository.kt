package com.claudetracker.app.data

import android.content.Context
import com.claudetracker.app.Config
import com.claudetracker.app.data.local.SecureStorage
import com.claudetracker.app.data.model.Account
import com.claudetracker.app.data.model.AccountUsage
import com.claudetracker.app.data.model.Platform
import com.claudetracker.app.data.model.UsageData
import com.claudetracker.app.data.model.UsageResult
import com.claudetracker.app.data.remote.UsageApiClient
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

class UsageRepository(
    context: Context,
    val secureStorage: SecureStorage,
    private val apiClient: UsageApiClient
) {
    private val widgetPrefs = context.getSharedPreferences(Config.WIDGET_PREFS_NAME, Context.MODE_PRIVATE)

    // ── Multi-platform usage fetch ──────────────────────

    /**
     * Fetch usage for ALL accounts across all platforms, in parallel.
     */
    suspend fun fetchAllUsage(): List<AccountUsage> = coroutineScope {
        val accounts = secureStorage.getAllAccounts()
        if (accounts.isEmpty()) return@coroutineScope emptyList()

        accounts.map { account ->
            async { fetchSingleAccountUsage(account) }
        }.awaitAll()
    }

    /**
     * Dispatch to the correct API based on the account's platform.
     */
    private suspend fun fetchSingleAccountUsage(account: Account): AccountUsage {
        val result = when (account.platform) {
            Platform.CLAUDE -> {
                apiClient.fetchClaudeUsage(account.allCookies, account.orgId, account.planName)
            }
            Platform.CODEX -> {
                fetchCodexWithTokenRefresh(account)
            }
        }

        return when (result) {
            is UsageResult.Success -> AccountUsage(account = account, usageData = result.data)
            is UsageResult.AuthExpired -> AccountUsage(account = account, usageData = null, isAuthExpired = true)
            is UsageResult.NetworkError -> AccountUsage(account = account, usageData = null, error = result.message)
        }
    }

    /**
     * Codex: first refresh the JWT using the session cookie, then fetch usage.
     */
    private suspend fun fetchCodexWithTokenRefresh(account: Account): UsageResult {
        // Step 1: Exchange session cookie for a fresh JWT
        val tokenResult = apiClient.fetchCodexAccessToken(account.codexSessionCookie)
        val accessToken = tokenResult.getOrElse {
            return UsageResult.AuthExpired
        }

        // Update the stored token
        secureStorage.updateCodexAccessToken(account.id, accessToken)

        // Step 2: Fetch usage with the JWT
        return apiClient.fetchCodexUsage(accessToken)
    }

    // ── Legacy single-account fetch (for backward compat) ──

    suspend fun fetchUsage(): UsageResult {
        val account = secureStorage.getAllAccounts().firstOrNull()
            ?: return UsageResult.AuthExpired
        return apiClient.fetchClaudeUsage(account.allCookies, account.orgId, account.planName)
    }

    // ── Widget cache ────────────────────────────────────

    fun getCachedUsage(): UsageData? {
        val sessionPercent = widgetPrefs.getFloat("session_percent", -1f)
        if (sessionPercent < 0) return null
        return UsageData(
            sessionPercentUsed = sessionPercent.toDouble(),
            weeklyPercentUsed = widgetPrefs.getFloat("weekly_percent", 0f).toDouble(),
            sessionResetTimestamp = widgetPrefs.getString("session_reset_timestamp", "") ?: "",
            weeklyResetTimestamp = widgetPrefs.getString("weekly_reset_timestamp", "") ?: "",
            planName = widgetPrefs.getString("plan_name", "Unknown") ?: "Unknown"
        )
    }

    fun cacheUsage(data: UsageData) {
        widgetPrefs.edit()
            .putFloat("session_percent", data.sessionPercentUsed.toFloat())
            .putFloat("weekly_percent", data.weeklyPercentUsed.toFloat())
            .putString("session_reset_timestamp", data.sessionResetTimestamp)
            .putString("weekly_reset_timestamp", data.weeklyResetTimestamp)
            .putString("plan_name", data.planName)
            .putLong("last_updated_millis", System.currentTimeMillis())
            .putBoolean("auth_expired", false)
            .apply()
    }

    // ── Reset timestamp tracking ────────────────────────

    fun getLastKnownResetTimestamp(key: String): String? {
        return widgetPrefs.getString(key, null)
    }

    fun saveLastKnownResetTimestamp(key: String, value: String) {
        widgetPrefs.edit().putString(key, value).apply()
    }

    // ── Auth management ─────────────────────────────────

    fun isLoggedIn(): Boolean = secureStorage.isLoggedIn()

    fun clearAuth() {
        secureStorage.clearAll()
        widgetPrefs.edit().clear().apply()
    }

    fun removeAccount(accountId: String) {
        secureStorage.removeAccount(accountId)
    }
}
