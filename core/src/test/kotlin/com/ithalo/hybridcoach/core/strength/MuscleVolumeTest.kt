package com.ithalo.hybridcoach.core.strength

import kotlin.test.Test
import kotlin.test.assertEquals

class MuscleVolumeTest {
    private val muscles = mapOf(
        "supino" to (listOf("peitoral") to listOf("tríceps", "deltoides")),
        "triceps" to (listOf("tríceps") to emptyList()),
        "velho" to (emptyList<String>() to emptyList()),
    )

    @Test
    fun `main muscles count a set, secondary ones half, most worked first`() {
        val v = MuscleVolume.sets(listOf("supino", "supino", "supino", "triceps", "triceps", "sem-catalogo")) { muscles[it] }
        assertEquals(listOf("peitoral" to 3.0, "tríceps" to 3.5, "deltoides" to 1.5).sortedByDescending { it.second }, v)
    }

    @Test
    fun `no sets or no muscles give an empty list`() {
        assertEquals(emptyList(), MuscleVolume.sets(emptyList()) { muscles[it] })
        assertEquals(emptyList(), MuscleVolume.sets(listOf("velho")) { muscles[it] })
    }
}
