package com.markus.basecamp.gaming.cs2

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.web.server.ResponseStatusException
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.LocalDate
import org.junit.jupiter.api.assertThrows

/**
 * Runs the whole app against a real Postgres, so the V3 migration and Hibernate schema validation are
 * exercised too. Skipped automatically where Docker is unavailable (e.g. local Windows).
 */
@SpringBootTest(
    properties = [
        "basecamp.bootstrap-admin.username=admin",
        "basecamp.bootstrap-admin.password=correct-horse-battery",
    ],
)
@Testcontainers(disabledWithoutDocker = true)
class Cs2ImprovementIntegrationTests {

    companion object {
        @Container
        @ServiceConnection
        @JvmStatic
        val postgres = PostgreSQLContainer<Nothing>("postgres:17")
    }

    @Autowired
    lateinit var service: Cs2ImprovementService

    @Autowired
    lateinit var jdbc: JdbcTemplate

    private fun adminId(): Long =
        jdbc.queryForObject("select id from core.app_user where username = 'admin'", Long::class.java)!!

    @Test
    fun `first visit seeds the coach routine and principles exactly once`() {
        val userId = adminId()
        val day = LocalDate.of(2026, 1, 15)

        val first = service.routine(userId, day)
        assertEquals(Cs2Defaults.routine.size, first.items.size)
        assertTrue(first.items.none { it.done })
        assertEquals(0, first.streak)

        service.routine(userId, day)
        assertEquals(Cs2Defaults.routine.size, service.routine(userId, day).items.size)

        val principles = service.listPrinciples(userId)
        // other tests may add their own principles, so only check that the defaults are there once
        val defaultsFound = principles.count { p -> Cs2Defaults.principles.any { it.title == p.title && it.category == p.category } }
        assertEquals(Cs2Defaults.principles.size, defaultsFound)
        assertTrue(principles.first().pinned, "pinned principles come first")
    }

    @Test
    fun `checking items off builds a streak and can be undone`() {
        val userId = adminId()
        val itemId = service.routine(userId, LocalDate.of(2026, 2, 10)).items.first().id

        // three days in a row, then a gap, then a day that must not count
        listOf(LocalDate.of(2026, 2, 10), LocalDate.of(2026, 2, 11), LocalDate.of(2026, 2, 12), LocalDate.of(2026, 2, 14))
            .forEach { service.setDone(userId, itemId, it, SetDoneRequest(done = true, minutes = 20)) }

        val onThe12th = service.routine(userId, LocalDate.of(2026, 2, 12))
        assertEquals(3, onThe12th.streak)
        assertTrue(onThe12th.items.first { it.id == itemId }.done)
        assertEquals(20, onThe12th.items.first { it.id == itemId }.minutes)

        service.setDone(userId, itemId, LocalDate.of(2026, 2, 12), SetDoneRequest(done = false))
        val afterUndo = service.routine(userId, LocalDate.of(2026, 2, 12))
        assertFalse(afterUndo.items.first { it.id == itemId }.done)
        // the 12th is now empty, so the streak is measured from "yesterday": the 11th and the 10th
        assertEquals(2, afterUndo.streak)
    }

    @Test
    fun `reviews are averaged and principles can be pinned`() {
        val userId = adminId()
        service.createReview(userId, CreateReviewRequest(focus = 4, movement = 2, utility = 3, notes = "  held W too much "))
        service.createReview(userId, CreateReviewRequest(focus = 2, movement = 4, utility = 5))

        val reviews = service.listReviews(userId)
        assertTrue(reviews.reviews.size >= 2)
        assertNotNull(reviews.averages)

        val created = service.createPrinciple(userId, CreatePrincipleRequest(title = "Crosshair placement"))
        assertFalse(created.pinned)
        assertTrue(service.updatePrinciple(userId, created.id, UpdatePrincipleRequest(pinned = true)).pinned)
    }

    @Test
    fun `other users cannot touch someone else's rows`() {
        val userId = adminId()
        val itemId = service.routine(userId, LocalDate.of(2026, 3, 1)).items.first().id
        val stranger = jdbc.queryForObject(
            "insert into core.app_user (username, password_hash, role) values ('stranger', 'x', 'USER') returning id",
            Long::class.java,
        )!!

        assertThrows<ResponseStatusException> { service.deleteItem(stranger, itemId) }
        assertThrows<ResponseStatusException> {
            service.setDone(stranger, itemId, LocalDate.of(2026, 3, 1), SetDoneRequest(done = true))
        }
    }
}
