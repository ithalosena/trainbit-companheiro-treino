package com.ithalo.hybridcoach.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        PlanEntity::class,
        PlannedSessionEntity::class,
        PlannedExerciseEntity::class,
        RunBlockEntity::class,
        ExerciseEntity::class,
        ExerciseAliasEntity::class,
        QuestionnaireEntity::class,
        ExportedSummaryEntity::class,
        SetLogEntity::class,
        SessionFeedbackEntity::class,
        RunLogEntity::class,
        RunSplitEntity::class,
        RunProgressEntity::class,
        RunTrackPointEntity::class,
    ],
    version = 5,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): HybridDao

    companion object {
        /** Phase 2: strength player. The CREATE statements are copied from the exported schema (schemas/.../2.json). */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `PlannedExerciseEntity` ADD COLUMN `status` TEXT NOT NULL DEFAULT 'pending'")
                db.execSQL("ALTER TABLE `PlannedExerciseEntity` ADD COLUMN `skipReason` TEXT")
                db.execSQL("ALTER TABLE `PlannedExerciseEntity` ADD COLUMN `substituteExerciseId` TEXT")
                db.execSQL("ALTER TABLE `PlannedExerciseEntity` ADD COLUMN `substituteReason` TEXT")
                db.execSQL("ALTER TABLE `PlannedExerciseEntity` ADD COLUMN `extraSets` INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `SetLogEntity` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`sessionId` INTEGER NOT NULL, `plannedExerciseId` INTEGER NOT NULL, `exerciseId` TEXT NOT NULL, " +
                        "`setIndex` INTEGER NOT NULL, `repsDone` INTEGER NOT NULL, `loadKg` REAL NOT NULL, " +
                        "`failed` INTEGER NOT NULL, `rpe` INTEGER, `timestamp` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`sessionId`) REFERENCES `PlannedSessionEntity`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
                        "FOREIGN KEY(`plannedExerciseId`) REFERENCES `PlannedExerciseEntity`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_SetLogEntity_sessionId` ON `SetLogEntity` (`sessionId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_SetLogEntity_plannedExerciseId` ON `SetLogEntity` (`plannedExerciseId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_SetLogEntity_exerciseId` ON `SetLogEntity` (`exerciseId`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `SessionFeedbackEntity` (`sessionId` INTEGER NOT NULL, " +
                        "`rpeOverall` INTEGER NOT NULL, `note` TEXT NOT NULL, PRIMARY KEY(`sessionId`), " +
                        "FOREIGN KEY(`sessionId`) REFERENCES `PlannedSessionEntity`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
                )
            }
        }

        /** Home version of strength sessions: which version a session uses and which version each exercise belongs to. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `PlannedSessionEntity` ADD COLUMN `variant` TEXT NOT NULL DEFAULT 'gym'")
                db.execSQL("ALTER TABLE `PlannedExerciseEntity` ADD COLUMN `variant` TEXT NOT NULL DEFAULT 'gym'")
            }
        }

        /** Phase 3: run player. The CREATE statements are copied from the exported schema (schemas/.../4.json). */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `RunLogEntity` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `sessionId` INTEGER NOT NULL, " +
                        "`startedAt` INTEGER NOT NULL, `endedAt` INTEGER NOT NULL, `distanceM` REAL NOT NULL, `durationSeconds` INTEGER NOT NULL, " +
                        "`avgPaceSecPerKm` INTEGER, `notes` TEXT NOT NULL, `stepsJson` TEXT NOT NULL, `batteryStart` INTEGER, `batteryEnd` INTEGER, " +
                        "FOREIGN KEY(`sessionId`) REFERENCES `PlannedSessionEntity`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_RunLogEntity_sessionId` ON `RunLogEntity` (`sessionId`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `RunSplitEntity` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `runLogId` INTEGER NOT NULL, " +
                        "`kmIndex` INTEGER NOT NULL, `seconds` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`runLogId`) REFERENCES `RunLogEntity`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_RunSplitEntity_runLogId` ON `RunSplitEntity` (`runLogId`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `RunProgressEntity` (`sessionId` INTEGER NOT NULL, `stateJson` TEXT NOT NULL, " +
                        "`batteryStart` INTEGER, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`sessionId`), " +
                        "FOREIGN KEY(`sessionId`) REFERENCES `PlannedSessionEntity`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `RunTrackPointEntity` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `sessionId` INTEGER NOT NULL, " +
                        "`timeMs` INTEGER NOT NULL, `lat` REAL NOT NULL, `lon` REAL NOT NULL, `accuracyM` REAL NOT NULL, " +
                        "FOREIGN KEY(`sessionId`) REFERENCES `PlannedSessionEntity`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_RunTrackPointEntity_sessionId` ON `RunTrackPointEntity` (`sessionId`)")
            }
        }

        /** Complementary sessions (`"optional": true` in the plan). */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `PlannedSessionEntity` ADD COLUMN `optional` INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "hybrid-coach.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .build()
    }
}
