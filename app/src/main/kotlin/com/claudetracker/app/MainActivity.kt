package com.claudetracker.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.claudetracker.app.worker.UsageRefreshWorker
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Schedule periodic background refresh (respects Android's ~15 min floor)
        val workRequest = PeriodicWorkRequestBuilder<UsageRefreshWorker>(
            Config.REFRESH_INTERVAL_MINUTES,
            TimeUnit.MINUTES
        ).build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "claude_usage_refresh",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )

        setContent {
            ClaudeTrackerTheme {
                val isLoggedIn = ClaudeTrackerApp.appInstance.secureStorage.isLoggedIn()
                val startDest = if (isLoggedIn) "status" else "login"
                AppNavigation(startDestination = startDest)
            }
        }
    }
}
