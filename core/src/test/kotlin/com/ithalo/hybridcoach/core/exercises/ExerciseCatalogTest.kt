package com.ithalo.hybridcoach.core.exercises

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ExerciseCatalogTest {
    private val catalog = ExerciseCatalog(
        ExerciseCatalog.parse(checkNotNull(javaClass.classLoader.getResource("exercises.json")).readText()),
    )

    @Test
    fun `normalize strips accents case and extra spaces`() {
        assertEquals("panturrilha em pe", ExerciseCatalog.normalize("  Panturrilha   EM Pé "))
        assertEquals("triceps frances", ExerciseCatalog.normalize("Tríceps Francês"))
    }

    @Test
    fun `normalize treats punctuation as a space`() {
        assertEquals("step up", ExerciseCatalog.normalize("Step-up"))
        assertEquals("leg press 45", ExerciseCatalog.normalize("Leg press 45!"))
    }

    @Test
    fun `matches canonical name ignoring accents and case`() {
        assertEquals("panturrilha-em-pe", catalog.match("panturrilha EM pe")?.id)
        assertEquals("supino-reto-barra", catalog.match("SUPINO RETO COM BARRA")?.id)
    }

    @Test
    fun `matches aliases in portuguese and english`() {
        assertEquals("supino-reto-barra", catalog.match("supino reto")?.id)
        assertEquals("supino-reto-barra", catalog.match("Bench Press")?.id)
        assertEquals("stiff", catalog.match("Romanian deadlift")?.id)
        assertEquals("step-up", catalog.match("step up")?.id)
    }

    @Test
    fun `unknown name does not match`() {
        assertNull(catalog.match("Supino em pé na bola suíça"))
        assertNull(catalog.match(""))
    }

    @Test
    fun `new custom ids are stable slugs`() {
        assertEquals("custom-supino-na-bola-suica", ExerciseCatalog.newId("Supino na Bola Suíça"))
    }

    @Test
    fun `first entry wins when two entries share a name`() {
        val c = ExerciseCatalog(listOf(CatalogEntry("a", "Remada", emptyList()), CatalogEntry("b", "Outra", listOf("remada"))))
        assertEquals("a", c.match("remada")?.id)
    }
}
