package org.webservices.legalresearch

import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class RoleAuthorizer(
    private val userInfoUrl: String,
    private val requiredRole: String,
    private val httpClient: HttpClient,
    private val json: Json
) {
    suspend fun permits(bearerToken: String): Boolean {
        val response = httpClient.get(userInfoUrl) { bearerAuth(bearerToken) }
        if (response.status != HttpStatusCode.OK) return false
        val identity = json.parseToJsonElement(response.bodyAsText()).jsonObject
        val directRoles = identity["groups"]?.jsonArray.orEmpty().map { it.jsonPrimitive.content }
        val realmRoles = identity["realm_access"]?.jsonObject?.get("roles")?.jsonArray.orEmpty()
            .map { it.jsonPrimitive.content }
        return requiredRole in directRoles || requiredRole in realmRoles
    }
}
