package com.claudetracker.app.worker

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.claudetracker.app.ClaudeTrackerApp
import com.claudetracker.app.Config
import com.claudetracker.app.UsageGlanceWidget
import com.claudetracker.app.data.model.AccountUsage
import com.claudetracker.app.data.model.Platform
import com.claudetracker.app.notification.NotificationHelper

class UsageRefreshWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? ClaudeTrackerApp ?: return Result.failure()
        val repository = app.usageRepository
        val notificationHelper = NotificationHelper(applicationContext)

        return try {
            val results = repository.fetchAllUsage()

            if (results.isEmpty()) {
                return Result.success()
            }

            // Process each account's result for reset detection + notifications
            for (accountUsage in results) {
                processResetDetection(accountUsage, repository, notificationHelper)
            }

            // Cache the first successful account's data for the widget
            results.firstOrNull { it.usageData != null }?.usageData?.let {
                repository.cacheUsage(it)
            }

            // Mark auth_expired if ALL accounts are expired
            val allExpired = results.all { it.isAuthExpired }
            if (allExpired) {
                val prefs = applicationContext.getSharedPreferences(
                    Config.WIDGET_PREFS_NAME, Context.MODE_PRIVATE
                )
                prefs.edit().putBoolean("auth_expired", true).apply()
            }

            // Update all widget instances
            updateWidgets()

            Result.success()
        } catch (e: Exception) {
            android.util.Log.e("UsageRefreshWorker", "Unhandled error", e)
            Result.retry()
        }
    }

    /**
     * Check for reset events on each account and fire notifications if needed.
     * Uses strict timestamp comparison: only fires when newTimestamp > oldTimestamp.
     */
    private fun processResetDetection(
        accountUsage: AccountUsage,
        repository: com.claudetracker.app.data.UsageRepository,
        notificationHelper: NotificationHelper
    ) {
        val data = accountUsage.usageData ?: return
        val account = accountUsage.account
        val accountId = account.id

        // ── Standard session + weekly resets (Claude, Codex, Antigravity primary) ──
        checkAndNotifyReset(
            repository, notificationHelper,
            key = "reset_${accountId}_session",
            newTimestamp = data.sessionResetTimestamp,
            platform = account.platform,
            accountLabel = account.displayName,
            windowType = "session",
            currentPercent = data.sessionPercentUsed.toInt()
        )

        checkAndNotifyReset(
            repository, notificationHelper,
            key = "reset_${accountId}_weekly",
            newTimestamp = data.weeklyResetTimestamp,
            platform = account.platform,
            accountLabel = account.displayName,
            windowType = "weekly",
            currentPercent = data.weeklyPercentUsed.toInt()
        )

        // ── Antigravity extra model group resets ──
        if (data.hasModelGroups) {
            data.geminiSession?.let {
                checkAndNotifyReset(
                    repository, notificationHelper,
                    key = "reset_${accountId}_gemini_session",
                    newTimestamp = it.resetsAt,
                    platform = account.platform,
                    accountLabel = account.displayName,
                    windowType = "session",
                    modelGroup = "Gemini",
                    currentPercent = it.percentUsed.toInt()
                )
            }
            data.geminiWeekly?.let {
                checkAndNotifyReset(
                    repository, notificationHelper,
                    key = "reset_${accountId}_gemini_weekly",
                    newTimestamp = it.resetsAt,
                    platform = account.platform,
                    accountLabel = account.displayName,
                    windowType = "weekly",
                    modelGroup = "Gemini",
                    currentPercent = it.percentUsed.toInt()
                )
            }
            data.claudeGptSession?.let {
                checkAndNotifyReset(
                    repository, notificationHelper,
                    key = "reset_${accountId}_claudegpt_session",
                    newTimestamp = it.resetsAt,
                    platform = account.platform,
                    accountLabel = account.displayName,
                    windowType = "session",
                    modelGroup = "Claude/GPT",
                    currentPercent = it.percentUsed.toInt()
                )
            }
            data.claudeGptWeekly?.let {
                checkAndNotifyReset(
                    repository, notificationHelper,
                    key = "reset_${accountId}_claudegpt_weekly",
                    newTimestamp = it.resetsAt,
                    platform = account.platform,
                    accountLabel = account.displayName,
                    windowType = "weekly",
                    modelGroup = "Claude/GPT",
                    currentPercent = it.percentUsed.toInt()
                )
            }
        }
    }

    private fun checkAndNotifyReset(
        repository: com.claudetracker.app.data.UsageRepository,
        notificationHelper: NotificationHelper,
        key: String,
        newTimestamp: String,
        platform: Platform,
        accountLabel: String,
        windowType: String,
        modelGroup: String? = null,
        currentPercent: Int = 0
    ) {
        val oldTimestamp = repository.getLastKnownResetTimestamp(key)

        if (notificationHelper.hasResetOccurred(oldTimestamp, newTimestamp)) {
            notificationHelper.showResetNotification(
                platform = platform,
                accountLabel = accountLabel,
                windowType = windowType,
                modelGroup = modelGroup,
                currentPercent = currentPercent
            )
        }

        // Always save the current timestamp
        if (newTimestamp.isNotBlank()) {
            repository.saveLastKnownResetTimestamp(key, newTimestamp)
        }
    }

    private suspend fun updateWidgets() {
        try {
            val manager = GlanceAppWidgetManager(applicationContext)
            val widget = UsageGlanceWidget()
            val glanceIds = manager.getGlanceIds(widget.javaClass)
            for (id in glanceIds) {
                widget.update(applicationContext, id)
            }
        } catch (_: Exception) {
            // Widget may not be placed on home screen
        }
    }
}
