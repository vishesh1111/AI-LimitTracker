package com.claudetracker.app

import android.util.Log
import android.webkit.CookieManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.claudetracker.app.data.model.Account
import com.claudetracker.app.data.model.Platform
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.webkit.JavascriptInterface
import org.json.JSONArray
import org.json.JSONObject

private class CodexJsInterface(private val viewModel: LoginViewModel) {
    @JavascriptInterface
    fun onResult(status: Int, json: String) {
        Log.d("CodexJsInterface", "onResult status=$status json=${json.take(100)}")
        viewModel.onCodexSessionJson(status, json)
    }
}

sealed interface LoginState {
    data object Loading : LoginState
    data object WaitingForLogin : LoginState
    data object CapturingCredentials : LoginState
    data object Success : LoginState
    data class Error(val message: String) : LoginState
}

class LoginViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<LoginState>(LoginState.WaitingForLogin)
    val uiState: StateFlow<LoginState> = _uiState.asStateFlow()
    private var captureJob: Job? = null
    private var autoCaptureJob: Job? = null
    private var hasCaptured = false
    private var retryCount = 0
    private var lastWebView: android.webkit.WebView? = null

    /** The platform being logged into — set before navigating to the login screen. */
    var targetPlatform: Platform = Platform.CLAUDE

    companion object {
        private const val MAX_RETRIES = 5
        private val RETRY_DELAYS = longArrayOf(2000, 3000, 4000, 5000, 6000)
    }

    fun onUrlChanged(url: String?) {
        Log.d("LoginViewModel", "URL: $url")
    }

    // ═══════════════════════════════════════════════════
    // ── Claude login flow ─────────────────────────────
    // ═══════════════════════════════════════════════════

    fun onAuthenticatedUrlDetected(url: String) {
        Log.d("LoginViewModel", "onAuthenticatedUrlDetected: $url")
        val state = _uiState.value
        if (state is LoginState.CapturingCredentials || state is LoginState.Success || hasCaptured) return

        autoCaptureJob?.cancel()
        retryCount = 0
        autoCaptureJob = viewModelScope.launch {
            delay(1500)
            if (!hasCaptured && _uiState.value !is LoginState.Success) {
                _uiState.value = LoginState.CapturingCredentials
                triggerJsCapture(lastWebView)
            }
        }
    }

    fun onPageFinished(url: String?, webView: android.webkit.WebView?) {
        if (url == null || webView == null) return
        Log.d("LoginViewModel", "onPageFinished: $url  state=${_uiState.value}")

        val state = _uiState.value
        if (state is LoginState.CapturingCredentials || state is LoginState.Success || hasCaptured) return

        lastWebView = webView

        when (targetPlatform) {
            Platform.CLAUDE -> handleClaudePageFinished(url, webView)
            Platform.CODEX -> handleCodexPageFinished(url, webView)
            Platform.ANTIGRAVITY -> { /* Antigravity uses manual token input, not WebView */ }
        }
    }

    private fun handleClaudePageFinished(url: String, webView: android.webkit.WebView) {
        if (url.contains("claude.ai") &&
            !url.contains("/login") &&
            !url.contains("/oauth") &&
            !url.contains("/signup") &&
            !url.contains("/consent") &&
            !url.contains("/verify")) {

            autoCaptureJob?.cancel()
            retryCount = 0
            autoCaptureJob = viewModelScope.launch {
                delay(4000) // Extra time for cookies to settle after Google OAuth
                if (!hasCaptured && _uiState.value !is LoginState.Success) {
                    _uiState.value = LoginState.CapturingCredentials
                    triggerJsCapture(webView)
                }
            }
        }
    }

    fun triggerJsCapture(webView: android.webkit.WebView?) {
        val wv = webView ?: lastWebView
        if (wv == null) {
            _uiState.value = LoginState.Error("WebView unavailable. Please try again.")
            return
        }
        if (hasCaptured) return

        val js = """
            (function() {
                fetch('https://claude.ai/api/organizations', {
                    credentials: 'include',
                    headers: { 'Accept': 'application/json' }
                })
                .then(function(r) {
                    var status = r.status;
                    return r.text().then(function(body) {
                        if (window.AndroidOrg) {
                            window.AndroidOrg.onResult(status, body);
                        }
                    });
                })
                .catch(function(e) {
                    if (window.AndroidOrg) {
                        window.AndroidOrg.onResult(0, JSON.stringify({fetchError: e.toString()}));
                    }
                });
            })();
        """.trimIndent()

        wv.evaluateJavascript(js) { /* no-op */ }
    }

    fun onOrganizationsJson(status: Int, json: String) {
        if (_uiState.value is LoginState.Success || hasCaptured) return

        if (status != 200) {
            retryCount++
            if (retryCount < MAX_RETRIES) {
                autoCaptureJob?.cancel()
                autoCaptureJob = viewModelScope.launch {
                    delay(RETRY_DELAYS[retryCount])
                    if (!hasCaptured && _uiState.value !is LoginState.Success) {
                        triggerJsCapture(lastWebView)
                    }
                }
                return
            } else {
                _uiState.value = LoginState.Error("Could not fetch account info after $MAX_RETRIES attempts.\n\nLast status: HTTP $status")
                return
            }
        }

        captureJob?.cancel()
        captureJob = viewModelScope.launch {
            try {
                val allCookies = CookieManager.getInstance().getCookie("https://claude.ai") ?: ""
                val sessionKeyValue = allCookies.split(";").map { it.trim() }
                    .find { it.startsWith("${Config.CLAUDE_SESSION_COOKIE_NAME}=") }
                    ?.substringAfter("=") ?: ""

                val array = try {
                    JSONArray(json)
                } catch (_: Exception) {
                    try { JSONArray().put(JSONObject(json)) }
                    catch (_: Exception) {
                        _uiState.value = LoginState.Error("Parse error:\n${json.take(200)}")
                        return@launch
                    }
                }

                if (array.length() == 0) {
                    _uiState.value = LoginState.Error("No organizations found")
                    return@launch
                }

                val org = array.getJSONObject(0)
                val orgId = when {
                    org.has("uuid") -> org.getString("uuid")
                    org.has("id") -> org.getString("id")
                    else -> {
                        _uiState.value = LoginState.Error("No org ID found")
                        return@launch
                    }
                }

                val orgName = org.optString("name", "Claude Account")
                val rawType = org.optString("type", org.optString("plan_type", ""))
                val planName = parsePlanName(rawType)

                val account = Account(
                    id = orgId,
                    platform = Platform.CLAUDE,
                    orgId = orgId,
                    displayName = orgName,
                    sessionCookie = sessionKeyValue,
                    planName = planName,
                    allCookies = allCookies
                )

                val storage = ClaudeTrackerApp.appInstance.secureStorage
                storage.addAccount(account)

                if (storage.getAllAccounts().any { it.id == orgId }) {
                    hasCaptured = true
                    _uiState.value = LoginState.Success
                } else {
                    _uiState.value = LoginState.Error("Account was not saved — storage error")
                }
            } catch (e: Exception) {
                _uiState.value = LoginState.Error("Error: ${e.message}")
            }
        }
    }

    // ═══════════════════════════════════════════════════
    // ── Codex (ChatGPT) login flow ────────────────────
    // ═══════════════════════════════════════════════════

    private fun handleCodexPageFinished(url: String, webView: android.webkit.WebView) {
        // Detect authenticated ChatGPT page
        if (url.contains("chatgpt.com") &&
            !url.contains("/auth") &&
            !url.contains("/login")) {

            Log.d("LoginViewModel", "Detected chatgpt.com main page → capturing session cookie")
            autoCaptureJob?.cancel()
            retryCount = 0
            autoCaptureJob = viewModelScope.launch {
                delay(3000) // Extra time for cookies to settle after OAuth redirect
                if (!hasCaptured && _uiState.value !is LoginState.Success) {
                    _uiState.value = LoginState.CapturingCredentials
                    captureCodexCookie(webView)
                }
            }
        }
    }

    private suspend fun captureCodexCookie(webView: android.webkit.WebView? = null) {
        try {
            // Try getting cookies from multiple possible domains
            val cookieDomains = listOf(
                "https://chatgpt.com",
                "https://chat.openai.com",
                "https://auth0.openai.com"
            )

            var allCookies = ""
            for (domain in cookieDomains) {
                val c = CookieManager.getInstance().getCookie(domain) ?: ""
                if (c.isNotBlank()) {
                    allCookies = if (allCookies.isEmpty()) c else "$allCookies; $c"
                }
            }

            Log.d("LoginViewModel", "Codex all cookies (${allCookies.length} chars): ${allCookies.take(300)}")

            val cookieParts = allCookies.split(";").map { it.trim() }

            // Strategy 1: Look for the single un-chunked token
            var sessionCookie = cookieParts
                .find { it.startsWith("${Config.CODEX_SESSION_COOKIE_NAME}=") }
                ?.substringAfter("=") ?: ""

            // Strategy 2: Reassemble chunked tokens (.0, .1, .2, ...)
            if (sessionCookie.isBlank()) {
                val chunks = cookieParts
                    .filter { it.startsWith("${Config.CODEX_SESSION_COOKIE_NAME}.") }
                    .sortedBy {
                        it.substringAfter("${Config.CODEX_SESSION_COOKIE_NAME}.")
                            .substringBefore("=")
                            .toIntOrNull() ?: 0
                    }
                    .map { it.substringAfter("=") }

                if (chunks.isNotEmpty()) {
                    sessionCookie = chunks.joinToString("")
                    Log.d("LoginViewModel", "Reassembled ${chunks.size} chunked cookie parts")
                }
            }

            // Strategy 3: Use JS to fetch session endpoint directly from WebView context
            if (sessionCookie.isBlank() && webView != null) {
                Log.d("LoginViewModel", "Cookie extraction failed, trying JS fetch fallback...")
                // Inject JS to call the session API from the WebView's cookie context
                val js = """
                    (function() {
                        fetch('/api/auth/session', { credentials: 'include' })
                            .then(r => r.json())
                            .then(data => {
                                if (data.accessToken) {
                                    window.AndroidOrg.onResult(200, JSON.stringify(data));
                                } else {
                                    window.AndroidOrg.onResult(0, JSON.stringify({error: 'no accessToken'}));
                                }
                            })
                            .catch(e => window.AndroidOrg.onResult(0, JSON.stringify({error: e.toString()})));
                    })();
                """.trimIndent()

                // Add JS interface if not already present for Codex
                try {
                    webView.addJavascriptInterface(CodexJsInterface(this), "AndroidOrg")
                } catch (_: Exception) { /* may already exist */ }

                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    webView.evaluateJavascript(js) { /* no-op */ }
                }
                // The JS callback will handle the rest
                return
            }

            if (sessionCookie.isBlank()) {
                // Retry with increasing delay
                retryCount++
                if (retryCount < MAX_RETRIES) {
                    Log.d("LoginViewModel", "No cookie found, retrying (${retryCount}/$MAX_RETRIES)...")
                    _uiState.value = LoginState.WaitingForLogin
                    autoCaptureJob?.cancel()
                    autoCaptureJob = viewModelScope.launch {
                        delay(RETRY_DELAYS[retryCount])
                        if (!hasCaptured && _uiState.value !is LoginState.Success) {
                            _uiState.value = LoginState.CapturingCredentials
                            captureCodexCookie(webView)
                        }
                    }
                    return
                }
                _uiState.value = LoginState.Error("Could not find session cookie after $MAX_RETRIES attempts.\n\nAvailable cookies: ${allCookies.take(200)}")
                return
            }

            // Got the cookie — exchange for access token
            val apiClient = ClaudeTrackerApp.appInstance.usageApiClient
            val tokenResult = apiClient.fetchCodexAccessToken(sessionCookie)
            val accessToken = tokenResult.getOrElse {
                _uiState.value = LoginState.Error("Could not exchange session token: ${it.message}")
                return
            }

            saveCodexAccount(sessionCookie, accessToken)
        } catch (e: Exception) {
            Log.e("LoginViewModel", "captureCodexCookie error", e)
            _uiState.value = LoginState.Error("Error: ${e.message}")
        }
    }

    /**
     * Called by JS fallback when it gets the session API response directly.
     */
    fun onCodexSessionJson(status: Int, json: String) {
        if (hasCaptured || _uiState.value is LoginState.Success) return
        Log.d("LoginViewModel", "onCodexSessionJson status=$status json=${json.take(200)}")

        if (status != 200) {
            _uiState.value = LoginState.Error("Could not fetch ChatGPT session: $json")
            return
        }

        captureJob?.cancel()
        captureJob = viewModelScope.launch {
            try {
                val obj = JSONObject(json)
                val accessToken = obj.optString("accessToken", "")
                if (accessToken.isBlank()) {
                    _uiState.value = LoginState.Error("No access token in session response")
                    return@launch
                }

                // We got the access token directly — no need for the session cookie exchange
                saveCodexAccount("direct_js_auth", accessToken)
            } catch (e: Exception) {
                _uiState.value = LoginState.Error("Parse error: ${e.message}")
            }
        }
    }

    private fun saveCodexAccount(sessionCookie: String, accessToken: String) {
        val accountId = "codex_${System.currentTimeMillis().toString(36)}"
        val account = Account(
            id = accountId,
            platform = Platform.CODEX,
            displayName = "ChatGPT Account",
            planName = "Plus",
            codexSessionCookie = sessionCookie,
            codexAccessToken = accessToken
        )

        ClaudeTrackerApp.appInstance.secureStorage.addAccount(account)
        hasCaptured = true
        _uiState.value = LoginState.Success
    }

    // ═══════════════════════════════════════════════════
    // ── Antigravity login flow (Google OAuth WebView) ─
    // ═══════════════════════════════════════════════════

    /**
     * Build the Google OAuth URL using gcloud's installed-app client.
     * Uses the OOB (out-of-band) redirect which shows the auth code
     * on a Google page after the user approves — no localhost redirect needed.
     */
    fun getAntigravityOAuthUrl(): String {
        val scopes = listOf(
            "openid",
            "https://www.googleapis.com/auth/userinfo.email",
            "https://www.googleapis.com/auth/cloud-platform"
        ).joinToString(" ")

        return "https://accounts.google.com/o/oauth2/v2/auth?" +
            "client_id=${Config.AGY_CLIENT_ID}" +
            "&redirect_uri=${java.net.URLEncoder.encode(Config.AGY_REDIRECT_URI, "UTF-8")}" +
            "&response_type=code" +
            "&scope=${java.net.URLEncoder.encode(scopes, "UTF-8")}" +
            "&access_type=offline" +
            "&prompt=consent"
    }

    /**
     * Called when the OAuth WebView's page title or body contains the auth code.
     * Google's OOB flow shows the code on the final page as the page title.
     */
    fun onAntigravityAuthCode(code: String) {
        if (hasCaptured || _uiState.value is LoginState.Success) return
        Log.d("LoginViewModel", "Got Antigravity auth code: ${code.take(20)}...")

        _uiState.value = LoginState.CapturingCredentials
        captureJob?.cancel()
        captureJob = viewModelScope.launch {
            try {
                val apiClient = ClaudeTrackerApp.appInstance.usageApiClient
                val tokenResult = apiClient.exchangeAntigravityCode(code)
                val triple = tokenResult.getOrElse {
                    _uiState.value = LoginState.Error("Token exchange failed: ${it.message}")
                    return@launch
                }
                val (accessToken, refreshToken, expiry) = triple
                val email = fetchGoogleEmail(accessToken) ?: "Google Account"

                val accountId = "agy_${System.currentTimeMillis().toString(36)}"
                val account = Account(
                    id = accountId,
                    platform = Platform.ANTIGRAVITY,
                    displayName = email,
                    planName = "Antigravity",
                    agyRefreshToken = refreshToken,
                    agyAccessToken = accessToken,
                    agyAccessTokenExpiry = expiry
                )
                ClaudeTrackerApp.appInstance.secureStorage.addAccount(account)
                hasCaptured = true
                _uiState.value = LoginState.Success
            } catch (e: Exception) {
                Log.e("LoginViewModel", "Antigravity auth error", e)
                _uiState.value = LoginState.Error("Error: ${e.message}")
            }
        }
    }

    /** Kept for backward-compat; not used in the new flow. */
    fun onAntigravityImplicitToken(accessToken: String, expiresIn: Long) {
        onAntigravityAuthCode("__implicit__$accessToken")
    }

    /**
     * Also keep manual token paste as a fallback.
     */
    fun submitAntigravityRefreshToken(refreshToken: String) {
        if (refreshToken.isBlank()) {
            _uiState.value = LoginState.Error("Refresh token is empty")
            return
        }

        _uiState.value = LoginState.CapturingCredentials
        captureJob?.cancel()
        captureJob = viewModelScope.launch {
            try {
                val apiClient = ClaudeTrackerApp.appInstance.usageApiClient
                val tokenResult = apiClient.refreshAntigravityToken(refreshToken)
                val (accessToken, expiry) = tokenResult.getOrElse {
                    _uiState.value = LoginState.Error("Invalid refresh token: ${it.message}")
                    return@launch
                }

                val accountId = "agy_${System.currentTimeMillis().toString(36)}"
                val account = Account(
                    id = accountId,
                    platform = Platform.ANTIGRAVITY,
                    displayName = "Antigravity",
                    planName = "Antigravity",
                    agyRefreshToken = refreshToken,
                    agyAccessToken = accessToken,
                    agyAccessTokenExpiry = expiry
                )

                ClaudeTrackerApp.appInstance.secureStorage.addAccount(account)
                hasCaptured = true
                _uiState.value = LoginState.Success
            } catch (e: Exception) {
                _uiState.value = LoginState.Error("Error: ${e.message}")
            }
        }
    }

    private suspend fun fetchGoogleEmail(accessToken: String): String? {
        return withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val request = okhttp3.Request.Builder()
                    .url("https://www.googleapis.com/oauth2/v2/userinfo")
                    .addHeader("Authorization", "Bearer $accessToken")
                    .build()
                val response = ClaudeTrackerApp.appInstance.okHttpClient.newCall(request).execute()
                val body = response.body?.string() ?: return@withContext null
                JSONObject(body).optString("email", null)
            } catch (_: Exception) { null }
        }
    }

    // ═══════════════════════════════════════════════════
    // ── Helpers ───────────────────────────────────────
    // ═══════════════════════════════════════════════════

    private fun parsePlanName(raw: String): String = when {
        raw.isBlank() || raw.equals("null", true) || raw.equals("unknown", true) -> "Unknown"
        raw.contains("free", true) -> "Free"
        raw.contains("pro", true) -> "Pro"
        raw.contains("max", true) -> "Max"
        raw.contains("team", true) -> "Team"
        raw.contains("enterprise", true) -> "Enterprise"
        else -> raw.replaceFirstChar { it.uppercase() }
    }

    fun clearWebViewCookies(clearAll: Boolean = false) {
        val cm = CookieManager.getInstance()
        if (clearAll) {
            cm.removeAllCookies(null)
        } else {
            cm.setCookie("https://claude.ai", "${Config.CLAUDE_SESSION_COOKIE_NAME}=; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Path=/")
        }
        cm.flush()
    }

    fun resetState() {
        captureJob?.cancel()
        autoCaptureJob?.cancel()
        hasCaptured = false
        retryCount = 0
        _uiState.value = LoginState.WaitingForLogin
    }

    override fun onCleared() {
        super.onCleared()
        captureJob?.cancel()
        autoCaptureJob?.cancel()
    }
}
