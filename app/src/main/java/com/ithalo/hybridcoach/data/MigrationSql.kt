package com.ithalo.hybridcoach.data

/**
 * SQL of the schema migrations, kept apart from Room so the unit tests can run it on a plain SQLite built
 * from the exported schema (app/schemas). Every step only adds; nothing is dropped or rewritten.
 */
object MigrationSql {
    /** 5 -> 6: workout state that survives leaving the screen (started session, exercise queue, stopwatch). */
    val V5_TO_6 = listOf(
        "ALTER TABLE `PlannedSessionEntity` ADD COLUMN `startedAt` INTEGER",
        "ALTER TABLE `PlannedExerciseEntity` ADD COLUMN `queueOrder` INTEGER NOT NULL DEFAULT 0",
        "ALTER TABLE `PlannedExerciseEntity` ADD COLUMN `postponeCount` INTEGER NOT NULL DEFAULT 0",
        "ALTER TABLE `PlannedExerciseEntity` ADD COLUMN `postponeReason` TEXT",
        "ALTER TABLE `PlannedExerciseEntity` ADD COLUMN `timerStartedAt` INTEGER",
        "ALTER TABLE `PlannedExerciseEntity` ADD COLUMN `timerAccumulatedMs` INTEGER NOT NULL DEFAULT 0",
        "UPDATE `PlannedExerciseEntity` SET `queueOrder` = `orderIndex`",
        // A workout already under way (sets logged, not finished) counts as started at its first set.
        "UPDATE `PlannedSessionEntity` SET `startedAt` = (SELECT MIN(`timestamp`) FROM `SetLogEntity` WHERE `SetLogEntity`.`sessionId` = `PlannedSessionEntity`.`id`) " +
            "WHERE `status` = 'pending'",
    )

    /** 6 -> 7: the muscle groups of exercises created in the app (catalog exercises have theirs in the asset). */
    val V6_TO_7 = listOf(
        "ALTER TABLE `ExerciseEntity` ADD COLUMN `muscles` TEXT",
    )

    /** 7 -> 8: block lifecycle (number, previous block, phase, closed) and its frozen snapshot; pain per exercise. */
    val V7_TO_8 = listOf(
        "ALTER TABLE `PlanEntity` ADD COLUMN `number` INTEGER NOT NULL DEFAULT 1",
        "ALTER TABLE `PlanEntity` ADD COLUMN `previousPlanId` INTEGER",
        "ALTER TABLE `PlanEntity` ADD COLUMN `phase` TEXT",
        "ALTER TABLE `PlanEntity` ADD COLUMN `closedAt` INTEGER",
        // Blocks imported before are numbered in the order they were created, each following the one before.
        "UPDATE `PlanEntity` SET `number` = (SELECT COUNT(*) FROM `PlanEntity` p2 WHERE p2.`id` <= `PlanEntity`.`id`), " +
            "`previousPlanId` = (SELECT MAX(p3.`id`) FROM `PlanEntity` p3 WHERE p3.`id` < `PlanEntity`.`id`)",
        "CREATE TABLE IF NOT EXISTS `BlockSnapshotEntity` (`planId` INTEGER NOT NULL, `json` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`planId`))",
        "ALTER TABLE `PlannedExerciseEntity` ADD COLUMN `pain` INTEGER NOT NULL DEFAULT 0",
        "ALTER TABLE `PlannedExerciseEntity` ADD COLUMN `painNote` TEXT",
    )

    /**
     * 8 -> 9: queues per modality. The planned date is copied from `date` before anything can move it, and each
     * session gets its place in its modality's queue (strength or run, per block) in the order of the calendar.
     */
    val V8_TO_9 = listOf(
        "ALTER TABLE `PlannedSessionEntity` ADD COLUMN `plannedDate` TEXT NOT NULL DEFAULT ''",
        "ALTER TABLE `PlannedSessionEntity` ADD COLUMN `sequence` INTEGER NOT NULL DEFAULT 0",
        "ALTER TABLE `PlannedSessionEntity` ADD COLUMN `daysLate` INTEGER",
        "ALTER TABLE `PlannedSessionEntity` ADD COLUMN `gapDaysAtStart` INTEGER",
        "ALTER TABLE `PlannedSessionEntity` ADD COLUMN `reentryApplied` TEXT",
        "UPDATE `PlannedSessionEntity` SET `plannedDate` = `date`",
        "UPDATE `PlannedSessionEntity` SET `sequence` = (SELECT COUNT(*) FROM `PlannedSessionEntity` p2 " +
            "WHERE p2.`planId` = `PlannedSessionEntity`.`planId` AND (p2.`type` = 'run') = (`PlannedSessionEntity`.`type` = 'run') " +
            "AND (p2.`date` < `PlannedSessionEntity`.`date` OR (p2.`date` = `PlannedSessionEntity`.`date` AND p2.`id` <= `PlannedSessionEntity`.`id`)))",
    )
}
