package com.example.util

import com.example.data.model.ForgeState
import kotlinx.serialization.json.Json

object JsonUtils {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun forgeStateToJson(state: ForgeState): String =
        json.encodeToString(ForgeState.serializer(), state)

    fun jsonToForgeState(jsonStr: String): ForgeState? = try {
        json.decodeFromString(ForgeState.serializer(), jsonStr)
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}
