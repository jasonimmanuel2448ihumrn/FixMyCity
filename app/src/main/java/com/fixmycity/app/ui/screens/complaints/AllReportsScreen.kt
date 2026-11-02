package com.fixmycity.app.ui.screens.complaints

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fixmycity.app.data.model.Report
import com.fixmycity.app.data.model.ReportStatus
import com.fixmycity.app.data.repository.FixMyCityRepository
import com.fixmycity.app.ui.components.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// ── ViewModel ─────────────────────────────────────────────────────────────────
data class AllReportsUiState(
    val allReports: List<Report> = emptyList(),
    val filteredReports: List<Report> = emptyList(),
    val searchQuery: String = "",
    val selectedFilter: String = "All",
    val isLoading: Boolean = false
)

class AllReportsViewModel(
    private val repository: FixMyCityRepository = FixMyCityRepository.getInstance()
) : ViewModel() {

    private val _uiState = MutableStateFlow(AllReportsUiState())
    val uiState: StateFlow<AllReportsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.reports.collect { list ->
                val current = _uiState.value.copy(allReports = list)
                _uiState.value = current.copy(filteredReports = applyFilters(current))
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            repository.fetchReports()
            _uiState.value = _uiState.value.copy(isLoading = false)
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

    private fun applyFilters(state: AllReportsUiState): List<Report> {
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
                report.userName.contains(state.searchQuery, ignoreCase = true)
            matchesFilter && matchesSearch
        }
    }
}

// ── Screen ────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllReportsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToDetail: (String) -> Unit,
    viewModel: AllReportsViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val filters = listOf("All", "Submitted", "Under Review", "In Progress", "Resolved")

    LaunchedEffect(Unit) { viewModel.refresh() }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                        Text("All Reports", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(
                            "${state.allReports.size} community reports",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            // Search
            item {
                SearchBar(
                    query = state.searchQuery,
                    onQueryChange = viewModel::updateSearch,
                    placeholder = "Search by title, category, location or person...",
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                )
            }

            // Filter chips
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filters) { filter ->
                        CategoryChip(
                            text = filter,
                            isSelected = state.selectedFilter == filter,
                            onClick = { viewModel.updateFilter(filter) }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            // Count
            item {
                Text(
                    "${state.filteredReports.size} report${if (state.filteredReports.size != 1) "s" else ""}",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                )
            }

            if (state.isLoading && state.filteredReports.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(top = 80.dp),
                        contentAlignment = androidx.compose.ui.Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            } else if (state.filteredReports.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(top = 60.dp)) {
                        EmptyState(
                            icon = Icons.Default.List,
                            title = "No Reports Found",
                            description = if (state.searchQuery.isNotBlank() || state.selectedFilter != "All")
                                "Try adjusting your search or filter"
                            else
                                "No community reports yet. Be the first to report an issue!",
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            } else {
                items(state.filteredReports, key = { it.id }) { report ->
                    ReportCard(
                        report = report,
                        onClick = { onNavigateToDetail(report.id) },
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}
