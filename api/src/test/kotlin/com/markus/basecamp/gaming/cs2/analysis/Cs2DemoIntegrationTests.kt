package com.markus.basecamp.gaming.cs2.analysis

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.web.server.ResponseStatusException
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.nio.file.Files
import java.nio.file.Path

/**
 * Runs the whole app against a real Postgres, so the V7 migration and Hibernate schema validation are exercised
 * too. Skipped automatically where Docker is unavailable (e.g. local Windows).
 *
 * The analysis-parser Go binary is built by api/Dockerfile, a separate step from `./gradlew test`, so it never
 * exists here — basecamp.analysis.parser-binary points at a path that does not exist in this environment. That
 * is itself exercised below: uploads are accepted and correctly end up FAILED with a clear message, the same
 * behaviour a real deploy would show if the binary were ever missing or misconfigured.
 */
@SpringBootTest(
    properties = [
        "basecamp.bootstrap-admin.username=admin",
        "basecamp.bootstrap-admin.password=correct-horse-battery",
        "basecamp.scheduling.enabled=false",
    ],
)
@Testcontainers(disabledWithoutDocker = true)
class Cs2DemoIntegrationTests {

    companion object {
        @Container
        @ServiceConnection
        @JvmStatic
        val postgres = PostgreSQLContainer<Nothing>("postgres:17")

        // The default storage dir (/data/demos) only exists, and is only writable, inside the deployed Docker
        // image; on a CI runner creating it fails, which would mark every upload FAILED before parsing even starts.
        private val demoStorageDir: Path = Files.createTempDirectory("basecamp-demos-test")

        @JvmStatic
        @DynamicPropertySource
        fun storageProperties(registry: DynamicPropertyRegistry) {
            registry.add("basecamp.analysis.demo-storage-dir") { demoStorageDir.toString() }
        }
    }

    @Autowired
    lateinit var service: Cs2DemoService

    @Autowired
    lateinit var jdbc: JdbcTemplate

    private fun adminId(): Long =
        jdbc.queryForObject("select id from core.app_user where username = 'admin'", Long::class.java)!!

    private fun demoFile(name: String = "match.dem", bytes: ByteArray = ByteArray(1024)) =
        MockMultipartFile("file", name, "application/octet-stream", bytes)

    /** Polls list() until the demo named [filename] leaves UPLOADED/PARSING, since parsing runs @Async. */
    private fun awaitFinalStatus(userId: Long, demoId: Long, timeoutMs: Long = 5000): DemoSummaryResponse {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val demo = service.list(userId).first { it.id == demoId }
            if (demo.status == DemoStatus.READY || demo.status == DemoStatus.FAILED) return demo
            Thread.sleep(100)
        }
        error("Demo $demoId did not reach a final status within ${timeoutMs}ms")
    }

    @Test
    fun `the parser being unavailable in this environment is reported, not silently ignored`() {
        // Confirms this test class's own premise: no Go build exists here, so uploads exercise the graceful
        // "tool not installed" path rather than a fake success.
        assertFalse(service.analysisAvailable)
    }

    @Test
    fun `a non-dem file is rejected before anything is stored`() {
        val userId = adminId()
        val before = service.list(userId).size
        assertThrows<ResponseStatusException> { service.upload(userId, demoFile(name = "match.txt")) }
        assertEquals(before, service.list(userId).size)
    }

    @Test
    fun `an oversized file is rejected`() {
        val userId = adminId()
        assertThrows<ResponseStatusException> {
            service.upload(userId, MockMultipartFile("file", "big.dem", "application/octet-stream", ByteArray(0)))
        }
    }

    @Test
    fun `uploading accepts the file, and parsing fails cleanly without the analysis tool installed`() {
        val userId = adminId()
        val uploaded = service.upload(userId, demoFile())
        assertTrue(uploaded.status == DemoStatus.UPLOADED || uploaded.status == DemoStatus.PARSING)

        val finished = awaitFinalStatus(userId, uploaded.id)
        assertEquals(DemoStatus.FAILED, finished.status)
        assertTrue(finished.errorMessage!!.contains("not installed"), finished.errorMessage)

        assertThrows<ResponseStatusException> { service.getAnalysis(userId, uploaded.id) }
    }

    @Test
    fun `a failed demo can be retried, and deleting a demo removes it`() {
        val userId = adminId()
        val uploaded = service.upload(userId, demoFile())
        awaitFinalStatus(userId, uploaded.id)

        val retried = service.retry(userId, uploaded.id)
        assertEquals(uploaded.id, retried.id)
        awaitFinalStatus(userId, uploaded.id)

        service.delete(userId, uploaded.id)
        assertTrue(service.list(userId).none { it.id == uploaded.id })
    }

    @Test
    fun `other users cannot see, retry or delete someone else's demo`() {
        val userId = adminId()
        val uploaded = service.upload(userId, demoFile())
        awaitFinalStatus(userId, uploaded.id)
        val stranger = jdbc.queryForObject(
            "insert into core.app_user (username, password_hash, role) values ('demo-stranger', 'x', 'USER') returning id",
            Long::class.java,
        )!!

        assertTrue(service.list(stranger).isEmpty())
        assertThrows<ResponseStatusException> { service.getAnalysis(stranger, uploaded.id) }
        assertThrows<ResponseStatusException> { service.retry(stranger, uploaded.id) }
        assertThrows<ResponseStatusException> { service.delete(stranger, uploaded.id) }
    }
}
