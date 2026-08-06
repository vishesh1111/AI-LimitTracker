package com.claudetracker.app

object Config {
    // ── Claude ──────────────────────────────────────────
    const val CLAUDE_USAGE_ENDPOINT_TEMPLATE = "https://claude.ai/api/organizations/{org_id}/usage"
    const val CLAUDE_SESSION_COOKIE_NAME = "sessionKey"

    // ── Codex (ChatGPT) ────────────────────────────────
    const val CODEX_SESSION_ENDPOINT = "https://chatgpt.com/api/auth/session"
    const val CODEX_USAGE_ENDPOINT = "https://chatgpt.com/backend-api/wham/usage"
    const val CODEX_SESSION_COOKIE_NAME = "__Secure-next-auth.session-token"

    // ── General ────────────────────────────────────────
    const val WIDGET_PREFS_NAME = "claude_tracker_widget_data"
    const val REFRESH_INTERVAL_MINUTES = 15L

    // ── Legacy aliases (keep existing references working) ──
    const val USAGE_ENDPOINT_TEMPLATE = CLAUDE_USAGE_ENDPOINT_TEMPLATE
    const val SESSION_COOKIE_NAME = CLAUDE_SESSION_COOKIE_NAME
}
