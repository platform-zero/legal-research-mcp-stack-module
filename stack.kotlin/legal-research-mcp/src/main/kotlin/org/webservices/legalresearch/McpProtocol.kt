package org.webservices.legalresearch

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class McpProtocol(private val repository: LegislationRepository, private val json: Json) {
    fun handle(raw: String): JsonObject {
        val request = json.parseToJsonElement(raw).jsonObject
        val id = request["id"] ?: JsonNull
        return when (request["method"]?.jsonPrimitive?.content) {
            "initialize" -> success(id, buildJsonObject {
                put("protocolVersion", "2025-06-18")
                put("capabilities", buildJsonObject { put("tools", buildJsonObject { }) })
                put("serverInfo", buildJsonObject { put("name", "legal-research-mcp"); put("version", "1.0.0") })
            })
            "tools/list" -> success(id, buildJsonObject { put("tools", tools()) })
            "tools/call" -> callTool(id, request["params"]?.jsonObject ?: return error(id, -32602, "missing params"))
            else -> error(id, -32601, "method not found")
        }
    }

    private fun tools(): JsonArray = buildJsonArray {
        add(tool("search_legislation", "Search current Australian legislation by words and optional jurisdiction."))
        add(tool("get_legislation", "Retrieve full text and provenance by stable legislation id."))
        add(tool("get_legislation_by_citation", "Retrieve current legislation by citation."))
        add(tool("get_data_freshness", "Return the latest source retrieval timestamp."))
    }

    private fun tool(name: String, description: String) = buildJsonObject {
        put("name", name); put("description", description)
        put("inputSchema", buildJsonObject { put("type", "object"); put("properties", buildJsonObject { }) })
    }

    private fun callTool(id: JsonElement, params: JsonObject): JsonObject {
        val name = params["name"]?.jsonPrimitive?.content ?: return error(id, -32602, "missing tool name")
        val arguments = params["arguments"]?.jsonObject ?: buildJsonObject { }
        return when (name) {
            "get_legislation" -> recordResponse(id, repository.byId(arguments.string("id") ?: return error(id, -32602, "id is required")))
            "get_legislation_by_citation" -> recordResponse(id, repository.byCitation(arguments.string("citation") ?: return error(id, -32602, "citation is required")))
            "get_data_freshness" -> success(id, textResult(buildJsonObject { put("latestRetrievedAt", repository.freshness() ?: "no data") }))
            "search_legislation" -> error(id, -32000, "search projection is not available until the first successful ingestion")
            else -> error(id, -32602, "unknown tool $name")
        }
    }

    private fun recordResponse(id: JsonElement, record: LegislationRecord?): JsonObject = if (record == null) {
        success(id, textResult(buildJsonObject { put("found", false) }))
    } else success(id, textResult(buildJsonObject {
        put("found", true); put("id", record.id); put("versionId", record.versionId); put("title", record.title)
        put("citation", record.citation); put("jurisdiction", record.jurisdiction); put("sourceUrl", record.sourceUrl)
        put("retrievedAt", record.retrievedAt); put("text", record.text)
    }))

    private fun textResult(value: JsonObject) = buildJsonObject {
        put("content", buildJsonArray { add(buildJsonObject { put("type", "text"); put("text", json.encodeToString(JsonObject.serializer(), value)) }) })
    }
    private fun JsonObject.string(name: String): String? = this[name]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
    private fun success(id: JsonElement, result: JsonObject) = buildJsonObject { put("jsonrpc", "2.0"); put("id", id); put("result", result) }
    private fun error(id: JsonElement, code: Int, message: String) = buildJsonObject {
        put("jsonrpc", "2.0"); put("id", id); put("error", buildJsonObject { put("code", code); put("message", message) })
    }
}
