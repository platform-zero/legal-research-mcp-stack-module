package org.webservices.legalresearch

import kotlinx.serialization.json.Json
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertTrue

class McpProtocolTest {
    @Test
    fun `tool discovery uses only legislation tools`() {
        val repository = LegislationRepository("jdbc:postgresql://invalid")
        val response = McpProtocol(repository, Json).handle("""{"jsonrpc":"2.0","id":1,"method":"tools/list"}""")
        val payload = response.toString()
        assertTrue(payload.contains("search_legislation"))
        assertTrue(payload.contains("get_legislation_by_citation"))
        assertTrue(!payload.contains("pipeline_status"))
    }
}
