package com.fixmycity.app.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fixmycity.app.data.model.Report
import com.fixmycity.app.data.repository.FixMyCityRepository
import com.fixmycity.app.domain.model.ImpactStats
import com.fixmycity.app.utils.DateUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class HomeUiState(
    val greeting: String = DateUtils.getGreeting(),
    val userName: String = "Citizen",
    val recentReports: List<Report> = emptyList(),
    val impactStats: ImpactStats = ImpactStats(0, 0, 0),
    val searchQuery: String = "",
    val unreadCount: Int = 0,
    val isBackendConnected: Boolean = false,
    val isRefreshing: Boolean = false
)

class HomeViewModel(
    private val repository: FixMyCityRepository = FixMyCityRepository.getInstance()
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                repository.currentUser,
                repository.reports,
                repository.impactStats,
                repository.notifications,
                repository.isBackendConnected
            ) { user, reports, stats, notifs, connected ->
                val firstName = user.name.split(" ").firstOrNull() ?: "Citizen"
                val unread = notifs.count { !it.isRead }
                _uiState.value.copy(
                    greeting = DateUtils.getGreeting(),
                    userName = firstName,
                    recentReports = reports.take(4),
                    impactStats = stats,
                    unreadCount = unread,
                    isBackendConnected = connected
                )
            }.collect { newState ->
                _uiState.value = newState
            }
        }
    }

    fun updateSearch(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true)
            repository.refreshAll()
            _uiState.value = _uiState.value.copy(isRefreshing = false)
        }
    }
}
