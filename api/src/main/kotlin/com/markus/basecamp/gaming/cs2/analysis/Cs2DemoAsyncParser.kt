package com.markus.basecamp.gaming.cs2.analysis

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import java.nio.file.Path
import kotlin.io.path.deleteIfExists

/**
 * Runs the actual parse off the request thread, so uploading a 200 MB demo doesn't hold the HTTP connection open
 * for as long as parsing takes. Must be a separate bean from Cs2DemoService for Spring's @Async proxy to apply
 * (a method calling @Async on `this` would just run synchronously). All DB writes are delegated to
 * Cs2DemoResultWriter, a separate bean again — see its doc-comment for why: the same self-invocation trap that
 * requires this class to be its own bean also silently defeats @Transactional if these calls stayed in here.
 */
@Service
class Cs2DemoAsyncParser(
    private val parser: Cs2DemoParserProcess,
    private val writer: Cs2DemoResultWriter,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Async
    fun parseInBackground(demoId: Long, demoFile: Path) {
        log.info("parseInBackground started for demo {} on thread {}", demoId, Thread.currentThread().name)
        writer.markParsing(demoId)
        try {
            val parsed = parser.parse(demoFile)
            log.info(
                "Demo {} parsed: map={}, rounds={}, kills={}, grenades={}, positions={}",
                demoId, parsed.map, parsed.rounds.size, parsed.kills.size, parsed.grenades.size, parsed.positions.size,
            )
            writer.store(demoId, parsed)
            writer.markReady(demoId, parsed.map)
            demoFile.deleteIfExists() // only the extracted data is kept once parsing succeeds
            log.info("Demo {} marked READY", demoId)
        } catch (e: DemoParseException) {
            log.warn("Demo {} failed to parse: {}", demoId, e.message)
            writer.markFailed(demoId, e.message ?: "Unknown error")
        } catch (e: Exception) {
            log.warn("Unexpected failure parsing demo {}: {}", demoId, e.toString())
            writer.markFailed(demoId, "Unexpected error: ${e.message}")
        }
    }
}
