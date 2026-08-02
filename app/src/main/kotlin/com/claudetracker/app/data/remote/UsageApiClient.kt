package com.claudetracker.app.data.remote

import com.claudetracker.app.Config
import com.claudetracker.app.data.model.UsageData
import com.claudetracker.app.data.model.UsageResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

data class OrgInfo(
    val orgId: String,
    val orgName: String,
    val planName: String
)

class UsageApiClient(private val client: OkHttpClient) {

    private fun buildRequest(url: String, fullCookies: String): Request {
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

    suspend fun fetchUsage(cookie: String, orgId: String, planName: String): UsageResult = withContext(Dispatchers.IO) {
        val url = Config.USAGE_ENDPOINT_TEMPLATE.replace("{org_id}", orgId)
        android.util.Log.d("UsageApiClient", "fetchUsage url=$url cookie_len=${cookie.length}")
        val request = buildRequest(url, cookie)

        try {
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""
            android.util.Log.d("UsageApiClient", "fetchUsage code=${response.code} body=${body.take(400)}")

            when {
                response.code in 400..403 -> UsageResult.AuthExpired
                !response.isSuccessful -> UsageResult.NetworkError("HTTP ${response.code}")
                body.isBlank() -> UsageResult.NetworkError("Empty response")
                else -> {
                    val json = JSONObject(body)
                    UsageResult.Success(UsageData.fromJson(json, planName))
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("UsageApiClient", "fetchUsage error", e)
            UsageResult.NetworkError(e.message ?: "Unknown error")
        }
    }

    suspend fun fetchOrganizationInfo(cookie: String): Result<OrgInfo> = withContext(Dispatchers.IO) {
        val request = buildRequest("https://claude.ai/api/organizations", cookie)
        try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(IOException("HTTP ${response.code}"))
            }
            val body = response.body?.string() ?: return@withContext Result.failure(IOException("Empty body"))
            android.util.Log.d("UsageApiClient", "Orgs response: ${body.take(500)}")
            val array = JSONArray(body)
            if (array.length() > 0) {
                val org = array.getJSONObject(0)
                android.util.Log.d("UsageApiClient", "First org keys: ${org.keys().asSequence().toList()}")
                // Try both 'uuid' and 'id' — the field name varies
                val orgId = when {
                    org.has("uuid") -> org.getString("uuid")
                    org.has("id") -> org.getString("id")
                    else -> return@withContext Result.failure(Exception("No org ID field found. Keys: ${org.keys().asSequence().toList()}"))
                }
                val orgName = org.optString("name", "Claude Account")
                // Try multiple fields for plan type
                val orgType = when {
                    org.has("type") -> org.optString("type", "")
                    org.has("plan_type") -> org.optString("plan_type", "")
                    org.has("billing_type") -> org.optString("billing_type", "")
                    else -> ""
                }
                android.util.Log.d("UsageApiClient", "orgId=$orgId, orgName=$orgName, orgType=$orgType")
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
                Result.failure(Exception("No organizations found in response"))
            }
        } catch (e: Exception) {
            android.util.Log.e("UsageApiClient", "fetchOrganizationInfo failed", e)
            Result.failure(e)
        }
    }
}
