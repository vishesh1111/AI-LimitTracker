package com.claudetracker.app

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.Button
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class UsageGlanceWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme {
                WidgetContent(context)
            }
        }
    }
}

@Composable
private fun WidgetContent(context: Context) {
    val prefs = context.getSharedPreferences(Config.WIDGET_PREFS_NAME, Context.MODE_PRIVATE)
    val hasData = prefs.contains("session_percent")
    val isAuthExpired = prefs.getBoolean("auth_expired", false)

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.background)
            .padding(14.dp)
            .clickable(actionStartActivity(Intent(context, MainActivity::class.java))),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.Top
    ) {
        // Header
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "⚡ Claude Tracker",
                style = TextStyle(
                    color = GlanceTheme.colors.primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                ),
                modifier = GlanceModifier.defaultWeight()
            )
            if (hasData && !isAuthExpired) {
                val planName = prefs.getString("plan_name", "") ?: ""
                if (planName.isNotBlank() && planName != "Unknown") {
                    Text(
                        text = "[$planName]",
                        style = TextStyle(
                            color = GlanceTheme.colors.primary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    )
                }
            }
        }

        Spacer(modifier = GlanceModifier.height(10.dp))

        if (!hasData) {
            // No data — prompt to open app
            Text(
                text = "Tap to open app & log in",
                style = TextStyle(
                    color = GlanceTheme.colors.onBackground,
                    fontSize = 13.sp
                )
            )
        } else if (isAuthExpired) {
            // Auth expired
            Text(
                text = "⚠ Session expired",
                style = TextStyle(
                    color = GlanceTheme.colors.error,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            )
            Spacer(modifier = GlanceModifier.height(4.dp))
            Text(
                text = "Tap to re-login",
                style = TextStyle(
                    color = GlanceTheme.colors.onBackground,
                    fontSize = 12.sp
                )
            )
        } else {
            // Show usage data
            val sessionPercent = prefs.getFloat("session_percent", 0f)
            val weeklyPercent = prefs.getFloat("weekly_percent", 0f)
            val lastUpdatedMillis = prefs.getLong("last_updated_millis", 0L)

            // Session usage
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Session (5h)",
                    style = TextStyle(
                        color = GlanceTheme.colors.onBackground,
                        fontSize = 12.sp
                    ),
                    modifier = GlanceModifier.defaultWeight()
                )
                Text(
                    text = "${formatPercent(sessionPercent)}  ${getStatusEmoji(sessionPercent)}",
                    style = TextStyle(
                        color = GlanceTheme.colors.onBackground,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                )
            }

            // Visual bar for session
            Text(
                text = buildBar(sessionPercent),
                style = TextStyle(fontSize = 10.sp, color = GlanceTheme.colors.onBackground)
            )

            Spacer(modifier = GlanceModifier.height(8.dp))

            // Weekly usage
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Weekly",
                    style = TextStyle(
                        color = GlanceTheme.colors.onBackground,
                        fontSize = 12.sp
                    ),
                    modifier = GlanceModifier.defaultWeight()
                )
                Text(
                    text = "${formatPercent(weeklyPercent)}  ${getStatusEmoji(weeklyPercent)}",
                    style = TextStyle(
                        color = GlanceTheme.colors.onBackground,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                )
            }

            // Visual bar for weekly
            Text(
                text = buildBar(weeklyPercent),
                style = TextStyle(fontSize = 10.sp, color = GlanceTheme.colors.onBackground)
            )

            Spacer(modifier = GlanceModifier.height(10.dp))

            // Footer
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val lastUpdatedStr = if (lastUpdatedMillis > 0) {
                    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(lastUpdatedMillis))
                } else "—"
                Text(
                    text = "Updated $lastUpdatedStr",
                    style = TextStyle(
                        color = GlanceTheme.colors.onBackground,
                        fontSize = 11.sp
                    ),
                    modifier = GlanceModifier.defaultWeight()
                )
                Button(
                    text = "↻ Refresh",
                    onClick = actionRunCallback<RefreshActionCallback>()
                )
            }
        }
    }
}

/** Build a text-based progress bar: ████████░░░░ */
private fun buildBar(percent: Float): String {
    val total = 20
    val filled = ((percent / 100f) * total).toInt().coerceIn(0, total)
    val empty = total - filled
    return "█".repeat(filled) + "░".repeat(empty)
}

/** Format percent nicely */
private fun formatPercent(percent: Float): String {
    return if (percent == percent.toInt().toFloat()) {
        "${percent.toInt()}%"
    } else {
        "${"%.1f".format(percent)}%"
    }
}

/** Color-coded status emoji */
private fun getStatusEmoji(percent: Float): String = when {
    percent >= 80f -> "🔴"
    percent >= 50f -> "🟡"
    else -> "🟢"
}

class RefreshActionCallback : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val intent = Intent().apply {
            action = "com.claudetracker.app.ACTION_REFRESH"
            setPackage(context.packageName)
        }
        context.sendBroadcast(intent)
    }
}
