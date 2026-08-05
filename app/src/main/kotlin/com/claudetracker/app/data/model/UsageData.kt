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
 * For Antigravity:  the four optional [geminiSession], [geminiWeekly], [claudeGptSession],
 * [claudeGptWeekly] windows carry per-model-group data, and [hasModelGroups] is true.
 */
data class UsageData(
    val sessionPercentUsed: Double,
    val weeklyPercentUsed: Double,
    val sessionResetTimestamp: String,
    val weeklyResetTimestamp: String,
    val planName: String,
    // ── Antigravity dual model groups ──
    val geminiSession: UsageWindow? = null,
    val geminiWeekly: UsageWindow? = null,
    val claudeGptSession: UsageWindow? = null,
    val claudeGptWeekly: UsageWindow? = null,
    val hasModelGroups: Boolean = false
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
            val sessionResetIso = java.time.Instant.ofEpochMilli(now + sessionResetSecs * 1000).toString()
            val weeklyResetIso = java.time.Instant.ofEpochMilli(now + weeklyResetSecs * 1000).toString()

            return UsageData(
                sessionPercentUsed = sessionPercent,
                weeklyPercentUsed = weeklyPercent,
                sessionResetTimestamp = sessionResetIso,
                weeklyResetTimestamp = weeklyResetIso,
                planName = planType
            )
        }

        /**
         * Parse Antigravity (Google Cloud Code) usage response.
         * Groups model configs into Gemini models vs Claude/GPT models.
         * For each group, picks the model with the highest usage (lowest remainingFraction).
         */
        fun fromAntigravityJson(json: JSONObject): UsageData {
            android.util.Log.d("UsageData", "Parsing Antigravity JSON: ${json.toString().take(600)}")

            val modelConfigs = json.optJSONArray("modelConfigs")
            val weeklyModelConfigs = json.optJSONArray("weeklyModelConfigs")

            data class ModelQuota(val label: String, val modelId: String, val percentUsed: Double, val resetTime: String)

            fun parseConfigs(configs: org.json.JSONArray?): List<ModelQuota> {
                if (configs == null) return emptyList()
                return (0 until configs.length()).mapNotNull { i ->
                    val cfg = configs.optJSONObject(i) ?: return@mapNotNull null
                    val label = cfg.optString("label", "")
                    val modelObj = cfg.optJSONObject("modelOrAlias")
                    val modelId = modelObj?.optString("model", label) ?: label
                    val quota = cfg.optJSONObject("quotaInfo")
                    val remaining = quota?.optDouble("remainingFraction", 1.0) ?: 1.0
                    val resetTime = quota?.optString("resetTime", "") ?: ""
                    ModelQuota(label, modelId, (1.0 - remaining) * 100.0, resetTime)
                }
            }

            val sessionModels = parseConfigs(modelConfigs)
            val weeklyModels = parseConfigs(weeklyModelConfigs)

            fun isGemini(id: String) = id.contains("gemini", ignoreCase = true)
            fun isClaudeGpt(id: String) = id.contains("claude", ignoreCase = true) || id.contains("gpt", ignoreCase = true)

            // Find worst-case (highest usage) model per group
            val geminiSessionWorst = sessionModels.filter { isGemini(it.modelId) }.maxByOrNull { it.percentUsed }
            val geminiWeeklyWorst = weeklyModels.filter { isGemini(it.modelId) }.maxByOrNull { it.percentUsed }
            val claudeGptSessionWorst = sessionModels.filter { isClaudeGpt(it.modelId) }.maxByOrNull { it.percentUsed }
            val claudeGptWeeklyWorst = weeklyModels.filter { isClaudeGpt(it.modelId) }.maxByOrNull { it.percentUsed }

            // Primary values = Gemini worst-case (or overall if no separation)
            val primarySession = geminiSessionWorst ?: sessionModels.maxByOrNull { it.percentUsed }
            val primaryWeekly = geminiWeeklyWorst ?: weeklyModels.maxByOrNull { it.percentUsed }

            return UsageData(
                sessionPercentUsed = primarySession?.percentUsed ?: 0.0,
                weeklyPercentUsed = primaryWeekly?.percentUsed ?: 0.0,
                sessionResetTimestamp = primarySession?.resetTime ?: "",
                weeklyResetTimestamp = primaryWeekly?.resetTime ?: "",
                planName = "Antigravity",
                geminiSession = geminiSessionWorst?.let { UsageWindow(it.percentUsed, it.resetTime) },
                geminiWeekly = geminiWeeklyWorst?.let { UsageWindow(it.percentUsed, it.resetTime) },
                claudeGptSession = claudeGptSessionWorst?.let { UsageWindow(it.percentUsed, it.resetTime) },
                claudeGptWeekly = claudeGptWeeklyWorst?.let { UsageWindow(it.percentUsed, it.resetTime) },
                hasModelGroups = geminiSessionWorst != null || claudeGptSessionWorst != null
            )
        }

        /**
         * Legacy factory — delegates to [fromClaudeJson].
         */
        fun fromJson(json: JSONObject, planName: String): UsageData = fromClaudeJson(json, planName)
    }
}
