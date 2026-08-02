package com.claudetracker.app

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.claudetracker.app.data.model.AccountUsage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed interface StatusState {
    data object Loading : StatusState
    data class Loaded(
        val accounts: List<AccountUsage>,
        val isRefreshing: Boolean,
        val lastUpdated: String
    ) : StatusState
    data object NoAccounts : StatusState
    data class Error(val message: String) : StatusState
}

class StatusViewModel : ViewModel() {
    private val repository = ClaudeTrackerApp.appInstance.usageRepository
    private val _uiState = MutableStateFlow<StatusState>(StatusState.Loading)
    val uiState: StateFlow<StatusState> = _uiState.asStateFlow()
    private var autoRefreshJob: Job? = null

    companion object {
        private const val AUTO_REFRESH_INTERVAL_MS = 5 * 60 * 1000L // 5 minutes
    }

    init {
        refreshData()
        startAutoRefresh()
    }

    fun onScreenVisible() {
        refreshData()
        startAutoRefresh()
    }

    fun onScreenHidden() {
        autoRefreshJob?.cancel()
    }

    private fun startAutoRefresh() {
        autoRefreshJob?.cancel()
        autoRefreshJob = viewModelScope.launch {
            while (isActive) {
                delay(AUTO_REFRESH_INTERVAL_MS)
                if (isActive) refreshData()
            }
        }
    }

    fun refreshData() {
        val currentState = _uiState.value
        if (currentState is StatusState.Loaded) {
            _uiState.value = currentState.copy(isRefreshing = true)
        } else {
            _uiState.value = StatusState.Loading
        }

        viewModelScope.launch {
            try {
                val accounts = repository.secureStorage.getAllAccounts()
                Log.d("StatusViewModel", "Found ${accounts.size} accounts")
                accounts.forEach {
                    Log.d("StatusViewModel", "  Account: ${it.displayName}, plan=${it.planName}, orgId=${it.orgId}")
                }

                if (accounts.isEmpty()) {
                    Log.d("StatusViewModel", "No accounts found, setting NoAccounts state")
                    _uiState.value = StatusState.NoAccounts
                    return@launch
                }

                Log.d("StatusViewModel", "Fetching usage for all accounts...")
                val results = repository.fetchAllUsage()
                Log.d("StatusViewModel", "Got ${results.size} results")
                results.forEach { r ->
                    Log.d("StatusViewModel", "  ${r.account.displayName}: data=${r.usageData}, error=${r.error}, authExpired=${r.isAuthExpired}")
                    r.usageData?.let {
                        Log.d("StatusViewModel", "    session=${it.sessionPercentUsed}%, weekly=${it.weeklyPercentUsed}%, plan=${it.planName}")
                    }
                }

                // Cache the first account's data for the widget
                results.firstOrNull()?.usageData?.let { repository.cacheUsage(it) }

                _uiState.value = StatusState.Loaded(
                    accounts = results,
                    isRefreshing = false,
                    lastUpdated = getCurrentTime()
                )
            } catch (e: Exception) {
                Log.e("StatusViewModel", "Error in refreshData", e)
                _uiState.value = StatusState.Error("Failed to load: ${e.message}")
            }
        }
    }

    fun removeAccount(orgId: String) {
        repository.removeAccount(orgId)
        val currentState = _uiState.value
        if (currentState is StatusState.Loaded) {
            val remaining = currentState.accounts.filter { it.account.orgId != orgId }
            if (remaining.isEmpty()) {
                _uiState.value = StatusState.NoAccounts
            } else {
                _uiState.value = currentState.copy(accounts = remaining)
            }
        }
    }

    fun logoutAll() {
        autoRefreshJob?.cancel()
        repository.clearAuth()
        _uiState.value = StatusState.NoAccounts
    }

    override fun onCleared() {
        super.onCleared()
        autoRefreshJob?.cancel()
    }

    private fun getCurrentTime(): String {
        return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
    }
}
