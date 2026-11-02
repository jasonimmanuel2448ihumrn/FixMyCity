package com.fixmycity.app.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fixmycity.app.ui.components.Avatar
import com.fixmycity.app.ui.theme.*
import com.fixmycity.app.utils.ServerConfig
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onLogout: () -> Unit,
    onAbout: () -> Unit,
    onThemeToggle: () -> Unit,
    isDarkTheme: Boolean,
    onOpenAdminDashboard: () -> Unit = {},
    onOpenAiChat: () -> Unit = {},
    viewModel: ProfileViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var serverHost by remember { mutableStateOf(ServerConfig.getServerHost(context)) }
    var showServerDialog by remember { mutableStateOf(false) }
    var serverStatusText by remember { mutableStateOf<String?>("Tap refresh icon to test connection") }
    var isCheckingServer by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.refresh()
        // Auto-ping laptop server in background
        scope.launch {
            val res = ServerConfig.pingServer(context)
            if (res.isSuccess) {
                serverStatusText = "🟢 " + res.getOrNull()
            } else {
                serverStatusText = "🔴 Offline — run ./server/start.sh on PC"
            }
        }
    }

    fun showSnack(msg: String) {
        scope.launch { snackbarHostState.showSnackbar(msg) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Profile", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
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
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── Profile Header ────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(listOf(Primary, PrimaryDark)),
                        RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)
                    )
                    .padding(horizontal = 20.dp, vertical = 28.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(84.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                            .padding(3.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Avatar(name = state.user.name, size = 78.dp)
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(state.user.name, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text(state.user.email, fontSize = 13.sp, color = Color.White.copy(alpha = 0.8f))
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = Color.White.copy(alpha = 0.2f)
                    ) {
                        Text(
                            "  ${state.user.role}  ",
                            fontSize = 12.sp,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        ProfileStatItem("${state.reportsSubmitted}", "Reports")
                        ProfileStatItem("${state.reportsResolved}", "Resolved")
                        ProfileStatItem("${state.inProgress}", "In Progress")
                    }
                }
            }

            // ── Municipal Administration Section ─────────────────────────────
            if (state.user.role.equals("Municipal", ignoreCase = true)) {
                ProfileSection(title = "Municipal Administration") {
                    ProfileListItem(
                        icon = Icons.Default.AdminPanelSettings,
                        title = "Open Municipal Admin Dashboard",
                        subtitle = "Manage civic reports, update statuses & view metrics",
                        onClick = onOpenAdminDashboard
                    )
                }
            }

            // ── Account Section ───────────────────────────────────────────────
            ProfileSection(title = "Account") {
                ProfileListItem(Icons.Default.Edit, "Edit Profile",
                    onClick = { showSnack("Edit Profile — coming soon!") })
                ProfileListItem(Icons.Default.Person, "Personal Information",
                    subtitle = state.user.phone,
                    onClick = { showSnack("Personal Information — coming soon!") })
            }

            // ── Preferences Section ───────────────────────────────────────────
            ProfileSection(title = "Preferences") {
                ProfileListItem(
                    Icons.Default.Notifications, "Notifications",
                    trailingContent = {
                        Switch(
                            checked = state.notificationsEnabled,
                            onCheckedChange = { viewModel.toggleNotifications() },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Primary,
                                checkedTrackColor = PrimaryLight
                            )
                        )
                    }
                )
                ProfileListItem(
                    Icons.Default.DarkMode, "Dark Mode",
                    trailingContent = {
                        Switch(
                            checked = isDarkTheme,
                            onCheckedChange = { onThemeToggle() },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Primary,
                                checkedTrackColor = PrimaryLight
                            )
                        )
                    }
                )
            }

            // ── Local PC Image Server Section ────────────────────────────────
            ProfileSection(title = "Local PC Image Server") {
                ProfileListItem(
                    icon = Icons.Default.Computer,
                    title = "PC Server: ${ServerConfig.getBaseUrl(context)}",
                    subtitle = serverStatusText ?: "Tap to test connection or configure laptop IP",
                    trailingContent = {
                        IconButton(onClick = {
                            scope.launch {
                                isCheckingServer = true
                                val res = ServerConfig.pingServer(context)
                                isCheckingServer = false
                                if (res.isSuccess) {
                                    serverStatusText = "🟢 " + res.getOrNull()
                                    showSnack("✅ Connected to Laptop Server!")
                                } else {
                                    serverStatusText = "🔴 Offline — start server/start.sh"
                                    showSnack("❌ Laptop server offline: ${res.exceptionOrNull()?.message}")
                                }
                            }
                        }) {
                            if (isCheckingServer) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = "Test connection", tint = Primary)
                            }
                        }
                    },
                    onClick = {
                        serverHost = ServerConfig.getServerHost(context)
                        showServerDialog = true
                    }
                )
            }

            // ── Support Section ───────────────────────────────────────────────
            ProfileSection(title = "AI & Support") {
                ProfileListItem(
                    icon = Icons.Default.AutoAwesome,
                    title = "FixMyCity AI Copilot",
                    subtitle = "Chat with smart assistant about civic issues & rules",
                    onClick = onOpenAiChat
                )
                ProfileListItem(Icons.Default.Help, "Help & Support",
                    onClick = { showSnack("Help & Support — coming soon!") })
                ProfileListItem(Icons.Default.BugReport, "Report a Problem",
                    onClick = { showSnack("Report a Problem — coming soon!") })
                ProfileListItem(Icons.Default.Info, "About FixMyCity", onClick = onAbout)
            }

            // ── Legal Section ─────────────────────────────────────────────────
            ProfileSection(title = "Legal") {
                ProfileListItem(Icons.Default.PrivacyTip, "Privacy Policy",
                    onClick = { showSnack("Privacy Policy — coming soon!") })
                ProfileListItem(Icons.Default.Description, "Terms of Service",
                    onClick = { showSnack("Terms of Service — coming soon!") })
            }

            // ── Logout ────────────────────────────────────────────────────────
            OutlinedButton(
                onClick = onLogout,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Error),
                border = ButtonDefaults.outlinedButtonBorder
            ) {
                Icon(Icons.Default.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Logout", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            }

            Text(
                "FixMyCity v1.0.0",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                textAlign = TextAlign.Center
            )
        }
    }

    if (showServerDialog) {
        var inputHost by remember { mutableStateOf(serverHost) }
        AlertDialog(
            onDismissRequest = { showServerDialog = false },
            title = {
                Text("Laptop Server Configuration", fontWeight = FontWeight.Bold, fontSize = 17.sp)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Set the IP address of your laptop where server.py is running. All complaint pictures will be uploaded and fetched directly from your PC.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = inputHost,
                        onValueChange = { inputHost = it },
                        label = { Text("Laptop IP / Host") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )
                    Text("Quick Presets:", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        SuggestionChip(
                            onClick = { inputHost = ServerConfig.DEFAULT_EMULATOR_HOST },
                            label = { Text("10.0.2.2 (Emulator)", fontSize = 10.sp) }
                        )
                        SuggestionChip(
                            onClick = { inputHost = ServerConfig.DEFAULT_LAN_HOST },
                            label = { Text("Wi-Fi LAN", fontSize = 10.sp) }
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val clean = inputHost.trim()
                        if (clean.isNotBlank()) {
                            ServerConfig.setServerHost(context, clean)
                            serverHost = clean
                            scope.launch {
                                val test = ServerConfig.pingServer(context)
                                if (test.isSuccess) {
                                    serverStatusText = "🟢 " + test.getOrNull()
                                    showSnack("✅ Connected to $clean:8080")
                                } else {
                                    serverStatusText = "🔴 Offline at $clean:8080"
                                }
                            }
                        }
                        showServerDialog = false
                    },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Save & Connect")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    ServerConfig.resetServerHost(context)
                    serverHost = ServerConfig.getServerHost(context)
                    showServerDialog = false
                    showSnack("Server host reset to automatic detection")
                }) {
                    Text("Auto Detect")
                }
            }
        )
    }
}

@Composable
private fun ProfileStatItem(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text(label, fontSize = 11.sp, color = Color.White.copy(alpha = 0.75f))
    }
}

@Composable
private fun ProfileSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
        Text(
            title,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 6.dp, start = 4.dp)
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(1.dp)
        ) {
            Column(content = content)
        }
    }
}

@Composable
private fun ProfileListItem(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit = {},
    trailingContent: @Composable (() -> Unit)? = null
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(PrimaryLight),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = Primary, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                if (subtitle != null) {
                    Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (trailingContent != null) {
                trailingContent()
            } else {
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        HorizontalDivider(
            modifier = Modifier.padding(start = 68.dp),
            color = Border.copy(alpha = 0.4f)
        )
    }
}
