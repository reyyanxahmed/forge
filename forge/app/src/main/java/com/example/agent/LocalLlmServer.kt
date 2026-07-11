package com.example.agent

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket

/**
 * On-device OpenAI-compatible chat server backed by the local LiteRT-LM engine.
 *
 * Implemented on a raw [ServerSocket] rather than com.sun.net.httpserver.HttpServer:
 * the latter is a JDK-only class that is NOT present in the Android runtime, so
 * HttpServer.create() throws NoClassDefFoundError on device. This minimal HTTP/1.1
 * handler is dependency-free and Android-safe.
 */
class LocalLlmServer(private val context: Context) {
    private var serverSocket: ServerSocket? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val helper = LiteRtLmHelper(context)
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    companion object {
        private const val TAG = "LocalLlmServer"
        private const val PORT = 8080
    }

    fun start() {
        if (serverSocket != null) return
        scope.launch {
            try {
                val modelPath = findModelFile()
                if (modelPath == null) {
                    Log.e(TAG, "No .litertlm model file found. Server will not run LiteRT inference.")
                    return@launch
                }

                Log.d(TAG, "Starting Local LLM Server on port $PORT with model: $modelPath")
                val initialized = helper.initializeEngine(modelPath)
                if (!initialized) {
                    Log.e(TAG, "Failed to initialize LiteRT-LM engine. Server will not run.")
                    return@launch
                }

                val ss = ServerSocket()
                ss.reuseAddress = true
                ss.bind(InetSocketAddress("127.0.0.1", PORT))
                serverSocket = ss
                Log.d(TAG, "Local LLM Server running on http://127.0.0.1:$PORT (backend=${helper.activeBackend})")

                while (!ss.isClosed) {
                    val socket = try {
                        ss.accept()
                    } catch (e: Exception) {
                        if (ss.isClosed) break
                        Log.e(TAG, "accept() failed: ${e.message}")
                        continue
                    }
                    scope.launch {
                        try {
                            handleConnection(socket)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error handling connection: ${e.message}", e)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error starting local LLM server: ${e.message}", e)
            }
        }
    }

    fun stop() {
        try {
            serverSocket?.close()
            serverSocket = null
            helper.close()
            Log.d(TAG, "Local LLM Server stopped.")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping local LLM server: ${e.message}", e)
        }
    }

    private fun findModelFile(): String? {
        val extDir = context.getExternalFilesDir(null)
        val possiblePaths = listOf(
            // App-private locations first: always readable by native open() without
            // storage permissions or MediaProvider mediation. Shared-storage paths
            // below can pass File.exists() yet fail native open() under scoped storage.
            File(context.filesDir, "gemma-4-E4B-it.litertlm").absolutePath,
            if (extDir != null) File(extDir, "gemma-4-E4B-it.litertlm").absolutePath else "",
            "/sdcard/gemma-4-E4B-it.litertlm",
            "/sdcard/Download/gemma-4-E4B-it.litertlm",
            "/storage/emulated/0/gemma-4-E4B-it.litertlm",
            "/storage/emulated/0/Download/gemma-4-E4B-it.litertlm"
        )
        for (path in possiblePaths) {
            if (path.isNotEmpty() && File(path).canRead()) {
                Log.d(TAG, "Found model file at: $path")
                return path
            }
        }
        return null
    }

    private suspend fun handleConnection(socket: Socket) {
        socket.use { sock ->
            val input = sock.getInputStream()
            val output = sock.getOutputStream()

            // --- Read the header block (up to CRLFCRLF) ---
            val headerBuf = ByteArrayOutputStream()
            var b: Int
            while (true) {
                b = input.read()
                if (b == -1) break
                headerBuf.write(b)
                val arr = headerBuf.toByteArray()
                val n = arr.size
                if (n >= 4 &&
                    arr[n - 4] == 13.toByte() && arr[n - 3] == 10.toByte() &&
                    arr[n - 2] == 13.toByte() && arr[n - 1] == 10.toByte()
                ) break
            }
            val headerText = headerBuf.toString("UTF-8")
            if (headerText.isBlank()) return
            val lines = headerText.split("\r\n")
            val requestLine = lines.firstOrNull().orEmpty()
            val requestParts = requestLine.split(" ")
            val method = requestParts.getOrNull(0).orEmpty()
            val path = requestParts.getOrNull(1).orEmpty()

            if (method.equals("OPTIONS", ignoreCase = true)) {
                writeResponse(output, 204, "")
                return
            }
            if (!method.equals("POST", ignoreCase = true) || !path.startsWith("/v1/chat/completions")) {
                writeResponse(output, 404, "Not Found", "text/plain")
                return
            }

            val contentLength = lines.drop(1)
                .firstOrNull { it.startsWith("Content-Length", ignoreCase = true) }
                ?.substringAfter(":")?.trim()?.toIntOrNull() ?: 0

            val body = ByteArray(contentLength)
            var read = 0
            while (read < contentLength) {
                val r = input.read(body, read, contentLength - read)
                if (r == -1) break
                read += r
            }
            val requestBody = String(body, 0, read, Charsets.UTF_8)
            Log.d(TAG, "Received request: $requestBody")

            try {
                val requestJson = json.parseToJsonElement(requestBody).jsonObject
                val messagesArray = requestJson["messages"]?.jsonArray ?: buildJsonArray {}
                val prompt = formatGemmaPrompt(messagesArray)
                Log.d(TAG, "Formatted prompt: $prompt")

                val responseText = helper.generateResponse(prompt)
                Log.d(TAG, "Generated response: $responseText")

                val responseJson = buildJsonObject {
                    put("choices", buildJsonArray {
                        add(buildJsonObject {
                            put("message", buildJsonObject {
                                put("role", "assistant")
                                put("content", responseText)
                            })
                        })
                    })
                }
                writeResponse(output, 200, responseJson.toString(), "application/json")
            } catch (e: Exception) {
                Log.e(TAG, "Error generating response: ${e.message}", e)
                writeResponse(output, 500, "Inference error: ${e.message}", "text/plain")
            }
        }
    }

    private fun formatGemmaPrompt(messages: JsonArray): String {
        val sb = StringBuilder()
        for (msgElement in messages) {
            val msg = msgElement.jsonObject
            val role = msg["role"]?.jsonPrimitive?.content ?: "user"
            val content = msg["content"]?.jsonPrimitive?.content ?: ""
            when (role) {
                "system" -> sb.append("<start_of_turn>user\nsystem: $content\n")
                "user" -> sb.append("<start_of_turn>user\n$content<end_of_turn>\n")
                "assistant" -> sb.append("<start_of_turn>model\n$content<end_of_turn>\n")
            }
        }
        sb.append("<start_of_turn>model\n")
        return sb.toString()
    }

    private fun writeResponse(
        out: OutputStream,
        statusCode: Int,
        response: String,
        contentType: String = "application/json",
    ) {
        try {
            val reason = when (statusCode) {
                200 -> "OK"; 204 -> "No Content"; 400 -> "Bad Request"
                404 -> "Not Found"; 405 -> "Method Not Allowed"; 500 -> "Internal Server Error"
                else -> "OK"
            }
            val bytes = response.toByteArray(Charsets.UTF_8)
            val header = buildString {
                append("HTTP/1.1 $statusCode $reason\r\n")
                append("Content-Type: $contentType\r\n")
                append("Content-Length: ${bytes.size}\r\n")
                append("Access-Control-Allow-Origin: *\r\n")
                append("Access-Control-Allow-Methods: POST, GET, OPTIONS\r\n")
                append("Access-Control-Allow-Headers: Content-Type\r\n")
                append("Connection: close\r\n")
                append("\r\n")
            }
            out.write(header.toByteArray(Charsets.US_ASCII))
            if (bytes.isNotEmpty()) out.write(bytes)
            out.flush()
        } catch (e: Exception) {
            Log.e(TAG, "Error sending response: ${e.message}", e)
        }
    }
}
