package com.example.agent

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Conversation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Helper to manage Google's LiteRT-LM (TFLite-backed on-device) execution.
 * Delivers low-latency edge AI utilizing Snapdragon's NPU/GPU hardware.
 */
class LiteRtLmHelper(private val context: Context) {
    private var engine: Engine? = null
    private var conversation: Conversation? = null
    private var currentModelPath: String? = null

    /** Backend that actually initialized ("GPU" or "CPU"); null until initialized. */
    @Volatile var activeBackend: String? = null
        private set

    companion object {
        private const val TAG = "LiteRtLmHelper"
    }

    suspend fun initializeEngine(modelPath: String): Boolean = withContext(Dispatchers.IO) {
        if (engine != null && currentModelPath == modelPath) {
            return@withContext true
        }
        try {
            Log.d(TAG, "Initializing LiteRT-LM Engine with model: $modelPath")
            val file = File(modelPath)
            if (!file.exists()) {
                Log.e(TAG, "Model file does not exist at $modelPath")
                return@withContext false
            }

            // Close existing before re-init
            close()

            // Prefer hardware acceleration: GPU (Adreno via the bundled
            // libLiteRtClGlAccelerator.so) dramatically cuts prompt-processing and
            // decode latency vs the CPU/XNNPACK path. Fall back to CPU if the GPU
            // backend cannot initialize on this device.
            val backends = listOf("GPU" to Backend.GPU(), "CPU" to Backend.CPU())
            var lastError: Throwable? = null
            for ((name, backend) in backends) {
                try {
                    Log.d(TAG, "Attempting LiteRT-LM init on $name backend...")
                    val started = System.currentTimeMillis()
                    // maxNumTokens = 8192 allows the model to generate up to ~32K chars
                    // of output. The library default is ~1024 tokens (~4K chars), which
                    // truncates HTML pages mid-generation. 8192 is sufficient for a
                    // complete single-file web app.
                    val config = EngineConfig(
                        modelPath = modelPath,
                        backend = backend,
                        visionBackend = null,
                        audioBackend = null,
                        maxNumTokens = 8192,
                        maxNumImages = 1,
                        cacheDir = null
                    )
                    val newEngine = Engine(config)
                    newEngine.initialize()
                    engine = newEngine
                    conversation = newEngine.createConversation()
                    currentModelPath = modelPath
                    activeBackend = name
                    EngineProvider.helper = this@LiteRtLmHelper
                    val ms = System.currentTimeMillis() - started
                    Log.d(TAG, "LiteRT-LM Engine initialized successfully on $name backend in ${ms}ms. maxNumTokens=8192")
                    return@withContext true
                } catch (e: Throwable) {
                    lastError = e
                    Log.e(TAG, "LiteRT-LM init failed on $name backend: ${e.message}")
                    close()
                }
            }
            Log.e(TAG, "All LiteRT-LM backends failed. Last error: ${lastError?.message}", lastError)
            false
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to initialize LiteRT-LM engine: ${e.message}", e)
            false
        }
    }

    suspend fun generateResponse(prompt: String): String = withContext(Dispatchers.Default) {
        val activeConv = conversation
        if (activeConv == null) {
            return@withContext "Error: LiteRT-LM Engine or Conversation not initialized. Please verify the model file path."
        }
        val builder = StringBuilder()
        try {
            kotlinx.coroutines.withTimeoutOrNull(90_000) {
                activeConv.sendMessageAsync(prompt).collect { chunk ->
                    builder.append(chunk)
                    if (builder.length > 12_000) {
                        throw kotlinx.coroutines.CancellationException("Length capped")
                    }
                }
            }
        } catch (ce: kotlinx.coroutines.CancellationException) {
            Log.w(TAG, "generateResponse capped: length limit of 12000 characters reached.")
        } catch (e: Throwable) {
            Log.e(TAG, "Error in sendMessageAsync: ${e.message}", e)
            builder.append("\n[Error during generation: ${e.message}]")
        }
        builder.toString()
    }

    fun isReady(): Boolean = engine != null

    /**
     * Streams a single-shot generation token-by-token. Creates a fresh
     * Conversation per call so each agent role (planner/coder/fixer/judge) is
     * independent and doesn't accumulate prior context. [onToken] is invoked for
     * each chunk on a background thread; the full text is returned at the end.
     */
    suspend fun generateStreaming(prompt: String, onToken: (String) -> Unit): String =
        withContext(Dispatchers.Default) {
            val eng = engine ?: return@withContext "Error: LiteRT-LM engine not initialized."
            val conv = try {
                eng.createConversation()
            } catch (e: Throwable) {
                Log.e(TAG, "createConversation failed: ${e.message}", e)
                return@withContext "Error: ${e.message}"
            }
            val builder = StringBuilder()
            try {
                kotlinx.coroutines.withTimeoutOrNull(90_000) {
                    conv.sendMessageAsync(prompt).collect { chunk ->
                        val piece = chunk.toString()
                        builder.append(piece)
                        if (builder.length > 12_000) {
                            throw kotlinx.coroutines.CancellationException("Length capped")
                        }
                        try { onToken(piece) } catch (_: Throwable) {}
                    }
                }
            } catch (ce: kotlinx.coroutines.CancellationException) {
                Log.w(TAG, "generateStreaming capped: length limit of 12000 characters reached.")
            } catch (e: Throwable) {
                Log.e(TAG, "Streaming generation error: ${e.message}", e)
                builder.append("\n[Error during generation: ${e.message}]")
            } finally {
                try { conv.close() } catch (_: Throwable) {}
            }
            builder.toString()
        }

    fun close() {
        if (EngineProvider.helper === this) EngineProvider.helper = null
        try {
            conversation?.close()
        } catch (e: Throwable) {
            Log.e(TAG, "Error closing conversation: ${e.message}")
        }
        try {
            engine?.close()
        } catch (e: Throwable) {
            Log.e(TAG, "Error closing engine: ${e.message}")
        }
        conversation = null
        engine = null
        currentModelPath = null
        activeBackend = null
    }
}
