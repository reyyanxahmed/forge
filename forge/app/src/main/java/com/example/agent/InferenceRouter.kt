package com.example.agent

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class InferenceRouter(
    val useMock: Boolean,
    private val endpoint: String = "http://localhost:8080/v1/chat/completions",
) {

    interface Listener {
        fun onDecide(model: String, message: String)
        fun onError(message: String)
    }

    var listener: Listener? = null

    data class InferRequest(
        val role: Role,
        val userPrompt: String,
        val systemPrompt: String,
        val expectJson: Boolean,
        val sketchPath: String? = null,
        val contextFiles: String = "",
    )

    enum class Role(val model: String) {
        PLANNER("gemma-4-e4b"),
        CODER("gemma-4-e4b"),
        FIXER("gemma-4-e4b"),
        JUDGE("gemma-4-e2b"),
    }

    /**
     * Runs one inference. Prefers the IN-PROCESS engine (streaming tokens via
     * [onToken]) and falls back to the loopback HTTP server. Returns a parsed
     * JSONObject for JSON roles, {"text": ...} otherwise, or null on failure.
     */
    suspend fun infer(req: InferRequest, onToken: (String) -> Unit = {}): JSONObject? = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        if (useMock) {
            val elapsed = 50L + (0..80).random()
            delay(elapsed)
            val res = MockModels.respond(req.role, req.userPrompt)
            res.optString("text", "").takeIf { it.isNotEmpty() }?.let { runCatching { onToken(it) } }
            val inTokens = estimateTokens(req.userPrompt + req.systemPrompt)
            val outTokens = estimateTokens(res.toString())
            listener?.onDecide(req.role.model, "[MOCK] Served role: ${req.role} | Latency: ${elapsed}ms | Tokens: In~$inTokens Out~$outTokens")
            return@withContext res
        }
        try {
            val text = generate(req, onToken)
            if (req.expectJson) {
                val parsed = parseJsonLenient(text)
                if (parsed != null) {
                    trace(req, start, text)
                    return@withContext parsed
                }
                listener?.onError("JSON Parse error on ${req.role.model}. Attempting self-correction retry...")
                val retry = selfCorrect(req, text)
                if (retry != null) {
                    trace(req, start, retry.toString())
                    return@withContext retry
                }
                listener?.onError("Self-correction also failed on ${req.role.model}.")
            } else {
                trace(req, start, text)
                return@withContext JSONObject().apply { put("text", text) }
            }
        } catch (e: Exception) {
            listener?.onError("Inference failed on ${req.role.model}: ${e.message}")
        }
        null
    }

    /** One generation, in-process (streaming) if the engine is ready, else HTTP.
     *  Falls back to mock if neither engine nor HTTP server is available yet —
     *  prevents silent total failure during the model init race window. */
    private suspend fun generate(req: InferRequest, onToken: (String) -> Unit): String {
        // Fast path: in-process engine with token streaming
        val helper = EngineProvider.helper
        if (helper != null && helper.isReady()) {
            return helper.generateStreaming(formatPrompt(req.systemPrompt, req.userPrompt), onToken)
        }
        // Fallback: HTTP loopback to LocalLlmServer
        val messages = JSONArray().apply {
            put(JSONObject().apply { put("role", "system"); put("content", req.systemPrompt) })

            val userMsg = JSONObject().apply { put("role", "user") }
            if (req.sketchPath != null && java.io.File(req.sketchPath).exists()) {
                try {
                    val bytes = java.io.File(req.sketchPath).readBytes()
                    val b64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                    val dataUrl = "data:image/png;base64,$b64"

                    val contentArr = JSONArray().apply {
                        put(JSONObject().apply {
                            put("type", "text")
                            put("text", req.userPrompt)
                        })
                        put(JSONObject().apply {
                            put("type", "image_url")
                            put("image_url", JSONObject().apply {
                                put("url", dataUrl)
                            })
                        })
                    }
                    userMsg.put("content", contentArr)
                } catch (e: Exception) {
                    userMsg.put("content", req.userPrompt)
                }
            } else {
                userMsg.put("content", req.userPrompt)
            }
            put(userMsg)
        }
        return try {
            extractContent(post(req.role.model, messages, temperature = 0.1, jsonMode = req.expectJson))
        } catch (e: java.net.ConnectException) {
            listener?.onError("Engine not ready yet — using fallback generator. Please wait for model to load.")
            val res = MockModels.respond(req.role, req.userPrompt)
            res.optString("text", "").takeIf { it.isNotEmpty() }?.let { runCatching { onToken(it) } }
            res.optString("text", "")
        } catch (e: Exception) {
            listener?.onError("HTTP inference unavailable (${e.message}) — using fallback.")
            val res = MockModels.respond(req.role, req.userPrompt)
            res.optString("text", "").takeIf { it.isNotEmpty() }?.let { runCatching { onToken(it) } }
            res.optString("text", "")
        }
    }

    private fun formatPrompt(system: String, user: String): String =
        "<start_of_turn>user\n$system\n\n$user<end_of_turn>\n<start_of_turn>model\n"

    private fun trace(req: InferRequest, start: Long, out: String) {
        val elapsed = System.currentTimeMillis() - start
        val inT = estimateTokens(req.userPrompt + req.systemPrompt)
        val outT = estimateTokens(out)
        listener?.onDecide(req.role.model, "Served role: ${req.role} | Latency: ${elapsed}ms | Tokens: In~$inT Out~$outT")
    }

    private suspend fun selfCorrect(req: InferRequest, prevText: String): JSONObject? {
        val corrective = "Your previous response was not valid JSON. Output ONLY valid raw JSON matching the requested schema. Do not include markdown code fences or conversational text."
        val helper = EngineProvider.helper
        val text = if (helper != null && helper.isReady()) {
            helper.generateStreaming(
                formatPrompt(req.systemPrompt, "${req.userPrompt}\n\nYour previous reply:\n$prevText\n\n$corrective")
            ) {}
        } else {
            val messages = JSONArray().apply {
                put(JSONObject().apply { put("role", "system"); put("content", req.systemPrompt) })
                put(JSONObject().apply { put("role", "user"); put("content", req.userPrompt) })
                put(JSONObject().apply { put("role", "assistant"); put("content", prevText) })
                put(JSONObject().apply { put("role", "user"); put("content", corrective) })
            }
            extractContent(post(req.role.model, messages, temperature = 0.0, jsonMode = true))
        }
        return parseJsonLenient(text)
    }

    private fun post(
        model: String,
        messages: JSONArray,
        temperature: Double,
        jsonMode: Boolean,
    ): JSONObject {
        val payload = JSONObject().apply {
            put("model", model)
            put("messages", messages)
            put("temperature", temperature)
            if (jsonMode) {
                put("response_format", JSONObject().apply { put("type", "json_object") })
            }
        }
        val body = payload.toString()
        val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 120_000
            setRequestProperty("Content-Type", "application/json")
            doOutput = true
            outputStream.use { it.write(body.toByteArray()) }
        }
        conn.inputStream.bufferedReader().use { return JSONObject(it.readText()) }
    }

    private fun extractContent(raw: JSONObject): String {
        val choices = raw.optJSONArray("choices") ?: return ""
        val first = choices.optJSONObject(0) ?: return ""
        val msg = first.optJSONObject("message") ?: return ""
        return msg.optString("content", "")
    }

    private fun parseJsonLenient(raw: String): JSONObject? {
        var cleaned = raw.trim()
            .replace(Regex("^```(?:json)?\\s*", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s*```$"), "")
            .trim()
        if (cleaned.isEmpty()) return null
        if (!cleaned.startsWith("{")) {
            val s = cleaned.indexOf('{')
            val e = cleaned.lastIndexOf('}')
            if (s in 0..<e) cleaned = cleaned.substring(s, e + 1)
        }
        return try { JSONObject(cleaned) } catch (e: Exception) { null }
    }

    private fun estimateTokens(text: String): Int = if (text.isEmpty()) 0 else (text.length / 4) + 1

    companion object {
        fun normalizeErrorSignature(stderr: String): String {
            val firstLine = stderr.lineSequence().firstOrNull { it.isNotBlank() } ?: return "unknown error"
            return firstLine
                .replace(Regex("/\\S+"), "")
                .replace(Regex("[0-9]+"), "#")
                .replace(Regex("\\(\\s*#\\s*,\\s*#\\s*\\)"), "(L,C)")
                .replace(Regex(":\\s*#\\s*,\\s*#\\s*:"), ":L:C")
                .replace(Regex("\\s+"), " ")
                .trim()
                .ifBlank { "unknown error" }
        }
    }
}
