package com.ithalo.hybridcoach.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Runs the migration SQL on a real SQLite: the database is created from the exported schema of the old version
 * (app/schemas), filled with data, migrated, and compared with the exported schema of the new version. The data
 * must survive the migration untouched.
 */
class MigrationSqlTest {
    private val schemas = File("schemas/com.ithalo.hybridcoach.data.AppDatabase")

    private fun schema(version: Int): JsonObject =
        Json.parseToJsonElement(File(schemas, "$version.json").readText()).jsonObject.getValue("database").jsonObject

    private fun create(version: Int): Connection {
        val db = DriverManager.getConnection("jdbc:sqlite::memory:")
        db.createStatement().use { st ->
            for (e in schema(version).getValue("entities").jsonArray.map { it.jsonObject }) {
                val table = e.getValue("tableName").jsonPrimitive.content
                st.execute(e.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table))
                e["indices"]?.jsonArray?.forEach { st.execute(it.jsonObject.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table)) }
            }
        }
        return db
    }

    /** Column name to (type, not null), as SQLite reports it. */
    private fun columns(db: Connection, table: String): Map<String, Pair<String, Boolean>> = buildMap {
        db.createStatement().use { st ->
            st.executeQuery("PRAGMA table_info(`$table`)").use { rs ->
                while (rs.next()) put(rs.getString("name"), rs.getString("type").uppercase() to (rs.getInt("notnull") == 1))
            }
        }
    }

    private fun expectedColumns(version: Int, table: String): Map<String, Pair<String, Boolean>> {
        val entity = schema(version).getValue("entities").jsonArray.map { it.jsonObject }.first { it.getValue("tableName").jsonPrimitive.content == table }
        return entity.getValue("fields").jsonArray.associate {
            val f = it.jsonObject
            f.getValue("columnName").jsonPrimitive.content to (f.getValue("affinity").jsonPrimitive.content to f.getValue("notNull").jsonPrimitive.boolean)
        }
    }

    private fun Connection.exec(sql: String) = createStatement().use { it.execute(sql) }

    private fun Connection.longs(sql: String): List<Long?> = createStatement().use { st ->
        st.executeQuery(sql).use { rs -> buildList { while (rs.next()) add(rs.getLong(1).takeUnless { rs.wasNull() }) } }
    }

    @Test
    fun `5 to 6 adds the workout state and keeps every row`() {
        val db = create(5)
        db.exec("INSERT INTO PlanEntity VALUES (1, 'Bloco 1', '2026-11-09', 4, 'objetivo', '', '{}', 0, 'active')")
        db.exec("INSERT INTO PlannedSessionEntity (id, planId, weekNumber, dayOfWeek, date, type, title, estimatedMinutes, status, skipReason, variant, optional) VALUES (10, 1, 1, 'mon', '2026-11-09', 'strength', 'A', 60, 'pending', NULL, 'gym', 0)")
        db.exec("INSERT INTO PlannedSessionEntity (id, planId, weekNumber, dayOfWeek, date, type, title, estimatedMinutes, status, skipReason, variant, optional) VALUES (11, 1, 1, 'wed', '2026-11-11', 'strength', 'B', 60, 'pending', NULL, 'gym', 0)")
        db.exec("INSERT INTO ExerciseEntity VALUES ('supino', 'Supino')")
        for ((id, order) in listOf(100L to 0, 101L to 1)) {
            db.exec("INSERT INTO PlannedExerciseEntity (id, sessionId, exerciseId, orderIndex, sets, reps, loadKg, restSeconds, rpeTarget, notes, status, skipReason, substituteExerciseId, substituteReason, extraSets, variant) VALUES ($id, 10, 'supino', $order, 3, '8', 40.0, 90, NULL, '', 'pending', NULL, NULL, NULL, 0, 'gym')")
        }
        db.exec("INSERT INTO SetLogEntity VALUES (1, 10, 101, 'supino', 0, 8, 40.0, 0, NULL, 5000)")
        db.exec("INSERT INTO SetLogEntity VALUES (2, 10, 101, 'supino', 1, 8, 40.0, 0, 8, 7000)")

        MigrationSql.V5_TO_6.forEach { db.exec(it) }

        for (table in listOf("PlannedSessionEntity", "PlannedExerciseEntity")) {
            assertEquals(expectedColumns(6, table), columns(db, table), table)
        }
        // A workout with sets is started at its first set; one without stays not started.
        assertEquals(listOf(5000L, null), db.longs("SELECT startedAt FROM PlannedSessionEntity ORDER BY id"))
        // The queue starts in the plan's order, nothing postponed, no stopwatch running.
        assertEquals(listOf(0L, 1L), db.longs("SELECT queueOrder FROM PlannedExerciseEntity ORDER BY id"))
        assertEquals(listOf(0L, 0L), db.longs("SELECT postponeCount FROM PlannedExerciseEntity ORDER BY id"))
        assertEquals(listOf(null, null), db.longs("SELECT timerStartedAt FROM PlannedExerciseEntity ORDER BY id"))
        // Nothing lost.
        assertEquals(listOf(2L), db.longs("SELECT COUNT(*) FROM SetLogEntity"))
        assertEquals(listOf(8L), db.longs("SELECT rpe FROM SetLogEntity WHERE id = 2"))
        assertNull(db.longs("SELECT rpe FROM SetLogEntity WHERE id = 1").single())
        db.close()
    }

    @Test
    fun `every other table is the same in 5 and 6`() {
        val v5 = schema(5).getValue("entities").jsonArray.map { it.jsonObject.getValue("tableName").jsonPrimitive.content }
        val db = create(5)
        MigrationSql.V5_TO_6.forEach { db.exec(it) }
        for (table in v5) assertEquals(expectedColumns(6, table), columns(db, table), table)
        db.close()
    }

    @Test
    fun `7 to 8 numbers the blocks, adds the snapshot table and keeps every plan`() {
        val db = create(7)
        db.exec("INSERT INTO PlanEntity VALUES (3, 'Bloco A', '2026-10-05', 4, '', '', '{}', 0, 'archived')")
        db.exec("INSERT INTO PlanEntity VALUES (8, 'Bloco B', '2026-11-09', 4, '', '', '{}', 0, 'active')")
        MigrationSql.V7_TO_8.forEach { db.exec(it) }
        for (table in schema(8).getValue("entities").jsonArray.map { it.jsonObject.getValue("tableName").jsonPrimitive.content }) {
            assertEquals(expectedColumns(8, table), columns(db, table), table)
        }
        assertEquals(listOf(1L, 2L), db.longs("SELECT number FROM PlanEntity ORDER BY id"))
        assertEquals(listOf(null, 3L), db.longs("SELECT previousPlanId FROM PlanEntity ORDER BY id"))
        assertEquals(listOf(null, null), db.longs("SELECT closedAt FROM PlanEntity ORDER BY id"))
        assertEquals(listOf(0L), db.longs("SELECT COUNT(*) FROM BlockSnapshotEntity"))
        db.close()
    }

    @Test
    fun `6 to 7 adds the muscles of app exercises and keeps them`() {
        val db = create(6)
        db.exec("INSERT INTO ExerciseEntity VALUES ('custom-x', 'Exercício X')")
        MigrationSql.V6_TO_7.forEach { db.exec(it) }
        for (table in schema(7).getValue("entities").jsonArray.map { it.jsonObject.getValue("tableName").jsonPrimitive.content }) {
            assertEquals(expectedColumns(7, table), columns(db, table), table)
        }
        assertEquals(listOf(1L), db.longs("SELECT COUNT(*) FROM ExerciseEntity WHERE id = 'custom-x' AND muscles IS NULL"))
        db.close()
    }
    @Test
    fun `8 to 9 keeps the planned date and numbers each modality queue`() {
        val db = create(8)
        db.exec("INSERT INTO PlanEntity (id, name, startDate, durationWeeks, goal, notes, rawJson, createdAt, status, number, previousPlanId, phase, closedAt) VALUES (1, 'Bloco', '2026-11-09', 4, '', '', '{}', 0, 'active', 1, NULL, NULL, NULL)")
        val rows = listOf(
            Triple(10, "2026-11-09", "strength") to "done",
            Triple(11, "2026-11-09", "run") to "pending",
            Triple(12, "2026-11-11", "strength") to "pending",
            Triple(13, "2026-11-12", "run") to "skipped",
            Triple(14, "2026-11-13", "strength") to "pending",
        )
        for ((r, status) in rows) {
            db.exec("INSERT INTO PlannedSessionEntity (id, planId, weekNumber, dayOfWeek, date, type, title, estimatedMinutes, status, skipReason, variant, optional, startedAt) VALUES (${r.first}, 1, 1, 'mon', '${r.second}', '${r.third}', 'S', 60, '$status', NULL, 'gym', 0, NULL)")
        }
        MigrationSql.V8_TO_9.forEach { db.exec(it) }
        for (table in schema(9).getValue("entities").jsonArray.map { it.jsonObject.getValue("tableName").jsonPrimitive.content }) {
            assertEquals(expectedColumns(9, table), columns(db, table), table)
        }
        assertEquals(5L, db.longs("SELECT COUNT(*) FROM PlannedSessionEntity WHERE plannedDate = date").single())
        assertEquals(listOf(1L, 1L, 2L, 2L, 3L), db.longs("SELECT sequence FROM PlannedSessionEntity ORDER BY id"))
        assertEquals(listOf(null, null, null, null, null), db.longs("SELECT daysLate FROM PlannedSessionEntity ORDER BY id"))
        assertEquals(1L, db.longs("SELECT COUNT(*) FROM PlannedSessionEntity WHERE status = 'done'").single())
        db.close()
    }
}
