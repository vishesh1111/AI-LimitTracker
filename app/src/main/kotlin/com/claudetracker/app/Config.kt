package com.claudetracker.app

object Config {
    // ── Claude ──────────────────────────────────────────
    const val CLAUDE_USAGE_ENDPOINT_TEMPLATE = "https://claude.ai/api/organizations/{org_id}/usage"
    const val CLAUDE_SESSION_COOKIE_NAME = "sessionKey"

    // ── Codex (ChatGPT) ────────────────────────────────
    const val CODEX_SESSION_ENDPOINT = "https://chatgpt.com/api/auth/session"
    const val CODEX_USAGE_ENDPOINT = "https://chatgpt.com/backend-api/wham/usage"
    const val CODEX_SESSION_COOKIE_NAME = "__Secure-next-auth.session-token"

    // ── Antigravity (Google) ───────────────────────────
    const val AGY_TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token"
    const val AGY_USAGE_ENDPOINT = "https://cloudcode-pa.googleapis.com/exa.language_server_pb.LanguageServerService/GetUserStatus"
    const val AGY_CLIENT_ID = "764086051850-6qr4p6gpi6hn506pt8ejuq83di341hur.apps.googleusercontent.com"
    // This is the public client_secret for gcloud's installed-app client — it's intentionally
    // public and ships inside the gcloud CLI binary (not a security concern).
    const val AGY_CLIENT_SECRET = "d-FL95Q19q7MQmFpd7hHD0Ty"
    const val AGY_REDIRECT_URI = "http://127.0.0.1"

    // ── General ────────────────────────────────────────
    const val WIDGET_PREFS_NAME = "claude_tracker_widget_data"
    const val REFRESH_INTERVAL_MINUTES = 15L

    // ── Legacy aliases (keep existing references working) ──
    const val USAGE_ENDPOINT_TEMPLATE = CLAUDE_USAGE_ENDPOINT_TEMPLATE
    const val SESSION_COOKIE_NAME = CLAUDE_SESSION_COOKIE_NAME
}
