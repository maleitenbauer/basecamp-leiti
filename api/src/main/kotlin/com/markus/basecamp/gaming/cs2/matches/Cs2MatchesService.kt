package com.markus.basecamp.gaming.cs2.matches

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClientResponseException
import org.springframework.web.server.ResponseStatusException
import java.time.Duration
import java.time.Instant

private const val MAX_MATCHES = 40

/**
 * Match history from FACEIT and Leetify. Match data is fetched live and only cached in memory for two minutes;
 * the database only holds each user's link settings (FACEIT nickname, Steam64 ID).
 */
@Service
class Cs2MatchesService(
    private val profiles: MatchProfileRepository,
    private val faceit: FaceitClient,
    private val leetify: LeetifyClient,
) {
    private val faceitCache = TtlCache<MatchesResponse>(Duration.ofMinutes(2))
    private val leetifyCache = TtlCache<MatchesResponse>(Duration.ofMinutes(2))

    fun settings(userId: Long): MatchSettingsResponse {
        val profile = profiles.findById(userId).orElse(null)
        return MatchSettingsResponse(profile?.faceitNickname, profile?.steam64Id, faceit.configured)
    }

    fun saveSettings(userId: Long, request: UpdateMatchSettingsRequest): MatchSettingsResponse {
        val profile = profiles.findById(userId).orElseGet { MatchProfile(userId) }
        val nickname = request.faceitNickname?.trim()?.ifEmpty { null }

        if (nickname != profile.faceitNickname) {
            profile.faceitNickname = nickname
            profile.faceitPlayerId = null
            if (nickname != null) {
                if (!faceit.configured) {
                    throw ResponseStatusException(HttpStatus.CONFLICT, "FACEIT is not set up on the server (FACEIT_API_KEY is missing)")
                }
                val player = upstream("FACEIT") { faceit.player(nickname) }
                    ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "No FACEIT player named '$nickname' found for CS2")
                profile.faceitNickname = player.path("nickname").asText(nickname)
                profile.faceitPlayerId = player.path("player_id").asText().ifEmpty { null }
            }
        }
        profile.steam64Id = request.steam64Id?.trim()?.ifEmpty { null }
        profile.updatedAt = Instant.now()
        profiles.save(profile)

        faceitCache.evict(userId)
        leetifyCache.evict(userId)
        return settings(userId)
    }

    fun faceitMatches(userId: Long, refresh: Boolean): MatchesResponse {
        val profile = profiles.findById(userId).orElse(null)
        val nickname = profile?.faceitNickname
            ?: return notConfigured("Add your FACEIT nickname in the settings to see your FACEIT matches.")
        if (!faceit.configured) return notConfigured("FACEIT is not set up on the server (FACEIT_API_KEY is missing).")

        if (!refresh) faceitCache.get(userId)?.let { return it }

        val player = upstream("FACEIT") { faceit.player(nickname) }
            ?: return notConfigured("The FACEIT player '$nickname' no longer exists. Check your nickname in the settings.")
        val playerId = player.path("player_id").asText()
        val stats = upstream("FACEIT") { faceit.gameStats(playerId, MAX_MATCHES) }

        val cs2 = player.path("games").path("cs2")
        val response = MatchesResponse(
            configured = true,
            message = null,
            matches = stats?.let { FaceitMapper.toSummaries(it) }.orEmpty().take(MAX_MATCHES),
            player = PlayerInfo(
                nickname = player.path("nickname").asText(nickname),
                level = cs2.path("skill_level").takeIf { it.isNumber }?.asInt(),
                elo = cs2.path("faceit_elo").takeIf { it.isNumber }?.asInt(),
            ),
            fetchedAt = Instant.now(),
            sourceUrl = player.path("faceit_url").asText().replace("{lang}", "en").ifEmpty { null },
        )
        faceitCache.put(userId, response)
        return response
    }

    fun leetifyMatches(userId: Long, refresh: Boolean): MatchesResponse {
        val steam64 = profiles.findById(userId).orElse(null)?.steam64Id
            ?: return notConfigured("Add your Steam64 ID in the settings to see your matchmaking matches.")

        if (!refresh) leetifyCache.get(userId)?.let { return it }

        val raw = upstream("Leetify") { leetify.matches(steam64) }
        val response = MatchesResponse(
            configured = true,
            message = null,
            matches = raw?.let { LeetifyMapper.toSummaries(it, steam64) }.orEmpty().take(MAX_MATCHES),
            player = null,
            fetchedAt = Instant.now(),
            sourceUrl = "https://leetify.com/app/profile/$steam64",
        )
        leetifyCache.put(userId, response)
        return response
    }

    private fun notConfigured(message: String) =
        MatchesResponse(configured = false, message = message, matches = emptyList(), player = null, fetchedAt = Instant.now(), sourceUrl = null)

    /** Turns provider failures into clear messages instead of a bare 500. */
    private fun <T> upstream(provider: String, call: () -> T): T =
        try {
            call()
        } catch (e: RestClientResponseException) {
            throw when (e.statusCode.value()) {
                401, 403 -> ResponseStatusException(HttpStatus.BAD_GATEWAY, "$provider rejected the server's API key")
                404 -> ResponseStatusException(HttpStatus.NOT_FOUND, "$provider has no data for this account (is the profile public?)")
                429 -> ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "$provider rate limit reached, try again in a minute")
                else -> ResponseStatusException(HttpStatus.BAD_GATEWAY, "$provider answered with HTTP ${e.statusCode.value()}")
            }
        } catch (e: ResourceAccessException) {
            throw ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, "$provider did not answer in time")
        }
}
