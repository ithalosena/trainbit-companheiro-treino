package com.ithalo.hybridcoach.core.strength

import com.ithalo.hybridcoach.core.strength.CoachLines.NextSet
import kotlin.test.Test
import kotlin.test.assertEquals

class CoachLinesTest {
    private val squat = NextSet("Agachamento", 2, 4, "8", 60.0)

    @Test
    fun `same exercise announces rest then the next set`() {
        assertEquals(
            "Descanse 1 minuto e 30. Depois, série 2 de 4, 8 repetições, 60 quilos.",
            CoachLines.afterSet(90, squat, sameExercise = true),
        )
        assertEquals("Vai! Série 2 de 4.", CoachLines.restOver(squat, sameExercise = true))
    }

    @Test
    fun `new exercise is named and the previous one closed`() {
        val bench = NextSet("Supino reto", 1, 3, "8-10", null)
        assertEquals(
            "Exercício concluído. Descanse 30 segundos. Próximo: Supino reto, série 1 de 3, 8 a 10 repetições.",
            CoachLines.afterSet(30, bench, sameExercise = false),
        )
        assertEquals("Vai! Supino reto, série 1.", CoachLines.restOver(bench, sameExercise = false))
    }

    @Test
    fun `no rest goes straight to the next set`() {
        val bike = NextSet("Bike ergométrica", 1, 1, "8min", 0.0)
        assertEquals("Próximo exercício: Bike ergométrica, série 1 de 1, 8 minutos.", CoachLines.afterSet(0, bike, sameExercise = false))
    }

    @Test
    fun `targets and durations read naturally`() {
        assertEquals("1 repetição", CoachLines.target("1"))
        assertEquals("máximo de repetições", CoachLines.target("AMRAP"))
        assertEquals("45 segundos", CoachLines.target("45s"))
        assertEquals("2 minutos", CoachLines.duration(120))
        assertEquals("2 minutos e 30", CoachLines.duration(150))
        assertEquals("1 segundo", CoachLines.duration(1))
    }
}
