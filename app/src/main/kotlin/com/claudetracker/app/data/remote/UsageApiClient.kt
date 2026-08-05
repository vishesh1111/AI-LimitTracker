package com.claudetracker.app.data.remote

import com.claudetracker.app.Config
import com.claudetracker.app.data.model.UsageData
import com.claudetracker.app.data.model.UsageResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

data class OrgInfo(
    val orgId: String,
    val orgName: String,
    val planName: String
)

class UsageApiClient(private val client: OkHttpClient) {

    // ═══════════════════════════════════════════════════
    // ── Claude API ─────────────────────────────────────
    // ═══════════════════════════════════════════════════

    private fun buildClaudeRequest(url: String, fullCookies: String): Request {
        return Request.Builder()
            .url(url)
            .addHeader("Cookie", fullCookies)
            .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36")
            .addHeader("Accept", "application/json")
            .addHeader("Referer", "https://claude.ai/")
            .addHeader("Origin", "https://claude.ai")
            .addHeader("anthropic-client-version", "claude.ai")
            .build()
    }

    suspend fun fetchClaudeUsage(cookie: String, orgId: String, planName: String): UsageResult = withContext(Dispatchers.IO) {
        val url = Config.CLAUDE_USAGE_ENDPOINT_TEMPLATE.replace("{org_id}", orgId)
        android.util.Log.d("UsageApiClient", "fetchClaudeUsage url=$url")
        val request = buildClaudeRequest(url, cookie)

        try {
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""
            android.util.Log.d("UsageApiClient", "Claude code=${response.code} body=${body.take(400)}")

            when {
                response.code in 400..403 -> UsageResult.AuthExpired
                !response.isSuccessful -> UsageResult.NetworkError("HTTP ${response.code}")
                body.isBlank() -> UsageResult.NetworkError("Empty response")
                else -> {
                    val json = JSONObject(body)
                    UsageResult.Success(UsageData.fromClaudeJson(json, planName))
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("UsageApiClient", "fetchClaudeUsage error", e)
            UsageResult.NetworkError(e.message ?: "Unknown error")
        }
    }

    /** Legacy name — delegates to [fetchClaudeUsage]. */
    suspend fun fetchUsage(cookie: String, orgId: String, planName: String): UsageResult =
        fetchClaudeUsage(cookie, orgId, planName)

    suspend fun fetchOrganizationInfo(cookie: String): Result<OrgInfo> = withContext(Dispatchers.IO) {
        val request = buildClaudeRequest("https://claude.ai/api/organizations", cookie)
        try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(java.io.IOException("HTTP ${response.code}"))
            }
            val body = response.body?.string() ?: return@withContext Result.failure(java.io.IOException("Empty body"))
            android.util.Log.d("UsageApiClient", "Orgs response: ${body.take(500)}")
            val array = JSONArray(body)
            if (array.length() > 0) {
                val org = array.getJSONObject(0)
                val orgId = when {
                    org.has("uuid") -> org.getString("uuid")
                    org.has("id") -> org.getString("id")
                    else -> return@withContext Result.failure(Exception("No org ID field found"))
                }
                val orgName = org.optString("name", "Claude Account")
                val orgType = when {
                    org.has("type") -> org.optString("type", "")
                    org.has("plan_type") -> org.optString("plan_type", "")
                    org.has("billing_type") -> org.optString("billing_type", "")
                    else -> ""
                }
                val planName = when {
                    orgType.contains("free", ignoreCase = true) -> "Free"
                    orgType.contains("pro", ignoreCase = true) -> "Pro"
                    orgType.contains("max", ignoreCase = true) -> "Max"
                    orgType.contains("team", ignoreCase = true) -> "Team"
                    orgType.contains("enterprise", ignoreCase = true) -> "Enterprise"
                    orgType.isBlank() -> "Unknown"
                    else -> orgType.replaceFirstChar { it.uppercase() }
                }
                Result.success(OrgInfo(orgId, orgName, planName))
            } else {
                Result.failure(Exception("No organizations found"))
            }
        } catch (e: Exception) {
            android.util.Log.e("UsageApiClient", "fetchOrganizationInfo failed", e)
            Result.failure(e)
        }
    }

    // ═══════════════════════════════════════════════════
    // ── Codex (ChatGPT) API ───────────────────────────
    // ═══════════════════════════════════════════════════

    /**
     * Exchange the __Secure-next-auth.session-token cookie for a JWT access token.
     */
    suspend fun fetchCodexAccessToken(sessionCookie: String): Result<String> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(Config.CODEX_SESSION_ENDPOINT)
            .addHeader("Cookie", "${Config.CODEX_SESSION_COOKIE_NAME}=$sessionCookie")
            .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36")
            .addHeader("Accept", "application/json")
            .build()

        try {
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""
            android.util.Log.d("UsageApiClient", "Codex session code=${response.code} body=${body.take(200)}")

            if (!response.isSuccessful) {
                return@withContext Result.failure(java.io.IOException("HTTP ${response.code}"))
            }
            val json = JSONObject(body)
            val accessToken = json.optString("accessToken", "")
            if (accessToken.isBlank()) {
                return@withContext Result.failure(Exception("No accessToken in session response"))
            }
            Result.success(accessToken)
        } catch (e: Exception) {
            android.util.Log.e("UsageApiClient", "fetchCodexAccessToken error", e)
            Result.failure(e)
        }
    }

    /**
     * Fetch Codex usage using the JWT access token.
     */
    suspend fun fetchCodexUsage(accessToken: String): UsageResult = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(Config.CODEX_USAGE_ENDPOINT)
            .addHeader("Authorization", "Bearer $accessToken")
            .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36")
            .addHeader("Accept", "application/json")
            .build()

        try {
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""
            android.util.Log.d("UsageApiClient", "Codex usage code=${response.code} body=${body.take(400)}")

            when {
                response.code in 401..403 -> UsageResult.AuthExpired
                !response.isSuccessful -> UsageResult.NetworkError("HTTP ${response.code}")
                body.isBlank() -> UsageResult.NetworkError("Empty response")
                else -> {
                    val json = JSONObject(body)
                    UsageResult.Success(UsageData.fromCodexJson(json))
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("UsageApiClient", "fetchCodexUsage error", e)
            UsageResult.NetworkError(e.message ?: "Unknown error")
        }
    }

    // ═══════════════════════════════════════════════════
    // ── Antigravity (Google) API ──────────────────────
    // ═══════════════════════════════════════════════════

    /**
     * Refresh the Google access token using a refresh token.
     * Returns (accessToken, expiryIsoString).
     */
    suspend fun refreshAntigravityToken(refreshToken: String): Result<Pair<String, String>> = withContext(Dispatchers.IO) {
        val formBody = FormBody.Builder()
            .add("grant_type", "refresh_token")
            .add("refresh_token", refreshToken)
            .add("client_id", Config.AGY_CLIENT_ID)
            .build()

        val request = Request.Builder()
            .url(Config.AGY_TOKEN_ENDPOINT)
            .post(formBody)
            .build()

        try {
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""
            android.util.Log.d("UsageApiClient", "AGY token refresh code=${response.code} body=${body.take(200)}")

            if (!response.isSuccessful) {
                return@withContext Result.failure(java.io.IOException("Token refresh failed: HTTP ${response.code}"))
            }
            val json = JSONObject(body)
            val accessToken = json.optString("access_token", "")
            val expiresIn = json.optLong("expires_in", 3600)
            if (accessToken.isBlank()) {
                return@withContext Result.failure(Exception("No access_token in response"))
            }
            val expiryIso = java.time.Instant.ofEpochMilli(
                System.currentTimeMillis() + expiresIn * 1000
            ).toString()
            Result.success(accessToken to expiryIso)
        } catch (e: Exception) {
            android.util.Log.e("UsageApiClient", "refreshAntigravityToken error", e)
            Result.failure(e)
        }
    }

    /**
     * Exchange an OAuth authorization code for access + refresh tokens.
     * Returns Triple(accessToken, refreshToken, expiryIsoString).
     */
    suspend fun exchangeAntigravityCode(code: String): Result<Triple<String, String, String>> = withContext(Dispatchers.IO) {
        val formBody = FormBody.Builder()
            .add("grant_type", "authorization_code")
            .add("code", code)
            .add("client_id", Config.AGY_CLIENT_ID)
            .add("client_secret", Config.AGY_CLIENT_SECRET)
            .add("redirect_uri", Config.AGY_REDIRECT_URI)
            .build()

        val request = Request.Builder()
            .url(Config.AGY_TOKEN_ENDPOINT)
            .post(formBody)
            .build()

        try {
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""
            android.util.Log.d("UsageApiClient", "AGY code exchange code=${response.code} body=${body.take(200)}")

            if (!response.isSuccessful) {
                return@withContext Result.failure(java.io.IOException("Code exchange failed: HTTP ${response.code}\n${body.take(200)}"))
            }
            val json = JSONObject(body)
            val accessToken = json.optString("access_token", "")
            val refreshToken = json.optString("refresh_token", "")
            val expiresIn = json.optLong("expires_in", 3600)

            if (accessToken.isBlank()) {
                return@withContext Result.failure(Exception("No access_token in response"))
            }
            if (refreshToken.isBlank()) {
                return@withContext Result.failure(Exception("No refresh_token in response. Make sure prompt=consent is set."))
            }

            val expiryIso = java.time.Instant.ofEpochMilli(
                System.currentTimeMillis() + expiresIn * 1000
            ).toString()

            Result.success(Triple(accessToken, refreshToken, expiryIso))
        } catch (e: Exception) {
            android.util.Log.e("UsageApiClient", "exchangeAntigravityCode error", e)
            Result.failure(e)
        }
    }

    /**
     * Fetch Antigravity usage using the access token.
     * Calls the Cloud Code GetUserStatus endpoint.
     */
    suspend fun fetchAntigravityUsage(accessToken: String): UsageResult = withContext(Dispatchers.IO) {
        val requestJson = JSONObject().apply {
            put("metadata", JSONObject().apply {
                put("ideName", "antigravity")
                put("extensionName", "antigravity")
                put("locale", "en")
            })
        }
        val jsonBody = requestJson.toString().toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url(Config.AGY_USAGE_ENDPOINT)
            .post(jsonBody)
            .addHeader("Authorization", "Bearer $accessToken")
            .addHeader("Content-Type", "application/json")
            .addHeader("Connect-Protocol-Version", "1")
            .build()

        try {
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""
            android.util.Log.d("UsageApiClient", "AGY usage code=${response.code} body=${body.take(600)}")

            when {
                response.code in 401..403 -> UsageResult.AuthExpired
                !response.isSuccessful -> UsageResult.NetworkError("HTTP ${response.code}")
                body.isBlank() -> UsageResult.NetworkError("Empty response")
                else -> {
                    val json = JSONObject(body)
                    UsageResult.Success(UsageData.fromAntigravityJson(json))
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("UsageApiClient", "fetchAntigravityUsage error", e)
            UsageResult.NetworkError(e.message ?: "Unknown error")
        }
    }
}
