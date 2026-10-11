package com.markus.basecamp.logbook.todos

import com.markus.basecamp.core.ReportStat
import com.markus.basecamp.core.ReportWeek
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/** 2026-03-02 is a Monday. */
class TodoReportTest {

    private val monday = LocalDate.of(2026, 3, 2)
    private val utcWeek = ReportWeek(monday, monday.plusDays(3), ZoneOffset.UTC) // up to Thursday

    private fun doneAt(title: String, instant: String) =
        Todo(1, title).also {
            it.done = true
            it.doneAt = Instant.parse(instant)
        }

    @Test
    fun `completed todos are counted, listed in the order they were done, and charted per day`() {
        val done = listOf(
            doneAt("Wednesday thing", "2026-03-04T09:00:00Z"),
            doneAt("First thing", "2026-03-02T10:00:00Z"),
            doneAt("Second thing", "2026-03-02T12:00:00Z"),
        )

        val section = TodoReport.build(done, added = 5, overdue = 2, week = utcWeek)!!

        assertEquals("3 todos completed", section.headline)
        assertEquals(listOf(ReportStat("Completed", "3"), ReportStat("Added", "5"), ReportStat("Overdue now", "2")), section.stats)
        assertEquals(listOf("Done: First thing", "Done: Second thing", "Done: Wednesday thing"), section.highlights)
        assertEquals(listOf(2.0, 0.0, 1.0), section.bars!!.bars.take(3).map { it.value })
        assertNull(section.score) // a count of todos isn't good or bad by itself
    }

    @Test
    fun `a todo finished late in the evening counts for the day it was in the user's own time zone`() {
        val vienna = ZoneId.of("Europe/Vienna") // UTC+1: 23:30Z on Monday is already Tuesday there
        val week = ReportWeek(monday, monday.plusDays(3), vienna)

        val section = TodoReport.build(listOf(doneAt("Late one", "2026-03-02T23:30:00Z")), 0, null, week)!!

        assertEquals(listOf(0.0, 1.0), section.bars!!.bars.take(2).map { it.value })
    }

    @Test
    fun `singular and none read naturally, and overdue is left out for a finished week`() {
        val one = TodoReport.build(listOf(doneAt("Only", "2026-03-02T10:00:00Z")), 0, null, utcWeek)!!
        assertEquals("1 todo completed", one.headline)
        assertTrue(one.stats.none { it.label == "Overdue now" })

        val none = TodoReport.build(emptyList(), added = 2, overdue = 0, week = utcWeek)!!
        assertEquals("No todos completed", none.headline)
    }

    @Test
    fun `a long list of finished todos is cut off`() {
        val done = (1..6).map { doneAt("Todo $it", "2026-03-02T0$it:00:00Z") }

        val section = TodoReport.build(done, 0, null, utcWeek)!!

        assertEquals(5, section.highlights.size)
        assertEquals("+ 2 more", section.highlights.last())
    }

    @Test
    fun `nothing done, added or overdue leaves the section out`() {
        assertNull(TodoReport.build(emptyList(), 0, null, utcWeek))
        assertNull(TodoReport.build(emptyList(), 0, 0, utcWeek))
    }

    @Test
    fun `being overdue alone is worth a section`() {
        val section = TodoReport.build(emptyList(), 0, 3, utcWeek)!!

        assertEquals("No todos completed", section.headline)
        assertTrue(section.stats.contains(ReportStat("Overdue now", "3")))
    }
}
