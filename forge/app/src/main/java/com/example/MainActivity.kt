package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.agent.LocalLlmServer
import com.example.ui.screens.*
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.ForgeViewModel

class MainActivity : ComponentActivity() {
    private var localLlmServer: LocalLlmServer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Start local LLM server on launch
        localLlmServer = LocalLlmServer(applicationContext).apply { start() }

        setContent {
            MyApplicationTheme {
                MainAppContainer()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        localLlmServer?.stop()
    }
}

@Composable
fun MainAppContainer() {
    val viewModel: ForgeViewModel = viewModel()
    val activeScreen by viewModel.activeScreen.collectAsState()
    val projects by viewModel.allProjects.collectAsState()
    val currentProject by viewModel.currentProject.collectAsState()
    val currentForgeState by viewModel.currentForgeState.collectAsState()

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
        bottomBar = {
            // Only show bottom navigation on main tabs (Home=0, Library=2)
            if (activeScreen == 0 || activeScreen == 2) {
                NavigationBar(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .testTag("bottom_nav"),
                    tonalElevation = 8.dp
                ) {
                    NavigationBarItem(
                        selected = activeScreen == 0,
                        onClick = { viewModel.setScreen(0) },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "Forge Tab"
                            )
                        },
                        label = { Text("Forge") },
                        modifier = Modifier.testTag("nav_tab_forge")
                    )

                    NavigationBarItem(
                        selected = activeScreen == 2,
                        onClick = { viewModel.setScreen(2) },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = "Library Tab"
                            )
                        },
                        label = { Text("Library") },
                        modifier = Modifier.testTag("nav_tab_library")
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (activeScreen) {
                0 -> {
                    HomeScreen(
                        viewModel = viewModel,
                        projects = projects,
                        onSelectProject = { id ->
                            viewModel.selectProject(id)
                        }
                    )
                }
                1 -> {
                    val proj = currentProject
                    val state = currentForgeState
                    if (proj != null && state != null) {
                        if (proj.status == "done") {
                            DetailScreen(
                                viewModel = viewModel,
                                project = proj,
                                state = state,
                                onBack = { viewModel.deselectProject() }
                            )
                        } else {
                            ProgressScreen(
                                viewModel = viewModel,
                                project = proj,
                                state = state,
                                onBack = { viewModel.deselectProject() }
                            )
                        }
                    } else {
                        // Fallback state
                        viewModel.deselectProject()
                    }
                }
                2 -> {
                    LibraryScreen(
                        viewModel = viewModel,
                        projects = projects,
                        onSelectProject = { id ->
                            viewModel.selectProject(id)
                        }
                    )
                }
                3 -> {
                    SettingsScreen(
                        viewModel = viewModel,
                        onBack = { viewModel.setScreen(0) }
                    )
                }
            }
        }
    }
}
