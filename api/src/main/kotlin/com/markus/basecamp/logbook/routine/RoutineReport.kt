package com.markus.basecamp.logbook.routine

import com.markus.basecamp.core.ReportBars
import com.markus.basecamp.core.ReportSection
import com.markus.basecamp.core.ReportStat
import com.markus.basecamp.core.ReportSupport
import com.markus.basecamp.core.ReportWeek
import com.markus.basecamp.core.WeeklyReportContributor
import org.springframework.stereotype.Component
import java.time.LocalDate

/** The routines part of the weekly report: how much of what was scheduled got done. */
@Component
class RoutineReportContributor(
    private val routines: RoutineRepository,
    private val completions: RoutineCompletionRepository,
) : WeeklyReportContributor {

    override val order = 10

    override fun section(userId: Long, week: ReportWeek): ReportSection? {
        val all = routines.findAllByUserIdOrderByIdAsc(userId)
        if (all.isEmpty()) return null
        return RoutineReport.build(all, completions.findAllByUserIdAndDayBetween(userId, week.start, week.end), week)
    }
}

/** Pure, so it can be tested without a database. */
object RoutineReport {

    private const val MAX_LISTED = 4

    /** How one routine did: what was scheduled, how much of it got done, and how much was done beyond that. */
    private class Row(val name: String, val scheduled: Int, val done: Int, val extra: Int, val flexible: Boolean)

    fun build(routines: List<Routine>, completions: List<RoutineCompletion>, week: ReportWeek): ReportSection? {
        val days = week.days
        if (days.isEmpty()) return null

        val byRoutine = completions.filter { it.day in days }.groupBy { it.routineId }

        val rows = routines.mapNotNull { routine ->
            val id = routine.id ?: return@mapNotNull null
            val mine = byRoutine[id].orEmpty()
            val recurrence = routine.recurrence()
            if (recurrence != null) {
                // scheduled on the days it occurs; the schedule itself already ignores days before it started
                val due = days.filter { recurrence.occursOn(it) }
                val doneOnDue = due.count { day -> mine.any { it.day == day } }
                if (due.isEmpty() && mine.isEmpty()) null else Row(routine.name, due.size, doneOnDue, mine.size - doneOnDue, false)
            } else {
                // "N times a week": the target is for the whole week, but not for weeks before it existed
                if (routine.createdAt.isAfter(week.endInstant) && mine.isEmpty()) {
                    null
                } else {
                    val done = minOf(mine.size, routine.timesPerWeek)
                    Row(routine.name, routine.timesPerWeek, done, mine.size - done, true)
                }
            }
        }
        if (rows.isEmpty()) return null

        val scheduled = rows.sumOf { it.scheduled }
        val done = rows.sumOf { it.done }
        val minutes = byRoutine.values.flatten().sumOf { it.minutes ?: 0 }

        val bars = ReportBars(
            title = "Routines done per day",
            bars = ReportSupport.dayBars(week, { day -> completions.count { it.day == day }.toDouble() }) { it.toInt().toString() },
        )

        val stats = buildList {
            if (scheduled > 0) add(ReportStat("Completed", "$done of $scheduled"))
            if (minutes > 0) add(ReportStat("Time spent", ReportSupport.minutes(minutes)))
            perfectDays(routines, byRoutine, days)?.let { (perfect, withRoutines) ->
                add(ReportStat("Perfect days", "$perfect of $withRoutines", "every scheduled routine done"))
            }
        }

        val missed = rows
            .filter { it.scheduled > it.done }
            .map { if (it.flexible) "${it.name}: ${it.done} of ${it.scheduled} times" else "Missed ${it.name} ×${it.scheduled - it.done}" }
        val nailed = rows.filter { it.scheduled > 0 && it.done == it.scheduled }.map { it.name }
        val highlights = ReportSupport.capped(missed, MAX_LISTED) +
            listOfNotNull(if (nailed.isEmpty()) null else "Nailed it: ${nailed.take(3).joinToString(", ")}")

        if (scheduled == 0) {
            val total = rows.sumOf { it.extra }
            return ReportSection(
                id = "routines", title = "Routines", moduleId = "lifestyle",
                headline = if (total == 1) "1 routine done" else "$total routines done",
                stats = stats, bars = bars, highlights = highlights,
            )
        }
        return ReportSection(
            id = "routines",
            title = "Routines",
            moduleId = "lifestyle",
            headline = "${ReportSupport.percent(done, scheduled)}% of your routines done ($done of $scheduled)",
            score = done.toDouble() / scheduled,
            stats = stats,
            bars = bars,
            highlights = highlights,
        )
    }

    /** Days on which something was scheduled, and how many of them had all of it done. Null if no day had anything. */
    private fun perfectDays(
        routines: List<Routine>,
        byRoutine: Map<Long, List<RoutineCompletion>>,
        days: List<LocalDate>,
    ): Pair<Int, Int>? {
        val scheduledDays = days.mapNotNull { day ->
            val due = routines.filter { r -> r.recurrence()?.occursOn(day) == true }
            if (due.isEmpty()) null else day to due
        }
        if (scheduledDays.isEmpty()) return null
        val perfect = scheduledDays.count { (day, due) ->
            due.all { r -> r.id?.let { byRoutine[it] }.orEmpty().any { it.day == day } }
        }
        return perfect to scheduledDays.size
    }
}
