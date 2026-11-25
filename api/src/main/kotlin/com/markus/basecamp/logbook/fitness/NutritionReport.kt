package com.markus.basecamp.logbook.fitness

import com.markus.basecamp.core.ReportBars
import com.markus.basecamp.core.ReportSection
import com.markus.basecamp.core.ReportStat
import com.markus.basecamp.core.ReportSupport
import com.markus.basecamp.core.ReportWeek
import com.markus.basecamp.core.WeeklyReportContributor
import org.springframework.stereotype.Component
import java.time.LocalDate

/** The nutrition part of the weekly report: average intake and how it compared to the daily limit. */
@Component
class NutritionReportContributor(
    private val goals: NutritionGoalRepository,
    private val entries: MealEntryRepository,
) : WeeklyReportContributor {

    override val order = 30

    override fun section(userId: Long, week: ReportWeek): ReportSection? {
        val meals = entries.findAllByUserIdAndDayBetween(userId, week.start, week.end)
        if (meals.isEmpty()) return null
        return NutritionReport.build(meals, goals.findById(userId).map { it.dailyKcal }.orElse(null), week)
    }
}

/** Pure, so it can be tested without a database. */
object NutritionReport {

    private const val MAX_LISTED = 3

    /**
     * Only days with something logged count: a day with nothing logged is more likely forgotten than fasted, so it
     * would otherwise drag the average down. The limit is a ceiling, so "within" means at or under it.
     */
    fun build(meals: List<MealEntry>, goal: Int?, week: ReportWeek): ReportSection? {
        val perDay: Map<LocalDate, Int> = meals.groupBy { it.day }.mapValues { (_, entries) -> entries.sumOf { it.kcal } }
        if (perDay.isEmpty()) return null

        val logged = perDay.size
        val average = Math.round(perDay.values.sum().toDouble() / logged).toInt()
        val within = goal?.let { limit -> perDay.values.count { it <= limit } }
        val highest = perDay.maxBy { it.value }

        val stats = buildList {
            add(ReportStat("Average", "$average kcal", "per logged day"))
            add(ReportStat("Days logged", "$logged of ${week.days.size}"))
            if (goal != null) add(ReportStat("Daily limit", "$goal kcal"))
            add(ReportStat("Highest day", "${highest.value} kcal", ReportSupport.dayLabel(highest.key)))
        }

        val over = if (goal == null) emptyList() else perDay.entries.sortedBy { it.key }.filter { it.value > goal }
            .map { "Over the limit on ${ReportSupport.dayLabel(it.key)}: ${it.value} kcal (+${it.value - goal})" }

        return ReportSection(
            id = "nutrition",
            title = "Nutrition",
            moduleId = "lifestyle",
            headline = if (within != null) {
                "Within your limit on $within of $logged logged ${if (logged == 1) "day" else "days"}"
            } else {
                "Averaging $average kcal on $logged logged ${if (logged == 1) "day" else "days"}"
            },
            score = within?.let { it.toDouble() / logged },
            stats = stats,
            bars = ReportBars(
                title = "Calories per day",
                bars = ReportSupport.dayBars(week, { day -> (perDay[day] ?: 0).toDouble() }) { if (it == 0.0) "–" else it.toInt().toString() },
                goal = goal?.toDouble(),
            ),
            highlights = ReportSupport.capped(over, MAX_LISTED),
        )
    }
}
