package com.markus.basecamp.gaming.cs2.matches

import com.fasterxml.jackson.databind.JsonNode
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClient
import java.net.http.HttpClient
import java.time.Duration

private fun restClient(baseUrl: String): RestClient {
    val factory = JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build())
    factory.setReadTimeout(Duration.ofSeconds(20))
    return RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build()
}

/** FACEIT Data API v4. The API key stays on the server. */
@Component
class FaceitClient(
    @Value("\${basecamp.faceit.base-url}") baseUrl: String,
    @Value("\${basecamp.faceit.api-key}") private val apiKey: String,
) {
    private val client = restClient(baseUrl)

    val configured: Boolean get() = apiKey.isNotBlank()

    /** The player, or null when the nickname does not exist. */
    fun player(nickname: String): JsonNode? =
        try {
            client.get()
                .uri { it.path("/players").queryParam("nickname", nickname).queryParam("game", "cs2").build() }
                .header("Authorization", "Bearer $apiKey")
                .retrieve()
                .body(JsonNode::class.java)
        } catch (e: HttpClientErrorException.NotFound) {
            null
        }

    fun gameStats(playerId: String, limit: Int): JsonNode? =
        client.get()
            .uri { it.path("/players/{id}/games/cs2/stats").queryParam("limit", limit).build(playerId) }
            .header("Authorization", "Bearer $apiKey")
            .retrieve()
            .body(JsonNode::class.java)
}

/** Leetify public API. A key is optional but gives higher rate limits. */
@Component
class LeetifyClient(
    @Value("\${basecamp.leetify.base-url}") baseUrl: String,
    @Value("\${basecamp.leetify.api-key}") private val apiKey: String,
) {
    private val client = restClient(baseUrl)

    fun matches(steam64Id: String): JsonNode? {
        val request = client.get().uri { it.path("/v3/profile/matches").queryParam("steam64_id", steam64Id).build() }
        if (apiKey.isNotBlank()) request.header("Authorization", "Bearer $apiKey")
        return request.retrieve().body(JsonNode::class.java)
    }
}
