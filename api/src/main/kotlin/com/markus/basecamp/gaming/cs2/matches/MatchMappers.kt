package com.markus.basecamp.gaming.cs2.matches

import com.fasterxml.jackson.databind.JsonNode
import java.time.Instant

/** Case-insensitive lookup of the first matching field, returned as trimmed text. */
private fun JsonNode.text(vararg names: String): String? {
    for (name in names) {
        val field = fieldNames().asSequence().firstOrNull { it.equals(name, ignoreCase = true) } ?: continue
        val value = get(field)
        if (value != null && !value.isNull) {
            val text = value.asText().trim()
            if (text.isNotEmpty()) return text
        }
    }
    return null
}

private fun JsonNode.number(vararg names: String): Double? =
    text(*names)?.replace(',', '.')?.toDoubleOrNull()

/** Accepts epoch seconds or milliseconds. */
private fun epochToInstant(value: Double): Instant =
    if (value > 100_000_000_000.0) Instant.ofEpochMilli(value.toLong()) else Instant.ofEpochSecond(value.toLong())

/**
 * FACEIT: GET /players/{id}/games/cs2/stats returns items[].stats, a free-form map of strings. The key names are
 * not documented in the OpenAPI spec, so the lookups below accept a few spellings and everything else is kept
 * in [MatchSummary.details].
 */
object FaceitMapper {

    fun toSummaries(root: JsonNode): List<MatchSummary> =
        root.path("items")
            .mapNotNull { item -> item.path("stats").takeIf { it.isObject }?.let { toSummary(it) } }
            .sortedByDescending { it.finishedAt ?: Instant.EPOCH }

    private fun toSummary(stats: JsonNode): MatchSummary? {
        val id = stats.text("Match Id", "MatchId", "match_id") ?: return null
        return MatchSummary(
            id = id,
            source = "faceit",
            mode = stats.text("Game Mode", "Mode"),
            finishedAt = stats.number("Match Finished At", "Finished At", "Updated At", "Created At")?.let(::epochToInstant),
            map = stats.text("Map"),
            result = parseResult(stats.text("Result")),
            score = stats.text("Final Score", "Score"),
            kills = stats.number("Kills")?.toInt(),
            deaths = stats.number("Deaths")?.toInt(),
            assists = stats.number("Assists")?.toInt(),
            kdRatio = stats.number("K/D Ratio", "KD Ratio", "K/D"),
            adr = stats.number("ADR", "Average Damage per Round"),
            headshotPercent = stats.number("Headshots %", "Headshots%", "HS %"),
            headshotKills = stats.number("Headshots")?.toInt(),
            rating = null,
            ratingLabel = null,
            url = "https://www.faceit.com/en/cs2/room/$id",
            details = stats.fieldNames().asSequence().associateWith { stats.get(it).asText() },
        )
    }

    private fun parseResult(raw: String?): MatchResult? = when (raw?.lowercase()) {
        "1", "true", "win", "won", "w" -> MatchResult.WIN
        "0", "false", "loss", "lost", "lose", "l" -> MatchResult.LOSS
        else -> null
    }
}

/**
 * Leetify: GET /v3/profile/matches?steam64_id= returns an array of matches; each has stats[] with the requested
 * player's row (verified against a live response). Only matchmaking matches are used here; FACEIT matches come from
 * the FACEIT API instead. Values are passed through unchanged, as Leetify's guidelines ask.
 */
object LeetifyMapper {

    private val MATCHMAKING_SOURCES = mapOf(
        "matchmaking" to "Matchmaking",
        "matchmaking_competitive" to "Competitive",
    )

    fun toSummaries(root: JsonNode, steam64Id: String): List<MatchSummary> =
        root.mapNotNull { match -> toSummary(match, steam64Id) }
            .sortedByDescending { it.finishedAt ?: Instant.EPOCH }

    private fun toSummary(match: JsonNode, steam64Id: String): MatchSummary? {
        val source = match.path("data_source").asText()
        val mode = MATCHMAKING_SOURCES[source] ?: return null
        val stats = match.path("stats")
        val row = stats.firstOrNull { it.path("steam64_id").asText() == steam64Id } ?: stats.firstOrNull() ?: return null

        val won = row.path("rounds_won").takeIf { it.isNumber }?.asInt()
        val lost = row.path("rounds_lost").takeIf { it.isNumber }?.asInt()

        return MatchSummary(
            id = match.path("id").asText().ifEmpty { return null },
            source = source,
            mode = mode,
            finishedAt = match.path("finished_at").asText().takeIf { it.isNotEmpty() }
                ?.let { runCatching { Instant.parse(it) }.getOrNull() },
            map = match.path("map_name").asText().ifEmpty { null },
            result = if (won != null && lost != null) {
                when {
                    won > lost -> MatchResult.WIN
                    won < lost -> MatchResult.LOSS
                    else -> MatchResult.DRAW
                }
            } else null,
            score = if (won != null && lost != null) "$won:$lost" else null,
            kills = row.intOrNull("total_kills"),
            deaths = row.intOrNull("total_deaths"),
            assists = row.intOrNull("total_assists"),
            kdRatio = row.doubleOrNull("kd_ratio"),
            adr = row.doubleOrNull("dpr"),
            headshotPercent = null,
            headshotKills = row.intOrNull("total_hs_kills"),
            rating = row.doubleOrNull("leetify_rating"),
            ratingLabel = "Leetify rating",
            url = "https://leetify.com/app/profile/$steam64Id",
            details = null,
        )
    }

    private fun JsonNode.intOrNull(name: String): Int? = path(name).takeIf { it.isNumber }?.asInt()

    private fun JsonNode.doubleOrNull(name: String): Double? = path(name).takeIf { it.isNumber }?.asDouble()
}
