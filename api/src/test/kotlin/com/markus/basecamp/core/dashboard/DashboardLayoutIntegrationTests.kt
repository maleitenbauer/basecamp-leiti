package com.markus.basecamp.core.dashboard

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.web.server.ResponseStatusException
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

/**
 * Runs the whole app against a real Postgres, so the V13 migration and Hibernate schema validation are exercised
 * too. Skipped automatically where Docker is unavailable (e.g. local Windows).
 */
@SpringBootTest(
    properties = [
        "basecamp.bootstrap-admin.username=admin",
        "basecamp.bootstrap-admin.password=correct-horse-battery",
        "basecamp.scheduling.enabled=false",
    ],
)
@Testcontainers(disabledWithoutDocker = true)
class DashboardLayoutIntegrationTests {

    companion object {
        @Container
        @ServiceConnection
        @JvmStatic
        val postgres = PostgreSQLContainer<Nothing>("postgres:17")
    }

    @Autowired
    lateinit var dashboard: DashboardLayoutService

    @Autowired
    lateinit var jdbc: JdbcTemplate

    private fun newUser(name: String): Long = jdbc.queryForObject(
        "insert into core.app_user (username, password_hash, role) values (?, 'x', 'USER') returning id",
        Long::class.java,
        name,
    )!!

    @Test
    fun `a user who never changed anything gets an empty layout, meaning all defaults`() {
        val userId = newUser("layout-fresh")

        assertEquals(DashboardLayoutDto(), dashboard.get(userId))
    }

    @Test
    fun `a layout is saved, returned, and replaced by the next save`() {
        val userId = newUser("layout-saver")

        dashboard.save(userId, DashboardLayoutDto(order = listOf("b", "a", "c"), hidden = listOf("c")))
        assertEquals(DashboardLayoutDto(order = listOf("b", "a", "c"), hidden = listOf("c")), dashboard.get(userId))

        dashboard.save(userId, DashboardLayoutDto(order = listOf("a", "b", "c"), hidden = emptyList()))
        assertEquals(DashboardLayoutDto(order = listOf("a", "b", "c"), hidden = emptyList()), dashboard.get(userId))
    }

    @Test
    fun `ids from modules added later are stored as they are, without the server knowing them`() {
        val userId = newUser("layout-new-module")

        val saved = dashboard.save(userId, DashboardLayoutDto(order = listOf("lifestyle.routine", "gaming.cs2.practice", "brand-new.module")))

        assertEquals(listOf("lifestyle.routine", "gaming.cs2.practice", "brand-new.module"), saved.order)
    }

    @Test
    fun `duplicates and stray whitespace are cleaned up, and the clean version is what comes back`() {
        val userId = newUser("layout-clean")

        val saved = dashboard.save(userId, DashboardLayoutDto(order = listOf(" a ", "b", "a"), hidden = listOf("b", "b")))

        assertEquals(DashboardLayoutDto(order = listOf("a", "b"), hidden = listOf("b")), saved)
        assertEquals(saved, dashboard.get(userId))
    }

    @Test
    fun `malformed ids and absurd sizes are rejected and nothing is stored`() {
        val userId = newUser("layout-invalid")
        dashboard.save(userId, DashboardLayoutDto(order = listOf("keep")))

        listOf(
            DashboardLayoutDto(order = listOf("has space")),
            DashboardLayoutDto(order = listOf("")),
            DashboardLayoutDto(order = listOf("<script>")),
            DashboardLayoutDto(hidden = listOf("x".repeat(101))),
            DashboardLayoutDto(order = List(201) { "w$it" }),
        ).forEach { bad ->
            assertEquals(400, assertThrows<ResponseStatusException> { dashboard.save(userId, bad) }.statusCode.value())
        }
        assertEquals(DashboardLayoutDto(order = listOf("keep")), dashboard.get(userId))
    }

    @Test
    fun `each user has their own layout`() {
        val first = newUser("layout-first")
        val second = newUser("layout-second")

        dashboard.save(first, DashboardLayoutDto(order = listOf("x"), hidden = listOf("y")))

        assertEquals(DashboardLayoutDto(), dashboard.get(second))
    }
}
