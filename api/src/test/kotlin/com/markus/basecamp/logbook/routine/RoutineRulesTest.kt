package com.markus.basecamp.logbook.routine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalTime

class RoutineRulesTest {

    private fun status(
        name: String,
        scheduleType: RoutineScheduleType = RoutineScheduleType.RECURRING,
        timesPerWeek: Int = 1,
        targetMinutes: Int? = null,
        doneDays: Int = 0,
        overdueSince: LocalDate? = null,
    ) = RoutineStatusResponse(
        id = 1,
        name = name,
        note = null,
        scheduleType = scheduleType,
        timesPerWeek = timesPerWeek,
        targetMinutes = targetMinutes,
        carryOver = overdueSince != null,
        summary = "",
        recurrence = null,
        state = RoutineState.OPEN,
        open = true,
        doneToday = false,
        minutesToday = null,
        doneDaysThisWeek = List(doneDays) { LocalDate.of(2026, 3, 2).plusDays(it.toLong()) },
        dueToday = true,
        nextDue = null,
        overdueSince = overdueSince,
    )

    @Test
    fun `weeks start on Monday`() {
        assertEquals(LocalDate.of(2026, 3, 2), RoutineRules.weekStart(LocalDate.of(2026, 3, 2))) // Monday itself
        assertEquals(LocalDate.of(2026, 3, 2), RoutineRules.weekStart(LocalDate.of(2026, 3, 4))) // Wednesday
        assertEquals(LocalDate.of(2026, 3, 2), RoutineRules.weekStart(LocalDate.of(2026, 3, 8))) // Sunday
        assertEquals(LocalDate.of(2026, 3, 9), RoutineRules.weekStart(LocalDate.of(2026, 3, 9))) // next Monday
    }

    @Test
    fun `a times-per-week routine stays open until its target is met, and is not re-asked on the day it was done`() {
        assertTrue(RoutineRules.isOpenTimesPerWeek(3, doneToday = false, doneThisWeek = 2))
        assertFalse(RoutineRules.isOpenTimesPerWeek(3, doneToday = false, doneThisWeek = 3))
        assertFalse(RoutineRules.isOpenTimesPerWeek(3, doneToday = true, doneThisWeek = 1))
        assertTrue(RoutineRules.isOpenTimesPerWeek(1, doneToday = false, doneThisWeek = 0))
    }

    @Test
    fun `the reminder is due once per local day, at or after the chosen time`() {
        val day = LocalDate.of(2026, 3, 2)
        val at = LocalTime.of(16, 0)
        assertFalse(RoutineRules.isDue(day, LocalTime.of(15, 59), at, null))
        assertTrue(RoutineRules.isDue(day, LocalTime.of(16, 0), at, null))
        assertTrue(RoutineRules.isDue(day, LocalTime.of(21, 30), at, day.minusDays(1))) // e.g. server was down at 16:00
        assertFalse(RoutineRules.isDue(day, LocalTime.of(16, 1), at, day))
    }

    @Test
    fun `the digest lists what is open, with time, weekly progress and how overdue it is`() {
        val digest = RoutineRules.digest(
            listOf(
                status("Meditation", targetMinutes = 10),
                status("Gym", scheduleType = RoutineScheduleType.TIMES_PER_WEEK, timesPerWeek = 3, doneDays = 1),
                status("Pay rent", overdueSince = LocalDate.of(2026, 3, 1)),
            ),
        )!!

        assertEquals("Routines: 3 still open", digest.title)
        assertEquals("Meditation · 10 min\nGym · 1/3 this week\nPay rent · overdue since 1 Mar", digest.body)
    }

    @Test
    fun `a long digest is cut off with a count of the rest, and nothing open means no digest`() {
        val digest = RoutineRules.digest((1..6).map { status("R$it") })!!

        assertEquals("R1\nR2\nR3\nR4\n+ 2 more", digest.body)
        assertNull(RoutineRules.digest(emptyList()))
    }
}
