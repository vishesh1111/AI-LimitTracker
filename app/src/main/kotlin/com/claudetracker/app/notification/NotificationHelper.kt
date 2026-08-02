package com.claudetracker.app.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.claudetracker.app.MainActivity
import com.claudetracker.app.data.model.UsageData

class NotificationHelper(private val context: Context) {
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Claude Usage Resets",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Notifies when your Claude usage limit resets"
        }
        notificationManager.createNotificationChannel(channel)
    }

    fun showResetNotification(windowType: String, usageData: UsageData) {
        val notificationId = if (windowType == "session") NOTIFICATION_ID_SESSION else NOTIFICATION_ID_WEEKLY
        val title = if (windowType == "session") {
            "Session limit has reset! 🎉"
        } else {
            "Weekly limit has reset! 🎉"
        }
        val text = "Session: ${usageData.sessionPercentUsed.toInt()}% used · " +
                "Weekly: ${usageData.weeklyPercentUsed.toInt()}% used"

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
            .setContentText(text)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(notificationId, notification)
    }

    fun cancelAll() {
        notificationManager.cancelAll()
    }

    companion object {
        private const val CHANNEL_ID = "claude_usage_resets"
        private const val NOTIFICATION_ID_SESSION = 1001
        private const val NOTIFICATION_ID_WEEKLY = 1002
    }
}
