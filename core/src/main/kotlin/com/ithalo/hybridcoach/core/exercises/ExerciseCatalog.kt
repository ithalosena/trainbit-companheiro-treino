package com.ithalo.hybridcoach.core.exercises

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.text.Normalizer

data class CatalogEntry(val id: String, val canonicalName: String, val aliases: List<String>)

/** Matches free-form exercise names against canonical names and aliases. */
class ExerciseCatalog(entries: List<CatalogEntry>) {
    private val byName: Map<String, CatalogEntry>

    init {
        val map = LinkedHashMap<String, CatalogEntry>()
        for (entry in entries) {
            for (name in listOf(entry.canonicalName) + entry.aliases) map.putIfAbsent(normalize(name), entry)
        }
        byName = map
    }

    fun match(name: String): CatalogEntry? = byName[normalize(name)]

    companion object {
        private val MARKS = Regex("\\p{M}+")
        private val NON_ALNUM = Regex("[^a-z0-9]+")

        /** Lowercase, no accents, punctuation treated as a space, single spaces. */
        fun normalize(name: String): String =
            Normalizer.normalize(name.lowercase(), Normalizer.Form.NFD)
                .replace(MARKS, "")
                .replace(NON_ALNUM, " ")
                .trim()

        fun newId(name: String): String = "custom-" + normalize(name).replace(' ', '-')

        fun parse(json: String): List<CatalogEntry> =
            Json.parseToJsonElement(json).jsonObject.getValue("exercises").jsonArray.map {
                val o = it.jsonObject
                CatalogEntry(
                    id = o.getValue("id").jsonPrimitive.content,
                    canonicalName = o.getValue("canonical_name").jsonPrimitive.content,
                    aliases = o.getValue("aliases").jsonArray.map { a -> a.jsonPrimitive.content },
                )
            }
    }
}
