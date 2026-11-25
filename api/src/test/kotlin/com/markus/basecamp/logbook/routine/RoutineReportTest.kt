package com.markus.basecamp.logbook.routine

import com.markus.basecamp.core.ReportStat
import com.markus.basecamp.core.ReportWeek
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** 2026-03-02 is a Monday. */
class RoutineReportTest {

    private val monday = LocalDate.of(2026, 3, 2)
    private val longAgo = Instant.parse("2026-01-01T00:00:00Z")

    private fun week(today: LocalDate) = ReportWeek(monday, today, ZoneOffset.UTC)
    private val thursday = monday.plusDays(3)
    private val nextMonday = monday.plusDays(7) // the week is over

    private fun daily(id: Long, name: String, from: LocalDate = LocalDate.of(2026, 1, 1)) =
        Routine(1, name).also {
            it.id = id
            it.recurUnit = RecurUnit.DAY
            it.startDate = from
        }

    private fun timesPerWeek(id: Long, name: String, times: Int) =
        Routine(1, name).also {
            it.id = id
            it.scheduleType = RoutineScheduleType.TIMES_PER_WEEK
            it.timesPerWeek = times
            it.createdAt = longAgo
        }

    private fun done(routineId: Long, day: LocalDate, minutes: Int? = null) = RoutineCompletion(1, routineId, day, minutes)

    @Test
    fun `a daily routine is scored on the days that have happened, and the misses are listed`() {
        val meditation = daily(1, "Meditation")

        // Monday to Thursday so far: done on Mon, Tue and Thu
        val section = RoutineReport.build(
            listOf(meditation),
            listOf(done(1, monday), done(1, monday.plusDays(1)), done(1, thursday)),
            week(thursday),
        )!!

        assertEquals("75% of your routines done (3 of 4)", section.headline)
        assertEquals(0.75, section.score)
        assertEquals(ReportStat("Completed", "3 of 4"), section.stats.first())
        assertTrue(section.stats.contains(ReportStat("Perfect days", "3 of 4", "every scheduled routine done")))
        assertEquals(listOf("Missed Meditation ×1"), section.highlights)
    }

    @Test
    fun `a routine done every day of a finished week is nailed`() {
        val stretch = daily(1, "Stretch")
        val everyDay = (0L..6L).map { done(1, monday.plusDays(it)) }

        val section = RoutineReport.build(listOf(stretch), everyDay, week(nextMonday))!!

        assertEquals("100% of your routines done (7 of 7)", section.headline)
        assertEquals(1.0, section.score)
        assertEquals(listOf("Nailed it: Stretch"), section.highlights)
    }

    @Test
    fun `a times-a-week routine is judged against its weekly target and added to the total`() {
        val meditation = daily(1, "Meditation")
        val gym = timesPerWeek(2, "Gym", 3)
        val completions = (0L..3L).map { done(1, monday.plusDays(it)) } + done(2, monday) + done(2, monday.plusDays(2))

        val section = RoutineReport.build(listOf(meditation, gym), completions, week(thursday))!!

        // meditation 4 of 4, gym 2 of 3
        assertEquals("86% of your routines done (6 of 7)", section.headline)
        assertTrue(section.highlights.contains("Gym: 2 of 3 times"))
        assertTrue(section.highlights.any { it.startsWith("Nailed it: Meditation") })
    }

    @Test
    fun `doing a times-a-week routine more often than its target doesn't push past 100 percent`() {
        val gym = timesPerWeek(1, "Gym", 2)
        val four = (0L..3L).map { done(1, monday.plusDays(it)) }

        val section = RoutineReport.build(listOf(gym), four, week(nextMonday))!!

        assertEquals("100% of your routines done (2 of 2)", section.headline)
    }

    @Test
    fun `time spent is added up`() {
        val section = RoutineReport.build(
            listOf(daily(1, "Meditation")),
            listOf(done(1, monday, 10), done(1, monday.plusDays(1), 25)),
            week(monday.plusDays(1)),
        )!!

        assertTrue(section.stats.contains(ReportStat("Time spent", "35 min")))
    }

    @Test
    fun `the chart has a bar for each day, with the days still to come left empty`() {
        val section = RoutineReport.build(
            listOf(daily(1, "A"), daily(2, "B")),
            listOf(done(1, monday), done(2, monday), done(1, monday.plusDays(1))),
            week(monday.plusDays(1)),
        )!!

        val bars = section.bars!!.bars
        assertEquals(7, bars.size)
        assertEquals(listOf(2.0, 1.0), bars.take(2).map { it.value })
        assertTrue(bars.drop(2).all { it.future })
    }

    @Test
    fun `routines that didn't exist yet that week leave the section out`() {
        val later = daily(1, "Not started yet", from = monday.plusDays(30))
        val laterFlexible = timesPerWeek(2, "Created later", 3).also { it.createdAt = Instant.parse("2027-01-01T00:00:00Z") }

        assertNull(RoutineReport.build(listOf(later, laterFlexible), emptyList(), week(nextMonday)))
    }

    @Test
    fun `completions on days a routine wasn't scheduled are counted but don't affect the score`() {
        // only due on Fridays (2026-01-02 was one), so Mon-Wed has nothing scheduled
        val fridays = Routine(1, "Review").also {
            it.id = 1
            it.recurUnit = RecurUnit.WEEK
            it.recurWeekdays = RoutineRules.weekdaysToMask(listOf(DayOfWeek.FRIDAY))
            it.startDate = LocalDate.of(2026, 1, 2)
        }

        val section = RoutineReport.build(listOf(fridays), listOf(done(1, monday.plusDays(1))), week(monday.plusDays(2)))!!

        assertEquals("1 routine done", section.headline)
        assertNull(section.score)
        assertFalse(section.stats.any { it.label == "Completed" })
    }

    @Test
    fun `a long list of misses is cut off`() {
        val routines = (1L..6L).map { daily(it, "R$it") }

        val section = RoutineReport.build(routines, emptyList(), week(monday))!!

        assertEquals(listOf("Missed R1 ×1", "Missed R2 ×1", "Missed R3 ×1", "Missed R4 ×1", "+ 2 more"), section.highlights)
    }
}
