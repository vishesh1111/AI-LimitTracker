package com.claudetracker.app

object Config {
    const val USAGE_ENDPOINT_TEMPLATE = "https://claude.ai/api/organizations/{org_id}/usage"
    const val SESSION_COOKIE_NAME = "sessionKey"
    const val WIDGET_PREFS_NAME = "claude_tracker_widget_data"
    const val REFRESH_INTERVAL_MINUTES = 15L
}
