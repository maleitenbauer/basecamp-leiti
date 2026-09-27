package com.markus.basecamp.gaming.cs2.analysis

import com.markus.basecamp.gaming.cs2.matches.MatchProfileRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.server.ResponseStatusException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.deleteIfExists

/** Every method takes the calling user's id and only ever touches that user's demos. */
@Service
class Cs2DemoService(
    private val demos: Cs2DemoRepository,
    private val rounds: Cs2DemoRoundRepository,
    private val kills: Cs2DemoKillRepository,
    private val grenades: Cs2DemoGrenadeRepository,
    private val asyncParser: Cs2DemoAsyncParser,
    private val parserProcess: Cs2DemoParserProcess,
    private val matchProfiles: MatchProfileRepository,
    @Value("\${basecamp.analysis.demo-storage-dir}") private val storageDir: String,
    @Value("\${basecamp.analysis.max-demo-size-bytes}") private val maxSizeBytes: Long,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    val analysisAvailable: Boolean get() = parserProcess.available

    @Transactional(readOnly = true)
    fun list(userId: Long): List<DemoSummaryResponse> =
        demos.findAllByUserIdOrderByUploadedAtDesc(userId).map { it.toSummary() }

    @Transactional
    fun upload(userId: Long, file: MultipartFile): DemoSummaryResponse {
        val filename = file.originalFilename?.trim().orEmpty().ifEmpty { "demo.dem" }
        if (!filename.lowercase().endsWith(".dem")) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Only .dem files are accepted")
        }
        if (file.size <= 0 || file.size > maxSizeBytes) {
            throw ResponseStatusException(
                HttpStatus.PAYLOAD_TOO_LARGE,
                "The file must be between 1 byte and ${maxSizeBytes / 1_000_000} MB",
            )
        }

        val demo = demos.save(Cs2Demo(userId = userId, originalFilename = filename, sizeBytes = file.size))
        val path = demoPath(userId, demo.id!!)
        log.info("Demo {} uploaded by user {}: {} ({} bytes) -> {}", demo.id, userId, filename, file.size, path)

        try {
            Files.createDirectories(path.parent)
            file.inputStream.use { Files.copy(it, path) }
        } catch (e: Exception) {
            log.warn("Demo {}: could not save uploaded file to {}: {}", demo.id, path, e.toString())
            demo.status = DemoStatus.FAILED
            demo.errorMessage = "Could not save the uploaded file: ${e.message}"
            return demo.toSummary()
        }

        // Deferred to afterCommit: parseInBackground() runs on another thread almost immediately, and its first
        // step reads this demo row by id in its own transaction. Calling it directly here would race the still-
        // open `upload()` transaction — the async thread would frequently find no row yet (READ COMMITTED) and
        // silently stay UPLOADED forever, since Cs2DemoAsyncParser's mark* methods use `.ifPresent { }`.
        val demoId = demo.id!!
        TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
            override fun afterCommit() {
                log.info("Demo {}: upload transaction committed, dispatching to async parser", demoId)
                asyncParser.parseInBackground(demoId, path)
            }
        })
        return demo.toSummary()
    }

    @Transactional(readOnly = true)
    fun getAnalysis(userId: Long, demoId: Long): DemoAnalysisResponse {
        val demo = findDemo(userId, demoId)
        if (demo.status != DemoStatus.READY) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "This demo is not ready yet (status: ${demo.status})")
        }
        return DemoAnalysisResponse(
            map = demo.map,
            rounds = rounds.findAllByDemoIdOrderByRoundNumberAsc(demoId).map { DemoRoundResponse(it.roundNumber, it.winnerTeam, it.ctScore, it.tScore) },
            kills = kills.findAllByDemoIdOrderByRoundNumberAscTickAsc(demoId).map {
                DemoKillResponse(
                    it.roundNumber, it.attackerSteamId, it.attackerName, it.attackerTeam, it.attackerX, it.attackerY,
                    it.victimSteamId, it.victimName, it.victimTeam, it.victimX, it.victimY, it.weapon, it.headshot,
                )
            },
            grenades = grenades.findAllByDemoIdOrderByRoundNumberAsc(demoId).map {
                DemoGrenadeResponse(it.roundNumber, it.type, it.throwerName, it.throwerTeam, it.detonateX, it.detonateY)
            },
            viewerSteamId = matchProfiles.findById(userId).orElse(null)?.steam64Id,
        )
    }

    /** Re-runs parsing, e.g. after a parser bugfix; only possible while the raw file is still on disk (a FAILED demo). */
    @Transactional
    fun retry(userId: Long, demoId: Long): DemoSummaryResponse {
        val demo = findDemo(userId, demoId)
        val path = demoPath(userId, demoId)
        if (demo.status != DemoStatus.FAILED || !Files.exists(path)) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Only a failed demo whose file is still stored can be retried")
        }
        asyncParser.parseInBackground(demoId, path)
        return demo.toSummary()
    }

    @Transactional
    fun delete(userId: Long, demoId: Long) {
        findDemo(userId, demoId)
        demoPath(userId, demoId).deleteIfExists()
        demos.deleteById(demoId) // rounds/kills/grenades/positions cascade via the FK
    }

    private fun demoPath(userId: Long, demoId: Long): Path = Path.of(storageDir, userId.toString(), "$demoId.dem")

    private fun findDemo(userId: Long, id: Long): Cs2Demo =
        demos.findByIdAndUserId(id, userId) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Demo not found")

    private fun Cs2Demo.toSummary() = DemoSummaryResponse(
        id = id!!,
        originalFilename = originalFilename,
        map = map,
        sizeBytes = sizeBytes,
        status = status,
        errorMessage = errorMessage,
        uploadedAt = uploadedAt,
        parsedAt = parsedAt,
        roundCount = rounds.countByDemoId(id!!).toInt(),
        killCount = kills.countByDemoId(id!!).toInt(),
    )
}
