package com.ithalo.hybridcoach.core.game

import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Level curve: going from level L to L+1 costs `round(50 * L^1.5)` XP (50, 141, 260, 400, ...).
 * Level 1 starts at 0 XP; the max level is [MAX_LEVEL]. Level 10 needs ~5.5k XP, level 20 ~36k.
 */
object Leveling {
    const val MAX_LEVEL = 99

    /** XP needed to go from [level] to the next one. */
    fun stepXp(level: Int): Int = (50 * level.toDouble().pow(1.5)).roundToInt()

    private val thresholds = IntArray(MAX_LEVEL + 1).also { t ->
        for (l in 2..MAX_LEVEL) t[l] = t[l - 1] + stepXp(l - 1)
    }

    /** Total XP at which [level] starts. */
    fun xpForLevel(level: Int): Int = thresholds[level.coerceIn(1, MAX_LEVEL)]

    fun levelFor(xp: Int): Int {
        var level = 1
        while (level < MAX_LEVEL && xp >= thresholds[level + 1]) level++
        return level
    }

    private val ranks = listOf(
        1 to "Novato 8-bit",
        5 to "Aprendiz de Disquete",
        10 to "Operador de Terminal",
        20 to "Cavaleiro do Cartucho",
        30 to "Mestre do Modem",
        40 to "Guardião da RAM",
        50 to "Campeão 16-bit",
        65 to "Arquimago do DOS",
        80 to "Herói do Fliperama",
        99 to "Lenda do Pixel",
    )

    fun rankTitle(level: Int): String = ranks.last { level >= it.first }.second
}
