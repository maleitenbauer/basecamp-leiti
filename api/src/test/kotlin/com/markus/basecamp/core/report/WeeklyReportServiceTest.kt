package com.markus.basecamp.core.report

import com.markus.basecamp.core.Notifier
import com.markus.basecamp.core.ReportSection
import com.markus.basecamp.core.ReportWeek
import com.markus.basecamp.core.WeeklyReportContributor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.web.server.ResponseStatusException
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.TemporalAdjusters

class WeeklyReportServiceTest {

    private val notifier = object : Notifier {
        override fun send(userId: Long, title: String, body: String?, url: String?, category: String) = Unit
        override fun zoneOf(userId: Long): ZoneId = ZoneOffset.UTC
        override fun setZone(userId: Long, zone: ZoneId) = Unit
    }

    private val today: LocalDate = LocalDate.now(ZoneOffset.UTC)
    private val thisMonday: LocalDate = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    private fun section(id: String, score: Double? = null) =
        ReportSection(id = id, title = id, moduleId = "m", headline = "headline of $id", score = score)

    private fun contributor(position: Int, section: ReportSection?) = object : WeeklyReportContributor {
        override val order = position
        override fun section(userId: Long, week: ReportWeek) = section
    }

    private fun service(vararg contributors: WeeklyReportContributor) = WeeklyReportService(contributors.toList(), notifier)

    @Test
    fun `sections come in contributor order, and modules with nothing to say are left out`() {
        val report = service(
            contributor(30, section("c")),
            contributor(10, section("a")),
            contributor(20, null),
        ).weekly(1, null, today)

        assertEquals(listOf("a", "c"), report.sections.map { it.id })
    }

    @Test
    fun `the overall score averages only the sections that have one`() {
        val scored = service(
            contributor(10, section("a", 1.0)),
            contributor(20, section("b", null)),
            contributor(30, section("c", 0.5)),
        ).weekly(1, null, today)
        assertEquals(0.75, scored.score)

        assertNull(service(contributor(10, section("a"))).weekly(1, null, today).score)
        assertNull(service().weekly(1, null, today).score)
    }

    @Test
    fun `a module that fails is skipped instead of taking the report down`() {
        val broken = object : WeeklyReportContributor {
            override val order = 5
            override fun section(userId: Long, week: ReportWeek): ReportSection = error("boom")
        }

        val report = service(broken, contributor(10, section("ok"))).weekly(1, null, today)

        assertEquals(listOf("ok"), report.sections.map { it.id })
    }

    @Test
    fun `any day in a week gives that Monday-to-Sunday week, and the current week is in progress`() {
        val wednesday = thisMonday.plusDays(2)

        val report = service().weekly(1, wednesday, today)

        assertEquals(thisMonday, report.weekStart)
        assertEquals(thisMonday.plusDays(6), report.weekEnd)
        assertTrue(report.inProgress)
        assertEquals(thisMonday, service().weekly(1, null, today).weekStart) // default: the current week
    }

    @Test
    fun `an earlier week is finished`() {
        val report = service().weekly(1, thisMonday.minusWeeks(2), today)

        assertEquals(thisMonday.minusWeeks(2), report.weekStart)
        assertFalse(report.inProgress)
    }

    @Test
    fun `the week and the days that count are handed to the contributors`() {
        var seen: ReportWeek? = null
        val spy = object : WeeklyReportContributor {
            override val order = 1
            override fun section(userId: Long, week: ReportWeek): ReportSection? {
                seen = week
                return null
            }
        }

        service(spy).weekly(1, thisMonday.minusWeeks(1), today)

        val week = seen!!
        assertEquals(thisMonday.minusWeeks(1), week.start)
        assertEquals(7, week.days.size) // a finished week has all seven days
        assertEquals(today, week.today)
    }

    @Test
    fun `a week in the future, an absurdly old one, and a wrong today are rejected`() {
        fun status(block: () -> Unit) = assertThrows<ResponseStatusException> { block() }.statusCode.value()

        assertEquals(400, status { service().weekly(1, thisMonday.plusWeeks(1), today) })
        assertEquals(400, status { service().weekly(1, LocalDate.of(1999, 1, 1), today) })
        assertEquals(400, status { service().weekly(1, null, today.plusDays(5)) })
    }
}
