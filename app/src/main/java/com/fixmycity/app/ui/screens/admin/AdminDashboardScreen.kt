package com.fixmycity.app.ui.screens.admin

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.fixmycity.app.data.mock.MockData
import com.fixmycity.app.data.model.AppNotification
import com.fixmycity.app.data.model.NotificationType
import com.fixmycity.app.data.model.Report
import com.fixmycity.app.data.model.ReportStatus
import com.fixmycity.app.data.model.User
import com.fixmycity.app.data.repository.FixMyCityRepository
import com.fixmycity.app.data.repository.isDummyReport
import com.fixmycity.app.data.repository.isDummyUser
import com.fixmycity.app.ui.components.AppButton
import com.fixmycity.app.ui.components.StatusBadge
import com.fixmycity.app.ui.screens.report.MapLayerType
import com.fixmycity.app.ui.screens.report.MapTilesGrid
import com.fixmycity.app.ui.theme.*
import com.fixmycity.app.utils.ServerConfig
import kotlinx.coroutines.launch
import kotlin.math.*

enum class AdminTab(val title: String) {
    REPORTS("Reports"),
    CITIZENS("Citizens"),
    ALERTS("Alerts"),
    ANALYTICS("Analytics")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardScreen(
    onLogout: () -> Unit,
    onNavigateToCitizenView: () -> Unit,
    repository: FixMyCityRepository = FixMyCityRepository.getInstance()
) {
    val currentUser by repository.currentUser.collectAsState()
    val reports by repository.reports.collectAsState()
    val notifications by repository.notifications.collectAsState()
    val impactStats by repository.impactStats.collectAsState()

    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var selectedTab by remember { mutableStateOf(AdminTab.REPORTS) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedStatusFilter by remember { mutableStateOf("All") }
    var selectedCategoryFilter by remember { mutableStateOf("All") }

    // Users list
    var usersList by remember { mutableStateOf<List<User>>(emptyList()) }
    var isLoadingUsers by remember { mutableStateOf(false) }

    // Status update modal state
    var reportToUpdate by remember { mutableStateOf<Report?>(null) }
    var newStatusSelection by remember { mutableStateOf(ReportStatus.UNDER_REVIEW) }
    var statusNote by remember { mutableStateOf("") }
    var isSavingStatus by remember { mutableStateOf(false) }

    // Delete confirmation
    var reportToDelete by remember { mutableStateOf<Report?>(null) }

    LaunchedEffect(Unit) {
        repository.refreshAll()
        isLoadingUsers = true
        usersList = repository.fetchAllUsers()
        isLoadingUsers = false
    }

    // Access control: only users with role "Municipal" are allowed
    val isMunicipal = currentUser.role.equals("Municipal", ignoreCase = true)
    if (!isMunicipal) {
        Scaffold { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(Error.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = Error, modifier = Modifier.size(32.dp))
                        }
                        Spacer(Modifier.height(16.dp))
                        Text("Access Restricted", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "This portal is exclusively accessible to authorized Municipal Corporation personnel.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(Modifier.height(20.dp))
                        AppButton(
                            text = "Back to Citizen Home",
                            onClick = onNavigateToCitizenView,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
        return
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.AccountBalance, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Municipal Portal", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = Primary.copy(alpha = 0.15f)
                                ) {
                                    Text("ADMIN", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Primary, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                                }
                            }
                            Text("City Corporation Operations", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                actions = {
                    IconButton(onClick = {
                        scope.launch {
                            repository.refreshAll()
                            usersList = repository.fetchAllUsers()
                            snackbarHostState.showSnackbar("Data refreshed from database")
                        }
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(onClick = onNavigateToCitizenView) {
                        Icon(Icons.Default.RemoveRedEye, contentDescription = "Citizen View", tint = Primary)
                    }
                    IconButton(onClick = onLogout) {
                        Icon(Icons.Default.Logout, contentDescription = "Logout", tint = Error)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Navigation Tabs
            ScrollableTabRow(
                selectedTabIndex = selectedTab.ordinal,
                edgePadding = 16.dp,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = Primary
            ) {
                AdminTab.values().forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        text = {
                            Text(
                                tab.title,
                                fontWeight = if (selectedTab == tab) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 14.sp
                            )
                        },
                        icon = {
                            Icon(
                                when (tab) {
                                    AdminTab.REPORTS -> Icons.Default.Assignment
                                    AdminTab.CITIZENS -> Icons.Default.Group
                                    AdminTab.ALERTS -> Icons.Default.Notifications
                                    AdminTab.ANALYTICS -> Icons.Default.BarChart
                                },
                                contentDescription = tab.title,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )
                }
            }

            val realReports = remember(reports) { reports.filterNot { it.isDummyReport() } }
            val realUsers = remember(usersList) { usersList.filterNot { it.isDummyUser() } }

            // Tab Content
            Box(modifier = Modifier.weight(1f)) {
                when (selectedTab) {
                    AdminTab.REPORTS -> {
                        ReportsManagementTab(
                            reports = realReports,
                            searchQuery = searchQuery,
                            onSearchChange = { searchQuery = it },
                            statusFilter = selectedStatusFilter,
                            onStatusFilterChange = { selectedStatusFilter = it },
                            categoryFilter = selectedCategoryFilter,
                            onCategoryFilterChange = { selectedCategoryFilter = it },
                            onOpenStatusModal = { r ->
                                reportToUpdate = r
                                newStatusSelection = r.status
                                statusNote = ""
                            },
                            onDeleteReport = { r ->
                                reportToDelete = r
                            }
                        )
                    }
                    AdminTab.CITIZENS -> {
                        CitizensTab(users = realUsers, isLoading = isLoadingUsers)
                    }
                    AdminTab.ALERTS -> {
                        AlertsTab(
                            reports = realReports,
                            onOpenReport = { r ->
                                reportToUpdate = r
                                newStatusSelection = r.status
                                statusNote = ""
                            }
                        )
                    }
                    AdminTab.ANALYTICS -> {
                        AnalyticsTab(
                            reports = realReports,
                            stats = impactStats,
                            totalUsers = realUsers.size
                        )
                    }
                }
            }
        }
    }

    // Status Update Dialog
    reportToUpdate?.let { targetReport ->
        AlertDialog(
            onDismissRequest = { if (!isSavingStatus) reportToUpdate = null },
            title = {
                Text("Update Issue Status", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "${targetReport.title} (#${targetReport.id.uppercase()})",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Text("Select New Status:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)

                    // Status radio group
                    val statuses = listOf(
                        ReportStatus.SUBMITTED to "Submitted",
                        ReportStatus.UNDER_REVIEW to "Under Review",
                        ReportStatus.ASSIGNED to "Assigned to Crew",
                        ReportStatus.IN_PROGRESS to "In Progress",
                        ReportStatus.RESOLVED to "Resolved / Closed",
                        ReportStatus.REJECTED to "Rejected / Invalid"
                    )

                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        statuses.forEach { (st, label) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { newStatusSelection = st }
                                    .padding(vertical = 4.dp, horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = newStatusSelection == st,
                                    onClick = { newStatusSelection = st }
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(label, fontSize = 13.sp)
                            }
                        }
                    }

                    OutlinedTextField(
                        value = statusNote,
                        onValueChange = { statusNote = it },
                        placeholder = { Text("e.g. Repair crew dispatched with materials", fontSize = 12.sp) },
                        label = { Text("Official Action Note (Optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            },
            confirmButton = {
                AppButton(
                    text = "Save & Notify Citizen",
                    onClick = {
                        scope.launch {
                            isSavingStatus = true
                            val result = repository.updateReportStatus(
                                reportId = targetReport.id,
                                newStatus = newStatusSelection,
                                note = statusNote.takeIf { it.isNotBlank() }
                            )
                            isSavingStatus = false
                            reportToUpdate = null
                            if (result.isSuccess) {
                                snackbarHostState.showSnackbar("Report #${targetReport.id.uppercase()} updated to ${newStatusSelection.name.replace('_', ' ')}")
                            } else {
                                snackbarHostState.showSnackbar("Failed to update status")
                            }
                        }
                    },
                    isLoading = isSavingStatus
                )
            },
            dismissButton = {
                TextButton(onClick = { reportToUpdate = null }, enabled = !isSavingStatus) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete Confirmation Dialog
    reportToDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { reportToDelete = null },
            title = { Text("Delete Report?") },
            text = { Text("Are you sure you want to permanently delete report #${target.id.uppercase()} (${target.title})?") },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            repository.deleteReport(target.id)
                            reportToDelete = null
                            snackbarHostState.showSnackbar("Report deleted")
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Error)
                ) {
                    Text("Delete", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { reportToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun AdminStatsGrid(
    totalReports: Int,
    inProgress: Int,
    resolved: Int,
    totalUsers: Int
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AdminStatCard(
                title = "Total Reports",
                count = totalReports.toString(),
                subtext = "Logged complaints",
                icon = Icons.Default.Assignment,
                color = Primary,
                modifier = Modifier.weight(1f)
            )
            AdminStatCard(
                title = "In Progress",
                count = inProgress.toString(),
                subtext = "Active field work",
                icon = Icons.Default.PendingActions,
                color = Color(0xFFF59E0B),
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AdminStatCard(
                title = "Resolved",
                count = resolved.toString(),
                subtext = "Completed fixes",
                icon = Icons.Default.CheckCircle,
                color = Color(0xFF10B981),
                modifier = Modifier.weight(1f)
            )
            AdminStatCard(
                title = "Citizens",
                count = totalUsers.toString(),
                subtext = "Registered users",
                icon = Icons.Default.People,
                color = Color(0xFF8B5CF6),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun AdminStatCard(
    title: String,
    count: String,
    subtext: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.2.dp, color.copy(alpha = 0.25f)),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(color.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
                }
                Text(
                    text = count,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 26.sp,
                    color = color
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtext,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ReportsManagementTab(
    reports: List<Report>,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    statusFilter: String,
    onStatusFilterChange: (String) -> Unit,
    categoryFilter: String,
    onCategoryFilterChange: (String) -> Unit,
    onOpenStatusModal: (Report) -> Unit,
    onDeleteReport: (Report) -> Unit
) {
    val filteredReports = remember(reports, searchQuery, statusFilter, categoryFilter) {
        reports.filter { r ->
            val matchesSearch = searchQuery.isBlank() ||
                r.title.contains(searchQuery, ignoreCase = true) ||
                r.description.contains(searchQuery, ignoreCase = true) ||
                r.location.contains(searchQuery, ignoreCase = true) ||
                r.id.contains(searchQuery, ignoreCase = true) ||
                r.category.contains(searchQuery, ignoreCase = true)

            val matchesStatus = statusFilter == "All" || r.status.name.equals(statusFilter, ignoreCase = true)
            val matchesCategory = categoryFilter == "All" || r.category.equals(categoryFilter, ignoreCase = true)

            matchesSearch && matchesStatus && matchesCategory
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // Search bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            placeholder = { Text("Search by title, location, category or ID...", fontSize = 13.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Primary) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(10.dp))

        // Status Filter Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val statuses = listOf("All", "SUBMITTED", "UNDER_REVIEW", "ASSIGNED", "IN_PROGRESS", "RESOLVED", "REJECTED")
            statuses.forEach { st ->
                val isSelected = statusFilter == st
                FilterChip(
                    selected = isSelected,
                    onClick = { onStatusFilterChange(st) },
                    label = { Text(if (st == "All") "All Status" else st.replace('_', ' '), fontSize = 11.sp) }
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        Text(
            "Showing ${filteredReports.size} reports",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(8.dp))

        if (filteredReports.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No matching civic reports found.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filteredReports, key = { it.id }) { report ->
                    AdminReportCard(
                        report = report,
                        onUpdateStatus = { onOpenStatusModal(report) },
                        onDelete = { onDeleteReport(report) }
                    )
                }
            }
        }
    }
}

@Composable
private fun AdminReportCard(
    report: Report,
    onUpdateStatus: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header: ID, Category & Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "#${report.id.uppercase()}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = Primary
                    )
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            report.category,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                StatusBadge(report.status)
            }

            Spacer(Modifier.height(8.dp))

            // Title & Description
            Text(report.title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                report.description,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2
            )

            Spacer(Modifier.height(8.dp))

            // Photo Evidence (from PC server with fallback)
            val adminContext = LocalContext.current
            val imageModel = ServerConfig.resolveImageUrl(adminContext, report.imageUrl, report.category)
            val fallbackRes = ServerConfig.getStockDrawableForCategory(report.category)

            AsyncImage(
                model = ImageRequest.Builder(adminContext)
                    .data(imageModel)
                    .crossfade(true)
                    .placeholder(fallbackRes)
                    .error(fallbackRes)
                    .build(),
                contentDescription = report.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .clip(RoundedCornerShape(10.dp))
            )

            Spacer(Modifier.height(8.dp))

            // Location & Date & Reporter
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.LocationOn, contentDescription = null, tint = Primary, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(report.location, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
                Text("📅 ${report.date}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = Border)
            Spacer(Modifier.height(8.dp))

            // Actions row: Update Status button & Delete
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Reported by: ${report.userName}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onUpdateStatus,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Update Status", fontSize = 12.sp)
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = Error, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun CitizensTab(users: List<User>, isLoading: Boolean) {
    val realUsers = remember(users) { users.filterNot { it.isDummyUser() } }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Registered Citizens & Guests", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text("Real user accounts & guests reporting problems via Firebase (${realUsers.size})", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (realUsers.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.PeopleOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "No real users or guests found in Firebase",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Registered citizen accounts and guests submitting complaints in Firestore will appear here.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(realUsers, key = { it.id }) { user ->
                    val isGuest = user.role.equals("Guest", ignoreCase = true)
                    val roleColor = when {
                        user.role == "Municipal" -> Color(0xFF8B5CF6)
                        isGuest -> Color(0xFFF59E0B)
                        else -> Primary
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(roleColor.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    user.name.take(1).ifBlank { "U" }.uppercase(),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    color = roleColor
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(user.name.ifBlank { if (isGuest) "Guest Citizen" else "Citizen" }, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text(user.email.ifBlank { "ID: ${user.id.take(8)}" }, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (user.phone.isNotBlank()) {
                                    Text("📞 ${user.phone}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = roleColor.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        if (isGuest) "GUEST" else user.role.uppercase(),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = roleColor,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                                Text("${user.reportsSubmitted} submitted", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${user.reportsResolved} resolved", fontSize = 11.sp, color = Color(0xFF10B981))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AlertsTab(
    reports: List<Report>,
    onOpenReport: (Report) -> Unit
) {
    // Alerts MUST ONLY notify when a new report is made, referring strictly to Firebase database
    val realNewReports = remember(reports) {
        reports.filterNot { it.isDummyReport() }.sortedByDescending { it.createdAt }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Municipal Alerts Feed", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(
                    "Real-time notifications for newly submitted civic reports (${realNewReports.size})",
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        if (realNewReports.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.NotificationsNone,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "No new reports submitted yet",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "When citizens or guests file new civic reports in Firebase, they will appear here.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(realNewReports, key = { it.id }) { rep ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenReport(rep) },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Primary.copy(alpha = 0.25f))
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(Error.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = Error, modifier = Modifier.size(20.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "🚨 New Report: ${rep.title}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.5.sp,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    StatusBadge(status = rep.status)
                                }

                                if (rep.description.isNotBlank()) {
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        rep.description,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                }

                                Spacer(Modifier.height(6.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant
                                        ) {
                                            Text(
                                                rep.category,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Medium,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            "👤 ${rep.userName}",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Text(
                                        "📅 ${rep.date}",
                                        fontSize = 10.5.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                    )
                                }

                                Spacer(Modifier.height(4.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f, fill = false)
                                    ) {
                                        Icon(Icons.Default.LocationOn, contentDescription = null, tint = Primary, modifier = Modifier.size(13.dp))
                                        Spacer(Modifier.width(3.dp))
                                        Text(
                                            rep.location,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                        )
                                    }

                                    Text(
                                        "Update Status →",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Primary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AnalyticsTab(
    reports: List<Report>,
    stats: com.fixmycity.app.domain.model.ImpactStats,
    totalUsers: Int
) {
    // Exclude dummy reports - analytics strictly reflects real reports from Firebase
    val realReports = remember(reports) { reports.filterNot { it.isDummyReport() } }
    val total = realReports.size.coerceAtLeast(1)
    val liveInProgress = realReports.count {
        it.status == ReportStatus.IN_PROGRESS || it.status == ReportStatus.ASSIGNED || it.status == ReportStatus.UNDER_REVIEW
    }
    val liveResolved = realReports.count {
        it.status == ReportStatus.RESOLVED
    }
    val liveTotal = realReports.size

    // Category breakdown from real reports
    val categoryCounts = remember(realReports) {
        realReports.groupingBy { it.category }.eachCount()
    }

    // Area & Ward clustering based directly on real report locations
    val areaHotspots = remember(realReports) {
        val grouped = realReports.groupBy { r ->
            val loc = r.location.trim()
            when {
                loc.contains("Vashi", ignoreCase = true) -> "Vashi Zone"
                loc.contains("Nerul", ignoreCase = true) -> "Nerul Ward"
                loc.contains("Kalyan", ignoreCase = true) -> "Kalyan West"
                loc.contains("Kharghar", ignoreCase = true) -> "Kharghar Sector"
                loc.contains("Belapur", ignoreCase = true) -> "CBD Belapur"
                loc.contains("Thane", ignoreCase = true) -> "Thane Central"
                loc.contains("Pune", ignoreCase = true) -> "Pune City"
                loc.contains("Delhi", ignoreCase = true) -> "Delhi North"
                loc.contains(",") -> loc.substringBefore(",").trim()
                loc.isNotBlank() -> loc
                else -> "Metro Central"
            }
        }

        grouped.map { (areaName, areaReports) ->
            val areaTotal = areaReports.size
            val areaResolved = areaReports.count { it.status == ReportStatus.RESOLVED }
            val areaActive = areaTotal - areaResolved
            val rate = if (areaTotal > 0) ((areaResolved.toDouble() / areaTotal.toDouble()) * 100).toInt() else 0
            val topCat = areaReports.groupingBy { it.category }.eachCount().maxByOrNull { it.value }?.key ?: "Civic Issue"
            val severity = when {
                areaActive >= 3 || (areaTotal >= 4 && rate < 40) -> "CRITICAL"
                areaActive >= 1 -> "MODERATE"
                else -> "OPTIMAL"
            }
            AreaClusterData(
                areaName = areaName,
                totalReports = areaTotal,
                resolvedCount = areaResolved,
                activeCount = areaActive,
                resolutionRate = rate,
                topCategory = topCat,
                severity = severity
            )
        }.sortedByDescending { it.totalReports }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ── 1. Top Executive Metric Cards ─────────────────────────────────────
        Text("Executive Metrics Overview", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        AdminStatsGrid(
            totalReports = liveTotal,
            inProgress = liveInProgress,
            resolved = liveResolved,
            totalUsers = totalUsers
        )

        // ── 2. Interactive Geographic Map of Reported Incidents ───────────────
        ReportsIncidentMapCard(reports = realReports)

        // ── 3. Report Status Distribution Graph ───────────────────────────────
        StatusDistributionCard(reports = realReports)

        // ── 4. Complaints by Civic Category Graph ─────────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.BarChart, contentDescription = null, tint = Primary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("Complaints Volume by Category", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text("Distribution across reported civic issues", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(14.dp))

                if (categoryCounts.isEmpty()) {
                    Text("No category data recorded yet from Firebase reports.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    categoryCounts.entries.sortedByDescending { it.value }.forEach { (category, count) ->
                        val fraction = count.toFloat() / total.toFloat()
                        val pct = ((count.toDouble() / total.toDouble()) * 100).toInt()
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(category, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                Text("$count ($pct%)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Primary)
                            }
                            Spacer(Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = { fraction },
                                modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(3.5.dp)),
                                color = Primary
                            )
                        }
                    }
                }
            }
        }

        // ── 5. Reports by Ward & Area Distribution ────────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LocationOn, contentDescription = null, tint = Primary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("Reports by Area & Ward", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text("Complaint frequency and resolution per locality", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                if (areaHotspots.isEmpty()) {
                    Text("No locality data recorded yet from Firebase reports.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    areaHotspots.forEach { cluster ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 5.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(cluster.areaName, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                            val (badgeBg, badgeText, badgeLabel) = when (cluster.severity) {
                                "CRITICAL" -> Triple(Error.copy(alpha = 0.15f), Error, "🔥 High Incident")
                                "MODERATE" -> Triple(Color(0xFFF59E0B).copy(alpha = 0.15f), Color(0xFFF59E0B), "⚡ Active")
                                else       -> Triple(Color(0xFF10B981).copy(alpha = 0.15f), Color(0xFF10B981), "✅ Resolved")
                            }
                            Surface(shape = RoundedCornerShape(6.dp), color = badgeBg) {
                                Text(badgeLabel, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = badgeText, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                        }

                        Spacer(Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "${cluster.totalReports} Total (${cluster.resolvedCount} Resolved • ${cluster.activeCount} Active)",
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "${cluster.resolutionRate}% Resolved",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (cluster.resolutionRate >= 70) Color(0xFF10B981) else if (cluster.resolutionRate >= 40) Color(0xFFF59E0B) else Error
                            )
                        }

                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { (cluster.resolutionRate / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                            color = if (cluster.resolutionRate >= 70) Color(0xFF10B981) else if (cluster.resolutionRate >= 40) Color(0xFFF59E0B) else Error
                        )
                    }
                }
            }
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}

/**
 * Interactive Geographic Incident Map that plots report pins based on their reported locations.
 */
@Composable
private fun ReportsIncidentMapCard(
    reports: List<Report>,
    onSelectReport: ((Report) -> Unit)? = null
) {
    var zoomLevel by remember { mutableIntStateOf(13) }
    var currentLat by remember { mutableDoubleStateOf(19.0480) }
    var currentLng by remember { mutableDoubleStateOf(73.0280) }
    var selectedLayer by remember { mutableStateOf(MapLayerType.STREET) }
    var selectedReport by remember { mutableStateOf<Report?>(null) }

    val reportsWithCoords = remember(reports) {
        reports.mapIndexed { idx, rep -> rep to getReportCoordinates(rep, idx) }
    }

    // Auto-center map on incidents whenever report data is loaded
    LaunchedEffect(reportsWithCoords.size) {
        if (reportsWithCoords.isNotEmpty()) {
            val validCoords = reportsWithCoords.map { it.second }
            currentLat = validCoords.map { it.first }.average()
            currentLng = validCoords.map { it.second }.average()
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("🗺️ Live Incident Map", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(
                        "${reportsWithCoords.size} incident locations plotted (pan & tap pins)",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Layer selection chips
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    MapLayerType.values().forEach { layer ->
                        val isSelected = selectedLayer == layer
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) Primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, Primary) else null,
                            modifier = Modifier.clickable { selectedLayer = layer }
                        ) {
                            Text(
                                layer.shortName,
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) Primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }

            // Quick-jump chips strip for each incident
            if (reportsWithCoords.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    reportsWithCoords.forEach { (rep, coords) ->
                        val isSelected = selectedReport?.id == rep.id
                        val pinColor = when (rep.status) {
                            ReportStatus.RESOLVED -> Color(0xFF10B981)
                            ReportStatus.IN_PROGRESS -> Color(0xFFF59E0B)
                            ReportStatus.ASSIGNED -> Color(0xFF8B5CF6)
                            ReportStatus.UNDER_REVIEW -> Color(0xFF3B82F6)
                            ReportStatus.REJECTED -> Color(0xFF6B7280)
                            ReportStatus.SUBMITTED -> Color(0xFFEF4444)
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) pinColor.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isSelected) pinColor else Border.copy(alpha = 0.6f)
                            ),
                            modifier = Modifier.clickable {
                                selectedReport = rep
                                currentLat = coords.first
                                currentLng = coords.second
                                zoomLevel = 15
                                onSelectReport?.invoke(rep)
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Box(Modifier.size(8.dp).clip(CircleShape).background(pinColor))
                                Text(
                                    rep.title.take(18).ifBlank { rep.category },
                                    fontSize = 10.5.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Map Container
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.dp, Border, RoundedCornerShape(14.dp))
            ) {
                val density = LocalDensity.current
                val tileSizePx = with(density) { 256.dp.toPx() }
                val boxWidthPx = constraints.maxWidth.toFloat()
                val boxHeightPx = constraints.maxHeight.toFloat()
                val n = 1 shl zoomLevel

                // Real OpenStreetMap / Esri tile grid with interactive drag gestures
                MapTilesGrid(
                    latitude = currentLat,
                    longitude = currentLng,
                    zoom = zoomLevel,
                    layerType = selectedLayer,
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                val currentN = 1 shl zoomLevel
                                val dLng = -dragAmount.x / tileSizePx * (360.0 / currentN)
                                currentLng = (currentLng + dLng).coerceIn(-180.0, 180.0)

                                val latRad = Math.toRadians(currentLat.coerceIn(-85.0, 85.0))
                                val yExact = ((1.0 - asinh(tan(latRad)) / Math.PI) / 2.0 * currentN)
                                val dyFraction = dragAmount.y / tileSizePx
                                val newYExact = (yExact - dyFraction).coerceIn(0.001, currentN.toDouble() - 0.001)
                                val sinhVal = sinh(Math.PI * (1.0 - 2.0 * newYExact / currentN))
                                currentLat = Math.toDegrees(atan(sinhVal)).coerceIn(-85.0, 85.0)
                            }
                        }
                )

                // Report pin markers
                val centerLatRad = Math.toRadians(currentLat.coerceIn(-85.0, 85.0))
                val centerYExact = ((1.0 - asinh(tan(centerLatRad)) / Math.PI) / 2.0 * n)

                reportsWithCoords.forEach { (rep, coords) ->
                    val (lat, lng) = coords
                    val dLng = (lng - currentLng)
                    val xPx = boxWidthPx / 2f + (dLng / 360.0 * n * tileSizePx).toFloat()

                    val repLatRad = Math.toRadians(lat.coerceIn(-85.0, 85.0))
                    val repYExact = ((1.0 - asinh(tan(repLatRad)) / Math.PI) / 2.0 * n)
                    val yPx = boxHeightPx / 2f + ((repYExact - centerYExact) * tileSizePx).toFloat()

                    if (xPx in -40f..(boxWidthPx + 40f) && yPx in -50f..(boxHeightPx + 40f)) {
                        val isSelected = selectedReport?.id == rep.id
                        val pinColor = when (rep.status) {
                            ReportStatus.RESOLVED -> Color(0xFF10B981)
                            ReportStatus.IN_PROGRESS -> Color(0xFFF59E0B)
                            ReportStatus.ASSIGNED -> Color(0xFF8B5CF6)
                            ReportStatus.UNDER_REVIEW -> Color(0xFF3B82F6)
                            ReportStatus.REJECTED -> Color(0xFF6B7280)
                            ReportStatus.SUBMITTED -> Color(0xFFEF4444)
                        }

                        val pinIcon = when (rep.status) {
                            ReportStatus.RESOLVED -> Icons.Default.Check
                            ReportStatus.IN_PROGRESS -> Icons.Default.Build
                            ReportStatus.ASSIGNED -> Icons.Default.Person
                            ReportStatus.UNDER_REVIEW -> Icons.Default.Visibility
                            ReportStatus.REJECTED -> Icons.Default.Close
                            ReportStatus.SUBMITTED -> Icons.Default.PriorityHigh
                        }

                        Box(
                            modifier = Modifier
                                .offset {
                                    IntOffset(
                                        (xPx - with(density) { (if (isSelected) 18.dp else 15.dp).toPx() }).roundToInt(),
                                        (yPx - with(density) { (if (isSelected) 36.dp else 30.dp).toPx() }).roundToInt()
                                    )
                                }
                                .clickable {
                                    selectedReport = if (isSelected) null else rep
                                    onSelectReport?.invoke(rep)
                                }
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                // Tooltip popup when selected
                                if (isSelected) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = Color.Black.copy(alpha = 0.82f),
                                        modifier = Modifier.padding(bottom = 2.dp)
                                    ) {
                                        Text(
                                            rep.title.take(18),
                                            color = Color.White,
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                // Vibrant colored pin body with white outline & drop shadow
                                Box(
                                    modifier = Modifier
                                        .size(if (isSelected) 36.dp else 30.dp)
                                        .clip(CircleShape)
                                        .background(pinColor)
                                        .border(2.5.dp, Color.White, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        pinIcon,
                                        contentDescription = rep.title,
                                        tint = Color.White,
                                        modifier = Modifier.size(if (isSelected) 18.dp else 15.dp)
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .size(width = 8.dp, height = 3.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.4f))
                                )
                            }
                        }
                    }
                }

                // Zoom & Center controls overlay
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    SmallFloatingActionButton(
                        onClick = {
                            if (reportsWithCoords.isNotEmpty()) {
                                val validCoords = reportsWithCoords.map { it.second }
                                currentLat = validCoords.map { it.first }.average()
                                currentLng = validCoords.map { it.second }.average()
                                zoomLevel = 13
                            }
                        },
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                        contentColor = Primary,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.MyLocation, contentDescription = "Center on incidents", modifier = Modifier.size(16.dp))
                    }
                    SmallFloatingActionButton(
                        onClick = { if (zoomLevel < 18) zoomLevel++ },
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Text("+", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                    SmallFloatingActionButton(
                        onClick = { if (zoomLevel > 9) zoomLevel-- },
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Text("−", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }

                // Map Status Markings Legend overlay
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFFEF4444)))
                        Text("New", fontSize = 9.sp, fontWeight = FontWeight.SemiBold)

                        Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF3B82F6)))
                        Text("Review", fontSize = 9.sp, fontWeight = FontWeight.SemiBold)

                        Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF8B5CF6)))
                        Text("Assigned", fontSize = 9.sp, fontWeight = FontWeight.SemiBold)

                        Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFFF59E0B)))
                        Text("Progress", fontSize = 9.sp, fontWeight = FontWeight.SemiBold)

                        Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF10B981)))
                        Text("Resolved", fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // Selected Report Popup Card
            selectedReport?.let { rep ->
                Spacer(Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Primary.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                StatusBadge(status = rep.status)
                                Spacer(Modifier.width(6.dp))
                                Text(rep.category, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Primary)
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(rep.title, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
                            Text("📍 ${rep.location}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                        IconButton(onClick = { selectedReport = null }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Close", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}

/**
 * Report Status Distribution multi-segment horizontal bar & legend.
 */
@Composable
private fun StatusDistributionCard(reports: List<Report>) {
    val total = reports.size.coerceAtLeast(1)
    val resolved = reports.count { it.status == ReportStatus.RESOLVED }
    val inProgress = reports.count { it.status == ReportStatus.IN_PROGRESS }
    val underReview = reports.count { it.status == ReportStatus.UNDER_REVIEW }
    val assigned = reports.count { it.status == ReportStatus.ASSIGNED }
    val submitted = reports.count { it.status == ReportStatus.SUBMITTED }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("📊 Report Status Breakdown", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text("Proportional lifecycle progress of all $total reports", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))

            // Multi-segment horizontal bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(RoundedCornerShape(6.dp))
            ) {
                if (resolved > 0) {
                    Box(modifier = Modifier.weight(resolved.toFloat()).fillMaxHeight().background(Color(0xFF10B981)))
                }
                if (inProgress > 0) {
                    Box(modifier = Modifier.weight(inProgress.toFloat()).fillMaxHeight().background(Color(0xFFF59E0B)))
                }
                if (assigned > 0) {
                    Box(modifier = Modifier.weight(assigned.toFloat()).fillMaxHeight().background(Color(0xFF8B5CF6)))
                }
                if (underReview > 0) {
                    Box(modifier = Modifier.weight(underReview.toFloat()).fillMaxHeight().background(Color(0xFF3B82F6)))
                }
                if (submitted > 0) {
                    Box(modifier = Modifier.weight(submitted.toFloat()).fillMaxHeight().background(Primary))
                }
            }

            Spacer(Modifier.height(12.dp))

            // Legend Grid
            val statusItems = listOf(
                Triple("Resolved", resolved, Color(0xFF10B981)),
                Triple("In Progress", inProgress, Color(0xFFF59E0B)),
                Triple("Assigned", assigned, Color(0xFF8B5CF6)),
                Triple("Under Review", underReview, Color(0xFF3B82F6)),
                Triple("Submitted", submitted, Primary)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                statusItems.take(3).forEach { (label, count, color) ->
                    val pct = (count * 100) / total
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
                        Spacer(Modifier.width(4.dp))
                        Text("$label: $count ($pct%)", fontSize = 10.5.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                statusItems.drop(3).forEach { (label, count, color) ->
                    val pct = (count * 100) / total
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
                        Spacer(Modifier.width(4.dp))
                        Text("$label: $count ($pct%)", fontSize = 10.5.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

private fun getReportCoordinates(report: Report, index: Int): Pair<Double, Double> {
    // 1. Exact GPS coordinates stored directly in Report model
    if (report.latitude != null && report.longitude != null &&
        report.latitude != 0.0 && report.longitude != 0.0 &&
        report.latitude in -85.0..85.0 && report.longitude in -180.0..180.0) {
        return report.latitude to report.longitude
    }

    // 2. Parse coordinate pairs from location text (e.g. "19.0760, 72.8777")
    val coordRegex = Regex("""(-?\d{1,3}\.\d+)\s*,\s*(-?\d{1,3}\.\d+)""")
    val match = coordRegex.find(report.location)
    if (match != null) {
        val lat = match.groupValues[1].toDoubleOrNull()
        val lng = match.groupValues[2].toDoubleOrNull()
        if (lat != null && lng != null && lat in -85.0..85.0 && lng in -180.0..180.0) {
            return lat to lng
        }
    }

    // 3. Fallback to geographic areas & cities
    val loc = report.location.lowercase()
    return when {
        loc.contains("vashi") -> 19.0748 + ((index % 3) - 1) * 0.003 to 72.9978 + ((index % 2) * 0.004)
        loc.contains("nerul") -> 19.0330 + ((index % 3) - 1) * 0.003 to 73.0185 + ((index % 2) * 0.004)
        loc.contains("belapur") -> 19.0180 + ((index % 3) - 1) * 0.003 to 73.0422 + ((index % 2) * 0.004)
        loc.contains("kharghar") -> 19.0473 + ((index % 3) - 1) * 0.003 to 73.0699 + ((index % 2) * 0.004)
        loc.contains("koparkhairane") || loc.contains("kopar") -> 19.0988 + ((index % 3) - 1) * 0.003 to 73.0076 + ((index % 2) * 0.004)
        loc.contains("sanpada") -> 19.0638 + ((index % 3) - 1) * 0.003 to 73.0104 + ((index % 2) * 0.004)
        loc.contains("airoli") -> 19.1551 + ((index % 3) - 1) * 0.003 to 72.9989 + ((index % 2) * 0.004)
        loc.contains("ghansoli") -> 19.1245 + ((index % 3) - 1) * 0.003 to 73.0150 + ((index % 2) * 0.004)
        loc.contains("kalyan") -> 19.2403 + ((index % 3) - 1) * 0.003 to 73.1305 + ((index % 2) * 0.004)
        loc.contains("thane") -> 19.2183 + ((index % 3) - 1) * 0.003 to 72.9781 + ((index % 2) * 0.004)
        loc.contains("mumbai") -> 19.0760 + ((index % 3) - 1) * 0.003 to 72.8777 + ((index % 2) * 0.004)
        loc.contains("pune") -> 18.5204 + ((index % 3) - 1) * 0.003 to 73.8567 + ((index % 2) * 0.004)
        loc.contains("delhi") -> 28.6139 + ((index % 3) - 1) * 0.003 to 77.2090 + ((index % 2) * 0.004)
        loc.contains("bangalore") || loc.contains("bengaluru") -> 12.9716 + ((index % 3) - 1) * 0.003 to 77.5946 + ((index % 2) * 0.004)
        else -> {
            val hash = abs(report.id.hashCode())
            val latOffset = (((hash % 80) - 40) * 0.0006)
            val lngOffset = ((((hash / 80) % 80) - 40) * 0.0006)
            19.0400 + latOffset to 73.0250 + lngOffset
        }
    }
}

private data class AreaClusterData(
    val areaName: String,
    val totalReports: Int,
    val resolvedCount: Int,
    val activeCount: Int,
    val resolutionRate: Int,
    val topCategory: String,
    val severity: String
)
