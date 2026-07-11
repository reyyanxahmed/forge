package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.*
import com.example.ui.components.LiveAppPreview
import com.example.ui.theme.*
import com.example.ui.viewmodel.ForgeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    viewModel: ForgeViewModel,
    project: Project,
    state: ForgeState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selfHealingExpanded by remember { mutableStateOf(false) }
    var showFullPreview by remember { mutableStateOf(false) }

    if (showFullPreview && state.artifactHtml.isNotBlank()) {
        Dialog(
            onDismissRequest = { showFullPreview = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = project.name,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    IconButton(onClick = { showFullPreview = false }) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close preview")
                    }
                }
                LiveAppPreview(
                    html = state.artifactHtml,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // App Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.testTag("detail_back_button")
            ) {
                Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = project.name,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Project Specifications & Diagnostics",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            // Live App Preview — the actual running result
            if (state.artifactHtml.isNotBlank()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = BentoCardNormal),
                        border = BorderStroke(1.dp, BentoBorder)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "LIVE PREVIEW",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                                    color = BentoPrimary
                                )
                                TextButton(onClick = { showFullPreview = true }) {
                                    Text("Fullscreen", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            LiveAppPreview(
                                html = state.artifactHtml,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(420.dp)
                            )
                        }
                    }
                }
            }

            // Summary Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = com.example.ui.theme.BentoCardHero
                    ),
                    border = BorderStroke(1.dp, com.example.ui.theme.BentoBorder)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = "ORIGINAL GOAL",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                            color = com.example.ui.theme.BentoPrimary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = project.objective,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Light, lineHeight = 20.sp),
                            color = Color.White
                        )
                    }
                }
            }

            // Dashboard Bento Grid
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Left Column (weight 1.1f): Task Pipeline Bento Box
                    Column(
                        modifier = Modifier.weight(1.1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = BentoCardNormal),
                            border = BorderStroke(1.dp, BentoBorder)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(bottom = 12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = PhaseActColor,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "TASK PIPELINE",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 1.sp
                                        ),
                                        color = PhaseActColor
                                    )
                                }

                                if (state.plan.isEmpty()) {
                                    Text(
                                        text = "No tasks planned yet.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    )
                                } else {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        state.plan.forEach { task ->
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.CheckCircle,
                                                    contentDescription = "Done",
                                                    tint = PhaseActColor,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = task.description,
                                                    style = MaterialTheme.typography.bodySmall.copy(
                                                        fontWeight = FontWeight.SemiBold,
                                                        fontSize = 12.sp
                                                    ),
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis,
                                                    lineHeight = 15.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Right Column (weight 0.9f): Stacked Metrics + Files Bento Boxes
                    Column(
                        modifier = Modifier.weight(0.9f),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Card A: Project Stats
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = BentoCardNormal),
                            border = BorderStroke(1.dp, BentoBorder)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = "PROJECT METRICS",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp
                                    ),
                                    color = BentoPrimary,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Score: ${project.score}/100",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                ) {
                                    val (statusLabel, statusColor) = when (project.status) {
                                        "done" -> "Completed" to PhaseActColor
                                        "escalated" -> "Needs Input" to MaterialTheme.colorScheme.error
                                        "in_progress" -> "Forging..." to PhaseDecideColor
                                        else -> "Pending" to MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .background(statusColor, CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = statusLabel,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp
                                        ),
                                        color = statusColor
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = null,
                                        tint = PhaseCheckColor,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Builds: ${state.buildHistory.size}",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }

                        // Card B: Generated Files Ledger
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = BentoCardNormal),
                            border = BorderStroke(1.dp, BentoBorder)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = "FILE LEDGER",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp
                                    ),
                                    color = PhaseSenseColor,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )

                                if (state.fileLedger.isEmpty()) {
                                    Text(
                                        text = "No files found.",
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    )
                                } else {
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        state.fileLedger.keys.take(2).forEach { filePath ->
                                            val fileName = filePath.substringAfterLast("/")
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Outlined.Code,
                                                    contentDescription = null,
                                                    tint = PhaseSenseColor,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = fileName,
                                                    style = MaterialTheme.typography.bodySmall.copy(
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.SemiBold
                                                    ),
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                        if (state.fileLedger.size > 2) {
                                            Text(
                                                text = "+ ${state.fileLedger.size - 2} more files",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold
                                                ),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Expandable Hypotheses registry ("Self-Healing Log")
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selfHealingExpanded = !selfHealingExpanded }
                        .testTag("self_healing_toggle"),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.HealthAndSafety,
                                    contentDescription = null,
                                    tint = PhaseCheckColor,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Self-Healing Log (${state.hypotheses.size} events)",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Icon(
                                imageVector = if (selfHealingExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        AnimatedVisibility(visible = selfHealingExpanded) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                if (state.hypotheses.isEmpty()) {
                                    Text(
                                        text = "No compile failures encountered during this run. Zero self-heals needed.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    )
                                } else {
                                    state.hypotheses.forEach { hypothesis ->
                                        HypothesisItem(hypothesis = hypothesis)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Build History Timeline
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "COMPILATION TIMELINE",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    if (state.buildHistory.isEmpty()) {
                        Text(
                            text = "No compilations recorded.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    } else {
                        state.buildHistory.forEachIndexed { index, log ->
                            BuildHistoryTimelineRow(buildLog = log, isLast = index == state.buildHistory.size - 1)
                        }
                    }
                }
            }
        }

        // Action controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = { viewModel.startNewProject(project.objective) },
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("rebuild_project_button")
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Rebuild")
                }
            }

            Button(
                onClick = { if (state.artifactHtml.isNotBlank()) showFullPreview = true },
                enabled = state.artifactHtml.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .weight(1.2f)
                    .height(48.dp)
                    .testTag("open_apk_detail_button")
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Open App")
                }
            }
        }
    }
}

@Composable
fun StaticTaskRow(task: Task) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = com.example.ui.theme.BentoCardNormal
        ),
        border = BorderStroke(1.dp, com.example.ui.theme.BentoBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(PhaseActColor)
            )
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Done",
                    tint = PhaseActColor,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "TASK ${task.id}",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                        color = PhaseActColor
                    )
                    Text(
                        text = task.description,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
fun FileLedgerRow(filePath: String, description: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = com.example.ui.theme.BentoCardNormal
        ),
        border = BorderStroke(1.dp, com.example.ui.theme.BentoBorder)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Code,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = filePath.substringAfterLast("/"),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = description,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun HypothesisItem(hypothesis: Hypothesis) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF0F1115) // Deep contrast card
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (hypothesis.outcome == "resolved") PhaseActColor.copy(alpha = 0.4f)
            else MaterialTheme.colorScheme.error.copy(alpha = 0.4f)
        )
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ERROR: ${hypothesis.errorSignature}",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
                Box(
                    modifier = Modifier
                        .background(
                            color = if (hypothesis.outcome == "resolved") PhaseActColor.copy(alpha = 0.15f)
                            else MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
                            shape = CircleShape
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = hypothesis.outcome.uppercase(),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (hypothesis.outcome == "resolved") PhaseActColor else MaterialTheme.colorScheme.error
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Diagnosis: ${hypothesis.diagnosis}",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF94A3B8)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Fix Attempted: ${hypothesis.fixAttempted}",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFFCBD5E1)
            )
        }
    }
}

@Composable
fun BuildHistoryTimelineRow(buildLog: BuildLog, isLast: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        // Vertical connector drawing
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(24.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(
                        color = if (buildLog.exitCode == 0) PhaseActColor else MaterialTheme.colorScheme.error,
                        shape = CircleShape
                    )
            )
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(36.dp)
                        .background(Color.Gray.copy(alpha = 0.3f))
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column {
            Text(
                text = if (buildLog.exitCode == 0) "Build SUCCESS" else "Build FAILED (Exit Code ${buildLog.exitCode})",
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                color = if (buildLog.exitCode == 0) PhaseActColor else MaterialTheme.colorScheme.error
            )
            if (buildLog.stderr.isNotEmpty()) {
                Text(
                    text = buildLog.stderr,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
        }
    }
}
