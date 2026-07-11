package com.example.agent

/**
 * Process-wide handle to the initialized on-device LiteRT-LM engine so the agent
 * loop can run inference IN-PROCESS (with token streaming) instead of going over
 * the loopback HTTP server. Direct calls avoid the HTTP read-timeout that kills
 * long generations (a full HTML page is thousands of tokens) and let the UI show
 * tokens as they are produced.
 *
 * Set by [LocalLlmServer] once the engine initializes; null until then.
 */
object EngineProvider {
    @Volatile
    var helper: LiteRtLmHelper? = null
}
