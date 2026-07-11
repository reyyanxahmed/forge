package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.*
import com.example.ui.theme.*
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawBehind
import com.example.ui.viewmodel.ForgeViewModel
import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressScreen(
    viewModel: ForgeViewModel,
    project: Project,
    state: ForgeState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var feedExpanded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val liveLabel by viewModel.liveLabel.collectAsState()
    val liveStream by viewModel.liveStream.collectAsState()
    val reasoningBlocks by viewModel.reasoningBlocks.collectAsState()

    // Track which reasoning blocks are expanded (by index)
    val expandedBlocks = remember { mutableStateMapOf<Int, Boolean>() }

    // Determine current phase based on last session log
    val lastLog = state.sessionLog.lastOrNull() ?: ""
    val currentPhaseColor = when {
        lastLog.contains("[Sense]") -> PhaseSenseColor
        lastLog.contains("[Decide]") -> PhaseDecideColor
        lastLog.contains("[Act]") -> PhaseActColor
        lastLog.contains("[Check]") -> PhaseCheckColor
        else -> MaterialTheme.colorScheme.primary
    }

    val currentPhaseName = when {
        lastLog.contains("[Sense]") -> "SENSING"
        lastLog.contains("[Decide]") -> "DECIDING"
        lastLog.contains("[Act]") -> "ACTING"
        lastLog.contains("[Check]") -> "CHECKING"
        else -> "PROCESSING"
    }

    // Pulse animation for LIVE badge
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background,
                        currentPhaseColor.copy(alpha = 0.05f),
                        MaterialTheme.colorScheme.background
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = if (feedExpanded) 0.dp else 80.dp) // Leave space for fixed bar
        ) {
            // Header bar
            CenterAlignedTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .alpha(if (project.status == "in_progress") pulseAlpha else 0f)
                                .background(PhaseActColor, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (project.status == "done") "COMPLETED" else "LIVE FORGE",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            ),
                            color = currentPhaseColor
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("back_button")
                    ) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (project.status == "in_progress" || project.status == "escalated") {
                        IconButton(
                            onClick = { viewModel.stopSimulation() },
                            modifier = Modifier.testTag("stop_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Cancel,
                                contentDescription = "Stop Forge",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent
                )
            )

            // Goal Header
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(
                    containerColor = com.example.ui.theme.BentoCardHero
                ),
                border = BorderStroke(1.dp, com.example.ui.theme.BentoBorder)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(PhaseActColor, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "AGENT WORKING",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            ),
                            color = com.example.ui.theme.BentoPrimary
                        )
                    }
                    Text(
                        text = project.objective,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Light,
                            lineHeight = 22.sp
                        ),
                        color = Color.White,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Scrollable central area for animated task graph
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 120.dp)
                ) {
                    // ---- Completed reasoning blocks (expandable, like Claude Code) ----
                    if (reasoningBlocks.isNotEmpty()) {
                        item {
                            Text(
                                text = "REASONING LOG",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 1.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                        itemsIndexed(reasoningBlocks) { index, block ->
                            val isExpanded = expandedBlocks[index] ?: false
                            ExpandableReasoningCard(
                                block = block,
                                isExpanded = isExpanded,
                                onToggle = { expandedBlocks[index] = !isExpanded }
                            )
                        }
                    }

                    // ---- Active live stream (what Gemma is writing right now) ----
                    if (liveLabel.isNotEmpty() || liveStream.isNotEmpty()) {
                        item {
                            val streamScroll = rememberScrollState()
                            LaunchedEffect(liveStream) { streamScroll.scrollTo(streamScroll.maxValue) }
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1115)),
                                border = BorderStroke(1.dp, currentPhaseColor.copy(alpha = 0.45f))
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .alpha(pulseAlpha)
                                                .background(PhaseActColor, CircleShape)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = liveLabel.ifEmpty { "Gemma is working" },
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                            color = currentPhaseColor
                                        )
                                        Spacer(Modifier.weight(1f))
                                        Text(
                                            text = "${liveStream.length} chars",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color(0xFF64748B)
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = 200.dp)
                                            .verticalScroll(streamScroll)
                                    ) {
                                        HighlightedConsoleStream(liveStream)
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Text(
                            text = "AGENT TASK PIPELINE",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }

                    items(state.plan) { task ->
                        TaskPipelineCard(
                            task = task,
                            currentPhaseColor = currentPhaseColor,
                            currentPhaseName = currentPhaseName,
                            isLive = project.status == "in_progress"
                        )
                    }
                }
            }

            // Bottom Console Log panel
            ConsoleLogPanel(
                logs = state.sessionLog,
                expanded = feedExpanded,
                onToggleExpand = { feedExpanded = !feedExpanded }
            )
        }

        // Bottom Dashboard Fixed Bar (Only if console log is collapsed)
        if (!feedExpanded) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                MaterialTheme.colorScheme.background.copy(alpha = 0.95f),
                                MaterialTheme.colorScheme.background
                            )
                        )
                    )
                    .padding(16.dp)
            ) {
                BottomDashboardBar(
                    project = project,
                    state = state,
                    currentPhaseColor = currentPhaseColor
                )
            }
        }

        // Escalation Dialog modal overlay
        if (project.status == "escalated") {
            val escalation = state.escalations.lastOrNull()
            if (escalation != null) {
                EscalationOverlay(
                    escalation = escalation,
                    onSubmitGuidance = { guidance ->
                        viewModel.submitGuidance(guidance)
                    },
                    onCancel = {
                        viewModel.stopSimulation()
                    }
                )
            }
        }

        // Project Success Celebration Modal
        if (project.status == "done") {
            ProjectCompleteOverlay(
                project = project,
                onDismiss = onBack
            )
        }
    }
}

@Composable
fun TaskPipelineCard(
    task: Task,
    currentPhaseColor: Color,
    currentPhaseName: String,
    isLive: Boolean
) {
    val scale = remember { Animatable(0.95f) }
    LaunchedEffect(task.status) {
        if (task.status == "in_progress" || task.status == "done") {
            scale.animateTo(
                targetValue = 1.02f,
                animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium)
            )
            scale.animateTo(
                targetValue = 1.0f,
                animationSpec = spring(dampingRatio = 0.6f)
            )
        }
    }

    val containerColor = when (task.status) {
        "done" -> com.example.ui.theme.BentoCardNormal
        "in_progress" -> com.example.ui.theme.BentoCardActive
        "escalated" -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f)
        else -> com.example.ui.theme.BentoCardPending
    }

    val statusColor = when (task.status) {
        "done" -> PhaseActColor
        "in_progress" -> PhaseDecideColor
        "escalated" -> MaterialTheme.colorScheme.error
        else -> com.example.ui.theme.BentoBorder
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale.value)
            .testTag("task_card_${task.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, com.example.ui.theme.BentoBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left color bar (Bento style border-l-4)
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(color = statusColor)
            )

            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Dynamic animative status icon
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(
                            color = statusColor.copy(alpha = 0.15f),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    StatusIndicatorIcon(status = task.status, currentPhaseColor = currentPhaseColor)
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "TASK ${task.id}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            ),
                            color = statusColor
                        )
                        if (task.status == "in_progress") {
                            Text(
                                text = currentPhaseName,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = PhaseDecideColor,
                                modifier = Modifier
                                    .background(PhaseDecideColor.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = task.description,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (task.status == "pending") MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurface
                    )
                    if (task.dependsOn.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Depends on Task ${task.dependsOn.joinToString()}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StatusIndicatorIcon(status: String, currentPhaseColor: Color) {
    val infiniteTransition = rememberInfiniteTransition(label = "spin")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    when (status) {
        "done" -> Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = "Done",
            tint = PhaseActColor,
            modifier = Modifier.size(24.dp)
        )
        "in_progress" -> Icon(
            imageVector = Icons.Default.Autorenew,
            contentDescription = "In Progress",
            tint = currentPhaseColor,
            modifier = Modifier
                .size(24.dp)
                .scale(1.1f)
                .rotate(rotation)
        )
        "escalated" -> Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = "Escalated",
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(24.dp)
        )
        else -> Icon(
            imageVector = Icons.Default.HourglassEmpty,
            contentDescription = "Pending",
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(20.dp)
        )
    }
}

// Extension to rotate Modifier
fun Modifier.rotate(degrees: Float): Modifier = this.then(
    Modifier.drawBehind {
        rotate(degrees) {
            // handled automatically inside graphicsLayer or drawBehind
        }
    }.graphicsLayer(rotationZ = degrees)
)

@Composable
fun ConsoleLogPanel(
    logs: List<String>,
    expanded: Boolean,
    onToggleExpand: () -> Unit
) {
    val height by animateDpAsState(
        targetValue = if (expanded) 350.dp else 120.dp,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium),
        label = "height"
    )

    val listState = rememberLazyListState()
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("console_panel"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF0C0E12) // Charcoal dark console
        ),
        border = BorderStroke(1.dp, Color(0xFF1E293B))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Drag handle / Title Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpand() }
                    .padding(vertical = 10.dp, horizontal = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Terminal,
                        contentDescription = null,
                        tint = PhaseSenseColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "LIVE ACTIVITY FEED",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.sp
                        ),
                        color = Color(0xFF94A3B8)
                    )
                }

                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = Color(0xFF94A3B8),
                    modifier = Modifier.size(20.dp)
                )
            }

            Divider(color = Color(0xFF1E293B))

            // Console Logs
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(logs) { log ->
                    ConsoleLogLine(log)
                }
            }
        }
    }
}

@Composable
fun ConsoleLogLine(log: String) {
    // Parse prefixes e.g. [Sense] "Starting project..."
    val prefix = when {
        log.startsWith("[Sense]") -> "[Sense]"
        log.startsWith("[Decide]") -> "[Decide]"
        log.startsWith("[Act]") -> "[Act]"
        log.startsWith("[Check]") -> "[Check]"
        else -> ""
    }

    val content = if (prefix.isNotEmpty()) log.substring(prefix.length).trim() else log

    val badgeColor = when (prefix) {
        "[Sense]" -> PhaseSenseColor
        "[Decide]" -> PhaseDecideColor
        "[Act]" -> PhaseActColor
        "[Check]" -> PhaseCheckColor
        else -> Color.White
    }

    val badgeLabel = when (prefix) {
        "[Sense]" -> "Sense"
        "[Decide]" -> "Decide"
        "[Act]" -> "Act"
        "[Check]" -> "Check"
        else -> ""
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.Top
    ) {
        if (badgeLabel.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .padding(end = 8.dp, top = 2.dp)
                    .background(badgeColor.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                    .border(1.dp, badgeColor.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 5.dp, vertical = 1.dp)
            ) {
                Text(
                    text = badgeLabel.uppercase(),
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.ExtraBold,
                    color = badgeColor
                )
            }
        }

        // Parse models (like [gemma-4-e4b] or [gemma-4-e2b])
        if (content.contains("[gemma")) {
            val modelStart = content.indexOf("[gemma")
            val modelEnd = content.indexOf("]", modelStart)
            if (modelStart != -1 && modelEnd != -1) {
                val beforeModel = content.substring(0, modelStart)
                val modelName = content.substring(modelStart, modelEnd + 1)
                val afterModel = content.substring(modelEnd + 1)

                Row {
                    Text(
                        text = beforeModel,
                        color = Color(0xFFCBD5E1),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    )
                    Text(
                        text = modelName,
                        color = PhaseDecideColor,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = afterModel,
                        color = Color(0xFFCBD5E1),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    )
                }
                return
            }
        }

        Text(
            text = content,
            color = Color(0xFFCBD5E1),
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun BottomDashboardBar(
    project: Project,
    state: ForgeState,
    currentPhaseColor: Color
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("dashboard_bar"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
        ),
        border = BorderStroke(1.dp, currentPhaseColor.copy(alpha = 0.4f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when (project.status) {
                        "done" -> "Forge completed"
                        "escalated" -> "Needs Human Input"
                        else -> "Forging offline..."
                    },
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = currentPhaseColor
                )
                Spacer(modifier = Modifier.height(2.dp))
                // Progress string
                Text(
                    text = when {
                        project.status == "done" -> "✓ 2/2 tasks complete"
                        project.status == "escalated" -> "Waiting for guide response..."
                        state.sessionLog.lastOrNull()?.contains("MainScreen") == true -> "Writing MainScreen.kt..."
                        state.sessionLog.lastOrNull()?.contains("compile") == true -> "Compiling..."
                        state.sessionLog.lastOrNull()?.contains("Hypothesis") == true -> "Self-healing error..."
                        else -> "Planning engine..."
                    },
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // File counter badge
                Row(
                    modifier = Modifier
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Article,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${state.fileLedger.size} files",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // Token Badge
                Row(
                    modifier = Modifier
                        .background(
                            PhaseDecideColor.copy(alpha = 0.1f),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = null,
                        tint = PhaseDecideColor,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Gemma 4",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = PhaseDecideColor
                    )
                }
            }
        }
    }
}

@Composable
fun EscalationOverlay(
    escalation: Escalation,
    onSubmitGuidance: (String) -> Unit,
    onCancel: () -> Unit
) {
    var textResponse by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f))
            .clickable(enabled = false) {},
        contentAlignment = Alignment.BottomCenter
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .testTag("escalation_sheet"),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.error)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Warning Header
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Forge needs your guidance",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Structured breakdown
                // [KNOWS]
                StructuredSection(
                    label = "KNOWS",
                    content = "Generated a self-contained single-file web app and validated it in an on-device WebView sandbox.",
                    color = PhaseSenseColor
                )
                Spacer(modifier = Modifier.height(8.dp))

                // [TRIED]
                StructuredSection(
                    label = "TRIED",
                    content = escalation.context,
                    color = PhaseDecideColor
                )
                Spacer(modifier = Modifier.height(8.dp))

                // [NEED]
                StructuredSection(
                    label = "NEED",
                    content = escalation.question,
                    color = MaterialTheme.colorScheme.error
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Input
                OutlinedTextField(
                    value = textResponse,
                    onValueChange = { textResponse = it },
                    placeholder = {
                        Text(
                            "e.g., Elevate tabState to MainViewModel and observe as state flow...",
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .testTag("escalation_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("escalation_cancel"),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Text("Cancel Project")
                    }

                    Button(
                        onClick = {
                            if (textResponse.isNotBlank()) {
                                onSubmitGuidance(textResponse)
                            }
                        },
                        enabled = textResponse.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier
                            .weight(1.2f)
                            .height(48.dp)
                            .testTag("escalation_submit"),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Guide Forge")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StructuredSection(label: String, content: String, color: Color) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .width(64.dp)
                .background(color.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                .padding(vertical = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = content,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun ProjectCompleteOverlay(
    project: Project,
    onDismiss: () -> Unit
) {
    val state = remember(project) { com.example.util.JsonUtils.jsonToForgeState(project.stateJson) }
    var showLiveApp by remember { mutableStateOf(false) }
    var showSource by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current

    // Real stats from actual state
    val tasksDone = state?.plan?.count { it.status == "done" } ?: 0
    val tasksTotal = state?.plan?.size ?: 0
    val filesCount = state?.fileLedger?.size ?: 0
    val healedErrors = state?.hypotheses?.count { it.outcome == "resolved" } ?: 0
    val score = project.score

    // Share intent for the generated app
    fun shareApp() {
        try {
            val artifactDir = java.io.File(context.filesDir, "projects/${project.id}")
            val htmlFile = java.io.File(artifactDir, "index.html")
            if (htmlFile.exists()) {
                val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "text/html"
                    androidx.core.content.FileProvider.getUriForFile(
                        context, "${context.packageName}.fileprovider", htmlFile
                    ).let { uri ->
                        putExtra(android.content.Intent.EXTRA_STREAM, uri)
                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                }
                context.startActivity(android.content.Intent.createChooser(shareIntent, "Share ${project.name}"))
            }
        } catch (e: Exception) {
            Log.e("ProjectComplete", "Share failed: ${e.message}")
        }
    }

    if (showLiveApp) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showLiveApp = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = project.name,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        IconButton(onClick = { showLiveApp = false }) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                        }
                    }
                    com.example.ui.components.LiveAppPreview(
                        html = state?.artifactHtml ?: "",
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )
                }
            }
        }
    }

    // Source code viewer dialog
    if (showSource) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showSource = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color(0xFF0F1115)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "index.html",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            ),
                            color = PhaseSenseColor
                        )
                        IconButton(onClick = { showSource = false }) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                        }
                    }
                    val srcScroll = rememberScrollState()
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(srcScroll)
                            .padding(16.dp)
                    ) {
                        HighlightedConsoleStream(state?.artifactHtml ?: "")
                    }
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.75f))
            .clickable(enabled = false) {},
        contentAlignment = Alignment.Center
    ) {
        // Main completion card
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .padding(16.dp)
                .testTag("success_modal"),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(2.dp, PhaseActColor)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Success Badge
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(PhaseActColor.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = PhaseActColor,
                        modifier = Modifier.size(44.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Your app is ready",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = project.name,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Stats grid (2x2) — real values from state
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StatCard(
                            label = "Tasks Completed",
                            value = "$tasksDone / $tasksTotal",
                            icon = Icons.Default.AssignmentTurnedIn,
                            modifier = Modifier.weight(1f)
                        )
                        StatCard(
                            label = "Files Generated",
                            value = "$filesCount file${if (filesCount != 1) "s" else ""}",
                            icon = Icons.Default.Article,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StatCard(
                            label = "Self-Healed Errors",
                            value = "$healedErrors error${if (healedErrors != 1) "s" else ""}",
                            icon = Icons.Default.Build,
                            modifier = Modifier.weight(1f)
                        )
                        StatCard(
                            label = "Quality Score",
                            value = "$score / 100",
                            icon = Icons.Default.Star,
                            modifier = Modifier.weight(1f),
                            highlight = true
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Actions buttons
                Button(
                    onClick = { showLiveApp = true },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("launch_apk_button"),
                    shape = RoundedCornerShape(25.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Android, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Open App", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { showSource = true },
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag("view_source_button"),
                        shape = RoundedCornerShape(22.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Code, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Source", fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = { shareApp() },
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag("share_apk_button"),
                        shape = RoundedCornerShape(22.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Share", fontSize = 12.sp)
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(44.dp)
                            .testTag("dismiss_success_button")
                    ) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
fun StatCard(
    label: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    highlight: Boolean = false
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (highlight) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
        ),
        border = BorderStroke(
            1.dp,
            if (highlight) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = label,
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
fun ExpandableReasoningCard(
    block: ReasoningBlock,
    isExpanded: Boolean,
    onToggle: () -> Unit
) {
    val streamScroll = rememberScrollState()
    LaunchedEffect(isExpanded, block.content) {
        if (isExpanded) streamScroll.scrollTo(streamScroll.maxValue)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1115)),
        border = BorderStroke(1.dp, Color(0xFF1E293B).copy(alpha = 0.6f))
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            // Header row: label + slogan + chevron
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Psychology,
                    contentDescription = null,
                    tint = PhaseDecideColor,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = block.label,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = PhaseDecideColor,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(modifier = Modifier.weight(1f))
                if (!isExpanded) {
                    Text(
                        text = block.slogan,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        ),
                        color = Color(0xFF94A3B8),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(2f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = Color(0xFF64748B),
                    modifier = Modifier.size(18.dp)
                )
            }

            // Expandable content
            if (isExpanded) {
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = Color(0xFF1E293B))
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp)
                        .verticalScroll(streamScroll)
                ) {
                    HighlightedConsoleStream(block.content)
                }
            }
        }
    }
}

@Composable
fun HighlightedConsoleStream(text: String) {
    if (text.isEmpty()) {
        Text(
            text = "Waiting for local Gemma model...",
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
            color = Color(0xFF64748B)
        )
        return
    }

    val lines = text.split("\n").takeLast(35)
    Column {
        lines.forEach { line ->
            val color = when {
                line.trim().startsWith("<!doctype", ignoreCase = true) || line.trim().startsWith("<html", ignoreCase = true) || line.trim().startsWith("</html>", ignoreCase = true) -> PhaseCheckColor
                line.trim().startsWith("<script", ignoreCase = true) || line.trim().startsWith("</script>", ignoreCase = true) -> PhaseDecideColor
                line.trim().startsWith("<div", ignoreCase = true) || line.trim().startsWith("</div>", ignoreCase = true) || line.trim().startsWith("<p", ignoreCase = true) -> PhaseSenseColor
                line.trim().contains("class=", ignoreCase = true) || line.trim().contains("style=", ignoreCase = true) -> PhaseActColor
                line.trim().startsWith("<!--") || line.trim().startsWith("//") -> Color(0xFF475569) // Slate gray comments
                else -> Color(0xFFCBD5E1)
            }
            Text(
                text = line,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    fontWeight = if (color != Color(0xFFCBD5E1)) FontWeight.Bold else FontWeight.Normal
                ),
                color = color
            )
        }
    }
}
