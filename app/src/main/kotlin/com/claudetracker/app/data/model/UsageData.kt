package com.claudetracker.app.data.model

import org.json.JSONObject

/**
 * Represents a single usage window (e.g. 5-hour session or weekly).
 */
data class UsageWindow(
    val percentUsed: Double,
    val resetsAt: String
)

/**
 * Usage data for any platform.
 *
 * For Claude & Codex: [sessionPercentUsed] / [weeklyPercentUsed] are the primary values.
 */
data class UsageData(
    val sessionPercentUsed: Double,
    val weeklyPercentUsed: Double,
    val sessionResetTimestamp: String,
    val weeklyResetTimestamp: String,
    val planName: String
) {
    companion object {
        /**
         * Parse Claude API response JSON into [UsageData].
         */
        fun fromClaudeJson(json: JSONObject, planName: String): UsageData {
            android.util.Log.d("UsageData", "Parsing Claude usage JSON keys: ${json.keys().asSequence().toList()}")

            val fiveHour = json.optJSONObject("five_hour")
            val messageLimit = json.optJSONObject("message_limit")
            val sessionWindow = fiveHour ?: messageLimit

            val sessionPercent = sessionWindow?.optDouble("utilization", 0.0) ?: 0.0
            val sessionReset = sessionWindow?.optString("resets_at", "") ?: ""

            val sevenDay = json.optJSONObject("seven_day")
            val weeklyPercent = sevenDay?.optDouble("utilization", 0.0) ?: 0.0
            val weeklyReset = sevenDay?.optString("resets_at", "") ?: ""

            return UsageData(
                sessionPercentUsed = toPercent(sessionPercent),
                weeklyPercentUsed = toPercent(weeklyPercent),
                sessionResetTimestamp = sessionReset,
                weeklyResetTimestamp = weeklyReset,
                planName = planName
            )
        }

        /**
         * Convert a utilization value to a percentage (0-100).
         * The Claude API may return either a fraction (0.0–1.0) or a
         * percentage (0–100). If > 1.0, assume it's already a percentage.
         */
        private fun toPercent(value: Double): Double {
            return if (value > 1.0) value else value * 100
        }

        /**
         * Parse Codex (ChatGPT) usage response.
         * Expected shape:
         * ```json
         * {
         *   "plan_type": "Plus",
         *   "rate_limit": {
         *     "primary_window": { "used_percent": 15, "reset_after_seconds": 9180 },
         *     "secondary_window": { "used_percent": 5, "reset_after_seconds": 582800 }
         *   }
         * }
         * ```
         */
        fun fromCodexJson(json: JSONObject): UsageData {
            android.util.Log.d("UsageData", "Parsing Codex usage JSON: ${json.toString().take(500)}")

            val planType = json.optString("plan_type", "Plus")
            val rateLimit = json.optJSONObject("rate_limit")

            val primary = rateLimit?.optJSONObject("primary_window")
            val secondary = rateLimit?.optJSONObject("secondary_window")

            val sessionPercent = primary?.optDouble("used_percent", 0.0) ?: 0.0
            val sessionResetSecs = primary?.optLong("reset_after_seconds", 0L) ?: 0L
            val weeklyPercent = secondary?.optDouble("used_percent", 0.0) ?: 0.0
            val weeklyResetSecs = secondary?.optLong("reset_after_seconds", 0L) ?: 0L

            val now = System.currentTimeMillis()
            // Round to the nearest minute to prevent jitter — each refresh recalculates
            // "now + remaining_seconds" which would give a different result every time.
            // Rounding means two refreshes 15 min apart both saying "resets in 2h"
            // produce the same timestamp, preventing spurious notifications.
            val sessionResetMs = now + sessionResetSecs * 1000
            val weeklyResetMs = now + weeklyResetSecs * 1000
            val roundedSessionMs = (sessionResetMs / 60_000L) * 60_000L
            val roundedWeeklyMs = (weeklyResetMs / 60_000L) * 60_000L
            val sessionResetIso = java.time.Instant.ofEpochMilli(roundedSessionMs).toString()
            val weeklyResetIso = java.time.Instant.ofEpochMilli(roundedWeeklyMs).toString()

            return UsageData(
                sessionPercentUsed = sessionPercent,
                weeklyPercentUsed = weeklyPercent,
                sessionResetTimestamp = sessionResetIso,
                weeklyResetTimestamp = weeklyResetIso,
                planName = planType
            )
        }

        /**
         * Legacy factory — delegates to [fromClaudeJson].
         */
        fun fromJson(json: JSONObject, planName: String): UsageData = fromClaudeJson(json, planName)
    }
}
