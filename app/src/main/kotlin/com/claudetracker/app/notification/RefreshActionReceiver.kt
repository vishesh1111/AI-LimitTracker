package com.claudetracker.app.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.claudetracker.app.worker.UsageRefreshWorker

class RefreshActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val request = OneTimeWorkRequestBuilder<UsageRefreshWorker>().build()
        WorkManager.getInstance(context).enqueue(request)
    }
}
