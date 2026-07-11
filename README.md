# Forge 🛠️
> **Autonomous On-Device Agentic Platform for Native Android Apps & PWAs**

Forge is a 100% offline, secure, autonomous coding agent running entirely on-device (e.g., Pixel 10 Pro or Samsung S23 Ultra). It is engineered to bridge the gap between spotty internet connections, high data costs, and the need for localized development capabilities. Forge takes natural language objectives (e.g., *"build a loan ledger for my 5-member self-help group"*), compiles dynamic multi-step plans, writes Kotlin/Jetpack Compose components, and deploys compiled APKs to physical mobile devices—all locally, offline, and with built-in failure recovery.

---

## 🏗️ System Architecture

Forge operates on a robust, cyclic **Sense-Decide-Act-Check** execution model:

```
                    +------------------------------------+
                    |        USER APPS REQUEST           |
                    +-----------------+------------------+
                                      |
                                      v
                             +--------+--------+
                             |   CLI (forge)   |
                             +--------+--------+
                                      |
                                      v
                             +--------+--------+
        +-------------------->|  AGENT LOOP     |<--------------------+
        |                     +--------+--------+                     |
        |                              |                              |
        |                              v                              |
     [SENSE]                    +------+------+                    [CHECK]
Loads active state,            |   ROUTER    |               Runs local compilation,
context, and error             +------+------+               runs quality benchmarking, and
histories.                            |                      parses stderr logs.
        ^                              |                              ^
        |               +--------------+--------------+               |
        |               |                             |               |
        |               v                             v               |
        |        [GEMMA 4 E4B]                 [GEMMA 4 E2B]          |
        |        (Quality Path)                (Fast Path)            |
        |        * Planning                    * Binary Decisions     |
        |        * Code Generation             * Task Completions     |
        |        * Fix Synthesis               * Success Grading      |
        |               |                             |               |
        |               +--------------+--------------+               |
        |                              |                              |
        |                              v                              |
        |                           [ACT]                             |
        |                  Executing File Modifications               |
        |                  & Allowlisted Shell Tasks                  |
        |                              |                              |
        +------------------------------+------------------------------+
```

---

## 🚀 Key Achievements & Core Workings

### 1. Dual-Model Policy Router
To maximize resource efficiency and model latency, Forge splits responsibilities between a heavy model (quality path) and a fast classifier model (fast path):
*   **Gemma-4-E4B (Quality Path):** Allocated for cognitively intensive tasks like architectural planning, code generation, and diagnosing build failures on the **Fixer path**.
*   **Gemma-4-E2B (Fast Path):** Allocated for binary classification checks, assessing whether build diagnostics succeeded or failed (the **Judge path**).
*   **Self-Correcting JSON Retries:** Implements an automated self-correction loop. If a model outputs malformed JSON or markdown-fenced content when a raw schema is expected, Forge re-injects the error message and forces an instant, temperature-zero recovery retry.

### 2. Atomic Persistent State Machine (`state.json`)
State is written atomically (.tmp -> rename) inside a local `.forge/state.json` file on every single iteration of the loop. This ensures:
*   **Resiliency:** The agent survives abrupt battery drains, VM closures, or process terminations.
*   **Re-grounding Briefings:** Upon running `forge resume`, the state engine parses the session history and compiles a 3-line briefing reconstructing the exact objective, task graph status, file ledger modifications, and failures to re-ground the newly initialized model's context.

### 3. Progressive Clarification & Dynamic Replanning
Most autonomous agents loop infinitely or crash when they hit a wall. Forge uses a **3-Strike Escalation** boundary:
*   If an error signature persists 3 times, Forge suspends execution and enters an interactive shell utilizing the `[KNOWS]/[TRIED]/[NEED]` framework.
*   The operator is prompted for guidance. Once entered, the feedback is dispatched back to Gemma 4, which dynamically **re-plans** the dependency graph—injecting new sub-tasks, modifying existing plans, and resuming compilation without starting over.

### 4. Zero-Dependency Sandbox & Path Jailing
*   **Path Jailing:** Prevents directory traversal attacks (`../../`) by sanitizing and resolving all generated files, strictly prisoning file outputs within the user's workspace.
*   **Command Allowlist:** Restricts shell execution exclusively to `node`, `npx`, and standard compilation binaries.
*   **LiteRT (TFLite) & Gemma 4 Bundles:** Embeds localized weight handlers (`gemma-4-E4B-it.litertlm`) running fully locally on-device without cloud API dependencies.

---

## ⚡ Multimodal Features (Milestone 7)

We merged both **Agent-Level** and **Artifact-Level** multimodalities to create a stunning developer-and-user experience:

*   **Agent Auditory Speech Tracing:** Utilizing native background TTS voice synthesizers. Forge speaks its operational thoughts out loud as it transitions across the SENSE-DECIDE-ACT-CHECK cycle, providing real-time audio progress.
*   **Multimodal Sketch-to-Code:** Operators can provide a visual layout wireframe sketch (`--sketch <path>`). The planner converts this image into base64 and feeds it to Gemma 4's visual encoder, translating hand-drawn sketches into Kotlin Compose layout blueprints.
*   **Artifact-Level Voice inputs/outputs:** Forge guides its coder templates to automatically generate accessible Jetpack Compose apps featuring built-in **Android SpeechRecognizer** and **TextToSpeech** interfaces—perfectly tailored for low-literacy users in rural and semi-urban Indian communities.
*   **Offline Quality Benchmarker:** Evaluates compiled files against Android development standards (proper `remember` state wrappers, state flows, material styling) and issues a Lighthouse-style final metric report (e.g., `Score: 80/100`).

---

## 📝 Premium Code Examples

### 1. Zero-Dependency JS Sandbox Runtime Checking
Used in the CHECK phase to catch ReferenceErrors and TypeErrors before compiling the app:
```javascript
import vm from 'node:vm';
import fs from 'node:fs';

export function executeRuntimeChecks(projDir) {
  const appJsPath = path.join(projDir, 'dist', 'app.js');
  if (!fs.existsSync(appJsPath)) return { success: true };

  const appJsCode = fs.readFileSync(appJsPath, 'utf8');

  // Minimal Browser/DOM Mocking Sandbox
  const domSandbox = {
    document: {
      getElementById: (id) => ({ addEventListener: () => {}, style: {}, value: '' }),
      querySelector: (sel) => ({ addEventListener: () => {}, style: {} }),
      createElement: (tag) => ({ style: {} }),
      body: { appendChild: () => {} }
    },
    window: { addEventListener: () => {}, localStorage: { getItem: () => null, setItem: () => {} } },
    console: { log: () => {}, error: () => {} }
  };

  try {
    const context = vm.createContext(domSandbox);
    const script = new vm.Script(appJsCode, { filename: 'app.js' });
    script.runInContext(context, { timeout: 1000 });
    return { success: true, stderr: '' };
  } catch (err) {
    return { success: false, stderr: `${err.name}: ${err.message}` };
  }
}
```

### 2. Reactive State Flow & Text-To-Speech ViewModel (Kotlin Compose)
This is a standard template generated by Forge to provide localized Hindi voice feedback in Rural Self-Help Group (SHG) applications:
```kotlin
package com.example.testapp.ui.main

import android.content.Context
import android.speech.tts.TextToSpeech
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

class MainScreenViewModel : ViewModel() {
    private val _currentName = MutableStateFlow("")
    val currentName: StateFlow<String> = _currentName

    private var tts: TextToSpeech? = null
    private var isTtsReady = false

    fun initTts(context: Context) {
        if (tts == null) {
            tts = TextToSpeech(context) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    tts?.language = Locale("hi", "IN") // Hindi fallback
                    isTtsReady = true
                }
            }
        }
    }

    fun speak(text: String) {
        if (isTtsReady) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
        }
    }
}
```

---

## 🛠️ CLI Installation & Commands

### 1. Start a New Project
Provide a natural language description and a wireframe sketch, and let Forge build your native Android App:
```bash
forge new "build a crop tracker" --sketch "/path/to/sketch.png" --voice
```

### 2. Check Active Status Graph
```bash
forge status
```

### 3. Reconstruct Narrative History & Diagnostic Timeline
```bash
forge explain
```

```
┌── Forge Timeline Explainer & Replay Rehearsal ───────────────────┐
│ 🎯 Goal: "build a loan ledger for my 5-member self-help group"
├── Step 1: Sequential Execution Plan ─────────────────────────────┤
│   ✅ [ID: 1] Design Jetpack Compose layouts and Material You theme colors inside MainScreen.kt and Color.kt
│   ✅ [ID: 2] Implement interactive calculation logic and state handling in MainScreenViewModel.kt
├── Step 2: Codebase Mod Ledger ───────────────────────────────────┤
│   📂 Written: app/src/main/java/com/example/testapp/theme/Color.kt
│   📂 Written: app/src/main/java/com/example/testapp/ui/main/MainScreen.kt
│   📂 Written: app/src/main/java/com/example/testapp/ui/main/MainScreenViewModel.kt
├── Step 3: Self-Healing & Cognitive Diagnostics ──────────────────┤
│   [Attempt #1] Error Signature: "Unresolved reference: activeTab"
│     💡 Diagnosis: Coder attempted to assign navigation tab variables out of scope.
│     🔧 Fix Action: Declared state variables inside parent Composable -> (🟢 SUCCESS)
└──────────────────────────────────────────────────────────────────┘
```

---

## 📱 Running Forge Agent Locally on Android (`forge-app/`)

Forge includes a native Android App shell located inside the `forge-app/` subdirectory. This app embeds the entire agent compiler service, compiling projects locally on-device.

### Build and Deploy on S23 Ultra / Pixel 10:
To compile and deploy the core agent interface over ADB:
```bash
cd forge-app
./gradlew :app:installDebug
```
This launches a Material You 3 Compose UI containing live telemetry logs, an integrated file explorer, and a local server status hub.

---

## 🏆 Summary of Hackathon Evaluation Mapping

| Hackathon Requirement | Forge Platform Alignment |
| :--- | :--- |
| **Sense-Decide-Act-Check (25%)** | Features a strict 4-phase cyclic loop. Analyzes compiler logs, synthesizes fixes, executes local checks, and iterates autonomously. |
| **Technical Depth (15%)** | Dual-model policy routing, sandboxed execution verification, error signature normalizations, and on-device LiteRT/Gemma orchestration. |
| **Creativity (35%)** | Progressive multi-turn clarification, live audio tracing, visual sketch-to-code pipelines, and graphical glassmorphic telemetry dashboards. |
| **India Impact (25%)** | Designed specifically for spotty-internet rural Indian scenarios (Self-Help Groups/Agri-Mandis) with native voice/TTS overlays. |

---

*Authored by Team Antigravity. Built with ❤️ on-device.*
