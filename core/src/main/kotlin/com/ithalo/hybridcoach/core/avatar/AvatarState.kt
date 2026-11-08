package com.ithalo.hybridcoach.core.avatar

import com.ithalo.hybridcoach.core.game.GameFacts
import com.ithalo.hybridcoach.core.game.SessionFact
import java.time.LocalDate
import kotlin.math.max
import kotlin.math.min

/** How the pet looks. Never punitive: tired means "trains a lot", sluggish means "come back when you want". */
enum class AvatarMood { ENERGIZED, NORMAL, TIRED, SLUGGISH, SLEEPING }

/** The recent history the mood is computed from; see [AvatarInputs.from]. */
data class AvatarInput(
    val today: LocalDate,
    /** Sessions done in the last 7 days (today included). */
    val doneLast7: Int,
    /** Sessions that were scheduled in the last 7 days (done, skipped or still pending in the past). */
    val plannedLast7: Int,
    /** Null when nothing was ever done. */
    val daysSinceLastWorkout: Int?,
    val weekStreak: Int,
    /** RPE of the last sessions that have one, most recent first (at most 3 are used). */
    val recentRpes: List<Int>,
    /** Days in a row with a done session, ending today or yesterday. */
    val consecutiveTrainingDays: Int,
    val doneLast3Days: Int,
    val plannedToday: Boolean,
    val doneToday: Boolean,
)

data class AvatarState(
    val mood: AvatarMood,
    /** 0..100, how in the groove the player is. */
    val vitality: Int,
    /** 0..100, load from hard and back-to-back sessions. */
    val fatigue: Int,
    /** A short positive line, in pt-BR. */
    val tip: String,
)

/** Tuning constants, all in one place. */
object AvatarTuning {
    const val SLEEP_FROM_HOUR = 23
    const val SLEEP_UNTIL_HOUR = 6
    const val FATIGUE_SLEEP = 90
    const val FATIGUE_TIRED = 60
    const val VITALITY_ENERGIZED = 70
    const val INACTIVE_DAYS = 5

    const val VITALITY_BASE = 30
    const val VITALITY_ADHERENCE = 35
    const val VITALITY_NO_PLAN_ADHERENCE = 0.5
    const val VITALITY_PER_STREAK_WEEK = 4
    const val VITALITY_STREAK_CAP = 24
    const val VITALITY_RECENT_1D = 11
    const val VITALITY_RECENT_3D = 5
    const val VITALITY_IDLE_FREE_DAYS = 4
    const val VITALITY_IDLE_PER_DAY = 6
    const val VITALITY_IDLE_CAP = 50

    const val FATIGUE_RPE_BASELINE = 5.0
    const val FATIGUE_PER_RPE = 14
    const val FATIGUE_RPE_CAP = 56
    const val FATIGUE_PER_EXTRA_SESSION_3D = 12
    const val FATIGUE_LOAD_CAP = 30
    const val FATIGUE_PER_STREAK_DAY = 7
    const val FATIGUE_STREAK_CAP = 14
    const val FATIGUE_REST_PER_DAY = 20
    const val FATIGUE_REST_CAP = 60
}

private val TIPS = mapOf(
    AvatarMood.ENERGIZED to listOf("Hoje o dia está bom pra treinar!", "Cheio de energia!", "Bora manter o ritmo!"),
    AvatarMood.NORMAL to listOf("Um treino de cada vez.", "Constância vale mais que pressa.", "Tudo certo por aqui."),
    AvatarMood.TIRED to listOf("Descanso também treina.", "O corpo cresce no descanso.", "Hora de cuidar da recuperação."),
    AvatarMood.SLUGGISH to listOf("Que tal um treino leve hoje?", "Quando quiser voltar, estou aqui.", "Um começo curto já conta."),
    AvatarMood.SLEEPING to listOf("Zzz… até amanhã.", "Dormir também faz parte."),
)
private const val TIP_TODAY = "Hoje tem treino. Vamos?"
private const val TIP_DONE = "Treino de hoje feito!"

private fun clamp(v: Int) = max(0, min(100, v))

/** Pure: the mood, the two indexes and a tip for [input] at [hour] (0..23). */
fun computeAvatarState(input: AvatarInput, hour: Int): AvatarState {
    val vitality = vitality(input)
    val fatigue = fatigue(input)
    val asleepByClock = hour >= AvatarTuning.SLEEP_FROM_HOUR || hour < AvatarTuning.SLEEP_UNTIL_HOUR
    val days = input.daysSinceLastWorkout
    val mood = when {
        asleepByClock || fatigue >= AvatarTuning.FATIGUE_SLEEP -> AvatarMood.SLEEPING
        fatigue >= AvatarTuning.FATIGUE_TIRED -> AvatarMood.TIRED
        days != null && days >= AvatarTuning.INACTIVE_DAYS -> AvatarMood.SLUGGISH
        vitality >= AvatarTuning.VITALITY_ENERGIZED -> AvatarMood.ENERGIZED
        else -> AvatarMood.NORMAL
    }
    val tips = TIPS.getValue(mood)
    val tip = when {
        input.doneToday && (mood == AvatarMood.NORMAL || mood == AvatarMood.ENERGIZED) -> TIP_DONE
        input.plannedToday && (mood == AvatarMood.NORMAL || mood == AvatarMood.ENERGIZED) -> TIP_TODAY
        else -> tips[Math.floorMod(input.today.toEpochDay(), tips.size.toLong()).toInt()]
    }
    return AvatarState(mood, vitality, fatigue, tip)
}

private fun vitality(i: AvatarInput): Int {
    val t = AvatarTuning
    val days = i.daysSinceLastWorkout
    if (days == null && i.plannedLast7 == 0) return t.VITALITY_BASE + 10 // brand new: neutral, no judgement
    val adherence = if (i.plannedLast7 > 0) min(1.0, i.doneLast7.toDouble() / i.plannedLast7) else t.VITALITY_NO_PLAN_ADHERENCE
    var v = t.VITALITY_BASE + t.VITALITY_ADHERENCE * adherence + min(t.VITALITY_STREAK_CAP, t.VITALITY_PER_STREAK_WEEK * i.weekStreak)
    if (days != null) {
        v += when {
            days <= 1 -> t.VITALITY_RECENT_1D
            days <= 3 -> t.VITALITY_RECENT_3D
            else -> 0
        }
        v -= min(t.VITALITY_IDLE_CAP, max(0, days - t.VITALITY_IDLE_FREE_DAYS) * t.VITALITY_IDLE_PER_DAY)
    }
    return clamp(v.toInt())
}

private fun fatigue(i: AvatarInput): Int {
    val t = AvatarTuning
    val rpes = i.recentRpes.take(3)
    val rpePart = if (rpes.isEmpty()) 0 else min(t.FATIGUE_RPE_CAP, max(0.0, (rpes.average() - t.FATIGUE_RPE_BASELINE) * t.FATIGUE_PER_RPE).toInt())
    val loadPart = min(t.FATIGUE_LOAD_CAP, t.FATIGUE_PER_EXTRA_SESSION_3D * max(0, i.doneLast3Days - 1))
    val streakPart = min(t.FATIGUE_STREAK_CAP, t.FATIGUE_PER_STREAK_DAY * max(0, i.consecutiveTrainingDays - 1))
    val rest = i.daysSinceLastWorkout?.let { min(t.FATIGUE_REST_CAP, it * t.FATIGUE_REST_PER_DAY) } ?: 0
    return clamp(rpePart + loadPart + streakPart - rest)
}

object AvatarInputs {
    /** Reads the avatar's inputs from the game history. [weekStreak] is the engine's current streak. */
    fun from(facts: GameFacts, weekStreak: Int): AvatarInput {
        val today = facts.today
        val weekAgo = today.minusDays(6)
        val past = facts.sessions.filter { !it.date.isAfter(today) }
        val done = past.filter { it.done }
        val doneDays = done.map { it.date }.toSet()
        var consecutive = 0
        var day = if (today in doneDays) today else today.minusDays(1)
        while (day in doneDays) {
            consecutive++
            day = day.minusDays(1)
        }
        val lastDone = doneDays.maxOrNull()
        return AvatarInput(
            today = today,
            doneLast7 = done.count { !it.date.isBefore(weekAgo) },
            plannedLast7 = past.count { !it.date.isBefore(weekAgo) },
            daysSinceLastWorkout = lastDone?.let { (today.toEpochDay() - it.toEpochDay()).toInt() },
            weekStreak = weekStreak,
            recentRpes = done.sortedWith(compareByDescending<SessionFact> { it.date }.thenByDescending { it.sessionId }).mapNotNull { it.rpe }.take(3),
            consecutiveTrainingDays = consecutive,
            doneLast3Days = done.count { !it.date.isBefore(today.minusDays(2)) },
            plannedToday = facts.sessions.any { it.date == today },
            doneToday = today in doneDays,
        )
    }
}
