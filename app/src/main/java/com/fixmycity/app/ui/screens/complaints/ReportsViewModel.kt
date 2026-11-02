package com.fixmycity.app.ui.screens.complaints

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fixmycity.app.data.model.Report
import com.fixmycity.app.data.model.ReportStatus
import com.fixmycity.app.data.repository.FixMyCityRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ReportsUiState(
    val allReports: List<Report> = emptyList(),
    val searchQuery: String = "",
    val selectedFilter: String = "All",
    val filteredReports: List<Report> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false
)

class ReportsViewModel(
    private val repository: FixMyCityRepository = FixMyCityRepository.getInstance()
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReportsUiState())
    val uiState: StateFlow<ReportsUiState> = _uiState.asStateFlow()

    init {
        // Keep local state in sync, but only show the current user's own reports
        viewModelScope.launch {
            repository.currentUser.collect { user ->
                val myReports = repository.reports.value.filter { it.userId == user.id }
                val current = _uiState.value.copy(allReports = myReports)
                _uiState.value = current.copy(filteredReports = applyFilters(current))
            }
        }
        viewModelScope.launch {
            repository.reports.collect { list ->
                val userId = repository.currentUser.value.id
                val myReports = list.filter { it.userId == userId }
                val current = _uiState.value.copy(allReports = myReports)
                _uiState.value = current.copy(filteredReports = applyFilters(current))
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true)
            // Fetch only this user's reports from the backend
            val myReports = repository.fetchMyReports()
            val current = _uiState.value.copy(allReports = myReports, isRefreshing = false)
            _uiState.value = current.copy(filteredReports = applyFilters(current))
        }
    }

    fun updateSearch(query: String) {
        val current = _uiState.value.copy(searchQuery = query)
        _uiState.value = current.copy(filteredReports = applyFilters(current))
    }

    fun updateFilter(filter: String) {
        val current = _uiState.value.copy(selectedFilter = filter)
        _uiState.value = current.copy(filteredReports = applyFilters(current))
    }

    private fun applyFilters(state: ReportsUiState): List<Report> {
        val statusFilter = when (state.selectedFilter) {
            "Submitted"    -> ReportStatus.SUBMITTED
            "Under Review" -> ReportStatus.UNDER_REVIEW
            "In Progress"  -> ReportStatus.IN_PROGRESS
            "Resolved"     -> ReportStatus.RESOLVED
            "Rejected"     -> ReportStatus.REJECTED
            else           -> null
        }
        return state.allReports.filter { report ->
            val matchesFilter = statusFilter == null || report.status == statusFilter
            val matchesSearch = state.searchQuery.isBlank() ||
                report.title.contains(state.searchQuery, ignoreCase = true) ||
                report.category.contains(state.searchQuery, ignoreCase = true) ||
                report.location.contains(state.searchQuery, ignoreCase = true) ||
                report.description.contains(state.searchQuery, ignoreCase = true)
            matchesFilter && matchesSearch
        }
    }
}
