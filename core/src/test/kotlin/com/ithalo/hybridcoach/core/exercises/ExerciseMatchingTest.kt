package com.ithalo.hybridcoach.core.exercises

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExerciseMatchingTest {
    private fun resource(name: String) = checkNotNull(javaClass.classLoader.getResource(name)).readText()

    private val appOnly = ExerciseCatalog(ExerciseCatalog.parse(resource("exercises.json")))
    private val full = ExerciseCatalog(ExerciseCatalog.parseCatalog(resource("exercises_catalog.json")))

    /** Names as chat AIs write them in real plans (accents, abbreviations, word order, extras in parentheses). */
    private val aiNames = listOf(
        "Supino reto com barra", "Supino inclinado com halteres", "Crucifixo na máquina (peck deck)", "Desenvolvimento militar com barra",
        "Elevação lateral com halteres", "Tríceps corda na polia", "Tríceps francês com halter", "Puxada alta pegada aberta",
        "Remada curvada com barra", "Remada unilateral com halter", "Rosca direta com barra W", "Rosca martelo alternada",
        "Agachamento livre", "Leg press 45°", "Cadeira extensora", "Mesa flexora deitada", "Stiff com halteres",
        "Elevação pélvica com barra", "Panturrilha sentado", "Prancha abdominal", "Face pull na polia", "Afundo búlgaro com halteres",
        "Levantamento terra convencional", "Supino reto c/ halteres", "Rosca alternada DB", "Barra fixa pegada pronada",
    )

    @Test
    fun `key ignores order, plural, accents, small words and abbreviations`() {
        assertEquals(ExerciseCatalog.key("Rosca alternada com halteres"), ExerciseCatalog.key("rosca halter alternada"))
        assertEquals(ExerciseCatalog.key("Supino inclinado DB"), ExerciseCatalog.key("supino com halteres inclinado"))
        assertEquals(ExerciseCatalog.key("Tríceps no cabo"), ExerciseCatalog.key("triceps na polia"))
        assertEquals("barra rosca", ExerciseCatalog.key("Roscas c/ barra"))
        assertEquals("", ExerciseCatalog.key("de com a"))
    }

    @Test
    fun `similarity is 1 for the same words and drops with different ones`() {
        val a = ExerciseCatalog.key("Remada curvada com barra")
        assertEquals(1.0, ExerciseCatalog.similarity(a, ExerciseCatalog.key("barra remada curvada")))
        val close = ExerciseCatalog.similarity(a, ExerciseCatalog.key("Remada curvada com halteres"))
        val far = ExerciseCatalog.similarity(a, ExerciseCatalog.key("Cadeira extensora"))
        assertTrue(close > 0.5, "close $close")
        assertTrue(far < ExerciseCatalog.MIN_SCORE, "far $far")
        assertEquals(0.0, ExerciseCatalog.similarity("", a))
    }

    @Test
    fun `suggestions are the three best, best first`() {
        val s = full.suggest("Supino inclinado com halter")
        assertEquals(3, s.size)
        assertTrue(s.zipWithNext().all { (x, y) -> x.score >= y.score })
        assertTrue(s.first().entry.canonicalName.startsWith("Supino"), s.toString())
        assertTrue(full.suggest("xyzzy plugh").isEmpty())
    }

    @Test
    fun `only a near certain suggestion is taken without asking`() {
        assertEquals("supino-reto-barra", full.resolve("SUPINO RETO COM BARRA")?.id)
        assertEquals("rosca-alternada-halteres", full.resolve("Rosca alternada DB")?.id)
        assertNull(full.resolve("Supino em pé na bola suíça com elástico"))
    }

    @Test
    fun `every exercise of the catalog has muscles except the ones without a source`() {
        val withoutMuscles = full.entries.filter { it.primary.isEmpty() }.map { it.id }
        assertEquals(listOf("bird-dog"), withoutMuscles)
        assertTrue(full.entries.size > 800)
        val supino = full.byId("supino-reto-barra")!!
        assertEquals(listOf("peitoral"), supino.primary)
        assertTrue("tríceps" in supino.secondary)
    }

    @Test
    fun `common AI names (the ones the aliases were written for) all resolve`() {
        assertEquals(emptyList(), aiNames.filter { full.resolve(it) == null })
    }

    /** Other AI names, not used to write aliases: they measure the matching itself. */
    private val unseenNames = listOf(
        "Supino declinado com barra", "Crucifixo inclinado com halteres", "Pullover com halter", "Remada baixa pegada neutra",
        "Remada curvada supinada", "Puxada frontal pegada fechada", "Rosca concentrada", "Rosca Scott com barra W",
        "Tríceps testa com barra W", "Tríceps coice com halter", "Agachamento sumô com halter", "Agachamento no Smith",
        "Leg press horizontal", "Cadeira flexora unilateral", "Elevação de panturrilha no leg press", "Abdominal na polia",
        "Levantamento terra romeno com halteres", "Desenvolvimento Arnold", "Elevação lateral na polia", "Encolhimento com barra",
        "Afundo reverso com halteres", "Passada com barra", "Hiperextensão lombar", "Supino fechado",
    )

    @Test
    fun `fewer unseen AI names are left unrecognized than with exact matching`() {
        val before = unseenNames.count { appOnly.match(ExerciseCatalog.normalize(it).let { n -> n }) == null && appOnly.entries.none { e -> ExerciseCatalog.normalize(e.canonicalName) == ExerciseCatalog.normalize(it) } }
        val after = unseenNames.count { full.resolve(it) == null }
        val topRight = unseenNames.count { full.resolve(it) == null && full.suggest(it).isNotEmpty() }
        println("Nomes novos de IA não reconhecidos: antes $before de ${unseenNames.size}, depois $after de ${unseenNames.size} ($topRight deles com sugestões na prévia)")
        unseenNames.filter { full.resolve(it) == null }.forEach { println("  sugestões: $it -> " + full.suggest(it).joinToString { s -> "${s.entry.canonicalName} (%.2f)".format(s.score) }) }
        unseenNames.filter { full.resolve(it) != null }.forEach { println("  casou: $it -> ${full.resolve(it)?.canonicalName}") }
        assertTrue(after < before, "antes $before, depois $after")
    }
}
