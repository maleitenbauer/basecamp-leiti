package com.markus.basecamp.gaming.cs2

import com.markus.basecamp.core.ReportBars
import com.markus.basecamp.core.ReportSection
import com.markus.basecamp.core.ReportStat
import com.markus.basecamp.core.ReportSupport
import com.markus.basecamp.core.ReportWeek
import com.markus.basecamp.core.WeeklyReportContributor
import org.springframework.stereotype.Component
import java.util.Locale

/** The CS2 part of the weekly report: how much of the daily practice routine got done, and session reviews. */
@Component
class Cs2PracticeReportContributor(
    private val items: RoutineItemRepository,
    private val logs: RoutineLogRepository,
    private val reviews: SessionReviewRepository,
) : WeeklyReportContributor {

    override val order = 40

    override fun section(userId: Long, week: ReportWeek): ReportSection? {
        val active = items.findAllByUserIdOrderBySortOrderAscIdAsc(userId).filter { it.active }
        return Cs2PracticeReport.build(
            activeItemIds = active.mapNotNull { it.id }.toSet(),
            logs = logs.findAllByUserIdAndLogDateBetween(userId, week.start, week.end),
            reviews = reviews.findAllByUserIdAndPlayedOnBetween(userId, week.start, week.end),
            week = week,
        )
    }
}

/** Pure, so it can be tested without a database. */
object Cs2PracticeReport {

    /** Every active routine item is meant to be done every day, so the target is items × days so far. */
    fun build(activeItemIds: Set<Long>, logs: List<RoutineLog>, reviews: List<SessionReview>, week: ReportWeek): ReportSection? {
        val days = week.days
        if (days.isEmpty()) return null
        val thisWeek = logs.filter { it.logDate in days }
        if (activeItemIds.isEmpty() && thisWeek.isEmpty() && reviews.isEmpty()) return null

        val possible = activeItemIds.size * days.size
        val done = thisWeek.filter { it.itemId in activeItemIds }.distinctBy { it.itemId to it.logDate }.size
        val minutes = thisWeek.sumOf { it.minutes ?: 0 }
        val daysPracticed = thisWeek.map { it.logDate }.distinct().size

        val stats = buildList {
            if (possible > 0) add(ReportStat("Practice done", "$done of $possible", "items, every day"))
            add(ReportStat("Days practised", "$daysPracticed of ${days.size}"))
            if (minutes > 0) add(ReportStat("Time practised", ReportSupport.minutes(minutes)))
            if (reviews.isNotEmpty()) add(ReportStat("Session reviews", reviews.size.toString()))
        }

        // ratings are 1-5
        val highlights = if (reviews.isEmpty()) {
            emptyList()
        } else {
            fun avg(rating: (SessionReview) -> Int) = String.format(Locale.ENGLISH, "%.1f", reviews.map(rating).average())
            listOf("Session ratings (of 5): focus ${avg { it.focus }}, movement ${avg { it.movement }}, utility ${avg { it.utility }}")
        }

        return ReportSection(
            id = "cs2-practice",
            title = "CS2 practice",
            moduleId = "gaming",
            headline = when {
                possible > 0 -> "${ReportSupport.percent(done, possible)}% of practice items done"
                reviews.isNotEmpty() -> "${reviews.size} session ${if (reviews.size == 1) "review" else "reviews"}"
                else -> "No practice logged"
            },
            score = if (possible > 0) done.toDouble() / possible else null,
            stats = stats,
            bars = ReportBars(
                title = "Practice items done per day",
                bars = ReportSupport.dayBars(week, { day -> thisWeek.count { it.logDate == day }.toDouble() }) { it.toInt().toString() },
            ),
            highlights = highlights,
        )
    }
}
