package com.ithalo.hybridcoach.core.plan

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

sealed interface ExtractResult {
    data class Found(val json: JsonObject) : ExtractResult
    data class Failed(val message: String) : ExtractResult
}

/**
 * Pulls the JSON object out of a chat reply, which may be wrapped in markdown fences
 * and surrounding prose. Rejects the text when more than one object parses.
 */
object JsonExtractor {
    fun extract(text: String): ExtractResult {
        val candidates = topLevelObjects(text)
        if (candidates.isEmpty()) {
            return if ('{' in text) {
                ExtractResult.Failed("JSON incompleto: uma chave '{' não foi fechada (a resposta pode ter sido cortada)")
            } else {
                ExtractResult.Failed("nenhum objeto JSON encontrado no texto")
            }
        }

        val parsed = mutableListOf<JsonObject>()
        var firstError: String? = null
        for (candidate in candidates) {
            try {
                val element: JsonElement = Json.parseToJsonElement(candidate)
                if (element is JsonObject) parsed += element
            } catch (e: Exception) {
                if (firstError == null) firstError = e.message?.lineSequence()?.firstOrNull()
            }
        }

        return when {
            parsed.size == 1 -> ExtractResult.Found(parsed.single())
            parsed.size > 1 -> ExtractResult.Failed("mais de um objeto JSON no texto (${parsed.size}); envie somente o JSON do plano")
            else -> ExtractResult.Failed("JSON malformado: ${firstError ?: "não foi possível interpretar"}")
        }
    }

    /** Balanced `{...}` spans at depth 0. Quotes only matter inside an object, so prose apostrophes are harmless. */
    private fun topLevelObjects(text: String): List<String> {
        val result = mutableListOf<String>()
        var depth = 0
        var start = -1
        var inString = false
        var escaped = false
        for (i in text.indices) {
            val c = text[i]
            if (depth > 0 && inString) {
                when {
                    escaped -> escaped = false
                    c == '\\' -> escaped = true
                    c == '"' -> inString = false
                }
                continue
            }
            when (c) {
                '"' -> if (depth > 0) inString = true
                '{' -> {
                    if (depth == 0) start = i
                    depth++
                }
                '}' -> if (depth > 0) {
                    depth--
                    if (depth == 0) result += text.substring(start, i + 1)
                }
            }
        }
        return result
    }
}
