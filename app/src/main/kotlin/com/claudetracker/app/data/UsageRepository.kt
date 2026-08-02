package com.claudetracker.app.data

import android.content.Context
import com.claudetracker.app.Config
import com.claudetracker.app.data.local.SecureStorage
import com.claudetracker.app.data.model.Account
import com.claudetracker.app.data.model.AccountUsage
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

    suspend fun fetchAllUsage(): List<AccountUsage> = coroutineScope {
        val accounts = secureStorage.getAllAccounts()
        if (accounts.isEmpty()) return@coroutineScope emptyList()

        accounts.map { account ->
            async {
                when (val result = apiClient.fetchUsage(account.allCookies, account.orgId, account.planName)) {
                    is UsageResult.Success -> AccountUsage(account = account, usageData = result.data)
                    is UsageResult.AuthExpired -> AccountUsage(account = account, usageData = null, isAuthExpired = true)
                    is UsageResult.NetworkError -> AccountUsage(account = account, usageData = null, error = result.message)
                }
            }
        }.awaitAll()
    }

    // Legacy single-account fetch for the background worker
    suspend fun fetchUsage(): UsageResult {
        val account = secureStorage.getAllAccounts().firstOrNull()
            ?: return UsageResult.AuthExpired
        return apiClient.fetchUsage(account.allCookies, account.orgId, account.planName)
    }

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

    fun getLastKnownResetTimestamp(key: String): String? {
        return widgetPrefs.getString(key, null)
    }

    fun saveLastKnownResetTimestamp(key: String, value: String) {
        widgetPrefs.edit().putString(key, value).apply()
    }

    fun isLoggedIn(): Boolean = secureStorage.isLoggedIn()

    fun clearAuth() {
        secureStorage.clearAll()
        widgetPrefs.edit().clear().apply()
    }

    fun removeAccount(orgId: String) {
        secureStorage.removeAccount(orgId)
    }
}
