package com.markus.basecamp.gaming.cs2.analysis

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.time.Instant

// ---- what analysis-parser prints as JSON on stdout (see analysis-parser/main.go's `result` struct) ----

@JsonIgnoreProperties(ignoreUnknown = true)
data class ParsedRound(
    val number: Int,
    val winnerTeam: String?,
    val ctScore: Int,
    val tScore: Int,
    /** 0 (Go's int zero value) if the parser never saw a RoundFreezetimeEnd event for this round. */
    val freezeTimeEndTick: Int,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ParsedKill(
    val round: Int,
    val tick: Int,
    val attackerSteamId: String?,
    val attackerName: String?,
    val attackerTeam: String?,
    val attackerX: Double?,
    val attackerY: Double?,
    val attackerZ: Double?,
    val victimSteamId: String?,
    val victimName: String?,
    val victimTeam: String?,
    val victimX: Double,
    val victimY: Double,
    val victimZ: Double,
    val weapon: String,
    val headshot: Boolean,
    val wallbang: Boolean,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ParsedTrajectoryPoint(val x: Double, val y: Double, val z: Double)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ParsedGrenade(
    val round: Int,
    val type: String,
    val throwerSteamId: String?,
    val throwerName: String?,
    val throwerTeam: String?,
    val throwX: Double?,
    val throwY: Double?,
    val throwZ: Double?,
    val detonateX: Double?,
    val detonateY: Double?,
    val detonateZ: Double?,
    val trajectory: List<ParsedTrajectoryPoint>,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ParsedPosition(
    val round: Int,
    val tick: Int,
    val steamId: String,
    val name: String,
    val team: String,
    val x: Double,
    val y: Double,
    val z: Double,
    val health: Int,
    val alive: Boolean,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ParsedDemo(
    val map: String?,
    val rounds: List<ParsedRound> = emptyList(),
    val kills: List<ParsedKill> = emptyList(),
    val grenades: List<ParsedGrenade> = emptyList(),
    val positions: List<ParsedPosition> = emptyList(),
)

// ---- API responses ----

data class DemoSummaryResponse(
    val id: Long,
    val originalFilename: String,
    val map: String?,
    val sizeBytes: Long,
    val status: DemoStatus,
    val errorMessage: String?,
    val uploadedAt: Instant,
    val parsedAt: Instant?,
    val roundCount: Int,
    val killCount: Int,
)

data class DemoKillResponse(
    val round: Int,
    val attackerSteamId: String?,
    val attackerName: String?,
    val attackerTeam: String?,
    val attackerX: Double?,
    val attackerY: Double?,
    val victimSteamId: String?,
    val victimName: String?,
    val victimTeam: String?,
    val victimX: Double,
    val victimY: Double,
    val weapon: String,
    val headshot: Boolean,
)

data class DemoRoundResponse(
    val number: Int,
    val winnerTeam: String?,
    val ctScore: Int,
    val tScore: Int,
    val freezeTimeEndTick: Int?,
)

data class DemoGrenadeTrajectoryPointResponse(val x: Double, val y: Double)

data class DemoGrenadeResponse(
    val round: Int,
    val type: String,
    val throwerSteamId: String?,
    val throwerName: String?,
    val throwerTeam: String?,
    val throwX: Double?,
    val throwY: Double?,
    val detonateX: Double?,
    val detonateY: Double?,
    val trajectory: List<DemoGrenadeTrajectoryPointResponse>,
)

data class DemoPositionResponse(
    val tick: Int,
    val steamId: String,
    val name: String,
    val team: String,
    val x: Double,
    val y: Double,
    val health: Int,
    val alive: Boolean,
)

/** Everything needed for the static (v1) analysis views: kills, rounds, and grenades (throw/detonate points plus full flight trajectory). */
data class DemoAnalysisResponse(
    val map: String?,
    val rounds: List<DemoRoundResponse>,
    val kills: List<DemoKillResponse>,
    val grenades: List<DemoGrenadeResponse>,
    /** The viewing user's own Steam64 ID (from their Matches-page profile), so the UI can default a player filter
     *  to "me" without the user having to pick themselves out of the list every time. Null if never set. */
    val viewerSteamId: String?,
)
