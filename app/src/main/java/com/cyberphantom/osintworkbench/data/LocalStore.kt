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
    val createdAt: Long,
    val updatedAt: Long
)

data class FindingRow(
    val id: Long,
    val entityId: Long?,
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

data class RelationRow(
    val fromValue: String,
    val fromKind: String,
    val toValue: String,
    val toKind: String,
    val relation: String,
    val source: String,
    val confidence: Int,
    val observedAt: Long
)

data class ScanRow(
    val id: Long,
    val target: String,
    val status: String,
    val startedAt: Long,
    val finishedAt: Long,
    val summary: String,
    val recordsCount: Int,
    val payloadJson: String
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
                records_count INTEGER NOT NULL DEFAULT 0,
                payload_json TEXT NOT NULL DEFAULT ''
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
        // Migrations are additive; existing user data is preserved.
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE scans ADD COLUMN payload_json TEXT NOT NULL DEFAULT ''")
        }
    }

    fun upsertEntity(
        value: String,
        kind: String,
        source: String,
        confidence: Int = 70,
        createdAt: Long = System.currentTimeMillis(),
        updatedAt: Long = System.currentTimeMillis()
    ): Long {
        val now = updatedAt
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
            put("created_at", createdAt)
            put("updated_at", now)
        }
        return db.insertOrThrow("entities", null, values)
    }

    fun addEdge(
        fromId: Long,
        toId: Long,
        relation: String,
        source: String,
        confidence: Int = 70,
        observedAt: Long = System.currentTimeMillis()
    ) {
        val db = writableDatabase
        val existing = db.rawQuery(
            "SELECT id FROM edges WHERE from_id = ? AND to_id = ? AND relation = ? LIMIT 1",
            arrayOf(fromId.toString(), toId.toString(), relation)
        ).use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else null }
        val values = ContentValues().apply {
            put("from_id", fromId)
            put("to_id", toId)
            put("relation", relation)
            put("source", source)
            put("confidence", confidence.coerceIn(0, 100))
            put("observed_at", observedAt)
        }
        if (existing != null) {
            db.update("edges", values, "id = ?", arrayOf(existing.toString()))
        } else {
            db.insertOrThrow("edges", null, values)
        }
    }

    fun addFinding(
        entityId: Long?,
        title: String,
        detail: String,
        source: String,
        severity: String = "info",
        observedAt: Long = System.currentTimeMillis()
    ) {
        val values = ContentValues().apply {
            if (entityId != null) put("entity_id", entityId) else putNull("entity_id")
            put("title", title)
            put("detail", detail)
            put("source", source)
            put("severity", severity)
            put("observed_at", observedAt)
        }
        writableDatabase.insert("findings", null, values)
    }

    fun addScan(
        target: String,
        status: String,
        startedAt: Long,
        summary: String,
        records: Int,
        payloadJson: String = "",
        finishedAt: Long = System.currentTimeMillis()
    ) {
        val values = ContentValues().apply {
            put("target", target)
            put("status", status)
            put("started_at", startedAt)
            put("finished_at", finishedAt)
            put("summary", summary)
            put("records_count", records)
            put("payload_json", payloadJson)
        }
        writableDatabase.insert("scans", null, values)
    }

    fun createCase(title: String, target: String, scope: String, createdAt: Long = System.currentTimeMillis()) {
        val values = ContentValues().apply {
            put("title", title.trim())
            put("target", target.trim())
            put("scope", scope.trim())
            put("notes", "")
            put("created_at", createdAt)
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
            "SELECT id, entity_id, title, detail, source, severity, observed_at FROM findings ORDER BY observed_at DESC LIMIT ?",
            arrayOf(limit.coerceIn(1, 50_000).toString())
        ).use { cursor ->
            while (cursor.moveToNext()) {
                rows += FindingRow(
                    id = cursor.getLong(0),
                    entityId = if (cursor.isNull(1)) null else cursor.getLong(1),
                    title = cursor.getString(2),
                    detail = cursor.getString(3),
                    source = cursor.getString(4),
                    severity = cursor.getString(5),
                    observedAt = cursor.getLong(6)
                )
            }
        }
        return rows
    }

    fun entities(limit: Int = 100): List<EntityRow> {
        val rows = mutableListOf<EntityRow>()
        readableDatabase.rawQuery(
            "SELECT id, value, kind, source, confidence, created_at, updated_at FROM entities ORDER BY updated_at DESC LIMIT ?",
            arrayOf(limit.coerceIn(1, 50_000).toString())
        ).use { cursor ->
            while (cursor.moveToNext()) {
                rows += EntityRow(
                    id = cursor.getLong(0),
                    value = cursor.getString(1),
                    kind = cursor.getString(2),
                    source = cursor.getString(3),
                    confidence = cursor.getInt(4),
                    createdAt = cursor.getLong(5),
                    updatedAt = cursor.getLong(6)
                )
            }
        }
        return rows
    }

    fun relations(limit: Int = 100): List<RelationRow> {
        val rows = mutableListOf<RelationRow>()
        readableDatabase.rawQuery(
            """SELECT source_node.value, source_node.kind, target_node.value, target_node.kind,
                      edges.relation, edges.source, edges.confidence, edges.observed_at
               FROM edges
               JOIN entities AS source_node ON source_node.id = edges.from_id
               JOIN entities AS target_node ON target_node.id = edges.to_id
               ORDER BY edges.observed_at DESC LIMIT ?""".trimIndent(),
            arrayOf(limit.coerceIn(1, 100_000).toString())
        ).use { cursor ->
            while (cursor.moveToNext()) {
                rows += RelationRow(
                    fromValue = cursor.getString(0),
                    fromKind = cursor.getString(1),
                    toValue = cursor.getString(2),
                    toKind = cursor.getString(3),
                    relation = cursor.getString(4),
                    source = cursor.getString(5),
                    confidence = cursor.getInt(6),
                    observedAt = cursor.getLong(7)
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

    fun allFindings(limit: Int = 1_000): List<FindingRow> {
        val rows = mutableListOf<FindingRow>()
        readableDatabase.rawQuery(
            "SELECT id, entity_id, title, detail, source, severity, observed_at FROM findings ORDER BY observed_at DESC LIMIT ?",
            arrayOf(limit.coerceIn(1, 5_000).toString())
        ).use { cursor ->
            while (cursor.moveToNext()) {
                rows += FindingRow(
                    id = cursor.getLong(0),
                    entityId = if (cursor.isNull(1)) null else cursor.getLong(1),
                    title = cursor.getString(2),
                    detail = cursor.getString(3),
                    source = cursor.getString(4),
                    severity = cursor.getString(5),
                    observedAt = cursor.getLong(6)
                )
            }
        }
        return rows
    }

    fun scans(limit: Int = 20): List<ScanRow> {
        val rows = mutableListOf<ScanRow>()
        readableDatabase.rawQuery(
            """SELECT id, target, status, started_at, finished_at, summary, records_count, payload_json
               FROM scans ORDER BY started_at DESC LIMIT ?""".trimIndent(),
            arrayOf(limit.coerceIn(1, 10_000).toString())
        ).use { cursor ->
            while (cursor.moveToNext()) {
                rows += ScanRow(
                    id = cursor.getLong(0),
                    target = cursor.getString(1),
                    status = cursor.getString(2),
                    startedAt = cursor.getLong(3),
                    finishedAt = cursor.getLong(4),
                    summary = cursor.getString(5),
                    recordsCount = cursor.getInt(6),
                    payloadJson = cursor.getString(7)
                )
            }
        }
        return rows
    }

    fun exportSnapshotJson(): String {
        val root = org.json.JSONObject()
            .put("format", "OSINT Workbench local workspace export")
            .put("formatVersion", 1)
            .put("exportedAt", System.currentTimeMillis())
        val entityArray = org.json.JSONArray()
        entities(50_000).forEach { entity ->
            entityArray.put(
                org.json.JSONObject()
                    .put("id", entity.id)
                    .put("value", entity.value)
                    .put("kind", entity.kind)
                    .put("source", entity.source)
                    .put("confidence", entity.confidence)
                    .put("createdAt", entity.createdAt)
                    .put("updatedAt", entity.updatedAt)
            )
        }
        val edgeArray = org.json.JSONArray()
        relations(100_000).forEach { edge ->
            edgeArray.put(
                org.json.JSONObject()
                    .put("from", edge.fromValue)
                    .put("fromKind", edge.fromKind)
                    .put("to", edge.toValue)
                    .put("toKind", edge.toKind)
                    .put("relation", edge.relation)
                    .put("source", edge.source)
                    .put("confidence", edge.confidence)
                    .put("observedAt", edge.observedAt)
            )
        }
        val findingArray = org.json.JSONArray()
        allFindings(50_000).forEach { finding ->
            findingArray.put(
                org.json.JSONObject()
                    .put("id", finding.id)
                    .put("entityId", finding.entityId)
                    .put("title", finding.title)
                    .put("detail", finding.detail)
                    .put("source", finding.source)
                    .put("severity", finding.severity)
                    .put("observedAt", finding.observedAt)
            )
        }
        val scanArray = org.json.JSONArray()
        scans(10_000).forEach { scan ->
            scanArray.put(
                org.json.JSONObject()
                    .put("id", scan.id)
                    .put("target", scan.target)
                    .put("status", scan.status)
                    .put("startedAt", scan.startedAt)
                    .put("finishedAt", scan.finishedAt)
                    .put("summary", scan.summary)
                    .put("recordsCount", scan.recordsCount)
                    .put("report", runCatching { org.json.JSONObject(scan.payloadJson) }.getOrNull())
            )
        }
        val caseArray = org.json.JSONArray()
        cases(10_000).forEach { item ->
            caseArray.put(
                org.json.JSONObject()
                    .put("id", item.id)
                    .put("title", item.title)
                    .put("target", item.target)
                    .put("scope", item.scope)
                    .put("createdAt", item.createdAt)
            )
        }
        root.put("entities", entityArray)
            .put("relationships", edgeArray)
            .put("findings", findingArray)
            .put("scans", scanArray)
            .put("cases", caseArray)
            .put("counts", org.json.JSONObject()
                .put("entities", stats().entities)
                .put("relationships", stats().relationships)
                .put("findings", stats().findings)
                .put("scans", stats().scans)
                .put("cases", stats().cases))
        return root.toString(2)
    }



    /**
     * Restores an OSINT Workbench JSON export into the current private database.
     * The import is additive and idempotent for entities, edges, findings, scans and cases.
     * It never deletes existing records.
     */
    fun restoreSnapshotJson(json: String): StoreStats {
        val root = org.json.JSONObject(json)
        require(root.optInt("formatVersion", -1) == 1 &&
            root.optString("format") == "OSINT Workbench local workspace export") {
            "هذا الملف ليس نسخة JSON مدعومة من OSINT Workbench."
        }
        val entityIds = mutableMapOf<Long, Long>()
        val db = writableDatabase
        db.beginTransaction()
        try {
            val entities = root.optJSONArray("entities") ?: org.json.JSONArray()
            for (index in 0 until entities.length()) {
                val item = entities.optJSONObject(index) ?: continue
                val value = item.optString("value").takeIf(String::isNotBlank) ?: continue
                val kind = item.optString("kind").ifBlank { "IMPORTED" }
                val newId = upsertEntity(
                    value = value,
                    kind = kind,
                    source = item.optString("source").ifBlank { "JSON restore" },
                    confidence = item.optInt("confidence", 50),
                    createdAt = item.optLong("createdAt", System.currentTimeMillis()),
                    updatedAt = item.optLong("updatedAt", System.currentTimeMillis())
                )
                val oldId = item.optLong("id", -1)
                if (oldId > 0) entityIds[oldId] = newId
            }

            val edges = root.optJSONArray("relationships") ?: org.json.JSONArray()
            for (index in 0 until edges.length()) {
                val item = edges.optJSONObject(index) ?: continue
                val fromValue = item.optString("from")
                val toValue = item.optString("to")
                val relation = item.optString("relation")
                if (fromValue.isBlank() || toValue.isBlank() || relation.isBlank()) continue
                val fromKind = item.optString("fromKind")
                val toKind = item.optString("toKind")
                val fromId = findEntityId(fromValue, fromKind) ?: continue
                val toId = findEntityId(toValue, toKind) ?: continue
                addEdge(
                    fromId = fromId,
                    toId = toId,
                    relation = relation,
                    source = item.optString("source").ifBlank { "JSON restore" },
                    confidence = item.optInt("confidence", 50),
                    observedAt = item.optLong("observedAt", System.currentTimeMillis())
                )
            }

            val findings = root.optJSONArray("findings") ?: org.json.JSONArray()
            for (index in 0 until findings.length()) {
                val item = findings.optJSONObject(index) ?: continue
                val oldEntityId = item.optLong("entityId", -1)
                val entityId = if (oldEntityId > 0) entityIds[oldEntityId] else null
                val title = item.optString("title")
                val detail = item.optString("detail")
                val source = item.optString("source").ifBlank { "JSON restore" }
                val observedAt = item.optLong("observedAt", System.currentTimeMillis())
                if (title.isBlank() || findingExists(entityId, title, detail, source, observedAt)) continue
                addFinding(
                    entityId = entityId,
                    title = title,
                    detail = detail,
                    source = source,
                    severity = item.optString("severity", "info"),
                    observedAt = observedAt
                )
            }

            val scans = root.optJSONArray("scans") ?: org.json.JSONArray()
            for (index in 0 until scans.length()) {
                val item = scans.optJSONObject(index) ?: continue
                val target = item.optString("target")
                val startedAt = item.optLong("startedAt", System.currentTimeMillis())
                if (target.isBlank() || scanExists(target, startedAt)) continue
                val report = item.optJSONObject("report")
                addScan(
                    target = target,
                    status = item.optString("status", "restored"),
                    startedAt = startedAt,
                    summary = item.optString("summary", "Imported from JSON"),
                    records = item.optInt("recordsCount", report?.optJSONArray("records")?.length() ?: 0),
                    payloadJson = report?.toString(2).orEmpty(),
                    finishedAt = item.optLong("finishedAt", startedAt)
                )
            }

            val cases = root.optJSONArray("cases") ?: org.json.JSONArray()
            for (index in 0 until cases.length()) {
                val item = cases.optJSONObject(index) ?: continue
                val title = item.optString("title")
                val target = item.optString("target")
                val createdAt = item.optLong("createdAt", System.currentTimeMillis())
                if (title.isBlank() || caseExists(title, target, createdAt)) continue
                createCase(title, target, item.optString("scope"), createdAt)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return stats()
    }

    private fun findEntityId(value: String, kind: String): Long? {
        val sql = if (kind.isBlank()) {
            "SELECT id FROM entities WHERE value = ? ORDER BY updated_at DESC LIMIT 1"
        } else {
            "SELECT id FROM entities WHERE value = ? AND kind = ? LIMIT 1"
        }
        val args = if (kind.isBlank()) arrayOf(value) else arrayOf(value, kind)
        return readableDatabase.rawQuery(sql, args).use { cursor ->
            if (cursor.moveToFirst()) cursor.getLong(0) else null
        }
    }

    private fun findingExists(
        entityId: Long?,
        title: String,
        detail: String,
        source: String,
        observedAt: Long
    ): Boolean = readableDatabase.rawQuery(
        "SELECT id FROM findings WHERE entity_id IS ? AND title = ? AND detail = ? AND source = ? AND observed_at = ? LIMIT 1",
        arrayOf(entityId?.toString(), title, detail, source, observedAt.toString())
    ).use { it.moveToFirst() }

    private fun scanExists(target: String, startedAt: Long): Boolean =
        readableDatabase.rawQuery(
            "SELECT id FROM scans WHERE target = ? AND started_at = ? LIMIT 1",
            arrayOf(target, startedAt.toString())
        ).use { it.moveToFirst() }

    private fun caseExists(title: String, target: String, createdAt: Long): Boolean =
        readableDatabase.rawQuery(
            "SELECT id FROM work_cases WHERE title = ? AND target = ? AND created_at = ? LIMIT 1",
            arrayOf(title, target, createdAt.toString())
        ).use { it.moveToFirst() }

    companion object {
        private const val DATABASE_NAME = "osint_workbench.db"
        private const val DATABASE_VERSION = 2
    }
}
