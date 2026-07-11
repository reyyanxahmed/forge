# Forge 🛠️
> **Autonomous On-Device Agentic Platform for Native Android Apps — Running 100% Offline on Android (LiteRT + Gemma 4)**

Forge is an **on-device, fully autonomous, offline agentic compilation platform** built as a native Android Application (`forge-app/`). It is designed to run completely offline on modern Android devices (such as the Google Pixel 10 Pro or Samsung S23 Ultra), leveraging on-device NPUs/GPUs via **Google's LiteRT-LM (TFLite) SDK** to run Gemma 4 E4B (Quality Path) and E2B (Fast Path) models. 

Forge takes a natural language objective (e.g., *"build a loan ledger for my 5-member self-help group"*), plans a sequential task graph, synthesizes Material You Jetpack Compose code, validates compilation, and deploys fully working apps natively—**all offline, completely local, on-device.**

---

## 🏗️ Production Architecture: The Android App (`forge-app/`)

Unlike traditional CLI wrappers or cloud-dependent chatbots, the production version of Forge is engineered as a **native Android Application** wrapping the agent's core cycle in a robust Android Service infrastructure.

```
                    +------------------------------------+
                    |        NATIVE ANDROID UI           |
                    +-----------------+------------------+
                                      |
                                      v
                             +--------+--------+
                             | FOREGROUND SVC  |  <--- Streams live telemetry
                             +--------+--------+       states to Compose UI
                                      |
                                      v
                             +--------+--------+
        +-------------------->|  AGENT LOOP     |<--------------------+
        |                     | (AgentLoop.kt)  |                     |
        |                     +--------+--------+                     |
        |                              |                              |
        |                              v                              |
     [SENSE]                    +------+------+                    [CHECK]
Loads active state,            |  ROUTER      |               Simulates compiled run,
grounding briefing, and        | (Inference   |               rates architectural
failure history.               |  Router.kt)  |               quality logs.
        ^                              |                              ^
        |               +--------------+--------------+               |
        |               |                             |               |
        |               v                             v               |
        |        [GEMMA 4 E4B]                 [GEMMA 4 E2B]          |
        |        (LiteRT-LM GPU)               (LiteRT-LM CPU)        |
        |        * Planning                    * Binary Decisions     |
        |        * Code Generation             * Task Completions     |
        |        * Fix Synthesis               * Success Grading      |
        |               |                             |               |
        |               +--------------+--------------+               |
        |                              |                              |
        |                              v                              |
        |                           [ACT]                             |
        |                    Saves source files and                   |
        |                    emits file-ledger changes                |
        |                              |                              |
        +------------------------------+------------------------------+
```

### 📱 Core Android App Features (`forge-app/`)
*   **Adreno GPU/NPU Hardware Acceleration:** Implements the Google `LiteRtLmHelper` to load and run `.litertlm` models utilizing Snapdragon hardware backends, cutting token generation latency to near-instantaneous levels.
*   **Foreground Agent Service:** The agent execution loop runs inside `ForgeAgentService.kt`, showing a persistent system notification so Android's low-memory killer does not abort compile cycles.
*   **Atomic State Preservation:** Writes state atomically to `state.json` inside private directories using a `.tmp` rename mechanism, ensuring durability across phone reboot or power loss.
*   **Multi-Turn Human-in-the-Loop Escalation:** When a compile error fails 3 consecutive repair attempts, the service suspends the thread, raises a notification, and presents a dynamic Compose UI prompt for the developer to provide clarifying guidance. It then re-plans the task graph in real-time.

---

## 🛠️ Ported Sense-Decide-Act-Check Kotlin Engine

The core loop found in `AgentLoop.kt` mirrors the CLI's logic but has been fully ported to structured, safe Kotlin Coroutines:

```kotlin
class AgentLoop(
    private val projectId: String,
    private val stateEngine: StateEngine,
    private val router: InferenceRouter,
    private val harness: LoopHarness
) {
    suspend fun run(state: AgentState) {
        var iteration = state.sessionLog.size
        while (iteration < maxIterations) {
            iteration++
            
            // 1. SENSE: Re-ground state
            harness.publish(state, AgentPhase.SENSE)
            
            // 2. DECIDE: Quality path vs Fast path routing
            val coder = router.infer(InferRequest(Role.CODER, Prompts.CODER, expectJson = true))
            
            // 3. ACT: Write code files in-memory and register on file-ledger
            applyFileOperations(state, coder)
            
            // 4. CHECK: Validation and Quality Benchmarking
            val isPass = router.infer(InferRequest(Role.JUDGE, Prompts.JUDGE, expectJson = true))
            if (isPass) {
                activeTask.status = TaskStatus.DONE
            } else {
                handleFailure(state, sig)
            }
        }
    }
}
```

---

## ⚡ Local Multimodality Integrations

Forge incorporates groundbreaking offline multimodalities designed specifically for low-connectivity, rural Indian scenarios:

1. **Multilingual Visual Planning (Sketch-to-Code):** The Android `InferenceRouter` base64-encodes photos of hand-drawn wireframes and passes them as input to Gemma's visual encoder, compiling initial task graphs from drawings.
2. **Built-in Multimodal App Templates:** Instructs Coder prompts to automatically design Material You 3 applications featuring:
   *   **Android SpeechRecognizer:** Voice commands bypass literacy/typing barriers.
   *   **Android TextToSpeech (TTS):** Audibly reads back digital ledger transactions in native regional languages (e.g., Hindi, Tamil, Marathi) for transparency during offline village gatherings.
3. **Lighthouse Quality Benchmarker:** Synthesizes an architectural health report, checking the generated Kotlin/Compose classes for stateflow reactive wrappers, state remember caching, and multilingual interfaces, presenting a final rating out of 100.

---

## 📦 Android App Build & Deployment

All code for the on-device compilation application is contained in the `forge-app/` subdirectory.

### System Prerequisites:
*   Java Development Kit (JDK) 17 or higher.
*   Android SDK Platform 36 and Android Build-Tools installed.

### Build and Package local APK:
From inside `forge-app/`, execute:
```bash
./gradlew :app:assembleDebug
```
The compiled APK will be generated at:
`forge-app/app/build/outputs/apk/debug/app-debug.apk`

### ADB Direct Installation:
To install and start the core Forge Agent UI on a connected mobile phone over ADB:
```bash
adb install -r forge-app/app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.forge.app/com.forge.app.MainActivity
```

---

## 🗺️ Legacy Harness: The Node.js CLI (`src/`)
For rapid desktop prototyping and unit testing, a secondary CLI-based harness is maintained in the root workspace.

*   **Initialize a Mock Scaffolding Session:**
    ```bash
    node bin/forge.js new "build a loan ledger for my 5-member self-help group" --mock --voice
    ```
*   **Show Telemetry History Replay:**
    ```bash
    node bin/forge.js explain
    ```

---

*Developed by Team Antigravity. Ported, tested, and running locally on Android.*
