package com.markus.basecamp.gaming.cs2.matches

import jakarta.validation.constraints.Pattern
import java.time.Instant

enum class MatchResult { WIN, LOSS, DRAW }

/**
 * One match as shown in the UI. Values are passed through exactly as the provider reports them; nothing here is
 * recalculated or rescaled. Fields a provider does not offer are null.
 */
data class MatchSummary(
    val id: String,
    /** faceit, matchmaking or matchmaking_competitive */
    val source: String,
    val mode: String?,
    val finishedAt: Instant?,
    val map: String?,
    val result: MatchResult?,
    val score: String?,
    val kills: Int?,
    val deaths: Int?,
    val assists: Int?,
    val kdRatio: Double?,
    val adr: Double?,
    val headshotPercent: Double?,
    val headshotKills: Int?,
    /** The provider's raw value. Leetify reports 0.0885; its app shows +8.85, so the UI multiplies by 100. */
    val rating: Double?,
    val ratingLabel: String?,
    val url: String?,
    /** Everything the provider sent for this match, for the "details" view. Only filled for FACEIT. */
    val details: Map<String, String>?,
)

data class PlayerInfo(val nickname: String, val level: Int?, val elo: Int?)

data class MatchesResponse(
    /** false when the user (or the server) still has to set something up; [message] says what. */
    val configured: Boolean,
    val message: String?,
    val matches: List<MatchSummary>,
    val player: PlayerInfo?,
    val fetchedAt: Instant,
    val sourceUrl: String?,
)

data class MatchSettingsResponse(
    val faceitNickname: String?,
    val steam64Id: String?,
    /** true when the server has a FACEIT API key */
    val faceitAvailable: Boolean,
)

/** Replaces both values; null clears one. */
data class UpdateMatchSettingsRequest(
    @field:Pattern(regexp = "^[A-Za-z0-9_.-]{2,64}$", message = "FACEIT nicknames use 2-64 letters, digits, . _ -")
    val faceitNickname: String? = null,
    @field:Pattern(regexp = "^7656119\\d{10}$", message = "A Steam64 ID is 17 digits starting with 7656119")
    val steam64Id: String? = null,
)
