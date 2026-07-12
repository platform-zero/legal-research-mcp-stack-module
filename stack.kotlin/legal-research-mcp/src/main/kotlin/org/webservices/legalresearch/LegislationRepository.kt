package org.webservices.legalresearch

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.sql.Connection
import java.sql.DriverManager
import java.time.Instant
import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class LegislationRecord(
    val id: String,
    val versionId: String,
    val title: String,
    val citation: String,
    val jurisdiction: String,
    val sourceUrl: String,
    val text: String,
    val retrievedAt: String
)

data class ImportSummary(val accepted: Int, val skipped: Int)

class LegislationRepository(private val dsn: String) {
    init { Class.forName("org.postgresql.Driver") }

    fun migrate() = connection().use { database ->
        database.createStatement().use { statement ->
            statement.execute("CREATE SCHEMA IF NOT EXISTS law")
            statement.execute("""
                CREATE TABLE IF NOT EXISTS law.legislation_version (
                  id text PRIMARY KEY, version_id text NOT NULL UNIQUE, title text NOT NULL,
                  citation text NOT NULL, jurisdiction text NOT NULL, source_url text NOT NULL,
                  body text NOT NULL, content_hash text NOT NULL, retrieved_at timestamptz NOT NULL,
                  ingested_at timestamptz NOT NULL DEFAULT now()
                )
            """.trimIndent())
            statement.execute("CREATE INDEX IF NOT EXISTS legislation_version_citation_idx ON law.legislation_version (citation)")
            statement.execute("CREATE INDEX IF NOT EXISTS legislation_version_jurisdiction_idx ON law.legislation_version (jurisdiction)")
        }
    }

    fun byId(id: String): LegislationRecord? = queryOne("SELECT * FROM law.legislation_version WHERE id = ?", id)

    fun byCitation(citation: String): LegislationRecord? = queryOne(
        "SELECT * FROM law.legislation_version WHERE lower(citation) = lower(?) ORDER BY retrieved_at DESC LIMIT 1", citation
    )

    fun freshness(): String? = connection().use { database ->
        database.prepareStatement("SELECT max(retrieved_at) FROM law.legislation_version").use { statement ->
            statement.executeQuery().use { rows -> if (rows.next()) rows.getObject(1)?.toString() else null }
        }
    }

    fun importCorpus(corpusPath: Path, json: Json): ImportSummary = connection().use { database ->
        database.autoCommit = false
        var accepted = 0
        var skipped = 0
        try {
            Files.newBufferedReader(corpusPath).useLines { lines -> lines.forEach { line ->
                val document = json.parseToJsonElement(line).jsonObject
                val type = document.required("type")
                if (type !in setOf("primary_legislation", "secondary_legislation")) {
                    skipped += 1
                } else {
                    upsert(database, document)
                    accepted += 1
                }
            } }
            database.commit()
            ImportSummary(accepted, skipped)
        } catch (exception: Exception) {
            database.rollback()
            throw exception
        }
    }

    private fun upsert(database: Connection, document: kotlinx.serialization.json.JsonObject) {
        val versionId = document.required("version_id")
        val text = document.required("text")
        database.prepareStatement("""
            INSERT INTO law.legislation_version
              (id, version_id, title, citation, jurisdiction, source_url, body, content_hash, retrieved_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::timestamptz)
            ON CONFLICT (version_id) DO UPDATE SET
              title = EXCLUDED.title, citation = EXCLUDED.citation, jurisdiction = EXCLUDED.jurisdiction,
              source_url = EXCLUDED.source_url, body = EXCLUDED.body, content_hash = EXCLUDED.content_hash,
              retrieved_at = EXCLUDED.retrieved_at, ingested_at = now()
        """.trimIndent()).use { statement ->
            statement.setString(1, UUID.nameUUIDFromBytes(versionId.toByteArray(StandardCharsets.UTF_8)).toString())
            statement.setString(2, versionId)
            statement.setString(3, document.required("citation"))
            statement.setString(4, document.required("citation"))
            statement.setString(5, document.required("jurisdiction"))
            statement.setString(6, document.required("url"))
            statement.setString(7, text)
            statement.setString(8, MessageDigest.getInstance("SHA-256").digest(text.toByteArray(StandardCharsets.UTF_8)).joinToString("") { "%02x".format(it) })
            statement.setString(9, document.required("when_scraped"))
            statement.executeUpdate()
        }
    }

    private fun queryOne(sql: String, value: String): LegislationRecord? = connection().use { database ->
        database.prepareStatement(sql).use { statement ->
            statement.setString(1, value)
            statement.executeQuery().use { rows -> if (rows.next()) rows.toRecord() else null }
        }
    }

    private fun java.sql.ResultSet.toRecord() = LegislationRecord(
        id = getString("id"), versionId = getString("version_id"), title = getString("title"),
        citation = getString("citation"), jurisdiction = getString("jurisdiction"),
        sourceUrl = getString("source_url"), text = getString("body"), retrievedAt = getObject("retrieved_at", Instant::class.java).toString()
    )

    private fun connection(): Connection = DriverManager.getConnection(dsn)

    private fun kotlinx.serialization.json.JsonObject.required(name: String): String =
        this[name]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() } ?: error("corpus document missing $name")
}
