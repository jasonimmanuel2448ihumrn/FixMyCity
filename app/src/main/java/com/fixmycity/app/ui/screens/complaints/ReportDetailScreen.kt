package com.fixmycity.app.ui.screens.complaints

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.fixmycity.app.data.model.Report
import com.fixmycity.app.data.model.ReportStatus
import com.fixmycity.app.data.model.TimelineStep
import com.fixmycity.app.data.repository.FixMyCityRepository
import com.fixmycity.app.ui.components.StatusBadge
import com.fixmycity.app.ui.theme.*
import com.fixmycity.app.utils.ServerConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportDetailScreen(
    reportId: String,
    onNavigateBack: () -> Unit,
    repository: FixMyCityRepository = FixMyCityRepository.getInstance()
) {
    val reports by repository.reports.collectAsState()
    val report = reports.find { it.id == reportId }

    var isAdvancing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(reportId) {
        repository.getReportById(reportId)
    }

    if (report == null) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text("Report Details") },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.Default.ArrowBack, "Back")
                        }
                    }
                )
            }
        ) { padding ->
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("Report not found", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }

    val categoryIcon = when (report.category) {
        "Pothole"        -> Icons.Default.Warning
        "Garbage"        -> Icons.Default.Delete
        "Streetlight"    -> Icons.Default.LightMode
        "Water Leakage"  -> Icons.Default.WaterDrop
        "Drainage"       -> Icons.Default.InvertColors
        "Road Damage"    -> Icons.Default.Construction
        "Public Property"-> Icons.Default.AccountBalance
        else             -> Icons.Default.ReportProblem
    }
    val categoryColor = when (report.category) {
        "Pothole"        -> Color(0xFFEF4444)
        "Garbage"        -> Color(0xFF84CC16)
        "Streetlight"    -> Color(0xFFF59E0B)
        "Water Leakage"  -> Color(0xFF06B6D4)
        "Drainage"       -> Color(0xFF8B5CF6)
        "Road Damage"    -> Color(0xFFEC4899)
        "Public Property"-> Color(0xFF14B8A6)
        else             -> Primary
    }

    // Compute live dynamic timeline based on current status
    val timeline = computeTimeline(report)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Report Details", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        scope.launch {
                            repository.getReportById(reportId)
                            snackbarHostState.showSnackbar("Status updated from server")
                        }
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh status")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Image banner — fetches from laptop PC server with offline stock image fallback
            val context = LocalContext.current
            val imageModel = ServerConfig.resolveImageUrl(context, report.imageUrl, report.category)
            val fallbackRes = ServerConfig.getStockDrawableForCategory(report.category)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(230.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(imageModel)
                        .crossfade(true)
                        .placeholder(fallbackRes)
                        .error(fallbackRes)
                        .build(),
                    contentDescription = report.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // Category pill overlay
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.Black.copy(alpha = 0.65f),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(categoryIcon, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(report.category, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // Status Badge & ID with animated appearance
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                AnimatedContent(
                    targetState = report.status,
                    transitionSpec = {
                        fadeIn(tween(300)) + scaleIn(tween(300)) togetherWith fadeOut(tween(200))
                    },
                    label = "statusBadge"
                ) { targetStatus ->
                    StatusBadge(targetStatus)
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    "Report #${report.id.uppercase()}",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Title
            Text(report.title, fontSize = 22.sp, fontWeight = FontWeight.Bold)

            // Description card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    DetailRow(Icons.Default.Description, "Description", report.description)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = Border)
                    DetailRow(Icons.Default.LocationOn, "Location", report.location)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = Border)
                    DetailRow(Icons.Default.CalendarToday, "Submitted On", report.date)
                }
            }

            // ── Animated Timeline Card ───────────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Progress Timeline", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        AnimatedContent(
                            targetState = report.status,
                            transitionSpec = {
                                (slideInVertically { it } + fadeIn()).togetherWith(slideOutVertically { -it } + fadeOut())
                            },
                            label = "statusLabel"
                        ) { targetStatus ->
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = if (targetStatus == ReportStatus.RESOLVED) Success.copy(alpha = 0.15f) else Primary.copy(alpha = 0.12f)
                            ) {
                                Text(
                                    text = "  ${targetStatus.name.replace('_', ' ')}  ",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (targetStatus == ReportStatus.RESOLVED) Success else Primary,
                                    modifier = Modifier.padding(vertical = 3.dp)
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(20.dp))

                    val lastCompletedIndex = timeline.indexOfLast { it.isCompleted }

                    timeline.forEachIndexed { index, step ->
                        val isNextStepCompleted = index < timeline.lastIndex && timeline[index + 1].isCompleted
                        val isCurrentActiveStep = index == lastCompletedIndex

                        AnimatedTimelineItem(
                            step = step,
                            isLast = index == timeline.lastIndex,
                            isNextCompleted = isNextStepCompleted,
                            isCurrentActive = isCurrentActiveStep
                        )
                    }
                }
            }

            // ── Advance Status Action / Resolution Banner ────────────────────
            // ── Status Tracking Notice or Municipal Authority Controls ──────
            val currentUser by repository.currentUser.collectAsState()
            val isMunicipalOfficer = currentUser.role.equals("Municipal", ignoreCase = true)

            if (report.status == ReportStatus.RESOLVED) {
                // Success celebration banner
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Success.copy(alpha = 0.12f))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Success),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                        }
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text("Issue Resolved!", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Success)
                            Text("Civic department marked this issue as complete.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            } else if (isMunicipalOfficer && report.status != ReportStatus.REJECTED) {
                // Only Municipal Officers have the authority to advance report status
                val nextStatus = when (report.status) {
                    ReportStatus.SUBMITTED    -> ReportStatus.UNDER_REVIEW
                    ReportStatus.UNDER_REVIEW -> ReportStatus.ASSIGNED
                    ReportStatus.ASSIGNED     -> ReportStatus.IN_PROGRESS
                    ReportStatus.IN_PROGRESS  -> ReportStatus.RESOLVED
                    else                      -> null
                }

                if (nextStatus != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Primary.copy(alpha = 0.08f)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Primary.copy(alpha = 0.2f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.AdminPanelSettings, contentDescription = null, tint = Primary, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Municipal Authority Control", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = PrimaryDark)
                            }
                            Spacer(Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    if (!isAdvancing) {
                                        scope.launch {
                                            isAdvancing = true
                                            val result = repository.updateReportStatus(report.id, nextStatus)
                                            delay(400)
                                            isAdvancing = false
                                            if (result.isSuccess) {
                                                snackbarHostState.showSnackbar("Status updated to ${nextStatus.name.replace('_', ' ')}!")
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Primary),
                                enabled = !isAdvancing
                            ) {
                                if (isAdvancing) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Updating Status...", fontSize = 13.sp)
                                } else {
                                    Icon(Icons.Default.CheckCircleOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Mark as ${nextStatus.name.replace('_', ' ')}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            } else if (!isMunicipalOfficer && report.status != ReportStatus.RESOLVED) {
                // Informational banner for citizens - status is managed only by municipal authorities
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = Primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                "Monitored by Municipal Corporation",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "Civic authorities manage status updates as field work progresses. You'll be notified automatically.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

private fun computeTimeline(report: Report): List<TimelineStep> {
    val statusOrder = listOf(
        ReportStatus.SUBMITTED,
        ReportStatus.UNDER_REVIEW,
        ReportStatus.ASSIGNED,
        ReportStatus.IN_PROGRESS,
        ReportStatus.RESOLVED
    )
    val statusLabels = mapOf(
        ReportStatus.SUBMITTED to "Submitted",
        ReportStatus.UNDER_REVIEW to "Under Review",
        ReportStatus.ASSIGNED to "Assigned",
        ReportStatus.IN_PROGRESS to "In Progress",
        ReportStatus.RESOLVED to "Resolved"
    )

    val currentIdx = when (report.status) {
        ReportStatus.SUBMITTED -> 0
        ReportStatus.UNDER_REVIEW -> 1
        ReportStatus.ASSIGNED -> 2
        ReportStatus.IN_PROGRESS -> 3
        ReportStatus.RESOLVED -> 4
        ReportStatus.REJECTED -> 0
    }

    val existingMap = report.timelineSteps.associateBy { it.status }

    return statusOrder.mapIndexed { index, stepStatus ->
        val existing = existingMap[stepStatus]
        val isDone = index <= currentIdx
        val dateText = existing?.date ?: if (isDone) report.date else null
        TimelineStep(
            status = stepStatus,
            label = statusLabels[stepStatus] ?: stepStatus.name,
            date = dateText,
            isCompleted = isDone
        )
    }
}

@Composable
private fun AnimatedTimelineItem(
    step: TimelineStep,
    isLast: Boolean,
    isNextCompleted: Boolean,
    isCurrentActive: Boolean
) {
    // Circle Color Animation
    val targetCircleColor = if (step.isCompleted) {
        if (step.status == ReportStatus.RESOLVED) Success else Primary
    } else {
        Border
    }
    val circleColor by animateColorAsState(
        targetValue = targetCircleColor,
        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
        label = "circleColor"
    )

    // Circle Scale Pop Animation
    val circleScale by animateFloatAsState(
        targetValue = if (step.isCompleted) 1f else 0.85f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "circleScale"
    )

    // Checkmark Scale In Animation
    val checkmarkScale by animateFloatAsState(
        targetValue = if (step.isCompleted) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "checkmarkScale"
    )

    // Connector Line Fill Animation (flows top to bottom smoothly)
    val lineFillProgress by animateFloatAsState(
        targetValue = if (isNextCompleted) 1f else 0f,
        animationSpec = tween(durationMillis = 600, delayMillis = 150, easing = FastOutSlowInEasing),
        label = "lineFill"
    )

    // Pulse animation for active step
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Row(modifier = Modifier.fillMaxWidth()) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(28.dp)
            ) {
                // Pulsing glow ring for current active step
                if (isCurrentActive && step.status != ReportStatus.RESOLVED) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .scale(pulseScale)
                            .clip(CircleShape)
                            .background(Primary.copy(alpha = pulseAlpha))
                    )
                }

                // Main Node Circle
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .scale(circleScale)
                        .clip(CircleShape)
                        .background(circleColor),
                    contentAlignment = Alignment.Center
                ) {
                    if (step.isCompleted) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier
                                .size(14.dp)
                                .scale(checkmarkScale)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                        )
                    }
                }
            }

            // Connecting Vertical Line with Animated Flow
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(38.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Border.copy(alpha = 0.6f))
                ) {
                    // Animated Fill Beam
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(lineFillProgress)
                            .background(
                                Brush.verticalGradient(
                                    listOf(circleColor, Primary)
                                )
                            )
                    )
                }
            }
        }

        Spacer(Modifier.width(16.dp))

        // Step Label & Timestamp Column
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = if (isLast) 0.dp else 12.dp)
        ) {
            val labelColor by animateColorAsState(
                targetValue = if (step.isCompleted) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                animationSpec = tween(400),
                label = "textColor"
            )

            Text(
                text = step.label,
                fontSize = 15.sp,
                fontWeight = if (step.isCompleted) FontWeight.SemiBold else FontWeight.Normal,
                color = labelColor
            )

            AnimatedVisibility(
                visible = !step.date.isNullOrBlank(),
                enter = fadeIn(tween(400)) + expandVertically(tween(400)),
                exit = fadeOut()
            ) {
                Text(
                    text = step.date ?: "",
                    fontSize = 12.sp,
                    color = if (step.isCompleted) Primary.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun DetailRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, tint = Primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Column {
            Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
    }
}
