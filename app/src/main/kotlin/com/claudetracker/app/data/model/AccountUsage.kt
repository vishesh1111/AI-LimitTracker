package com.claudetracker.app.data.model

data class AccountUsage(
    val account: Account,
    val usageData: UsageData?,
    val error: String? = null,
    val isAuthExpired: Boolean = false
)
