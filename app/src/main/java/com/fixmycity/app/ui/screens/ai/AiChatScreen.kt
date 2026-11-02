package com.fixmycity.app.ui.screens.ai

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fixmycity.app.data.model.Report
import com.fixmycity.app.data.model.ReportStatus
import com.fixmycity.app.data.repository.FixMyCityRepository
import com.fixmycity.app.ui.theme.*
import com.fixmycity.app.utils.ServerConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

enum class Sender { USER, AI }

enum class ChatActionType {
    REPORT_ISSUE,
    VIEW_MY_REPORTS,
    VIEW_REPORT_DETAIL,
    CALL_HELPLINE,
    NONE
}

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val sender: Sender,
    val text: String,
    val timestamp: String = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date()),
    val actionType: ChatActionType = ChatActionType.NONE,
    val actionLabel: String? = null,
    val actionPayload: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiChatScreen(
    onNavigateBack: () -> Unit,
    onNavigateToReport: (category: String?) -> Unit,
    onNavigateToMyReports: () -> Unit = {},
    onNavigateToReportDetail: (reportId: String) -> Unit,
    repository: FixMyCityRepository = FixMyCityRepository.getInstance()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val listState = rememberLazyListState()

    val currentUser by repository.currentUser.collectAsState()
    val reports by repository.reports.collectAsState()

    var inputText by remember { mutableStateOf("") }
    var isThinking by remember { mutableStateOf(false) }
    var activeEngineInfo by remember { mutableStateOf("Ollama LLM • RAG Active") }

    val messages = remember {
        mutableStateListOf(
            ChatMessage(
                sender = Sender.AI,
                text = "Hello ${currentUser.name}! 👋 I am FixMyCity Civic AI Copilot, powered by local LLM intelligence.\n\nI can:\n• 📊 Summarize reports in an area (e.g. \"Summarize reports in Vashi\")\n• 🔍 Track your complaints & check SLAs\n• 📝 Draft formal complaints for fast submission\n• 🏛️ Explain municipal rules, helplines & schedules\n\nWhat would you like to explore today?",
                actionType = ChatActionType.NONE
            )
        )
    }

    val quickQuestions = listOf(
        "📊 Summarize reports in Vashi",
        "📍 Issues in Sector 17",
        "🚨 Summarize open potholes",
        "📝 Draft a complaint for me",
        "🔍 Track my complaints",
        "⏱️ How long do repairs take?",
        "📞 Emergency city helplines",
        "🧹 Garbage collection schedule"
    )

    fun scrollToBottom() {
        scope.launch {
            if (messages.isNotEmpty()) {
                listState.animateScrollToItem(messages.size - 1)
            }
        }
    }

    fun handleSend(text: String) {
        val query = text.trim()
        if (query.isBlank() || isThinking) return

        focusManager.clearFocus()
        inputText = ""

        // Append user message
        messages.add(ChatMessage(sender = Sender.USER, text = query))
        scrollToBottom()

        // Asynchronous AI reasoning via Laptop Ollama / Local Engine
        scope.launch {
            isThinking = true
            val aiResult = ServerConfig.queryAiChat(
                context = context,
                query = query,
                userId = currentUser.id,
                userName = currentUser.name,
                reports = reports
            )
            isThinking = false

            activeEngineInfo = if (aiResult.source == "ollama") "🧠 Ollama (${aiResult.model})" else "⚡ Local Civic Engine"

            val parsedAction = when (aiResult.actionType.uppercase()) {
                "REPORT_ISSUE" -> ChatActionType.REPORT_ISSUE
                "VIEW_MY_REPORTS" -> ChatActionType.VIEW_MY_REPORTS
                "VIEW_REPORT_DETAIL" -> ChatActionType.VIEW_REPORT_DETAIL
                "CALL_HELPLINE" -> ChatActionType.CALL_HELPLINE
                else -> ChatActionType.NONE
            }

            messages.add(
                ChatMessage(
                    sender = Sender.AI,
                    text = aiResult.text.replace("**", "").replace("__", ""),
                    actionType = parsedAction,
                    actionLabel = aiResult.actionLabel,
                    actionPayload = aiResult.actionPayload
                )
            )
            scrollToBottom()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        listOf(Primary, Color(0xFF6366F1), Color(0xFFEC4899))
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Civic AI Copilot", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = Color(0xFF10B981).copy(alpha = 0.15f)
                                ) {
                                    Text("ONLINE", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF10B981), modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                                }
                            }
                            Text(activeEngineInfo, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        messages.clear()
                        messages.add(
                            ChatMessage(
                                sender = Sender.AI,
                                text = "Conversation reset. What would you like to know or report next?"
                            )
                        )
                    }) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "Clear Chat", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .imePadding()
            ) {
                // Quick Suggestion Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    quickQuestions.forEach { prompt ->
                        SuggestionChip(
                            onClick = { handleSend(prompt.substringAfter(" ")) },
                            label = { Text(prompt, fontSize = 11.sp) },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                            ),
                            shape = RoundedCornerShape(16.dp)
                        )
                    }
                }

                // Input Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = { Text("Ask anything about city services...", fontSize = 13.sp) },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp, max = 110.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Primary,
                            unfocusedBorderColor = Border
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { handleSend(inputText) }),
                        maxLines = 3
                    )

                    Spacer(Modifier.width(8.dp))

                    FilledIconButton(
                        onClick = { handleSend(inputText) },
                        modifier = Modifier.size(46.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = if (inputText.isNotBlank()) Primary else Primary.copy(alpha = 0.5f)
                        ),
                        enabled = inputText.isNotBlank() || isThinking
                    ) {
                        Icon(Icons.Default.Send, contentDescription = "Send", tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(messages, key = { it.id }) { msg ->
                ChatBubble(
                    message = msg,
                    onActionClick = { actionType, payload ->
                        when (actionType) {
                            ChatActionType.REPORT_ISSUE -> {
                                val cleanCat = payload?.takeIf { it.isNotBlank() && it != "null" }
                                onNavigateToReport(cleanCat)
                            }
                            ChatActionType.VIEW_MY_REPORTS -> {
                                onNavigateToMyReports()
                            }
                            ChatActionType.VIEW_REPORT_DETAIL -> {
                                val cleanId = payload?.takeIf { it.isNotBlank() && it != "null" }
                                if (cleanId != null) {
                                    onNavigateToReportDetail(cleanId)
                                } else {
                                    onNavigateToMyReports()
                                }
                            }
                            ChatActionType.CALL_HELPLINE -> {
                                val phone = payload?.takeIf { it.isNotBlank() && it != "null" } ?: "1916"
                                val intent = Intent(Intent.ACTION_DIAL).apply {
                                    data = Uri.parse("tel:$phone")
                                }
                                context.startActivity(intent)
                            }
                            ChatActionType.NONE -> {}
                        }
                    }
                )
            }

            if (isThinking) {
                item {
                    AiTypingIndicator()
                }
            }
        }
    }
}

@Composable
private fun ChatBubble(
    message: ChatMessage,
    onActionClick: (ChatActionType, String?) -> Unit
) {
    val isUser = message.sender == Sender.USER

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (!isUser) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
            }
            Spacer(Modifier.width(8.dp))
        }

        Column(
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = 310.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(
                    topStart = 18.dp,
                    topEnd = 18.dp,
                    bottomStart = if (isUser) 18.dp else 4.dp,
                    bottomEnd = if (isUser) 4.dp else 18.dp
                ),
                color = if (isUser) Primary else MaterialTheme.colorScheme.surface,
                shadowElevation = if (isUser) 0.dp else 1.5.dp,
                border = if (isUser) null else androidx.compose.foundation.BorderStroke(1.dp, Border.copy(alpha = 0.6f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = message.text.replace("**", "").replace("__", ""),
                        fontSize = 13.5.sp,
                        lineHeight = 20.sp,
                        color = if (isUser) Color.White else MaterialTheme.colorScheme.onSurface
                    )

                    // Action button if AI recommended an action
                    if (message.actionType != ChatActionType.NONE && message.actionLabel != null) {
                        Spacer(Modifier.height(10.dp))
                        Button(
                            onClick = { onActionClick(message.actionType, message.actionPayload) },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (message.actionType == ChatActionType.CALL_HELPLINE) Error else Primary
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            val icon = when (message.actionType) {
                                ChatActionType.REPORT_ISSUE -> Icons.Default.AddCircle
                                ChatActionType.VIEW_MY_REPORTS -> Icons.Default.List
                                ChatActionType.VIEW_REPORT_DETAIL -> Icons.Default.Visibility
                                ChatActionType.CALL_HELPLINE -> Icons.Default.Phone
                                ChatActionType.NONE -> Icons.Default.ArrowForward
                            }
                            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(message.actionLabel, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
            Text(
                text = message.timestamp,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}

@Composable
private fun AiTypingIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "dots")
    val dot1Alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600, 0, FastOutSlowInEasing), RepeatMode.Reverse),
        label = "d1"
    )
    val dot2Alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600, 200, FastOutSlowInEasing), RepeatMode.Reverse),
        label = "d2"
    )
    val dot3Alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600, 400, FastOutSlowInEasing), RepeatMode.Reverse),
        label = "d3"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 36.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 1.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(Primary.copy(alpha = dot1Alpha)))
                Box(Modifier.size(7.dp).clip(CircleShape).background(Primary.copy(alpha = dot2Alpha)))
                Box(Modifier.size(7.dp).clip(CircleShape).background(Primary.copy(alpha = dot3Alpha)))
                Spacer(Modifier.width(6.dp))
                Text("AI is typing...", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}


