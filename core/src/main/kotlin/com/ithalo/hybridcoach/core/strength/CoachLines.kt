package com.ithalo.hybridcoach.core.strength

import java.util.Locale

/** What the strength player says, in natural pt-BR (numbers and units spelled the way TTS reads well). */
object CoachLines {
    /** The set the athlete will do next. */
    data class NextSet(val exercise: String, val setNumber: Int, val totalSets: Int, val target: String, val loadKg: Double?)

    /**
     * Said right after logging a set: how long to rest and what comes next.
     * [sameExercise] keeps it short ("Depois, série 3 de 4") when the exercise does not change.
     */
    fun afterSet(restSeconds: Int, next: NextSet, sameExercise: Boolean): String {
        val what = describe(next, sameExercise)
        if (restSeconds <= 0) return if (sameExercise) "Agora, $what" else "Próximo exercício: $what"
        val rest = "Descanse ${duration(restSeconds)}."
        return if (sameExercise) "$rest Depois, $what" else "Exercício concluído. $rest Próximo: $what"
    }

    /** Said when the rest runs out. */
    fun restOver(next: NextSet, sameExercise: Boolean): String =
        if (sameExercise) "Vai! Série ${next.setNumber} de ${next.totalSets}." else "Vai! ${next.exercise}, série ${next.setNumber}."

    const val TEN_SECONDS_LEFT = "Faltam 10 segundos"
    const val SESSION_DONE = "Treino concluído. Mandou bem!"
    const val NEW_RECORD = "Novo recorde!"

    private fun describe(n: NextSet, sameExercise: Boolean): String {
        val parts = buildList {
            add(if (sameExercise) "série ${n.setNumber} de ${n.totalSets}" else "${n.exercise}, série ${n.setNumber} de ${n.totalSets}")
            target(n.target)?.let(::add)
            n.loadKg?.takeIf { it > 0 }?.let { add(load(it)) }
        }
        return parts.joinToString(", ") + "."
    }

    private val RANGE = Regex("^(\\d+)\\s*[-–]\\s*(\\d+)$")

    /** "8" → "8 repetições", "8-10" → "8 a 10 repetições", "8min" → "8 minutos", "AMRAP" → "máximo de repetições". */
    fun target(reps: String): String? {
        val t = reps.trim()
        if (t.isEmpty()) return null
        TimedTarget.seconds(t)?.let { return duration(it) }
        t.toIntOrNull()?.let { return if (it == 1) "1 repetição" else "$it repetições" }
        RANGE.matchEntire(t)?.let { return "${it.groupValues[1]} a ${it.groupValues[2]} repetições" }
        if (t.equals("amrap", ignoreCase = true)) return "máximo de repetições"
        return t
    }

    /** 30 → "30 segundos", 60 → "1 minuto", 90 → "1 minuto e 30", 120 → "2 minutos". */
    fun duration(seconds: Int): String {
        val m = seconds / 60
        val s = seconds % 60
        val min = if (m == 1) "1 minuto" else "$m minutos"
        return when {
            m == 0 -> if (s == 1) "1 segundo" else "$s segundos"
            s == 0 -> min
            else -> "$min e $s"
        }
    }

    private fun load(kg: Double): String {
        val text = if (kg == kg.toLong().toDouble()) kg.toLong().toString() else String.format(Locale.forLanguageTag("pt-BR"), "%.1f", kg)
        return "$text quilos"
    }
}
