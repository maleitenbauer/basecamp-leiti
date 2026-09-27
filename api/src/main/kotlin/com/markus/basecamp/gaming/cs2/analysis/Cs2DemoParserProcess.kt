package com.markus.basecamp.gaming.cs2.analysis

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.nanoseconds

class DemoParseException(message: String) : RuntimeException(message)

/** Shells out to the analysis-parser Go binary (see analysis-parser/main.go) and reads back its JSON result. */
@Component
class Cs2DemoParserProcess(
    @Value("\${basecamp.analysis.parser-binary}") private val binaryPath: String,
    @Value("\${basecamp.analysis.parse-timeout-seconds}") private val timeoutSeconds: Long,
    private val mapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** False in local dev on Windows unless a Go build of analysis-parser was placed at the configured path. */
    val available: Boolean get() = Files.isExecutable(Path.of(binaryPath))

    fun parse(demoFile: Path): ParsedDemo {
        if (!available) {
            log.warn("Parser binary not available/executable at {}", binaryPath)
            throw DemoParseException(
                "The demo analysis tool is not installed on this server (expected an executable at $binaryPath). " +
                    "It is built by api/Dockerfile from analysis-parser/ and only exists once deployed via Docker.",
            )
        }

        log.info("Starting parser: {} -demo {}", binaryPath, demoFile)
        val startedAt = System.nanoTime()
        val process = ProcessBuilder(binaryPath, "-demo", demoFile.toString())
            .redirectErrorStream(false)
            .start()

        val stdout = process.inputStream.bufferedReader().use { it.readText() }
        val stderr = process.errorStream.bufferedReader().use { it.readText() }
        val finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
        val elapsed = (System.nanoTime() - startedAt).nanoseconds
        if (!finished) {
            process.destroyForcibly()
            log.warn("Parser for {} timed out after {}", demoFile, elapsed)
            throw DemoParseException("Parsing took longer than ${timeoutSeconds}s and was cancelled")
        }
        if (process.exitValue() != 0) {
            log.warn("Parser for {} exited {} after {}: {}", demoFile, process.exitValue(), elapsed, stderr.trim())
            throw DemoParseException(stderr.trim().ifEmpty { "the parser exited with status ${process.exitValue()}" })
        }
        log.info("Parser for {} finished in {} ({} bytes of JSON)", demoFile, elapsed, stdout.length)

        return try {
            mapper.readValue(stdout, ParsedDemo::class.java)
        } catch (e: Exception) {
            log.warn("Parser output for {} could not be deserialized: {}", demoFile, e.toString())
            throw DemoParseException("The parser's output could not be read: ${e.message}")
        }
    }
}
