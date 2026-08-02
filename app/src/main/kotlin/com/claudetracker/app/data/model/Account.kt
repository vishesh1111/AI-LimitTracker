package com.claudetracker.app.data.model

import org.json.JSONObject

data class Account(
    val orgId: String,
    val displayName: String,
    val sessionCookie: String,   // The sessionKey value only (for identification)
    val planName: String,
    val allCookies: String = sessionCookie // Full cookie header for API requests
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("orgId", orgId)
            put("displayName", displayName)
            put("sessionCookie", sessionCookie)
            put("planName", planName)
            put("allCookies", allCookies)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): Account {
            val sessionCookie = json.getString("sessionCookie")
            return Account(
                orgId = json.getString("orgId"),
                displayName = json.optString("displayName", "Unknown"),
                sessionCookie = sessionCookie,
                planName = json.optString("planName", "Unknown"),
                allCookies = json.optString("allCookies", sessionCookie)
            )
        }
    }
}
