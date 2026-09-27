package com.markus.basecamp.gaming.cs2.analysis

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * All DB writes for a parse run. A separate bean from Cs2DemoAsyncParser for the same reason that one is a
 * separate bean from Cs2DemoService: Spring's @Transactional (like @Async) is proxy-based, so a method calling
 * these on `this` from within the same class would silently skip the proxy — findById() would still return a
 * result (each repository call gets its own implicit transaction), but it'd be detached by the time the caller
 * mutated it, so status changes would never actually flush to the DB despite no exception being thrown.
 */
@Service
class Cs2DemoResultWriter(
    private val demos: Cs2DemoRepository,
    private val rounds: Cs2DemoRoundRepository,
    private val kills: Cs2DemoKillRepository,
    private val grenades: Cs2DemoGrenadeRepository,
    private val positions: Cs2DemoPositionRepository,
    private val mapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun store(demoId: Long, parsed: ParsedDemo) {
        // safe to call more than once (e.g. a retry): clear any partial data from a previous attempt first
        rounds.deleteAllByDemoId(demoId)
        kills.deleteAllByDemoId(demoId)
        grenades.deleteAllByDemoId(demoId)
        positions.deleteAllByDemoId(demoId)

        rounds.saveAll(parsed.rounds.map { Cs2DemoRound(demoId, it.number, it.winnerTeam, it.ctScore, it.tScore) })
        kills.saveAll(
            parsed.kills.map {
                Cs2DemoKill(
                    demoId, it.round, it.tick,
                    it.attackerSteamId, it.attackerName, it.attackerTeam, it.attackerX, it.attackerY, it.attackerZ,
                    it.victimSteamId, it.victimName, it.victimTeam, it.victimX, it.victimY, it.victimZ,
                    it.weapon, it.headshot, it.wallbang,
                )
            },
        )
        grenades.saveAll(
            parsed.grenades.map {
                Cs2DemoGrenade(
                    demoId, it.round, it.type, it.throwerSteamId, it.throwerName, it.throwerTeam,
                    it.detonateX, it.detonateY, it.detonateZ, mapper.writeValueAsString(it.trajectory),
                )
            },
        )
        positions.saveAll(
            parsed.positions.map {
                Cs2DemoPosition(demoId, it.round, it.tick, it.steamId, it.name, it.team, it.x, it.y, it.z, it.health, it.alive)
            },
        )
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun markParsing(demoId: Long) {
        val found = demos.findById(demoId)
        if (found.isEmpty) {
            // Should not happen: the demo row must already be committed before parseInBackground() is triggered
            // (see the afterCommit registration in Cs2DemoService.upload()). If this logs, that guarantee broke.
            log.warn("markParsing: demo {} not found — status will stay stuck at UPLOADED", demoId)
        }
        found.ifPresent { it.status = DemoStatus.PARSING }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun markReady(demoId: Long, map: String?) {
        demos.findById(demoId).ifPresent {
            it.status = DemoStatus.READY
            it.map = map
            it.parsedAt = Instant.now()
            it.errorMessage = null
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun markFailed(demoId: Long, message: String) {
        demos.findById(demoId).ifPresent {
            it.status = DemoStatus.FAILED
            it.errorMessage = message.take(2000)
        }
    }
}
