package com.markus.basecamp.core.report

import com.markus.basecamp.core.Notifier
import com.markus.basecamp.core.ReportSection
import com.markus.basecamp.core.ReportWeek
import com.markus.basecamp.core.WeeklyReportContributor
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

data class WeeklyReportResponse(
    val weekStart: LocalDate,
    val weekEnd: LocalDate,
    val today: LocalDate,
    /** The week isn't over yet, so the figures cover only the days that have happened. */
    val inProgress: Boolean,
    /** 0..1: the average of the sections that have a score; null when none do. Picks the tone of the welcome message. */
    val score: Double?,
    val sections: List<ReportSection>,
)

/** Assembles the weekly report from every module's [WeeklyReportContributor]. Knows nothing about the modules. */
@Service
@Transactional(readOnly = true)
class WeeklyReportService(
    private val contributors: List<WeeklyReportContributor>,
    private val notifier: Notifier,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * The report for the Monday-to-Sunday week containing [weekStart] (default: the current week). [today] is the
     * user's today as their browser sees it; it only needs to be plausible, since time zones can disagree by a day.
     */
    fun weekly(userId: Long, weekStart: LocalDate?, today: LocalDate?): WeeklyReportResponse {
        val zone = notifier.zoneOf(userId)
        val serverToday = LocalDate.now(zone)
        val userToday = today ?: serverToday
        if (ChronoUnit.DAYS.between(serverToday, userToday).let { it < -2 || it > 2 }) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "That date looks wrong")
        }

        val start = (weekStart ?: userToday).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        if (start.isBefore(EARLIEST)) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "That week is too far back")
        if (start.isAfter(userToday)) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "There's no report for a future week")

        val week = ReportWeek(start, userToday, zone)
        val sections = contributors.sortedBy { it.order }.mapNotNull { contributor ->
            // one module's problem must never take the whole report down
            try {
                contributor.section(userId, week)
            } catch (e: Exception) {
                log.warn("Weekly report section from {} failed: {}", contributor.javaClass.simpleName, e.message)
                null
            }
        }
        val scores = sections.mapNotNull { it.score }
        return WeeklyReportResponse(
            weekStart = week.start,
            weekEnd = week.end,
            today = userToday,
            inProgress = week.inProgress,
            score = if (scores.isEmpty()) null else scores.average(),
            sections = sections,
        )
    }

    private companion object {
        val EARLIEST: LocalDate = LocalDate.of(2000, 1, 3)
    }
}
