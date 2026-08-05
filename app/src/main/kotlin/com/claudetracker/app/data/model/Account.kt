package com.claudetracker.app.data.model

import org.json.JSONObject

/** Supported platforms in the tracker. */
enum class Platform {
    CLAUDE, CODEX, ANTIGRAVITY;

    val displayName: String
        get() = when (this) {
            CLAUDE -> "Claude"
            CODEX -> "Codex (ChatGPT)"
            ANTIGRAVITY -> "Antigravity"
        }
}

/**
 * Unified account model that stores credentials for any platform.
 * Only the fields relevant to the account's [platform] will be populated.
 */
data class Account(
    val id: String,                     // Unique identifier (orgId for Claude, generated for others)
    val platform: Platform = Platform.CLAUDE,
    val displayName: String,
    val planName: String,
    // ── Claude-specific ──
    val sessionCookie: String = "",     // sessionKey value only
    val allCookies: String = sessionCookie, // Full cookie header for API requests
    val orgId: String = "",
    // ── Codex-specific ──
    val codexSessionCookie: String = "", // __Secure-next-auth.session-token
    val codexAccessToken: String = "",   // JWT bearer token
    // ── Antigravity-specific ──
    val agyRefreshToken: String = "",
    val agyAccessToken: String = "",
    val agyAccessTokenExpiry: String = ""
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("platform", platform.name)
            put("orgId", orgId)
            put("displayName", displayName)
            put("sessionCookie", sessionCookie)
            put("planName", planName)
            put("allCookies", allCookies)
            put("codexSessionCookie", codexSessionCookie)
            put("codexAccessToken", codexAccessToken)
            put("agyRefreshToken", agyRefreshToken)
            put("agyAccessToken", agyAccessToken)
            put("agyAccessTokenExpiry", agyAccessTokenExpiry)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): Account {
            val platformStr = json.optString("platform", "CLAUDE")
            val platform = try {
                Platform.valueOf(platformStr)
            } catch (_: Exception) {
                Platform.CLAUDE
            }

            val sessionCookie = json.optString("sessionCookie", "")
            // Legacy accounts don't have "id" — fall back to orgId
            val id = json.optString("id", json.optString("orgId", ""))

            return Account(
                id = id,
                platform = platform,
                displayName = json.optString("displayName", "Unknown"),
                sessionCookie = sessionCookie,
                planName = json.optString("planName", "Unknown"),
                allCookies = json.optString("allCookies", sessionCookie),
                orgId = json.optString("orgId", ""),
                codexSessionCookie = json.optString("codexSessionCookie", ""),
                codexAccessToken = json.optString("codexAccessToken", ""),
                agyRefreshToken = json.optString("agyRefreshToken", ""),
                agyAccessToken = json.optString("agyAccessToken", ""),
                agyAccessTokenExpiry = json.optString("agyAccessTokenExpiry", "")
            )
        }
    }
}
