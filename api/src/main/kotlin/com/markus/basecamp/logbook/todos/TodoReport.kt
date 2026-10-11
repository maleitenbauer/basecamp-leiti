package com.markus.basecamp.logbook.todos

import com.markus.basecamp.core.ReportBars
import com.markus.basecamp.core.ReportSection
import com.markus.basecamp.core.ReportStat
import com.markus.basecamp.core.ReportSupport
import com.markus.basecamp.core.ReportWeek
import com.markus.basecamp.core.WeeklyReportContributor
import org.springframework.stereotype.Component

/** The todos part of the weekly report: what got finished, what was added, and what is overdue. */
@Component
class TodoReportContributor(private val todos: TodoRepository) : WeeklyReportContributor {

    override val order = 20

    override fun section(userId: Long, week: ReportWeek): ReportSection? {
        val done = todos.findAllByUserIdAndDoneTrueAndDoneAtBetween(userId, week.startInstant, week.endInstant)
        val added = todos.countByUserIdAndCreatedAtBetween(userId, week.startInstant, week.endInstant)
        // "overdue" only means something for the week that's happening now
        val overdue = if (week.inProgress) todos.findAllByUserIdAndDoneFalseAndDueDateLessThan(userId, week.today).size else null
        return TodoReport.build(done, added, overdue, week)
    }
}

/** Pure, so it can be tested without a database. */
object TodoReport {

    private const val MAX_LISTED = 4

    fun build(done: List<Todo>, added: Long, overdue: Int?, week: ReportWeek): ReportSection? {
        if (done.isEmpty() && added == 0L && (overdue ?: 0) == 0) return null

        val doneOn = done.filter { it.doneAt != null }.groupBy { it.doneAt!!.atZone(week.zone).toLocalDate() }

        val stats = buildList {
            add(ReportStat("Completed", done.size.toString()))
            add(ReportStat("Added", added.toString()))
            if (overdue != null) add(ReportStat("Overdue now", overdue.toString()))
        }

        val finished = done.sortedBy { it.doneAt }.map { "Done: ${it.title}" }

        return ReportSection(
            id = "todos",
            title = "Todos",
            moduleId = "lifestyle",
            headline = when (done.size) {
                0 -> "No todos completed"
                1 -> "1 todo completed"
                else -> "${done.size} todos completed"
            },
            stats = stats,
            bars = ReportBars(
                title = "Todos completed per day",
                bars = ReportSupport.dayBars(week, { day -> doneOn[day]?.size?.toDouble() ?: 0.0 }) { it.toInt().toString() },
            ),
            highlights = ReportSupport.capped(finished, MAX_LISTED),
        )
    }
}
