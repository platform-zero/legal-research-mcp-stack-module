package org.webservices.legalresearch

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

data class ServiceConfig(
    val port: Int,
    val postgresDsn: String,
    val userInfoUrl: String,
    val requiredRole: String,
    val corpusPath: java.nio.file.Path,
    val ingestionToken: String
)

fun main() {
    val config = ServiceConfig(
        port = System.getenv("LEGAL_RESEARCH_MCP_PORT")?.toIntOrNull() ?: 8130,
        postgresDsn = requireNotNull(System.getenv("LEGAL_RESEARCH_POSTGRES_DSN")) { "LEGAL_RESEARCH_POSTGRES_DSN is required" },
        userInfoUrl = requireNotNull(System.getenv("LEGAL_RESEARCH_OIDC_USERINFO_URL")) { "LEGAL_RESEARCH_OIDC_USERINFO_URL is required" },
        requiredRole = System.getenv("LEGAL_RESEARCH_REQUIRED_ROLE") ?: "legal-research",
        corpusPath = java.nio.file.Path.of(System.getenv("LEGAL_RESEARCH_CORPUS_PATH") ?: "/corpus/current.jsonl"),
        ingestionToken = requireNotNull(System.getenv("LEGAL_RESEARCH_INGESTION_TOKEN")) { "LEGAL_RESEARCH_INGESTION_TOKEN is required" }
    )
    val repository = LegislationRepository(config.postgresDsn).also { it.migrate() }
    embeddedServer(Netty, port = config.port) { legalResearchApplication(config, repository, HttpClient(CIO)) }.start(wait = true)
}

fun Application.legalResearchApplication(config: ServiceConfig, repository: LegislationRepository, client: HttpClient) {
    val json = Json { ignoreUnknownKeys = true }
    val protocol = McpProtocol(repository, json)
    val authorizer = RoleAuthorizer(config.userInfoUrl, config.requiredRole, client, json)
    install(ContentNegotiation) { json(json) }
    routing {
        get("/health") { call.respond(mapOf("status" to "ok")) }
        post("/mcp") {
            val bearer = call.request.headers[HttpHeaders.Authorization]?.removePrefix("Bearer ")?.trim()
            if (bearer.isNullOrBlank() || !authorizer.permits(bearer)) {
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "legal-research role required")); return@post
            }
            call.respond(protocol.handle(call.receiveText()))
        }
        post("/internal/import-corpus") {
            if (call.request.headers["X-Legal-Ingestion-Token"] != config.ingestionToken) {
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "invalid ingestion token")); return@post
            }
            if (!java.nio.file.Files.isRegularFile(config.corpusPath)) {
                call.respond(HttpStatusCode.Conflict, mapOf("error" to "corpus file is not available")); return@post
            }
            call.respond(repository.importCorpus(config.corpusPath, json))
        }
    }
}
