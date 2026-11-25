package com.ithalo.hybridcoach.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.io.File

@Database(
    entities = [
        PlanEntity::class,
        BlockSnapshotEntity::class,
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
    version = AppDatabase.VERSION,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): HybridDao

    companion object {
        const val VERSION = 9

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

        /** Workout state kept in the database: started session, exercise queue, stopwatch (see [MigrationSql.V5_TO_6]). */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MigrationSql.V5_TO_6.forEach(db::execSQL)
            }
        }

        /** Muscle groups of exercises created in the app (see [MigrationSql.V6_TO_7]). */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MigrationSql.V6_TO_7.forEach(db::execSQL)
            }
        }

        /** Block lifecycle, snapshot and pain per exercise (see [MigrationSql.V7_TO_8]). */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MigrationSql.V7_TO_8.forEach(db::execSQL)
            }
        }

        /** Queues per modality: planned date, place in the queue, lateness and comebacks (see [MigrationSql.V8_TO_9]). */
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MigrationSql.V8_TO_9.forEach(db::execSQL)
            }
        }

        fun create(context: Context): AppDatabase {
            keepCopyBeforeMigration(context)
            return Room.databaseBuilder(context, AppDatabase::class.java, BackupManager.DB_NAME)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9)
                .build()
        }

        /**
         * Before Room migrates an older database, a copy of it is kept as `files/pre-migration-v<old>.db` (one per
         * old version, never overwritten). Migrations only add, but this way the data survives even a bad one.
         */
        private fun keepCopyBeforeMigration(context: Context) {
            runCatching {
                val file = context.getDatabasePath(BackupManager.DB_NAME)
                if (!file.exists()) return
                val old = SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
                    db.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { it.moveToFirst() }
                    db.version
                }
                if (old in 1 until VERSION) {
                    val copy = File(context.filesDir, "pre-migration-v$old.db")
                    if (!copy.exists()) file.copyTo(copy)
                }
            }.onFailure { Log.w("AppDatabase", "Could not keep a copy before the migration", it) }
        }
    }
}
