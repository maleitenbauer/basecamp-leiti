package com.markus.basecamp.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

/**
 * The contract for the weekly report, for every module — like [Notifier] for notifications. A module adds a section
 * to the report by exposing a Spring bean that implements [WeeklyReportContributor]; core collects all of them, and
 * the frontend renders any section it gets, so a new module needs no change here or in the frontend.
 *
 * A contributor looks only at its own module's data for the given user and week, and returns null when the module
 * has nothing to say (never used, or empty that week) so the report doesn't fill up with blank sections.
 */
interface WeeklyReportContributor {

    /** Position in the report; lower comes first. Leave gaps (10, 20, 30…) so later modules can slot in. */
    val order: Int

    fun section(userId: Long, week: ReportWeek): ReportSection?
}

/**
 * The Monday-to-Sunday week a report covers. [today] is the user's today (as their browser sees it), so a week that
 * is still going on is only judged on the days that have actually happened.
 */
data class ReportWeek(val start: LocalDate, val today: LocalDate, val zone: ZoneId) {

    val end: LocalDate get() = start.plusDays(6)

    /** The week isn't over yet: today is on or before its Sunday. */
    val inProgress: Boolean get() = !today.isAfter(end)

    /** All seven days, including ones that haven't happened yet. */
    val allDays: List<LocalDate> get() = (0L..6L).map { start.plusDays(it) }

    /** The days that have happened so far (the whole week once it's over). */
    val days: List<LocalDate> get() = allDays.filter { !it.isAfter(today) }

    /** The first and last instant of the week in the user's time zone, for timestamps rather than dates. */
    val startInstant: Instant get() = start.atStartOfDay(zone).toInstant()
    val endInstant: Instant get() = end.plusDays(1).atStartOfDay(zone).toInstant().minusNanos(1)
}

/**
 * One module's part of the report. [headline] is the one-line summary (also what the dashboard tile shows).
 * [score] is an optional 0..1 "how did it go" used to pick the tone of the overall message; leave it null when
 * there's no natural measure of success (a count of todos done isn't good or bad by itself).
 */
data class ReportSection(
    val id: String,
    val title: String,
    /** The module's id in the frontend registry, for the icon. */
    val moduleId: String,
    val headline: String,
    val score: Double? = null,
    val stats: List<ReportStat> = emptyList(),
    val bars: ReportBars? = null,
    val highlights: List<String> = emptyList(),
)

data class ReportStat(val label: String, val value: String, val detail: String? = null)

/** A small bar chart, one bar per day. [goal] draws a line across it (a calorie limit, for example). */
data class ReportBars(val title: String, val bars: List<ReportBar>, val goal: Double? = null)

data class ReportBar(val label: String, val value: Double, val display: String, val future: Boolean = false)

/** Small helpers so every module's section reads the same way. */
object ReportSupport {

    fun dayLabel(day: LocalDate): String = day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)

    fun percent(done: Int, total: Int): Int = if (total == 0) 0 else Math.round(done * 100.0 / total).toInt()

    /** "45 min", "2 h", "2 h 5 min". */
    fun minutes(total: Int): String = when {
        total < 60 -> "$total min"
        total % 60 == 0 -> "${total / 60} h"
        else -> "${total / 60} h ${total % 60} min"
    }

    /** One bar for each of the seven days; days that haven't happened yet are empty and flagged as such. */
    fun dayBars(week: ReportWeek, valueOf: (LocalDate) -> Double, display: (Double) -> String): List<ReportBar> =
        week.allDays.map { day ->
            if (day.isAfter(week.today)) {
                ReportBar(dayLabel(day), 0.0, "", future = true)
            } else {
                val value = valueOf(day)
                ReportBar(dayLabel(day), value, display(value))
            }
        }

    /** At most [max] lines, with a "+ N more" for the rest. */
    fun capped(lines: List<String>, max: Int): List<String> =
        if (lines.size <= max) lines else lines.take(max) + "+ ${lines.size - max} more"
}
