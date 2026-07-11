package com.example.data.model

import kotlinx.serialization.Serializable

/**
 * A persisted Forge project. Formerly a Room @Entity; now a plain
 * kotlinx.serialization model stored as JSON by [com.example.data.local.ProjectRepository].
 */
@Serializable
data class Project(
    val id: Int = 0,
    val name: String,
    val objective: String,
    val status: String, // pending, in_progress, done, failed, escalated
    val stateJson: String, // Serialized ForgeState JSON
    val score: Int = 0,
    val createdAt: Long = 0L
)
