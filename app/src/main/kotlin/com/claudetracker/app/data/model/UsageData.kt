package com.claudetracker.app.data.model

import org.json.JSONObject

data class UsageData(
    val sessionPercentUsed: Double,
    val weeklyPercentUsed: Double,
    val sessionResetTimestamp: String,
    val weeklyResetTimestamp: String,
    val planName: String
) {
    companion object {
        fun fromJson(json: JSONObject, planName: String): UsageData {
            android.util.Log.d("UsageData", "Parsing usage JSON keys: ${json.keys().asSequence().toList()}")
            android.util.Log.d("UsageData", "Full JSON: ${json.toString().take(600)}")

            // Try "five_hour" (5h session window) — may also be "message_limit"
            val fiveHour = json.optJSONObject("five_hour")
            val messageLimit = json.optJSONObject("message_limit")
            val sessionWindow = fiveHour ?: messageLimit

            val sessionPercent = sessionWindow?.optDouble("utilization", 0.0) ?: 0.0
            val sessionReset = sessionWindow?.optString("resets_at", "") ?: ""

            // Try "seven_day" weekly window
            val sevenDay = json.optJSONObject("seven_day")
            val weeklyPercent = sevenDay?.optDouble("utilization", 0.0) ?: 0.0
            val weeklyReset = sevenDay?.optString("resets_at", "") ?: ""

            android.util.Log.d("UsageData", "Parsed → session=${sessionPercent}% weekly=${weeklyPercent}%")

            return UsageData(
                sessionPercentUsed = sessionPercent,
                weeklyPercentUsed = weeklyPercent,
                sessionResetTimestamp = sessionReset,
                weeklyResetTimestamp = weeklyReset,
                planName = planName
            )
        }
    }
}
