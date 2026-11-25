package com.markus.basecamp.logbook.fitness

import com.markus.basecamp.core.ReportBars
import com.markus.basecamp.core.ReportSection
import com.markus.basecamp.core.ReportStat
import com.markus.basecamp.core.ReportSupport
import com.markus.basecamp.core.ReportWeek
import com.markus.basecamp.core.WeeklyReportContributor
import org.springframework.stereotype.Component

/** The training part of the weekly report: how often, how long and how much was lifted. */
@Component
class TrainingReportContributor(private val training: TrainingService) : WeeklyReportContributor {

    override val order = 25

    override fun section(userId: Long, week: ReportWeek): ReportSection? =
        TrainingReport.build(training.workoutsBetween(userId, week.start, week.end), week)
}

/** Pure, so it can be tested without a database. No score: how often is enough is a decision for the routine, which has one. */
object TrainingReport {

    private const val MAX_LISTED = 4

    fun build(workouts: List<WorkoutSummary>, week: ReportWeek): ReportSection? {
        if (workouts.isEmpty()) return null

        val minutes = workouts.sumOf { it.minutes }
        val sets = workouts.sumOf { it.sets }
        val volume = Math.round(workouts.sumOf { it.volumeKg }).toInt()
        val perDay = workouts.groupBy { it.day }

        val stats = buildList {
            add(ReportStat("Workouts", workouts.size.toString()))
            add(ReportStat("Time trained", ReportSupport.minutes(minutes)))
            add(ReportStat("Sets", sets.toString()))
            if (volume > 0) add(ReportStat("Weight moved", "$volume kg", "reps × weight"))
        }

        return ReportSection(
            id = "training",
            title = "Training",
            moduleId = "lifestyle",
            headline = (if (workouts.size == 1) "1 workout" else "${workouts.size} workouts") + ", " + ReportSupport.minutes(minutes),
            stats = stats,
            bars = ReportBars(
                title = "Minutes trained per day",
                bars = ReportSupport.dayBars(week, { day -> perDay[day]?.sumOf { it.minutes }?.toDouble() ?: 0.0 }) {
                    if (it > 0) it.toInt().toString() else ""
                },
            ),
            highlights = ReportSupport.capped(
                workouts.map { "${it.name} on ${ReportSupport.dayLabel(it.day)}: ${ReportSupport.minutes(it.minutes)}, ${it.sets} sets" },
                MAX_LISTED,
            ),
        )
    }
}
