package com.example.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Task(
    val id: String,
    val description: String,
    val status: String, // pending, in_progress, done, failed, escalated
    val dependsOn: List<String> = emptyList()
)

@Serializable
data class BuildLog(
    val command: String,
    val exitCode: Int,
    val stderr: String,
    val timestamp: Long
)

@Serializable
data class Hypothesis(
    val errorSignature: String,
    val diagnosis: String,
    val fixAttempted: String,
    val outcome: String // failed, resolved
)

@Serializable
data class Escalation(
    val question: String,
    val context: String
)

@Serializable
data class ReasoningBlock(
    val id: String,
    val label: String,       // e.g. "🧠 Planning tasks"
    val slogan: String,      // compact 1-line summary shown when collapsed
    val content: String,     // full token output
    val timestamp: Long,
    val isStreaming: Boolean = false
)

@Serializable
data class ForgeState(
    val objective: String,
    val plan: List<Task> = emptyList(),
    val fileLedger: Map<String, String> = emptyMap(),
    val buildHistory: List<BuildLog> = emptyList(),
    val hypotheses: List<Hypothesis> = emptyList(),
    val escalations: List<Escalation> = emptyList(),
    val sessionLog: List<String> = emptyList(),
    /** The generated self-contained single-file web app (index.html) — the live artifact. */
    val artifactHtml: String = ""
)
