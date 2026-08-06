package com.claudetracker.app.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.claudetracker.app.MainActivity
import com.claudetracker.app.data.model.Platform

class NotificationHelper(private val context: Context) {
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Usage Limit Resets",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Notifies when any platform usage limit resets"
        }
        notificationManager.createNotificationChannel(channel)
    }

    /**
     * Detect if a genuine reset has occurred.
     *
     * A real reset means the old window has expired and a new window has started.
     * We check:
     *   1. Both timestamps are valid ISO-8601 instants.
     *   2. The old reset time is in the PAST (the window actually expired).
     *   3. The new reset time is DIFFERENT enough from the old one (> 60s apart)
     *      to rule out minor jitter from recalculating "now + remaining_seconds".
     *
     * This prevents spurious notifications caused by:
     *   - Codex recalculating `now + reset_after_seconds` each refresh (always different)
     *   - Timestamps shifting by a few seconds between API calls
     */
    fun hasResetOccurred(oldTimestamp: String?, newTimestamp: String?): Boolean {
        if (oldTimestamp.isNullOrBlank() || newTimestamp.isNullOrBlank()) return false
        return try {
            val oldMs = java.time.Instant.parse(oldTimestamp).toEpochMilli()
            val newMs = java.time.Instant.parse(newTimestamp).toEpochMilli()
            val nowMs = System.currentTimeMillis()

            // The old window must have actually expired (reset time is in the past)
            val oldExpired = oldMs < nowMs
            // The new reset time must differ by more than 60 seconds to filter out jitter
            val significantlyDifferent = kotlin.math.abs(newMs - oldMs) > 60_000L

            oldExpired && significantlyDifferent
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Fire a local notification when a usage limit resets.
     * Title includes platform name and account label for clarity.
     *
     * @param platform    The platform that reset
     * @param accountLabel  Human-readable label for the account
     * @param windowType  "session" or "weekly"
     * @param modelGroup  Optional label for a usage subgroup
     * @param currentPercent  Current usage percent after reset (shown in notification body)
     */
    fun showResetNotification(
        platform: Platform,
        accountLabel: String,
        windowType: String,
        modelGroup: String? = null,
        currentPercent: Int = 0
    ) {
        // Unique notification ID based on platform + account + window
        val notificationId = (platform.name + accountLabel + windowType + (modelGroup ?: "")).hashCode()

        val groupPrefix = if (modelGroup != null) "$modelGroup " else ""
        val windowLabel = if (windowType == "session") "5-Hour" else "Weekly"

        val title = "${platform.displayName} · $accountLabel"
        val body = "${groupPrefix}${windowLabel} limit has reset! 🎉 (now ${currentPercent}% used)"

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, notificationId, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(notificationId, notification)
    }

    fun cancelAll() {
        notificationManager.cancelAll()
    }

    companion object {
        private const val CHANNEL_ID = "usage_limit_resets"
    }
}
