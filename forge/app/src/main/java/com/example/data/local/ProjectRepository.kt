package com.example.data.local

import android.content.Context
import com.example.data.model.Project
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

/**
 * File-backed project store using kotlinx.serialization — replaces the former
 * Room database + DAO (which required a KSP annotation processor). Projects are
 * persisted as a single JSON array in filesDir/projects.json. A [MutableStateFlow]
 * makes reads reactive, matching Room's Flow-emits-on-change behavior so the
 * existing ViewModel collectors keep working unchanged.
 */
class ProjectRepository(context: Context) {

    private val file = File(context.filesDir, "projects.json")
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }
    private val listSerializer = ListSerializer(Project.serializer())
    private val projects = MutableStateFlow(load())

    /** All projects, newest first (mirrors the old `ORDER BY createdAt DESC`). */
    val allProjects: Flow<List<Project>> =
        projects.map { list -> list.sortedByDescending { it.createdAt } }

    fun getProjectById(id: Int): Flow<Project?> =
        projects.map { list -> list.find { it.id == id } }

    /** Insert (REPLACE semantics). Assigns a new id when [Project.id] is 0; returns the id. */
    suspend fun insertProject(project: Project): Long {
        val current = projects.value
        val newId = if (project.id != 0) project.id else (current.maxOfOrNull { it.id } ?: 0) + 1
        val record = project.copy(id = newId)
        projects.value = current.filterNot { it.id == newId } + record
        persist()
        return newId.toLong()
    }

    suspend fun updateProject(project: Project) {
        projects.value = projects.value.map { if (it.id == project.id) project else it }
        persist()
    }

    suspend fun deleteProject(project: Project) = deleteProjectById(project.id)

    suspend fun deleteProjectById(id: Int) {
        projects.value = projects.value.filterNot { it.id == id }
        persist()
    }

    private fun load(): List<Project> = try {
        if (file.exists()) json.decodeFromString(listSerializer, file.readText()) else emptyList()
    } catch (e: Exception) {
        emptyList()
    }

    private fun persist() {
        try {
            file.writeText(json.encodeToString(listSerializer, projects.value))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
