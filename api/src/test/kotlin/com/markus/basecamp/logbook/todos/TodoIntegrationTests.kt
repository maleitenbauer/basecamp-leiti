package com.markus.basecamp.logbook.todos

import com.markus.basecamp.core.Notifier
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.web.server.ResponseStatusException
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Replaces real delivery so the test can see exactly what would have been sent. */
class RecordingNotifier : Notifier {
    class Sent(val userId: Long, val title: String, val body: String?, val url: String?)

    val sent = mutableListOf<Sent>()
    private val zones = mutableMapOf<Long, ZoneId>()

    override fun send(userId: Long, title: String, body: String?, url: String?, category: String) {
        sent += Sent(userId, title, body, url)
    }

    override fun zoneOf(userId: Long): ZoneId = zones[userId] ?: ZoneId.of("UTC")

    override fun setZone(userId: Long, zone: ZoneId) {
        zones[userId] = zone
    }
}

@TestConfiguration
class RecordingNotifierConfig {
    @Bean
    @Primary
    fun recordingNotifier() = RecordingNotifier()
}

/**
 * Runs the whole app against a real Postgres, so the V5 migration and Hibernate schema validation are exercised too.
 * Skipped automatically where Docker is unavailable (e.g. local Windows).
 */
@SpringBootTest(
    properties = [
        "basecamp.bootstrap-admin.username=admin",
        "basecamp.bootstrap-admin.password=correct-horse-battery",
        "basecamp.scheduling.enabled=false",
    ],
)
@Import(RecordingNotifierConfig::class)
@Testcontainers(disabledWithoutDocker = true)
class TodoIntegrationTests {

    companion object {
        @Container
        @ServiceConnection
        @JvmStatic
        val postgres = PostgreSQLContainer<Nothing>("postgres:17")
    }

    @Autowired
    lateinit var todos: TodoService

    @Autowired
    lateinit var reminders: TodoReminderService

    @Autowired
    lateinit var notifier: RecordingNotifier

    @Autowired
    lateinit var jdbc: JdbcTemplate

    private fun adminId(): Long =
        jdbc.queryForObject("select id from core.app_user where username = 'admin'", Long::class.java)!!

    @Test
    fun `todos can be created, noted, finished, reopened and deleted`() {
        val userId = adminId()
        val created = todos.create(userId, CreateTodoRequest(title = "  Pay rent  ", dueDate = LocalDate.of(2090, 1, 1), note = "  bank app  "))
        assertEquals("Pay rent", created.title)
        assertEquals("bank app", created.note)

        val finished = todos.update(userId, created.id, UpdateTodoRequest(done = true))
        assertTrue(finished.done)
        assertTrue(todos.list(userId).done.any { it.id == created.id })
        assertFalse(todos.list(userId).open.any { it.id == created.id })

        val reopened = todos.update(userId, created.id, UpdateTodoRequest(done = false, clearDueDate = true, note = ""))
        assertFalse(reopened.done)
        assertNull(reopened.dueDate)
        assertNull(reopened.note)

        todos.delete(userId, created.id)
        assertThrows<ResponseStatusException> { todos.update(userId, created.id, UpdateTodoRequest(title = "x")) }
    }

    @Test
    fun `other users cannot touch someone else's todos`() {
        val userId = adminId()
        val todo = todos.create(userId, CreateTodoRequest(title = "Private"))
        val stranger = jdbc.queryForObject(
            "insert into core.app_user (username, password_hash, role) values ('todo-stranger', 'x', 'USER') returning id",
            Long::class.java,
        )!!

        assertThrows<ResponseStatusException> { todos.update(stranger, todo.id, UpdateTodoRequest(done = true)) }
        assertThrows<ResponseStatusException> { todos.delete(stranger, todo.id) }
        assertTrue(todos.list(stranger).open.isEmpty())
    }

    @Test
    fun `the daily reminder lists due and overdue todos once per day at the chosen time`() {
        val userId = adminId()
        // reminders are evaluated in the user's time zone: Vienna is UTC+1 in January
        reminders.update(userId, UpdateReminderSettingsRequest(true, "08:00", true, true, "Europe/Vienna"))

        val today = LocalDate.of(2090, 1, 15)
        todos.create(userId, CreateTodoRequest(title = "Due today", dueDate = today))
        todos.create(userId, CreateTodoRequest(title = "Late", dueDate = today.minusDays(3)))
        todos.create(userId, CreateTodoRequest(title = "Later", dueDate = today.plusDays(5)))
        val finishedLate = todos.create(userId, CreateTodoRequest(title = "Finished late", dueDate = today.minusDays(1)))
        todos.update(userId, finishedLate.id, UpdateTodoRequest(done = true))

        val before = notifier.sent.size

        // 06:59 UTC is 07:59 in Vienna: too early
        reminders.runDue(Instant.parse("2090-01-15T06:59:00Z"))
        assertEquals(before, notifier.sent.size)

        // 07:00 UTC is 08:00 in Vienna: due
        reminders.runDue(Instant.parse("2090-01-15T07:00:00Z"))
        assertEquals(before + 1, notifier.sent.size)
        val message = notifier.sent.last()
        assertEquals("Todos: 1 due today, 1 overdue", message.title)
        assertTrue(message.body!!.contains("Overdue: Late"))
        assertTrue(message.body!!.contains("Today: Due today"))
        assertFalse(message.body!!.contains("Later"))
        assertFalse(message.body!!.contains("Finished late"))
        assertEquals("/logbook/todos", message.url)

        // the same day, later: not again
        reminders.runDue(Instant.parse("2090-01-15T15:00:00Z"))
        assertEquals(before + 1, notifier.sent.size)

        // next day: again. "Due today" and "Late" are both overdue now, and nothing is due on the 16th.
        reminders.runDue(Instant.parse("2090-01-16T07:30:00Z"))
        assertEquals(before + 2, notifier.sent.size)
        assertEquals("Todos: 2 overdue", notifier.sent.last().title)
    }

    @Test
    fun `send now respects the notify-about switches`() {
        val userId = adminId()
        // the other tests only use dates in 2090, so nothing is overdue relative to the real today
        val today = LocalDate.now(ZoneId.of("UTC"))
        val todo = todos.create(userId, CreateTodoRequest(title = "Due right now", dueDate = today))
        try {
            reminders.update(userId, UpdateReminderSettingsRequest(true, "08:00", notifyDueToday = false, notifyOverdue = true, timezone = "UTC"))
            assertFalse(reminders.sendNow(userId).sent, "due-today is switched off and nothing is overdue")

            reminders.update(userId, UpdateReminderSettingsRequest(true, "08:00", notifyDueToday = true, notifyOverdue = true, timezone = "UTC"))
            val result = reminders.sendNow(userId)
            assertTrue(result.sent)
            assertTrue(result.message.contains("1 due today"), result.message)
        } finally {
            // it is due today in the real calendar, so it would count as overdue in the tests that pretend it is 2090
            todos.delete(userId, todo.id)
        }
    }
}
