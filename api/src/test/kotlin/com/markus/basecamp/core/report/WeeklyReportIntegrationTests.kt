package com.markus.basecamp.core.report

import com.markus.basecamp.gaming.cs2.RoutineItem
import com.markus.basecamp.gaming.cs2.RoutineItemRepository
import com.markus.basecamp.gaming.cs2.RoutineLog
import com.markus.basecamp.gaming.cs2.RoutineLogRepository
import com.markus.basecamp.logbook.fitness.AddMealEntryRequest
import com.markus.basecamp.logbook.fitness.NutritionService
import com.markus.basecamp.logbook.fitness.SetNutritionGoalRequest
import com.markus.basecamp.logbook.routine.RecurUnit
import com.markus.basecamp.logbook.routine.RecurrenceRequest
import com.markus.basecamp.logbook.routine.RoutineRequest
import com.markus.basecamp.logbook.routine.RoutineService
import com.markus.basecamp.logbook.todos.Todo
import com.markus.basecamp.logbook.todos.TodoRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.jdbc.core.JdbcTemplate
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Runs the whole app against a real Postgres, so the report's date-range queries are exercised for real. Skipped
 * automatically where Docker is unavailable (e.g. local Windows). The week is 2026-03-02 to 2026-03-08, which is
 * long over by the time this runs, so it counts as a finished week of seven days.
 */
@SpringBootTest(
    properties = [
        "basecamp.bootstrap-admin.username=admin",
        "basecamp.bootstrap-admin.password=correct-horse-battery",
        "basecamp.scheduling.enabled=false",
    ],
)
@Testcontainers(disabledWithoutDocker = true)
class WeeklyReportIntegrationTests {

    companion object {
        @Container
        @ServiceConnection
        @JvmStatic
        val postgres = PostgreSQLContainer<Nothing>("postgres:17")

        private val MONDAY = LocalDate.of(2026, 3, 2)
    }

    @Autowired
    lateinit var report: WeeklyReportService

    @Autowired
    lateinit var routines: RoutineService

    @Autowired
    lateinit var nutrition: NutritionService

    @Autowired
    lateinit var todos: TodoRepository

    @Autowired
    lateinit var cs2Items: RoutineItemRepository

    @Autowired
    lateinit var cs2Logs: RoutineLogRepository

    @Autowired
    lateinit var jdbc: JdbcTemplate

    private fun newUser(name: String): Long = jdbc.queryForObject(
        "insert into core.app_user (username, password_hash, role) values (?, 'x', 'USER') returning id",
        Long::class.java,
        name,
    )!!

    private fun weekly(userId: Long) = report.weekly(userId, MONDAY, LocalDate.now(ZoneOffset.UTC))

    @Test
    fun `a user with nothing set up gets an empty report`() {
        val report = weekly(newUser("report-empty"))

        assertTrue(report.sections.isEmpty())
        assertEquals(null, report.score)
        assertEquals(MONDAY, report.weekStart)
        assertEquals(MONDAY.plusDays(6), report.weekEnd)
    }

    @Test
    fun `every module with something that week adds its section, in order, and the score averages theirs`() {
        val userId = newUser("report-full")

        // routines: a daily routine since January, done on two of the week's seven days
        val routineId = routines.create(
            userId,
            RoutineRequest(name = "Meditation", recurrence = RecurrenceRequest(unit = RecurUnit.DAY, startDate = LocalDate.of(2026, 1, 1))),
        )
        routines.markDone(userId, routineId, MONDAY, null)
        routines.markDone(userId, routineId, MONDAY.plusDays(1), null)

        // todos: one finished that Tuesday, one finished the week before (must not count)
        todos.save(Todo(userId, "This week").also { it.done = true; it.doneAt = Instant.parse("2026-03-03T10:00:00Z") })
        todos.save(Todo(userId, "Last week").also { it.done = true; it.doneAt = Instant.parse("2026-02-24T10:00:00Z") })

        // nutrition: a 2000 kcal limit, 1800 eaten on one day
        nutrition.setGoal(userId, SetNutritionGoalRequest(2000))
        nutrition.addEntry(userId, AddMealEntryRequest(day = MONDAY, kcal = 1800, name = "Dinner"))

        // CS2: one active practice item, done once
        val item = cs2Items.save(RoutineItem(userId, "Aim practice"))
        cs2Logs.save(RoutineLog(userId, item.id!!, MONDAY, 15))

        val report = weekly(userId)

        assertEquals(listOf("routines", "todos", "nutrition", "cs2-practice"), report.sections.map { it.id })
        val byId = report.sections.associateBy { it.id }
        assertEquals("29% of your routines done (2 of 7)", byId.getValue("routines").headline)
        assertEquals("1 todo completed", byId.getValue("todos").headline)
        assertEquals("Within your limit on 1 of 1 logged day", byId.getValue("nutrition").headline)
        assertEquals("14% of practice items done", byId.getValue("cs2-practice").headline)

        // the todos section has no score, so the overall one is the mean of the other three
        assertEquals((2.0 / 7 + 1.0 + 1.0 / 7) / 3, report.score!!, 1e-9)
    }

    @Test
    fun `each user's report only contains their own data`() {
        val busy = newUser("report-busy")
        val quiet = newUser("report-quiet")
        todos.save(Todo(busy, "Mine").also { it.done = true; it.doneAt = Instant.parse("2026-03-03T10:00:00Z") })

        assertEquals(listOf("todos"), weekly(busy).sections.map { it.id })
        assertTrue(weekly(quiet).sections.isEmpty())
    }
}
