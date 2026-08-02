package com.claudetracker.app.worker

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.claudetracker.app.ClaudeTrackerApp
import com.claudetracker.app.Config
import com.claudetracker.app.UsageGlanceWidget
import com.claudetracker.app.data.model.UsageResult
import com.claudetracker.app.notification.NotificationHelper

class UsageRefreshWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? ClaudeTrackerApp ?: return Result.failure()
        val repository = app.usageRepository
        val notificationHelper = NotificationHelper(applicationContext)

        return when (val result = repository.fetchUsage()) {
            is UsageResult.Success -> {
                val data = result.data
                repository.cacheUsage(data)

                // Reset detection: compare session reset timestamp against last known
                val lastSessionReset = repository.getLastKnownResetTimestamp("last_session_reset")
                if (lastSessionReset != null && lastSessionReset != data.sessionResetTimestamp) {
                    notificationHelper.showResetNotification("session", data)
                }
                repository.saveLastKnownResetTimestamp("last_session_reset", data.sessionResetTimestamp)

                // Reset detection: compare weekly reset timestamp against last known
                val lastWeeklyReset = repository.getLastKnownResetTimestamp("last_weekly_reset")
                if (lastWeeklyReset != null && lastWeeklyReset != data.weeklyResetTimestamp) {
                    notificationHelper.showResetNotification("weekly", data)
                }
                repository.saveLastKnownResetTimestamp("last_weekly_reset", data.weeklyResetTimestamp)

                // Update all widget instances
                updateWidgets()

                Result.success()
            }
            is UsageResult.AuthExpired -> {
                val prefs = applicationContext.getSharedPreferences(
                    Config.WIDGET_PREFS_NAME, Context.MODE_PRIVATE
                )
                prefs.edit().putBoolean("auth_expired", true).apply()
                updateWidgets()
                // Return success — don't let WorkManager retry-storm an expired cookie
                Result.success()
            }
            is UsageResult.NetworkError -> {
                Result.retry()
            }
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
            // Widget may not be placed on home screen — that's fine
        }
    }
}
