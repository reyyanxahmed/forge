package com.example.ui.viewmodel

import android.app.Application
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.ProjectRepository
import com.example.data.model.*
import com.example.util.JsonUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale
import com.example.agent.*

class ForgeViewModel(application: Application) : AndroidViewModel(application), TextToSpeech.OnInitListener {

    private fun hasModelFile(): Boolean {
        val extDir = getApplication<Application>().getExternalFilesDir(null)
        val possiblePaths = listOf(
            "/sdcard/gemma-4-E4B-it.litertlm",
            "/sdcard/Download/gemma-4-E4B-it.litertlm",
            "/storage/emulated/0/gemma-4-E4B-it.litertlm",
            "/storage/emulated/0/Download/gemma-4-E4B-it.litertlm",
            java.io.File(getApplication<Application>().filesDir, "gemma-4-E4B-it.litertlm").absolutePath,
            if (extDir != null) java.io.File(extDir, "gemma-4-E4B-it.litertlm").absolutePath else ""
        )
        return possiblePaths.any { it.isNotEmpty() && java.io.File(it).exists() }
    }

    private val repository: ProjectRepository
    val allProjects: StateFlow<List<Project>>

    // Settings State
    private val _voiceNarrationEnabled = MutableStateFlow(true)
    val voiceNarrationEnabled = _voiceNarrationEnabled.asStateFlow()

    private val _shareUsageStats = MutableStateFlow(false)
    val shareUsageStats = _shareUsageStats.asStateFlow()

    // Current Project & Agent State
    private val _currentProjectId = MutableStateFlow<Int?>(null)
    val currentProjectId = _currentProjectId.asStateFlow()

    private val _currentProject = MutableStateFlow<Project?>(null)
    val currentProject = _currentProject.asStateFlow()

    private val _currentForgeState = MutableStateFlow<ForgeState?>(null)
    val currentForgeState = _currentForgeState.asStateFlow()

    // Live token stream from the model (what Gemma is writing right now) + a label
    // describing the current phase, so the UI can show generation as it happens.
    private val _liveStream = MutableStateFlow("")
    val liveStream = _liveStream.asStateFlow()
    private val _liveLabel = MutableStateFlow("")
    val liveLabel = _liveLabel.asStateFlow()

    // Active screen index (0: Home, 1: Progress, 2: Library, 3: Settings)
    private val _activeScreen = MutableStateFlow(0)
    val activeScreen = _activeScreen.asStateFlow()

    // TTS Engine
    private var tts: TextToSpeech? = null
    private var isTtsReady = false

    // Simulation Job
    private var simulationJob: Job? = null

    // For resuming after escalation
    private var continuationBlock: (suspend (String) -> Unit)? = null

    init {
        repository = ProjectRepository(application)
        allProjects = repository.allProjects.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        // Initialize TTS
        try {
            tts = TextToSpeech(application, this)
        } catch (e: Exception) {
            Log.e("ForgeViewModel", "TTS Initialization failed", e)
        }

        // Observe active project changes
        viewModelScope.launch {
            _currentProjectId.collect { id ->
                if (id != null) {
                    repository.getProjectById(id).collect { proj ->
                        _currentProject.value = proj
                        if (proj != null) {
                            val state = JsonUtils.jsonToForgeState(proj.stateJson)
                            _currentForgeState.value = state
                        } else {
                            _currentForgeState.value = null
                        }
                    }
                } else {
                    _currentProject.value = null
                    _currentForgeState.value = null
                }
            }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.let {
                val result = it.setLanguage(Locale.US)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e("ForgeViewModel", "Language is not supported")
                } else {
                    isTtsReady = true
                    speak("Forge engine initialized.")
                }
            }
        } else {
            Log.e("ForgeViewModel", "TTS Init failed")
        }
    }

    fun speak(text: String) {
        if (_voiceNarrationEnabled.value && isTtsReady) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "forge_narrator")
        }
    }

    fun toggleVoiceNarration() {
        _voiceNarrationEnabled.value = !_voiceNarrationEnabled.value
        if (_voiceNarrationEnabled.value) {
            speak("Voice narration activated.")
        }
    }

    fun toggleShareUsageStats() {
        _shareUsageStats.value = !_shareUsageStats.value
    }

    fun setScreen(index: Int) {
        _activeScreen.value = index
    }

    fun selectProject(projectId: Int) {
        _currentProjectId.value = projectId
        setScreen(1) // Open Progress or Detail depending on status
    }

    fun deselectProject() {
        _currentProjectId.value = null
        setScreen(0)
    }

    fun deleteProject(project: Project) {
        viewModelScope.launch {
            repository.deleteProject(project)
            if (_currentProjectId.value == project.id) {
                deselectProject()
            }
        }
    }

    // Helper to derive a clean app name from user's spoken or typed goal
    private fun deriveAppName(objective: String): String {
        val lower = objective.lowercase()
        return when {
            lower.contains("ledger") -> "Loan Ledger"
            lower.contains("crop") || lower.contains("cycle") -> "Crop Tracker"
            lower.contains("symptom") || lower.contains("health") -> "Symptom Log"
            lower.contains("diary") || lower.contains("note") -> "Offline Diary"
            lower.contains("calc") -> "Smart Calc"
            lower.contains("budget") -> "Group Budget"
            else -> {
                // capitalize words of first 2 words
                val words = objective.split(" ").filter { it.isNotBlank() }
                if (words.isNotEmpty()) {
                    words.take(2).joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }
                } else {
                    "Custom App"
                }
            }
        }
    }

    fun startNewProject(objective: String) {
        // Cancel any active simulation
        simulationJob?.cancel()
        _currentProjectId.value = null

        val appName = deriveAppName(objective)
        val initialTasks = listOf(
            Task(id = "1", description = "Generate the single-file web app", status = "pending"),
            Task(id = "2", description = "Validate in sandbox and self-heal", status = "pending", dependsOn = listOf("1"))
        )
        val initialState = ForgeState(
            objective = objective,
            plan = initialTasks,
            sessionLog = listOf("[Sense] Initializing on-device coding agent...")
        )

        val project = Project(
            name = appName,
            objective = objective,
            status = "pending",
            stateJson = JsonUtils.forgeStateToJson(initialState),
            createdAt = System.currentTimeMillis()
        )

        viewModelScope.launch {
            val id = repository.insertProject(project)
            _currentProjectId.value = id.toInt()
            setScreen(1) // Transition to progress screen
            
            // Start simulation loop
            simulationJob = launch {
                runAgentSimulation(id.toInt(), appName, objective, initialTasks)
            }
        }
    }

    private suspend fun runAgentSimulation(projectId: Int, appName: String, objective: String, tasks: List<Task>) {
        var currentTasks = tasks.map { it.copy() }
        val logs = mutableListOf<String>()
        val ledger = linkedMapOf<String, String>()
        val buildHist = mutableListOf<BuildLog>()
        val hypotheses = mutableListOf<Hypothesis>()
        var artifactHtml = ""

        fun updateState(status: String, escalations: List<Escalation> = emptyList(), score: Int = 0) {
            if (status == "done" || status == "failed" || status == "escalated") {
                _liveLabel.value = ""
            }
            val state = ForgeState(
                objective = objective,
                plan = currentTasks,
                fileLedger = ledger,
                buildHistory = buildHist,
                hypotheses = hypotheses,
                escalations = escalations,
                sessionLog = logs.toList(),
                artifactHtml = artifactHtml
            )
            viewModelScope.launch {
                val proj = Project(
                    id = projectId,
                    name = appName,
                    objective = objective,
                    status = status,
                    stateJson = JsonUtils.forgeStateToJson(state),
                    score = score,
                    createdAt = System.currentTimeMillis()
                )
                repository.updateProject(proj)
            }
        }

        val router = com.example.agent.InferenceRouter(useMock = !hasModelFile()).apply {
            listener = object : com.example.agent.InferenceRouter.Listener {
                override fun onDecide(model: String, message: String) {
                    logs.add("[Decide] [$model] $message"); updateState("in_progress")
                }
                override fun onError(message: String) {
                    logs.add("[Check] [error] $message"); updateState("in_progress")
                }
            }
        }

        // ---------- PHASE 1: SENSE + PLAN ----------
        logs.add("[Sense] New offline project: \"$objective\"")
        speak("Planning your app.")
        updateState("in_progress")
        logs.add("[Decide] Requesting task plan from local Gemma model...")
        _liveLabel.value = "🧠 Planning tasks"; _liveStream.value = ""
        updateState("in_progress")
        val plannerRes = router.infer(com.example.agent.InferenceRouter.InferRequest(
            role = com.example.agent.InferenceRouter.Role.PLANNER,
            systemPrompt = com.example.agent.Prompts.PLANNER,
            userPrompt = "Objective: \"$objective\". Generate the task graph now.",
            expectJson = true
        )) { chunk -> _liveStream.value += chunk }
        val plannedTasks = mutableListOf<Task>()
        plannerRes?.optJSONArray("tasks")?.let { arr ->
            for (i in 0 until arr.length()) {
                val tObj = arr.getJSONObject(i)
                val deps = mutableListOf<String>()
                tObj.optJSONArray("depends_on")?.let { d -> for (j in 0 until d.length()) deps.add(d.getString(j)) }
                plannedTasks.add(Task(
                    id = tObj.optString("id", (i + 1).toString()),
                    description = tObj.optString("description", "Build the app"),
                    status = "pending",
                    dependsOn = deps
                ))
            }
        }
        currentTasks = if (plannedTasks.isNotEmpty()) plannedTasks else listOf(
            Task(id = "1", description = "Generate the single-file web app", status = "pending"),
            Task(id = "2", description = "Validate in a sandbox and self-heal", status = "pending", dependsOn = listOf("1"))
        )
        logs.add("[Act] Plan ready: ${currentTasks.size} tasks.")
        updateState("in_progress")

        // ---------- PHASE 2: GENERATE (CODER) ----------
        currentTasks = currentTasks.mapIndexed { i, t -> if (i == 0) t.copy(status = "in_progress") else t }
        updateState("in_progress")
        logs.add("[Sense] Generating a self-contained offline web app with Gemma...")
        speak("Generating the application code.")
        _liveLabel.value = "✍️ Writing index.html"; _liveStream.value = ""
        val coderRes = router.infer(com.example.agent.InferenceRouter.InferRequest(
            role = com.example.agent.InferenceRouter.Role.CODER,
            systemPrompt = com.example.agent.Prompts.CODER,
            userPrompt = "Objective: \"$objective\". Build the complete index.html now.",
            expectJson = false
        )) { chunk -> _liveStream.value += chunk }
        val generated = extractHtml(coderRes?.optString("text", ""))
        if (generated.isBlank()) {
            logs.add("[Check] Generation failed — the model did not return valid HTML.")
            speak("Generation failed.")
            updateState("failed")
            return
        }
        artifactHtml = generated
        writeArtifact(projectId, artifactHtml)
        val genLines = artifactHtml.count { it == '\n' } + 1
        ledger["index.html"] = "Generated ($genLines lines, ${artifactHtml.length} bytes)"
        logs.add("[Act] Wrote index.html ($genLines lines).")
        currentTasks = currentTasks.mapIndexed { i, t -> if (i == 0) t.copy(status = "done") else t }
        updateState("in_progress")

        // ---------- PHASE 3: CHECK + SELF-HEAL (real WebView sandbox) ----------
        currentTasks = currentTasks.map { if (it.status != "done") it.copy(status = "in_progress") else it }
        updateState("in_progress")
        logs.add("[Check] Running app in on-device WebView sandbox to detect runtime errors...")
        speak("Testing the app in a sandbox.")
        var errors = com.example.agent.PwaValidator.validate(getApplication<Application>(), artifactHtml)
        val maxAttempts = 3
        var attempt = 0
        while (errors.isNotEmpty() && attempt < maxAttempts) {
            attempt++
            val sig = com.example.agent.InferenceRouter.normalizeErrorSignature(errors.first())
            buildHist.add(BuildLog("webview_runtime", 1, errors.joinToString("\n").take(400), System.currentTimeMillis()))
            logs.add("[Check] Runtime error (attempt $attempt/$maxAttempts): ${errors.first()}")
            speak("Found a runtime error. Self healing.")
            updateState("in_progress")
            logs.add("[Decide] [gemma-4-e4b] Diagnosing and repairing the app...")
            _liveLabel.value = "🔧 Repairing index.html (attempt $attempt)"; _liveStream.value = ""
            updateState("in_progress")
            val shortened = getShortenedHtml(artifactHtml)
            val fixRes = router.infer(com.example.agent.InferenceRouter.InferRequest(
                role = com.example.agent.InferenceRouter.Role.FIXER,
                systemPrompt = com.example.agent.Prompts.FIXER,
                userPrompt = "Objective: \"$objective\".\nRuntime error(s):\n${errors.joinToString("\n")}\n\nCurrent index.html:\n$shortened",
                expectJson = false
            )) { chunk -> _liveStream.value += chunk }
            val fixedRaw = extractHtml(fixRes?.optString("text", ""))
            val fixed = mergeStylesAndSvgs(artifactHtml, fixedRaw)
            if (fixed.isNotBlank()) {
                artifactHtml = fixed
                writeArtifact(projectId, artifactHtml)
                val fl = artifactHtml.count { it == '\n' } + 1
                ledger["index.html"] = "Repaired ($fl lines)"
                hypotheses.add(Hypothesis(
                    errorSignature = sig,
                    diagnosis = "Runtime error: ${errors.first()}",
                    fixAttempted = "Regenerated corrected index.html (attempt $attempt)",
                    outcome = "failed"
                ))
                logs.add("[Act] Applied fix, re-testing...")
            } else {
                logs.add("[Check] Fixer returned no HTML; re-testing current build.")
            }
            updateState("in_progress")
            errors = com.example.agent.PwaValidator.validate(getApplication<Application>(), artifactHtml)
        }

        if (errors.isEmpty()) {
            buildHist.add(BuildLog("webview_runtime", 0, "", System.currentTimeMillis()))
            if (hypotheses.isNotEmpty()) {
                val resolved = hypotheses.map { it.copy(outcome = "resolved") }
                hypotheses.clear(); hypotheses.addAll(resolved)
            }
            logs.add("[Check] WebView sandbox: 0 runtime errors.")
            val judgeRes = router.infer(com.example.agent.InferenceRouter.InferRequest(
                role = com.example.agent.InferenceRouter.Role.JUDGE,
                systemPrompt = com.example.agent.Prompts.JUDGE,
                userPrompt = "Validation outcome: SUCCESS. No runtime or console errors for objective: \"$objective\".",
                expectJson = true
            ))
            val verdict = judgeRes?.optString("verdict", "pass") ?: "pass"
            logs.add("[Decide] [gemma-4-e2b] Judge verdict: $verdict.")
            currentTasks = currentTasks.map { it.copy(status = "done") }
            logs.add("[Act] App forged successfully and running live on-device.")
            speak("Your application is ready and running.")
            updateState("done", emptyList(), score = 88 + (0..11).random())
            return
        }

        // ---------- PHASE 4: ESCALATE (real error, human-in-the-loop) ----------
        val sig = com.example.agent.InferenceRouter.normalizeErrorSignature(errors.first())
        logs.add("[Check] Self-healing hit the $maxAttempts-attempt limit. Escalating to human operator.")
        speak("I need your guidance to finish.")
        currentTasks = currentTasks.map { if (it.status == "in_progress") it.copy(status = "escalated") else it }
        val esc = Escalation(
            question = "The app still throws: \"${errors.first()}\". How should I resolve it — simplify the feature, guard against missing data, or change the approach?",
            context = "After $maxAttempts autonomous repair attempts the WebView still reports: ${errors.joinToString("; ").take(300)}"
        )
        updateState("escalated", listOf(esc))
        val guidance = suspendContinuation { }
        logs.add("[Sense] Human guidance received: \"$guidance\"")
        speak("Applying your guidance.")
        updateState("in_progress")
        logs.add("[Decide] [gemma-4-e4b] Re-synthesizing the app with your guidance...")
        _liveLabel.value = "🔧 Applying your guidance"; _liveStream.value = ""
        updateState("in_progress")
        val guidedRes = router.infer(com.example.agent.InferenceRouter.InferRequest(
            role = com.example.agent.InferenceRouter.Role.FIXER,
            systemPrompt = com.example.agent.Prompts.FIXER,
            userPrompt = "Objective: \"$objective\". Human guidance: \"$guidance\".\nRuntime error(s):\n${errors.joinToString("\n")}\n\nCurrent index.html:\n$artifactHtml",
            expectJson = false
        )) { chunk -> _liveStream.value += chunk }
        val guidedHtml = extractHtml(guidedRes?.optString("text", ""))
        if (guidedHtml.isNotBlank()) {
            artifactHtml = guidedHtml
            writeArtifact(projectId, artifactHtml)
            ledger["index.html"] = "Repaired via guidance (${artifactHtml.count { it == '\n' } + 1} lines)"
        }
        updateState("in_progress")
        val finalErrors = com.example.agent.PwaValidator.validate(getApplication<Application>(), artifactHtml)
        if (finalErrors.isEmpty()) {
            buildHist.add(BuildLog("webview_runtime", 0, "", System.currentTimeMillis()))
            hypotheses.add(Hypothesis(sig, "Resolved with human guidance", guidance, "resolved"))
            currentTasks = currentTasks.map { it.copy(status = "done") }
            logs.add("[Act] Guidance resolved the issue. App running live on-device.")
            speak("Fixed with your guidance. Your app is ready.")
            updateState("done", emptyList(), score = 85)
        } else {
            buildHist.add(BuildLog("webview_runtime", 1, finalErrors.joinToString("\n").take(400), System.currentTimeMillis()))
            logs.add("[Check] Still failing after guidance: ${finalErrors.first()}")
            speak("Still failing. Please advise further.")
            currentTasks = currentTasks.map { if (it.status != "done") it.copy(status = "failed") else it }
            updateState("failed", listOf(esc))
        }
    }

    /** Extracts a complete HTML document from a model response (strips fences/prose). */
    private fun extractHtml(raw: String?): String {
        var s = (raw ?: "").trim()
        if (s.isEmpty()) return ""
        s = s.replace(Regex("^```(?:html)?\\s*", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s*```$"), "")
            .trim()
        val lower = s.lowercase()
        var start = lower.indexOf("<!doctype")
        if (start < 0) start = lower.indexOf("<html")
        val end = lower.lastIndexOf("</html>")
        if (start >= 0 && end > start) return s.substring(start, end + "</html>".length)
        if (s.contains("<") && s.contains(">")) {
            return "<!DOCTYPE html><html><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"></head><body>$s</body></html>"
        }
        return ""
    }

    /** Persists the generated app to filesDir/projects/<id>/index.html for reference. */
    private fun writeArtifact(projectId: Int, html: String) {
        try {
            val dir = java.io.File(getApplication<Application>().filesDir, "projects/$projectId")
            dir.mkdirs()
            java.io.File(dir, "index.html").writeText(html)
        } catch (e: Exception) {
            Log.e("ForgeViewModel", "Failed to persist artifact: ${e.message}")
        }
    }

    private suspend fun suspendContinuation(block: suspend (String) -> Unit): String {
        return kotlinx.coroutines.suspendCancellableCoroutine { cont ->
            continuationBlock = { guidance ->
                continuationBlock = null
                cont.resume(guidance) {
                    // cancellation block
                }
            }
        }
    }

    fun submitGuidance(guidance: String) {
        val block = continuationBlock
        if (block != null) {
            viewModelScope.launch {
                block(guidance)
            }
        }
    }

    fun stopSimulation() {
        simulationJob?.cancel()
        simulationJob = null
        _currentProjectId.value = null
        setScreen(0)
    }

    override fun onCleared() {
        super.onCleared()
        tts?.let {
            it.stop()
            it.shutdown()
        }
    }

    private fun getShortenedHtml(html: String): String {
        var result = html
        // 1. Remove lengthy inline style blocks to prevent LLM prompt saturation
        val styleRegex = Regex("<style[^>]*>[\\s\\S]*?</style>", RegexOption.IGNORE_CASE)
        result = styleRegex.replace(result, "<style>/* [CSS Stylesheets omitted for context brevity] */</style>")
        
        // 2. Remove giant inline SVGs
        val svgRegex = Regex("<svg[^>]*>[\\s\\S]*?</svg>", RegexOption.IGNORE_CASE)
        result = svgRegex.replace(result, "<svg><!-- [Inline SVG Assets omitted] --></svg>")
        return result
    }

    private fun mergeStylesAndSvgs(originalHtml: String, fixedHtml: String): String {
        var merged = fixedHtml
        
        // Re-inject the original stylesheet if the model preserved the placeholder style block
        val styleRegex = Regex("<style[^>]*>([\\s\\S]*?)</style>", RegexOption.IGNORE_CASE)
        val styleMatch = styleRegex.find(originalHtml)
        if (styleMatch != null) {
            val originalStyles = styleMatch.groupValues[1]
            val fixedStyleRegex = Regex("<style[^>]*>([\\s\\S]*?)</style>", RegexOption.IGNORE_CASE)
            merged = fixedStyleRegex.replace(merged) { matchResult ->
                val content = matchResult.groupValues[1]
                if (content.contains("omitted", ignoreCase = true) || content.isBlank() || content.contains("brevity", ignoreCase = true)) {
                    "<style>$originalStyles</style>"
                } else {
                    matchResult.value
                }
            }
        }
        
        // Re-inject the original SVGs if they were omitted in the repair output
        val svgRegex = Regex("<svg[^>]*>([\\s\\S]*?)</svg>", RegexOption.IGNORE_CASE)
        val svgMatch = svgRegex.find(originalHtml)
        if (svgMatch != null) {
            val originalSvgs = svgMatch.value
            val fixedSvgRegex = Regex("<svg[^>]*>([\\s\\S]*?)</svg>", RegexOption.IGNORE_CASE)
            merged = fixedSvgRegex.replace(merged) { matchResult ->
                val content = matchResult.groupValues[1]
                if (content.contains("omitted", ignoreCase = true) || content.isBlank()) {
                    originalSvgs
                } else {
                    matchResult.value
                }
            }
        }
        
        return merged
    }
}
