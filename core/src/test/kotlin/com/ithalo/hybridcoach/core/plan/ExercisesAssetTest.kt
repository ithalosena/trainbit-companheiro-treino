package com.ithalo.hybridcoach.core.plan

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.text.Normalizer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExercisesAssetTest {
    private val entries = Json.parseToJsonElement(
        checkNotNull(javaClass.classLoader.getResource("exercises.json")).readText(),
    ).jsonObject.getValue("exercises").jsonArray.map { it.jsonObject }

    private fun normalize(s: String) =
        Normalizer.normalize(s.trim().lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").replace(Regex("\\s+"), " ")

    @Test
    fun `has about sixty exercises`() {
        assertTrue(entries.size in 55..70, "found ${entries.size}")
    }

    @Test
    fun `ids are unique and well formed`() {
        val ids = entries.map { it.getValue("id").jsonPrimitive.content }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(ids.all { it.matches(Regex("[a-z0-9]+(-[a-z0-9]+)*")) })
    }

    @Test
    fun `no name or alias maps to two different exercises`() {
        val owner = mutableMapOf<String, String>()
        for (e in entries) {
            val id = e.getValue("id").jsonPrimitive.content
            val names = listOf(e.getValue("canonical_name").jsonPrimitive.content) +
                e.getValue("aliases").jsonArray.map { it.jsonPrimitive.content }
            for (name in names) {
                val key = normalize(name)
                val previous = owner.put(key, id)
                assertTrue(previous == null || previous == id, "\"$name\" belongs to both $previous and $id")
            }
        }
    }

    @Test
    fun `every exercise name used by valid_full is recognised`() {
        val known = entries.flatMap { e ->
            listOf(e.getValue("canonical_name").jsonPrimitive.content) + e.getValue("aliases").jsonArray.map { it.jsonPrimitive.content }
        }.map(::normalize).toSet()
        val doc = checkNotNull(PlanValidator.validateText(
            checkNotNull(javaClass.classLoader.getResource("valid_full.json")).readText(),
        ).document)
        val names = doc.weeks.flatMap { it.sessions }.filterIsInstance<StrengthSessionDef>().flatMap { it.exercises }.map { it.name }
        names.forEach { assertTrue(normalize(it) in known, "\"$it\" is missing from exercises.json") }
    }
}
