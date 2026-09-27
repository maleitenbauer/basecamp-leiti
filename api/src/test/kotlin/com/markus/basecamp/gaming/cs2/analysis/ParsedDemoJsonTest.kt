package com.markus.basecamp.gaming.cs2.analysis

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Verifies Jackson can deserialize the exact JSON shape analysis-parser/main.go's `result` struct encodes
 * (field names and null-ability match its `json:"..."` tags). This is the one contract between the Go
 * program and the Kotlin backend that unit tests can check without Docker or a Go toolchain.
 */
class ParsedDemoJsonTest {

    private val mapper = jacksonObjectMapper()

    // Field names/shape copied from analysis-parser/main.go's json struct tags.
    private val sample = """
        {
          "map": "de_mirage",
          "rounds": [
            {"number": 1, "winnerTeam": "CT", "ctScore": 1, "tScore": 0},
            {"number": 2, "winnerTeam": "", "ctScore": 1, "tScore": 1}
          ],
          "kills": [
            {
              "round": 1, "tick": 4200,
              "attackerSteamId": "76561198000000001", "attackerName": "me", "attackerTeam": "CT",
              "attackerX": 100.5, "attackerY": -200.25, "attackerZ": 64.0,
              "victimSteamId": "76561198000000002", "victimName": "them", "victimTeam": "T",
              "victimX": 150.0, "victimY": -180.0, "victimZ": 64.0,
              "weapon": "AK-47", "headshot": true, "wallbang": false
            },
            {
              "round": 1, "tick": 4300,
              "attackerSteamId": null, "attackerName": null, "attackerTeam": null,
              "attackerX": null, "attackerY": null, "attackerZ": null,
              "victimSteamId": "76561198000000003", "victimName": "fallvictim", "victimTeam": "T",
              "victimX": 0.0, "victimY": 0.0, "victimZ": 0.0,
              "weapon": "World", "headshot": false, "wallbang": false
            }
          ],
          "grenades": [
            {
              "round": 1, "type": "Flash", "throwerSteamId": "76561198000000001", "throwerName": "me", "throwerTeam": "CT",
              "detonateX": 10.0, "detonateY": 20.0, "detonateZ": 5.0,
              "trajectory": [{"x": 1.0, "y": 2.0, "z": 3.0}, {"x": 4.0, "y": 5.0, "z": 6.0}]
            }
          ],
          "positions": [
            {"round": 1, "tick": 4096, "steamId": "76561198000000001", "name": "me", "team": "CT",
             "x": 100.0, "y": -200.0, "z": 64.0, "health": 87, "alive": true}
          ]
        }
    """.trimIndent()

    @Test
    fun `deserializes the analysis-parser JSON shape, including nullable killer fields`() {
        val parsed = mapper.readValue(sample, ParsedDemo::class.java)

        assertEquals("de_mirage", parsed.map)
        assertEquals(2, parsed.rounds.size)
        assertEquals("CT", parsed.rounds[0].winnerTeam)

        assertEquals(2, parsed.kills.size)
        val normalKill = parsed.kills[0]
        assertEquals("me", normalKill.attackerName)
        assertTrue(normalKill.headshot)
        assertEquals(100.5, normalKill.attackerX)

        val worldKill = parsed.kills[1] // no attacker (e.g. fall damage / world)
        assertNull(worldKill.attackerSteamId)
        assertNull(worldKill.attackerX)
        assertEquals("fallvictim", worldKill.victimName)

        assertEquals(1, parsed.grenades.size)
        assertEquals("Flash", parsed.grenades[0].type)
        assertEquals(2, parsed.grenades[0].trajectory.size)
        assertEquals(6.0, parsed.grenades[0].trajectory[1].z)

        assertEquals(1, parsed.positions.size)
        assertEquals(87, parsed.positions[0].health)
        assertTrue(parsed.positions[0].alive)
    }

    @Test
    fun `tolerates extra fields the Go program might add later`() {
        val withExtra = sample.replaceFirst("\"map\": \"de_mirage\",", "\"map\": \"de_mirage\", \"tickRate\": 128,")
        val parsed = mapper.readValue(withExtra, ParsedDemo::class.java)
        assertEquals("de_mirage", parsed.map)
    }
}
