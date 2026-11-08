package com.ithalo.hybridcoach.core.game

import java.time.LocalDate

enum class SessionKind { STRENGTH, RUN }

/** A scheduled session. Pending sessions have both [done] and [skipped] false. */
data class SessionFact(
    val sessionId: Long,
    val planId: Long,
    val date: LocalDate,
    val kind: SessionKind,
    val done: Boolean,
    val skipped: Boolean,
    val rpe: Int?,
)

/** A logged set. For [timed] exercises (planks, intervals) [reps] holds the seconds done. */
data class SetFact(
    val sessionId: Long,
    val exerciseId: String,
    val exerciseName: String,
    val reps: Int,
    val loadKg: Double,
    val failed: Boolean,
    val date: LocalDate,
    val timed: Boolean = false,
)

/** [splitSeconds] are the times of each full kilometre, in order. */
data class RunFact(
    val sessionId: Long,
    val date: LocalDate,
    val distanceM: Double,
    val durationSeconds: Long,
    val avgPaceSecPerKm: Int?,
    val splitSeconds: List<Int>,
)

data class PlanFact(
    val planId: Long,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val totalSessions: Int,
    val doneSessions: Int,
)

/** Everything the engine needs, read from the app's history. [today] replaces the system clock. */
data class GameFacts(
    val sessions: List<SessionFact>,
    val sets: List<SetFact>,
    val runs: List<RunFact>,
    val plans: List<PlanFact>,
    val summariesExported: Int,
    val questionnaireDone: Boolean,
    val today: LocalDate,
)

enum class TrophyTier { BRONZE, SILVER, GOLD, PLATINUM }

/** [icon] is one of [GameIcons.all]. [target] is the progress needed (1 for one-shot trophies). */
data class AchievementDef(
    val id: String,
    val title: String,
    val description: String,
    val tier: TrophyTier,
    val icon: String,
    val hidden: Boolean,
    val target: Int,
    val xp: Int,
)

/** [progress] is capped at the target; [unlockedOn] is the day the threshold was crossed. */
data class AchievementState(val def: AchievementDef, val progress: Int, val unlockedOn: LocalDate?) {
    val unlocked: Boolean get() = unlockedOn != null
}

enum class ItemKind { TITLE, FRAME, STICKER }

data class ItemDef(
    val id: String,
    val name: String,
    val description: String,
    val kind: ItemKind,
    val icon: String,
    val unlock: ItemUnlock,
)

sealed interface ItemUnlock {
    data class Level(val level: Int) : ItemUnlock
    data class Achievement(val id: String) : ItemUnlock
}

data class ItemState(val def: ItemDef, val unlocked: Boolean)

/** Best set of an exercise. When [timed], [reps] is the longest time in seconds. */
data class PersonalRecord(
    val exerciseId: String,
    val exerciseName: String,
    val loadKg: Double,
    val reps: Int,
    val date: LocalDate,
    val timed: Boolean = false,
)

enum class RunRecordKind { LONGEST_DISTANCE, LONGEST_TIME, BEST_PACE, FASTEST_KM }

/**
 * A running best and the day it was set. [value] is meters for [RunRecordKind.LONGEST_DISTANCE], seconds for
 * [RunRecordKind.LONGEST_TIME] and seconds per km for the pace ones (average of a run of 1 km or more, fastest single km).
 */
data class RunRecord(val kind: RunRecordKind, val value: Double, val date: LocalDate)

enum class MissionKind { SESSIONS, RUN_KM, SETS, RECORD, EARLY }

/** One of the three goals of an ISO week. [progress] is capped at [target]. */
data class MissionState(val kind: MissionKind, val title: String, val target: Int, val progress: Int, val xp: Int) {
    val done: Boolean get() = progress >= target
}

data class GameStats(
    val sessionsDone: Int,
    val strengthSessions: Int,
    val runSessions: Int,
    val totalSets: Int,
    val tonnageKg: Double,
    val totalKm: Double,
    val currentWeekStreak: Int,
    val bestWeekStreak: Int,
    val longestRunKm: Double,
    val bestPaceSecPerKm: Int?,
)

data class XpEvent(val date: LocalDate, val amount: Int, val reason: String)

/**
 * At the max level [xpIntoLevel] is capped at [xpForNextLevel], so a progress bar shows full.
 * [records] are sorted by exercise name; [xpLog] is most recent first, capped at [GameEngine.XP_LOG_SIZE].
 */
data class GameState(
    val xp: Int,
    val level: Int,
    val xpIntoLevel: Int,
    val xpForNextLevel: Int,
    val rankTitle: String,
    val achievements: List<AchievementState>,
    val items: List<ItemState>,
    val stats: GameStats,
    val records: List<PersonalRecord>,
    val xpLog: List<XpEvent>,
    /** This week's missions (see [WeeklyMissions]). */
    val missions: List<MissionState> = emptyList(),
    /** Running bests, in [RunRecordKind] order; a kind with no run yet is left out. */
    val runRecords: List<RunRecord> = emptyList(),
)

sealed interface Unlock {
    data class LevelUp(val level: Int, val rankTitle: String) : Unlock
    data class AchievementUnlocked(val achievement: AchievementState) : Unlock
    data class ItemUnlocked(val item: ItemState) : Unlock
}

/** Icon keys the UI knows how to draw as pixel art. */
object GameIcons {
    val all = setOf(
        "trophy", "medal", "star", "heart", "coin", "dumbbell", "shoe", "calendar", "clock", "hourglass",
        "flame", "floppy", "folder", "skull", "check", "warning", "crown", "lightning", "map", "mountain",
    )
}
