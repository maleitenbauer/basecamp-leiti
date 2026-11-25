package com.markus.basecamp.core.report

import com.markus.basecamp.core.ReportBar
import com.markus.basecamp.core.ReportSupport
import com.markus.basecamp.core.ReportWeek
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/** 2026-03-02 is a Monday. */
class ReportWeekTest {

    private val monday = LocalDate.of(2026, 3, 2)

    @Test
    fun `a week in progress only counts the days that have happened`() {
        val week = ReportWeek(monday, today = monday.plusDays(2), zone = ZoneOffset.UTC) // Wednesday

        assertTrue(week.inProgress)
        assertEquals(listOf(monday, monday.plusDays(1), monday.plusDays(2)), week.days)
        assertEquals(7, week.allDays.size)
        assertEquals(monday.plusDays(6), week.end)
    }

    @Test
    fun `a finished week has all seven days`() {
        val week = ReportWeek(monday, today = monday.plusDays(9), zone = ZoneOffset.UTC)

        assertFalse(week.inProgress)
        assertEquals(7, week.days.size)
    }

    @Test
    fun `Sunday still counts as in progress, because the day isn't over`() {
        assertTrue(ReportWeek(monday, today = monday.plusDays(6), zone = ZoneOffset.UTC).inProgress)
        assertFalse(ReportWeek(monday, today = monday.plusDays(7), zone = ZoneOffset.UTC).inProgress)
    }

    @Test
    fun `the instants cover the week in the user's own time zone`() {
        val vienna = ZoneId.of("Europe/Vienna") // UTC+1 in early March
        val week = ReportWeek(monday, today = monday, zone = vienna)

        assertEquals("2026-03-01T23:00:00Z", week.startInstant.toString()) // Monday 00:00 in Vienna
        assertEquals("2026-03-08T22:59:59.999999999Z", week.endInstant.toString()) // end of Sunday in Vienna
    }

    @Test
    fun `day bars cover the whole week, with days yet to come empty and flagged`() {
        val week = ReportWeek(monday, today = monday.plusDays(1), zone = ZoneOffset.UTC) // Tuesday

        val bars = ReportSupport.dayBars(week, { day -> if (day == monday) 3.0 else 1.0 }) { it.toInt().toString() }

        assertEquals(listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"), bars.map { it.label })
        assertEquals(ReportBar("Mon", 3.0, "3"), bars[0])
        assertEquals(ReportBar("Tue", 1.0, "1"), bars[1])
        assertEquals(ReportBar("Wed", 0.0, "", future = true), bars[2])
        assertTrue(bars.drop(2).all { it.future })
    }

    @Test
    fun `formatting helpers`() {
        assertEquals("45 min", ReportSupport.minutes(45))
        assertEquals("2 h", ReportSupport.minutes(120))
        assertEquals("2 h 5 min", ReportSupport.minutes(125))
        assertEquals(67, ReportSupport.percent(2, 3))
        assertEquals(0, ReportSupport.percent(0, 0))
        assertEquals(listOf("a", "b", "+ 1 more"), ReportSupport.capped(listOf("a", "b", "c"), 2))
        assertEquals(listOf("a", "b"), ReportSupport.capped(listOf("a", "b"), 2))
    }
}
