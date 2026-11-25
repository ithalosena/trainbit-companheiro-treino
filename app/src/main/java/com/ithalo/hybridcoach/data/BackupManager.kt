package com.ithalo.hybridcoach.data

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** What a backup file holds, read before anything is replaced. */
data class BackupPreview(
    val exportedOn: String,
    val appVersion: String,
    val blocks: Int,
    val sets: Int,
    val runs: Int,
)

/** Why a file cannot be restored. */
class BackupException(val reason: Reason) : Exception(reason.name) {
    enum class Reason { NOT_A_BACKUP, CORRUPT, NEWER_APP }
}

/**
 * Backup and restore as one `.zip`: the whole database (plans, workouts, runs; XP and trophies are derived from
 * it), every setting and a small manifest. Restoring never starts from an unchecked file: the database inside is
 * opened read-only and checked first, the current database is kept aside as `before-restore.db`, and an older
 * database is migrated by Room when the app starts again.
 */
class BackupManager(
    private val context: Context,
    private val repository: PlanRepository,
    private val settings: SettingsRepository,
) {
    private val dbFile: File get() = context.getDatabasePath(DB_NAME)
    private val staging: File get() = File(context.cacheDir, "restore")

    /** Suggested file name, e.g. `trainbit-backup-2026-11-11.zip`. */
    fun suggestedName(): String = "trainbit-backup-${LocalDate.now()}.zip"

    suspend fun export(target: Uri) = withContext(Dispatchers.IO) {
        repository.checkpoint()
        val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"
        val manifest = JSONObject()
            .put("app", "trainbit")
            .put("exportedOn", LocalDate.now().toString())
            .put("appVersion", version)
            .put("dbVersion", AppDatabase.VERSION)
        val out = context.contentResolver.openOutputStream(target, "wt") ?: error("no output")
        ZipOutputStream(out.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry(MANIFEST))
            zip.write(manifest.toString().toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry(SETTINGS))
            zip.write(settings.exportAll().toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry(DB_ENTRY))
            dbFile.inputStream().use { it.copyTo(zip) }
            zip.closeEntry()
        }
    }

    /** Unpacks [source] into the cache and checks it. Throws [BackupException] when it cannot be restored. */
    suspend fun inspect(source: Uri): BackupPreview = withContext(Dispatchers.IO) {
        staging.deleteRecursively()
        staging.mkdirs()
        var manifest: JSONObject? = null
        var hasDb = false
        try {
            ZipInputStream((context.contentResolver.openInputStream(source) ?: error("no input")).buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    when (entry.name) {
                        MANIFEST -> manifest = JSONObject(zip.readBytes().toString(Charsets.UTF_8))
                        SETTINGS -> File(staging, SETTINGS).writeBytes(zip.readBytes())
                        DB_ENTRY -> {
                            File(staging, DB_ENTRY).outputStream().use { zip.copyTo(it) }
                            hasDb = true
                        }
                    }
                }
            }
        } catch (e: Exception) {
            throw BackupException(BackupException.Reason.NOT_A_BACKUP)
        }
        val m = manifest
        if (m == null || m.optString("app") != "trainbit" || !hasDb) throw BackupException(BackupException.Reason.NOT_A_BACKUP)
        try {
            SQLiteDatabase.openDatabase(File(staging, DB_ENTRY).path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                if (db.version > AppDatabase.VERSION) throw BackupException(BackupException.Reason.NEWER_APP)
                val ok = db.rawQuery("PRAGMA integrity_check", null).use { c -> c.moveToFirst() && c.getString(0) == "ok" }
                if (!ok) throw BackupException(BackupException.Reason.CORRUPT)
                BackupPreview(
                    exportedOn = m.optString("exportedOn"),
                    appVersion = m.optString("appVersion"),
                    blocks = count(db, "PlanEntity"),
                    sets = count(db, "SetLogEntity"),
                    runs = count(db, "RunLogEntity"),
                )
            }
        } catch (e: BackupException) {
            throw e
        } catch (e: Exception) {
            throw BackupException(BackupException.Reason.CORRUPT)
        }
    }

    private fun count(db: SQLiteDatabase, table: String): Int =
        runCatching { db.rawQuery("SELECT COUNT(*) FROM `$table`", null).use { if (it.moveToFirst()) it.getInt(0) else 0 } }.getOrDefault(0)

    /**
     * Replaces the data with the checked backup from [inspect] and restarts the app. The current database is kept
     * as `before-restore.db` in the app's files (only the last one).
     */
    suspend fun restore() = withContext(Dispatchers.IO) {
        val staged = File(staging, DB_ENTRY)
        check(staged.exists()) { "nothing staged" }
        repository.checkpoint()
        repository.closeDatabase()
        dbFile.copyTo(File(context.filesDir, "before-restore.db"), overwrite = true)
        File(dbFile.path + "-wal").delete()
        File(dbFile.path + "-shm").delete()
        File(dbFile.path + "-journal").delete()
        staged.copyTo(dbFile, overwrite = true)
        File(staging, SETTINGS).takeIf { it.exists() }?.let { settings.importAll(it.readText()) }
        staging.deleteRecursively()
        restartApp()
    }

    /** Starts the launcher activity in a fresh process in a moment, then ends this one. */
    private fun restartApp() {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)!!
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        val pending = PendingIntent.getActivity(context, 0, launch, PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).set(AlarmManager.RTC, System.currentTimeMillis() + 400, pending)
        android.os.Process.killProcess(android.os.Process.myPid())
    }

    companion object {
        const val DB_NAME = "hybrid-coach.db"
        private const val MANIFEST = "manifest.json"
        private const val SETTINGS = "settings.json"
        private const val DB_ENTRY = "hybrid-coach.db"
    }
}
