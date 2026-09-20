package com.markus.basecamp.gaming.cs2.matches

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MatchMappersTest {

    private val mapper = ObjectMapper()

    // Field names below are taken from a real response of GET /v3/profile/matches (values are made up).
    private val leetifyJson = """
        [
          {"id":"m-older","finished_at":"2026-09-18T20:00:00.000Z","data_source":"matchmaking_competitive","map_name":"de_mirage",
           "team_scores":[{"team_number":2,"score":13},{"team_number":3,"score":2}],
           "stats":[{"steam64_id":"76561198000000001","name":"me","initial_team_number":2,"total_kills":18,"total_deaths":7,
                     "total_assists":4,"kd_ratio":2.57,"dpr":101,"leetify_rating":0.21,"total_hs_kills":9,"rounds_won":13,"rounds_lost":2}]},
          {"id":"m-newer","finished_at":"2026-09-19T14:19:20.000Z","data_source":"matchmaking","map_name":"de_cache",
           "team_scores":[{"team_number":2,"score":14},{"team_number":3,"score":16}],
           "stats":[{"steam64_id":"76561198000000001","name":"me","initial_team_number":2,"total_kills":36,"total_deaths":22,
                     "total_assists":3,"kd_ratio":1.64,"dpr":117,"leetify_rating":0.0885,"total_hs_kills":12,"rounds_won":14,"rounds_lost":16}]},
          {"id":"m-faceit","finished_at":"2026-09-19T10:00:00.000Z","data_source":"faceit","map_name":"de_dust2","stats":[{"steam64_id":"76561198000000001"}]},
          {"id":"m-pro","finished_at":"2026-09-17T10:00:00.000Z","data_source":"hltv","map_name":"de_nuke","stats":[{"steam64_id":"76561198000000001"}]}
        ]
    """.trimIndent()

    // ASSUMED shape: the FACEIT OpenAPI spec only says items[].stats is a free-form map. Verify against a real response.
    private val faceitJson = """
        {"items":[
          {"stats":{"Match Id":"1-abc","Map":"de_inferno","Result":"1","Score":"13 / 9","Kills":"22","Deaths":"15","Assists":"5",
                    "K/D Ratio":"1.47","Headshots %":"41","ADR":"88.3","Match Finished At":"1789000000000","Game Mode":"5v5"}},
          {"stats":{"Match Id":"1-def","Map":"de_mirage","Result":"0","Kills":"10","Deaths":"20","Match Finished At":"1788000000000"}},
          {"stats":{"Map":"de_nuke"}}
        ]}
    """.trimIndent()

    @Test
    fun `leetify keeps only matchmaking matches, newest first`() {
        val matches = LeetifyMapper.toSummaries(mapper.readTree(leetifyJson), "76561198000000001")

        assertEquals(listOf("m-newer", "m-older"), matches.map { it.id })
        assertEquals(listOf("Matchmaking", "Competitive"), matches.map { it.mode })
    }

    @Test
    fun `leetify derives the result from rounds and passes values through unchanged`() {
        val (newer, older) = LeetifyMapper.toSummaries(mapper.readTree(leetifyJson), "76561198000000001")

        assertEquals(MatchResult.LOSS, newer.result)
        assertEquals("14:16", newer.score)
        assertEquals("de_cache", newer.map)
        assertEquals(36, newer.kills)
        assertEquals(22, newer.deaths)
        assertEquals(1.64, newer.kdRatio)
        assertEquals(117.0, newer.adr)
        assertEquals(0.0885, newer.rating) // never rescaled or recalculated
        assertEquals(12, newer.headshotKills)
        assertEquals("https://leetify.com/app/profile/76561198000000001", newer.url)

        assertEquals(MatchResult.WIN, older.result)
    }

    @Test
    fun `leetify tolerates an empty history`() {
        assertTrue(LeetifyMapper.toSummaries(mapper.readTree("[]"), "76561198000000001").isEmpty())
    }

    @Test
    fun `faceit maps stats tolerantly, skips items without a match id, newest first`() {
        val matches = FaceitMapper.toSummaries(mapper.readTree(faceitJson))

        assertEquals(listOf("1-abc", "1-def"), matches.map { it.id })
        val win = matches.first()
        assertEquals(MatchResult.WIN, win.result)
        assertEquals("13 / 9", win.score)
        assertEquals(22, win.kills)
        assertEquals(15, win.deaths)
        assertEquals(5, win.assists)
        assertEquals(1.47, win.kdRatio)
        assertEquals(88.3, win.adr)
        assertEquals(41.0, win.headshotPercent)
        assertNotNull(win.finishedAt)
        assertEquals("https://www.faceit.com/en/cs2/room/1-abc", win.url)
        assertEquals("de_inferno", win.details?.get("Map"))

        val loss = matches.last()
        assertEquals(MatchResult.LOSS, loss.result)
        assertNull(loss.adr) // missing stats stay null instead of failing the whole list
    }

    @Test
    fun `faceit tolerates an empty or unexpected response`() {
        assertTrue(FaceitMapper.toSummaries(mapper.readTree("{}")).isEmpty())
        assertTrue(FaceitMapper.toSummaries(mapper.readTree("""{"items":[]}""")).isEmpty())
    }
}
