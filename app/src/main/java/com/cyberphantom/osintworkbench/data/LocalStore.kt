package com.cyberphantom.osintworkbench.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.database.DatabaseUtils

data class StoreStats(
    val entities: Long = 0,
    val relationships: Long = 0,
    val findings: Long = 0,
    val scans: Long = 0,
    val cases: Long = 0
)

data class EntityRow(
    val id: Long,
    val value: String,
    val kind: String,
    val source: String,
    val confidence: Int,
    val updatedAt: Long
)

data class FindingRow(
    val id: Long,
    val title: String,
    val detail: String,
    val source: String,
    val severity: String,
    val observedAt: Long
)

data class CaseRow(
    val id: Long,
    val title: String,
    val target: String,
    val scope: String,
    val createdAt: Long
)

/**
 * Small offline-first store. Data remains on this device; no account or backend is required.
 * Values are parameterised to avoid SQL injection. Sensitive exports/encryption are separate
 * follow-up features; this database should not be described as encrypted at rest yet.
 */
class LocalStore(context: Context) : SQLiteOpenHelper(
    context.applicationContext,
    DATABASE_NAME,
    null,
    DATABASE_VERSION
) {
    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE entities (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                value TEXT NOT NULL,
                kind TEXT NOT NULL,
                source TEXT NOT NULL DEFAULT '',
                confidence INTEGER NOT NULL DEFAULT 50,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                UNIQUE(value, kind)
            )""".trimIndent()
        )
        db.execSQL(
            """CREATE TABLE edges (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                from_id INTEGER NOT NULL REFERENCES entities(id) ON DELETE CASCADE,
                to_id INTEGER NOT NULL REFERENCES entities(id) ON DELETE CASCADE,
                relation TEXT NOT NULL,
                source TEXT NOT NULL DEFAULT '',
                confidence INTEGER NOT NULL DEFAULT 50,
                observed_at INTEGER NOT NULL,
                UNIQUE(from_id, to_id, relation)
            )""".trimIndent()
        )
        db.execSQL(
            """CREATE TABLE findings (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                entity_id INTEGER REFERENCES entities(id) ON DELETE SET NULL,
                title TEXT NOT NULL,
                detail TEXT NOT NULL,
                source TEXT NOT NULL DEFAULT '',
                severity TEXT NOT NULL DEFAULT 'info',
                observed_at INTEGER NOT NULL
            )""".trimIndent()
        )
        db.execSQL(
            """CREATE TABLE scans (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                target TEXT NOT NULL,
                status TEXT NOT NULL,
                started_at INTEGER NOT NULL,
                finished_at INTEGER NOT NULL,
                summary TEXT NOT NULL DEFAULT '',
                records_count INTEGER NOT NULL DEFAULT 0
            )""".trimIndent()
        )
        db.execSQL(
            """CREATE TABLE work_cases (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT NOT NULL,
                target TEXT NOT NULL DEFAULT '',
                scope TEXT NOT NULL DEFAULT '',
                notes TEXT NOT NULL DEFAULT '',
                created_at INTEGER NOT NULL
            )""".trimIndent()
        )
        db.execSQL("CREATE INDEX idx_entities_kind ON entities(kind)")
        db.execSQL("CREATE INDEX idx_findings_observed ON findings(observed_at DESC)")
        db.execSQL("CREATE INDEX idx_edges_from ON edges(from_id)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Increment the schema version and add explicit migrations here; never drop user data.
    }

    fun upsertEntity(value: String, kind: String, source: String, confidence: Int = 70): Long {
        val now = System.currentTimeMillis()
        val db = writableDatabase
        val existing = db.rawQuery(
            "SELECT id FROM entities WHERE value = ? AND kind = ? LIMIT 1",
            arrayOf(value, kind)
        ).use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else null }

        if (existing != null) {
            val update = ContentValues().apply {
                put("source", source)
                put("confidence", confidence.coerceIn(0, 100))
                put("updated_at", now)
            }
            db.update("entities", update, "id = ?", arrayOf(existing.toString()))
            return existing
        }

        val values = ContentValues().apply {
            put("value", value)
            put("kind", kind)
            put("source", source)
            put("confidence", confidence.coerceIn(0, 100))
            put("created_at", now)
            put("updated_at", now)
        }
        return db.insertOrThrow("entities", null, values)
    }

    fun addEdge(
        fromId: Long,
        toId: Long,
        relation: String,
        source: String,
        confidence: Int = 70
    ) {
        val values = ContentValues().apply {
            put("from_id", fromId)
            put("to_id", toId)
            put("relation", relation)
            put("source", source)
            put("confidence", confidence.coerceIn(0, 100))
            put("observed_at", System.currentTimeMillis())
        }
        writableDatabase.insertWithOnConflict("edges", null, values, SQLiteDatabase.CONFLICT_IGNORE)
    }

    fun addFinding(
        entityId: Long?,
        title: String,
        detail: String,
        source: String,
        severity: String = "info"
    ) {
        val values = ContentValues().apply {
            if (entityId != null) put("entity_id", entityId) else putNull("entity_id")
            put("title", title)
            put("detail", detail)
            put("source", source)
            put("severity", severity)
            put("observed_at", System.currentTimeMillis())
        }
        writableDatabase.insert("findings", null, values)
    }

    fun addScan(target: String, status: String, startedAt: Long, summary: String, records: Int) {
        val values = ContentValues().apply {
            put("target", target)
            put("status", status)
            put("started_at", startedAt)
            put("finished_at", System.currentTimeMillis())
            put("summary", summary)
            put("records_count", records)
        }
        writableDatabase.insert("scans", null, values)
    }

    fun createCase(title: String, target: String, scope: String) {
        val values = ContentValues().apply {
            put("title", title.trim())
            put("target", target.trim())
            put("scope", scope.trim())
            put("notes", "")
            put("created_at", System.currentTimeMillis())
        }
        writableDatabase.insertOrThrow("work_cases", null, values)
    }

    fun stats(): StoreStats {
        val db = readableDatabase
        fun count(table: String): Long = DatabaseUtils.longForQuery(db, "SELECT COUNT(*) FROM $table", null)
        return StoreStats(
            entities = count("entities"),
            relationships = count("edges"),
            findings = count("findings"),
            scans = count("scans"),
            cases = count("work_cases")
        )
    }

    fun recentFindings(limit: Int = 20): List<FindingRow> {
        val rows = mutableListOf<FindingRow>()
        readableDatabase.rawQuery(
            "SELECT id, title, detail, source, severity, observed_at FROM findings ORDER BY observed_at DESC LIMIT ?",
            arrayOf(limit.coerceIn(1, 100).toString())
        ).use { cursor ->
            while (cursor.moveToNext()) {
                rows += FindingRow(
                    id = cursor.getLong(0),
                    title = cursor.getString(1),
                    detail = cursor.getString(2),
                    source = cursor.getString(3),
                    severity = cursor.getString(4),
                    observedAt = cursor.getLong(5)
                )
            }
        }
        return rows
    }

    fun entities(limit: Int = 100): List<EntityRow> {
        val rows = mutableListOf<EntityRow>()
        readableDatabase.rawQuery(
            "SELECT id, value, kind, source, confidence, updated_at FROM entities ORDER BY updated_at DESC LIMIT ?",
            arrayOf(limit.coerceIn(1, 500).toString())
        ).use { cursor ->
            while (cursor.moveToNext()) {
                rows += EntityRow(
                    id = cursor.getLong(0),
                    value = cursor.getString(1),
                    kind = cursor.getString(2),
                    source = cursor.getString(3),
                    confidence = cursor.getInt(4),
                    updatedAt = cursor.getLong(5)
                )
            }
        }
        return rows
    }

    fun cases(limit: Int = 100): List<CaseRow> {
        val rows = mutableListOf<CaseRow>()
        readableDatabase.rawQuery(
            "SELECT id, title, target, scope, created_at FROM work_cases ORDER BY created_at DESC LIMIT ?",
            arrayOf(limit.coerceIn(1, 500).toString())
        ).use { cursor ->
            while (cursor.moveToNext()) {
                rows += CaseRow(
                    id = cursor.getLong(0),
                    title = cursor.getString(1),
                    target = cursor.getString(2),
                    scope = cursor.getString(3),
                    createdAt = cursor.getLong(4)
                )
            }
        }
        return rows
    }

    companion object {
        private const val DATABASE_NAME = "osint_workbench.db"
        private const val DATABASE_VERSION = 1
    }
}
