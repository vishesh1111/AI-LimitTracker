package com.claudetracker.app

import android.util.Log
import android.webkit.CookieManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.claudetracker.app.data.model.Account
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

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

    companion object {
        private const val MAX_RETRIES = 5
        private val RETRY_DELAYS = longArrayOf(2000, 3000, 4000, 5000, 6000) // increasing delays
    }

    fun onUrlChanged(url: String?) {
        Log.d("LoginViewModel", "URL: $url")
    }

    /**
     * Called by the JS URL watcher when it detects SPA (pushState) navigation
     * to an authenticated claude.ai page. This is the KEY entry point that
     * catches the post-OAuth redirect that onPageFinished misses.
     */
    fun onAuthenticatedUrlDetected(url: String) {
        Log.d("LoginViewModel", "onAuthenticatedUrlDetected: $url")
        val state = _uiState.value
        if (state is LoginState.CapturingCredentials || state is LoginState.Success || hasCaptured) return

        Log.d("LoginViewModel", "JS URL watcher caught authenticated page → auto-capturing")
        autoCaptureJob?.cancel()
        retryCount = 0
        autoCaptureJob = viewModelScope.launch {
            // Wait a moment for auth cookies to settle
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

        // Detect authenticated claude.ai page (not login/oauth/consent/signup)
        if (url.contains("claude.ai") &&
            !url.contains("/login") &&
            !url.contains("/oauth") &&
            !url.contains("/signup") &&
            !url.contains("/consent") &&
            !url.contains("/verify")) {

            Log.d("LoginViewModel", "Detected claude.ai main page → auto-capturing")
            autoCaptureJob?.cancel()
            retryCount = 0
            autoCaptureJob = viewModelScope.launch {
                // Wait for cookies and page to fully settle
                delay(RETRY_DELAYS[0])
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
            Log.e("LoginViewModel", "WebView is null!")
            _uiState.value = LoginState.Error("WebView unavailable. Please try again.")
            return
        }
        if (hasCaptured) return

        Log.d("LoginViewModel", "triggerJsCapture (attempt ${retryCount + 1}/$MAX_RETRIES)")

        // Use FULL URL to avoid relative-path issues on different pages
        val js = """
            (function() {
                console.log('[ClaudeTracker] Attempt ${retryCount + 1}: Fetching org info...');
                fetch('https://claude.ai/api/organizations', {
                    credentials: 'include',
                    headers: { 'Accept': 'application/json' }
                })
                .then(function(r) {
                    var status = r.status;
                    return r.text().then(function(body) {
                        console.log('[ClaudeTracker] Status: ' + status + ' Body: ' + body.substring(0, 80));
                        if (window.AndroidOrg) {
                            window.AndroidOrg.onResult(status, body);
                        }
                    });
                })
                .catch(function(e) {
                    console.error('[ClaudeTracker] Error: ' + e);
                    if (window.AndroidOrg) {
                        window.AndroidOrg.onResult(0, JSON.stringify({fetchError: e.toString()}));
                    }
                });
            })();
        """.trimIndent()

        wv.evaluateJavascript(js) { result ->
            Log.d("LoginViewModel", "JS eval: $result")
        }
    }

    fun onOrganizationsJson(status: Int, json: String) {
        Log.d("LoginViewModel", "onOrganizationsJson status=$status body=${json.take(300)}")

        if (_uiState.value is LoginState.Success || hasCaptured) return

        // If non-200, retry with increasing delay
        if (status != 200) {
            retryCount++
            if (retryCount < MAX_RETRIES) {
                Log.d("LoginViewModel", "Non-200 ($status), scheduling retry $retryCount in ${RETRY_DELAYS[retryCount]}ms")
                autoCaptureJob?.cancel()
                autoCaptureJob = viewModelScope.launch {
                    delay(RETRY_DELAYS[retryCount])
                    if (!hasCaptured && _uiState.value !is LoginState.Success) {
                        triggerJsCapture(lastWebView)
                    }
                }
                return
            } else {
                Log.e("LoginViewModel", "All $MAX_RETRIES retries exhausted")
                _uiState.value = LoginState.Error(
                    "Could not fetch account info after $MAX_RETRIES attempts.\n\nLast status: HTTP $status"
                )
                return
            }
        }

        // Status 200 — process the org data
        captureJob?.cancel()
        captureJob = viewModelScope.launch {
            try {
                val allCookies = CookieManager.getInstance().getCookie("https://claude.ai") ?: ""
                val sessionKeyValue = allCookies.split(";").map { it.trim() }
                    .find { it.startsWith("${Config.SESSION_COOKIE_NAME}=") }
                    ?.substringAfter("=") ?: ""

                Log.d("LoginViewModel", "Cookies: ${allCookies.length} chars, sessionKey: ${sessionKeyValue.isNotBlank()}")

                val array = try {
                    JSONArray(json)
                } catch (e: Exception) {
                    try { JSONArray().put(JSONObject(json)) }
                    catch (e2: Exception) {
                        _uiState.value = LoginState.Error("Parse error:\n${json.take(200)}")
                        return@launch
                    }
                }

                if (array.length() == 0) {
                    _uiState.value = LoginState.Error("No organizations found")
                    return@launch
                }

                val org = array.getJSONObject(0)
                Log.d("LoginViewModel", "Org keys: ${org.keys().asSequence().toList()}")

                val orgId = when {
                    org.has("uuid") -> org.getString("uuid")
                    org.has("id") -> org.getString("id")
                    else -> {
                        _uiState.value = LoginState.Error("No org ID. Keys: ${org.keys().asSequence().toList()}")
                        return@launch
                    }
                }

                val orgName = org.optString("name", "Claude Account")
                val rawType = org.optString("type", org.optString("plan_type", ""))
                val planName = parsePlanName(rawType)

                Log.d("LoginViewModel", "Saving: $orgName ($planName) orgId=$orgId")

                val account = Account(
                    orgId = orgId,
                    displayName = orgName,
                    sessionCookie = sessionKeyValue,
                    planName = planName,
                    allCookies = allCookies
                )

                val storage = ClaudeTrackerApp.appInstance.secureStorage
                storage.addAccount(account)

                if (storage.getAllAccounts().any { it.orgId == orgId }) {
                    hasCaptured = true
                    _uiState.value = LoginState.Success
                } else {
                    _uiState.value = LoginState.Error("Account was not saved — storage error")
                }

            } catch (e: Exception) {
                Log.e("LoginViewModel", "Error", e)
                _uiState.value = LoginState.Error("Error: ${e.message}")
            }
        }
    }

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
            cm.setCookie("https://claude.ai", "${Config.SESSION_COOKIE_NAME}=; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Path=/")
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
