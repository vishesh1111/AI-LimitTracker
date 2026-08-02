package com.claudetracker.app.data.model

sealed interface UsageResult {
    data class Success(val data: UsageData) : UsageResult
    object AuthExpired : UsageResult
    data class NetworkError(val message: String) : UsageResult
}
