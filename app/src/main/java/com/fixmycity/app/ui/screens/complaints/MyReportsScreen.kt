package com.fixmycity.app.ui.screens.complaints

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fixmycity.app.ui.components.*
import com.fixmycity.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyReportsScreen(
    onNavigateToDetail: (String) -> Unit,
    viewModel: ReportsViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val filters = listOf("All", "Submitted", "Under Review", "In Progress", "Resolved", "Rejected")

    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("My Reports", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(
                            "Track your submitted issues",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh reports")
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

            // ── Your Impact Stats ─────────────────────────────────────────────
            item {
                Text(
                    text = "Your Impact",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp)
                )
            }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatCard(
                        value = state.allReports.size.toString(),
                        label = "Submitted",
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        value = state.allReports.count {
                            it.status.name in listOf("IN_PROGRESS", "UNDER_REVIEW", "ASSIGNED")
                        }.toString(),
                        label = "In Progress",
                        modifier = Modifier.weight(1f),
                        valueColor = Warning
                    )
                    StatCard(
                        value = state.allReports.count { it.status.name == "RESOLVED" }.toString(),
                        label = "Resolved",
                        modifier = Modifier.weight(1f),
                        valueColor = Success
                    )
                }
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
            }

            // Search
            item {
                SearchBar(
                    query = state.searchQuery,
                    onQueryChange = viewModel::updateSearch,
                    placeholder = "Search reports...",
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                )
            }

            // Filter Chips
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

            if (state.filteredReports.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(top = 60.dp)) {
                        EmptyState(
                            icon = Icons.Default.List,
                            title = "No Reports Found",
                            description = if (state.searchQuery.isNotBlank() || state.selectedFilter != "All")
                                "Try adjusting your search or filter"
                            else
                                "You haven't submitted any reports yet. Tap the Report tab to get started!",
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
