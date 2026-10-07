package com.markus.basecamp.logbook.routine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
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
import java.time.Instant
import java.time.LocalDate

/**
 * Runs the whole app against a real Postgres, so the V11/V12 migrations and Hibernate schema validation are
 * exercised too. Skipped automatically where Docker is unavailable (e.g. local Windows).
 *
 * Calendar facts (2026): Mar 2 is a Monday; Mar 1 a Sunday; Jan 1 a Thursday.
 */
@SpringBootTest(
    properties = [
        "basecamp.bootstrap-admin.username=admin",
        "basecamp.bootstrap-admin.password=correct-horse-battery",
        "basecamp.scheduling.enabled=false",
    ],
)
@Testcontainers(disabledWithoutDocker = true)
class RoutineIntegrationTests {

    companion object {
        @Container
        @ServiceConnection
        @JvmStatic
        val postgres = PostgreSQLContainer<Nothing>("postgres:17")

        private val START = LocalDate.of(2026, 1, 1)
        private val MONDAY = LocalDate.of(2026, 3, 2)
    }

    @Autowired
    lateinit var routines: RoutineService

    @Autowired
    lateinit var reminders: RoutineReminderService

    @Autowired
    lateinit var jdbc: JdbcTemplate

    private fun newUser(name: String): Long = jdbc.queryForObject(
        "insert into core.app_user (username, password_hash, role) values (?, 'x', 'USER') returning id",
        Long::class.java,
        name,
    )!!

    private fun scheduled(
        name: String,
        recurrence: RecurrenceRequest,
        carryOver: Boolean = false,
        targetMinutes: Int? = null,
        note: String? = null,
    ) = RoutineRequest(name = name, note = note, recurrence = recurrence, carryOver = carryOver, targetMinutes = targetMinutes)

    private fun daily(name: String, targetMinutes: Int? = null) =
        scheduled(name, RecurrenceRequest(unit = RecurUnit.DAY, startDate = START), targetMinutes = targetMinutes)

    private fun timesPerWeek(name: String, times: Int) =
        RoutineRequest(name = name, scheduleType = RoutineScheduleType.TIMES_PER_WEEK, timesPerWeek = times)

    private fun status(userId: Long, id: Long, day: LocalDate) =
        routines.overview(userId, day).routines.single { it.id == id }

    private fun reminderCount(userId: Long): Long = jdbc.queryForObject(
        "select count(*) from core.notification where user_id = ? and category = 'routine-reminder'",
        Long::class.java,
        userId,
    )!!

    private fun status400(block: () -> Unit) =
        assertEquals(400, assertThrows<ResponseStatusException> { block() }.statusCode.value())

    // ---- the original behaviour, now through the new model ----

    @Test
    fun `a daily routine is open until done, and undoing reopens it`() {
        val userId = newUser("daily-user")
        val id = routines.create(userId, daily("  Stretch  "))
        assertEquals("Stretch", status(userId, id, MONDAY).name)
        assertEquals("Every day", status(userId, id, MONDAY).summary)
        assertTrue(status(userId, id, MONDAY).open)
        assertEquals(1, routines.overview(userId, MONDAY).openCount)

        routines.markDone(userId, id, MONDAY, null)
        routines.markDone(userId, id, MONDAY, null) // doing it twice the same day is harmless
        assertTrue(status(userId, id, MONDAY).doneToday)
        assertEquals(RoutineState.DONE, status(userId, id, MONDAY).state)
        assertFalse(status(userId, id, MONDAY).open)
        assertEquals(listOf(MONDAY), status(userId, id, MONDAY).doneDaysThisWeek)
        assertTrue(status(userId, id, MONDAY.plusDays(1)).open) // the next day it's open again

        routines.undo(userId, id, MONDAY)
        routines.undo(userId, id, MONDAY) // and undoing twice is too
        assertTrue(status(userId, id, MONDAY).open)
    }

    @Test
    fun `an N-times-a-week routine stays open until the target is met, then reopens the next week`() {
        val userId = newUser("weekly-user")
        val id = routines.create(userId, timesPerWeek("Gym", 2))
        assertEquals("2 times a week, any days", status(userId, id, MONDAY).summary)
        assertNull(status(userId, id, MONDAY).recurrence)

        routines.markDone(userId, id, MONDAY, null)
        assertFalse(status(userId, id, MONDAY).open) // done today
        assertTrue(status(userId, id, MONDAY.plusDays(1)).open) // 1 of 2 so far

        routines.markDone(userId, id, MONDAY.plusDays(1), null)
        assertFalse(status(userId, id, MONDAY.plusDays(2)).open) // target met
        assertEquals(RoutineState.DONE, status(userId, id, MONDAY.plusDays(2)).state)
        assertEquals(2, status(userId, id, MONDAY.plusDays(6)).doneDaysThisWeek.size)

        assertTrue(status(userId, id, MONDAY.plusDays(7)).open) // new week
        assertTrue(status(userId, id, MONDAY.plusDays(7)).doneDaysThisWeek.isEmpty())
    }

    @Test
    fun `a timed routine defaults to its target minutes, which can be overridden, and untimed ones store none`() {
        val userId = newUser("timed-user")
        val meditation = routines.create(userId, daily("Meditation", targetMinutes = 10))
        val plain = routines.create(userId, daily("Vitamins"))

        routines.markDone(userId, meditation, MONDAY, null)
        assertEquals(10, status(userId, meditation, MONDAY).minutesToday)

        routines.markDone(userId, meditation, MONDAY, 25)
        assertEquals(25, status(userId, meditation, MONDAY).minutesToday)

        routines.markDone(userId, plain, MONDAY, 30)
        assertNull(status(userId, plain, MONDAY).minutesToday)
        assertTrue(status(userId, plain, MONDAY).doneToday)
    }

    @Test
    fun `a routine can be switched between schedule kinds, and deleted along with its history`() {
        val userId = newUser("edit-user")
        val id = routines.create(userId, daily("Read", targetMinutes = 20))
        routines.markDone(userId, id, MONDAY, null)

        routines.update(userId, id, RoutineRequest(name = "Read a book", note = " chapter a day ", scheduleType = RoutineScheduleType.TIMES_PER_WEEK, timesPerWeek = 5))
        val flexible = status(userId, id, MONDAY)
        assertEquals("Read a book", flexible.name)
        assertEquals("chapter a day", flexible.note)
        assertEquals(5, flexible.timesPerWeek)
        assertNull(flexible.targetMinutes) // a full update replaces the target, so it can be removed
        assertNull(flexible.recurrence)

        routines.update(userId, id, scheduled("Read a book", RecurrenceRequest(unit = RecurUnit.WEEK, weekdays = listOf(1, 3), startDate = START)))
        val scheduled = status(userId, id, MONDAY)
        assertEquals("Every week on Mon, Wed", scheduled.summary)
        assertNotNull(scheduled.recurrence)

        routines.delete(userId, id)
        assertTrue(routines.overview(userId, MONDAY).routines.isEmpty())
        assertEquals(0L, jdbc.queryForObject("select count(*) from logbook.routine_completion where routine_id = ?", Long::class.java, id))
    }

    @Test
    fun `users cannot see or touch each other's routines`() {
        val owner = newUser("routine-owner")
        val stranger = newUser("routine-stranger")
        val id = routines.create(owner, daily("Private"))

        assertTrue(routines.overview(stranger, MONDAY).routines.isEmpty())
        assertThrows<ResponseStatusException> { routines.markDone(stranger, id, MONDAY, null) }
        assertThrows<ResponseStatusException> { routines.undo(stranger, id, MONDAY) }
        assertThrows<ResponseStatusException> { routines.update(stranger, id, daily("x")) }
        assertThrows<ResponseStatusException> { routines.delete(stranger, id) }
    }

    // ---- calendar-style recurrence ----

    @Test
    fun `a monthly routine is only due on its day, and says when it is next due`() {
        val userId = newUser("monthly-user")
        val id = routines.create(
            userId,
            scheduled("Check the smoke alarms", RecurrenceRequest(unit = RecurUnit.MONTH, monthDay = 15, startDate = START)),
        )

        val due = status(userId, id, LocalDate.of(2026, 3, 15))
        assertTrue(due.dueToday)
        assertEquals(RoutineState.OPEN, due.state)
        assertEquals("Every month on day 15", due.summary)

        val before = status(userId, id, LocalDate.of(2026, 3, 14))
        assertFalse(before.open)
        assertEquals(RoutineState.NOT_DUE, before.state)
        assertEquals(LocalDate.of(2026, 3, 15), before.nextDue)

        val after = status(userId, id, LocalDate.of(2026, 3, 16))
        assertEquals(RoutineState.NOT_DUE, after.state)
        assertEquals(LocalDate.of(2026, 4, 15), after.nextDue)

        routines.markDone(userId, id, LocalDate.of(2026, 3, 15), null)
        assertEquals(RoutineState.DONE, status(userId, id, LocalDate.of(2026, 3, 15)).state)
    }

    @Test
    fun `without carry-over a missed occurrence lapses, with it the routine stays open until it is done`() {
        val userId = newUser("carry-user")
        val firstOfMonth = RecurrenceRequest(unit = RecurUnit.MONTH, monthDay = 1, startDate = START)
        val lapses = routines.create(userId, scheduled("Lapses", firstOfMonth))
        val carries = routines.create(userId, scheduled("Pay rent", firstOfMonth, carryOver = true))
        val fifth = LocalDate.of(2026, 3, 5)

        assertFalse(status(userId, lapses, fifth).open)
        val overdue = status(userId, carries, fifth)
        assertTrue(overdue.open)
        assertEquals(LocalDate.of(2026, 3, 1), overdue.overdueSince)
        assertEquals(1, routines.overview(userId, fifth).openCount)

        routines.markDone(userId, carries, LocalDate.of(2026, 3, 3), null) // done late, between the 1st and the 5th
        assertFalse(status(userId, carries, fifth).open)

        val nextMonth = status(userId, carries, LocalDate.of(2026, 4, 1))
        assertTrue(nextMonth.open) // a fresh occurrence
        assertNull(nextMonth.overdueSince) // due today, not overdue
    }

    @Test
    fun `an after-N-times end becomes a date, and the routine stops being due after it`() {
        val userId = newUser("ending-user")
        val id = routines.create(
            userId,
            scheduled(
                "Course",
                RecurrenceRequest(unit = RecurUnit.DAY, startDate = START, endType = RoutineEndType.AFTER_COUNT, endCount = 3),
            ),
        )

        assertTrue(status(userId, id, LocalDate.of(2026, 1, 3)).dueToday)
        val afterEnd = status(userId, id, LocalDate.of(2026, 1, 4))
        assertEquals(RoutineState.NOT_DUE, afterEnd.state)
        assertNull(afterEnd.nextDue)
        assertEquals("Every day, 3 times", afterEnd.summary)
        assertEquals(3, afterEnd.recurrence!!.endCount)
        assertEquals(RoutineEndType.AFTER_COUNT, afterEnd.recurrence!!.endType)

        val until = routines.create(
            userId,
            scheduled(
                "Until",
                RecurrenceRequest(unit = RecurUnit.DAY, startDate = START, endType = RoutineEndType.ON_DATE, endDate = LocalDate.of(2026, 1, 10)),
            ),
        )
        assertTrue(status(userId, until, LocalDate.of(2026, 1, 10)).dueToday)
        assertFalse(status(userId, until, LocalDate.of(2026, 1, 11)).dueToday)
    }

    @Test
    fun `whatever the request leaves out is taken from the start date, like a calendar`() {
        val userId = newUser("defaults-user")

        // Wednesday 4 March: weekly with no weekdays picked means Wednesdays
        val weekly = routines.create(userId, scheduled("Weekly", RecurrenceRequest(unit = RecurUnit.WEEK, startDate = LocalDate.of(2026, 3, 4))))
        assertEquals(listOf(3), status(userId, weekly, MONDAY).recurrence!!.weekdays)
        assertTrue(status(userId, weekly, LocalDate.of(2026, 3, 11)).dueToday)

        // Tuesday 10 March is the second Tuesday of the month
        val nth = routines.create(
            userId,
            scheduled("Nth", RecurrenceRequest(unit = RecurUnit.MONTH, monthMode = MonthMode.NTH_WEEKDAY, startDate = LocalDate.of(2026, 3, 10))),
        )
        val rule = status(userId, nth, MONDAY).recurrence!!
        assertEquals(2, rule.nth)
        assertEquals(2, rule.weekday)
        assertEquals("Every month on the second Tuesday", status(userId, nth, MONDAY).summary)
        assertTrue(status(userId, nth, LocalDate.of(2026, 4, 14)).dueToday)

        // 15 March: yearly means March 15th
        val yearly = routines.create(userId, scheduled("Yearly", RecurrenceRequest(unit = RecurUnit.YEAR, startDate = LocalDate.of(2026, 3, 15))))
        assertEquals("Every year on March 15", status(userId, yearly, MONDAY).summary)
        assertTrue(status(userId, yearly, LocalDate.of(2027, 3, 15)).dueToday)
    }

    @Test
    fun `an incomplete or impossible schedule is rejected`() {
        val userId = newUser("invalid-user")

        status400 { routines.create(userId, RoutineRequest(name = "No schedule")) }
        status400 { routines.create(userId, scheduled("Bad weekday", RecurrenceRequest(unit = RecurUnit.WEEK, weekdays = listOf(8), startDate = START))) }
        status400 {
            routines.create(userId, scheduled("No end date", RecurrenceRequest(unit = RecurUnit.DAY, startDate = START, endType = RoutineEndType.ON_DATE)))
        }
        status400 {
            routines.create(
                userId,
                scheduled(
                    "Ends before it starts",
                    RecurrenceRequest(unit = RecurUnit.DAY, startDate = START, endType = RoutineEndType.ON_DATE, endDate = START.minusDays(1)),
                ),
            )
        }
        status400 {
            routines.create(userId, scheduled("No count", RecurrenceRequest(unit = RecurUnit.DAY, startDate = START, endType = RoutineEndType.AFTER_COUNT)))
        }
        assertTrue(routines.overview(userId, MONDAY).routines.isEmpty()) // nothing half-saved
    }

    // ---- the reminder ----

    @Test
    fun `the reminder goes out once a day at the chosen local time, and only while something is open`() {
        val userId = newUser("reminder-user")
        val id = routines.create(userId, daily("Meditation", targetMinutes = 10))
        reminders.update(userId, UpdateRoutineReminderRequest(enabled = true, remindAt = "16:00", timezone = "Europe/Vienna"))

        // January, so Vienna is UTC+1: 16:00 local is 15:00Z
        reminders.runDue(Instant.parse("2090-01-15T14:59:00Z"))
        assertEquals(0, reminderCount(userId))

        reminders.runDue(Instant.parse("2090-01-15T15:00:00Z"))
        assertEquals(1, reminderCount(userId))

        reminders.runDue(Instant.parse("2090-01-15T18:00:00Z")) // later the same day: not again
        assertEquals(1, reminderCount(userId))

        reminders.runDue(Instant.parse("2090-01-16T15:00:00Z")) // next day, still not done
        assertEquals(2, reminderCount(userId))

        routines.markDone(userId, id, LocalDate.of(2090, 1, 17), null)
        reminders.runDue(Instant.parse("2090-01-17T15:00:00Z")) // done that day: nothing to remind about
        assertEquals(2, reminderCount(userId))
    }

    @Test
    fun `routines that are not scheduled for the day stay out of the reminder`() {
        val userId = newUser("quiet-user")
        // monthly on the 15th: on the 17th it is not due, so there is nothing to say
        routines.create(userId, scheduled("Monthly", RecurrenceRequest(unit = RecurUnit.MONTH, monthDay = 15, startDate = LocalDate.of(2090, 1, 1))))
        reminders.update(userId, UpdateRoutineReminderRequest(enabled = true, remindAt = "16:00", timezone = "UTC"))

        reminders.runDue(Instant.parse("2090-01-17T16:00:00Z"))
        assertEquals(0, reminderCount(userId))

        reminders.update(userId, UpdateRoutineReminderRequest(enabled = true, remindAt = "16:00", timezone = "UTC")) // re-arms today
        reminders.runDue(Instant.parse("2090-02-15T16:00:00Z")) // the 15th: due
        assertEquals(1, reminderCount(userId))
    }

    @Test
    fun `send now reports what it sent, a disabled reminder stays quiet, and settings default to 16 00`() {
        val userId = newUser("sendnow-user")
        assertEquals("16:00", reminders.get(userId).remindAt)
        assertEquals(RoutineSendNowResponse(false, "Nothing is open today."), reminders.sendNow(userId))

        routines.create(userId, scheduled("Journal", RecurrenceRequest(unit = RecurUnit.DAY, startDate = LocalDate.of(2000, 1, 1))))
        val sent = reminders.sendNow(userId)
        assertTrue(sent.sent)
        assertEquals("Routines: 1 still open", sent.message)
        assertEquals(1, reminderCount(userId))

        reminders.update(userId, UpdateRoutineReminderRequest(enabled = false, remindAt = "16:00"))
        reminders.runDue(Instant.parse("2090-02-01T23:00:00Z"))
        assertEquals(1, reminderCount(userId)) // unchanged: disabled
    }
}
