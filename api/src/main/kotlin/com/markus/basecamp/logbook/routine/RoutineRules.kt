package com.markus.basecamp.logbook.routine

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.min

/** Pure decisions and texts for routines and their reminder, kept free of Spring so they are easy to test. */
object RoutineRules {

    /** Weeks run Monday to Sunday. */
    fun weekStart(day: LocalDate): LocalDate = day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    /**
     * A "N times a week" routine stays open until the week's target is met, but once it's been done on the day itself
     * it is not asked about again that day.
     */
    fun isOpenTimesPerWeek(timesPerWeek: Int, doneToday: Boolean, doneThisWeek: Int): Boolean =
        !doneToday && doneThisWeek < timesPerWeek

    /** A reminder goes out once per local day, at or after the chosen time (also when the server was down at that time). */
    fun isDue(localDate: LocalDate, localTime: LocalTime, remindAt: LocalTime, lastSentOn: LocalDate?): Boolean =
        lastSentOn != localDate && !localTime.isBefore(remindAt)

    /** Which occurrence of its weekday a date is in its month: the 1st-7th is the 1st, 8th-14th the 2nd, and so on. */
    fun nthWeekdayOfMonth(date: LocalDate): Int = (date.dayOfMonth - 1) / 7 + 1

    /**
     * A calendar-style repeat rule. Every field is already resolved (defaults come from the start date when a routine
     * is saved), so [occursOn] is a plain yes/no for any date.
     *
     * Intervals count from the start: "every 2 weeks" means the start's week, then every second week after it.
     */
    data class Recurrence(
        val unit: RecurUnit,
        val interval: Int,
        val weekdays: Set<DayOfWeek>,
        val monthMode: MonthMode,
        val monthDay: Int,
        val nth: Int,
        val weekday: DayOfWeek,
        val yearMonth: Int,
        val start: LocalDate,
        val endOn: LocalDate?,
    ) {
        fun occursOn(date: LocalDate): Boolean {
            if (date.isBefore(start)) return false
            if (endOn != null && date.isAfter(endOn)) return false
            return when (unit) {
                RecurUnit.DAY -> ChronoUnit.DAYS.between(start, date) % interval == 0L
                RecurUnit.WEEK ->
                    ChronoUnit.WEEKS.between(weekStart(start), weekStart(date)) % interval == 0L && date.dayOfWeek in weekdays
                RecurUnit.MONTH ->
                    ChronoUnit.MONTHS.between(YearMonth.from(start), YearMonth.from(date)) % interval == 0L && monthMatches(date)
                RecurUnit.YEAR ->
                    (date.year - start.year) % interval == 0 &&
                        date.monthValue == yearMonth &&
                        date.dayOfMonth == min(monthDay, date.lengthOfMonth())
            }
        }

        private fun monthMatches(date: LocalDate): Boolean = when (monthMode) {
            MonthMode.DAY_OF_MONTH -> date.dayOfMonth == min(monthDay, date.lengthOfMonth())
            MonthMode.LAST_DAY -> date.dayOfMonth == date.lengthOfMonth()
            MonthMode.NTH_WEEKDAY ->
                date.dayOfWeek == weekday &&
                    if (nth == LAST) date.plusDays(7).month != date.month else nthWeekdayOfMonth(date) == nth
        }

        /** How far apart two occurrences can be at most, so a search never needs to look further than this. */
        private fun horizonDays(): Long = when (unit) {
            RecurUnit.DAY -> interval.toLong()
            RecurUnit.WEEK -> 7L * interval
            RecurUnit.MONTH -> 31L * interval + 31
            RecurUnit.YEAR -> 366L * interval + 366
        }.coerceAtMost(MAX_HORIZON_DAYS)

        /** The first occurrence on or after [from], or null once the rule has ended. */
        fun nextOnOrAfter(from: LocalDate): LocalDate? {
            var day = if (from.isBefore(start)) start else from
            val last = day.plusDays(horizonDays())
            while (!day.isAfter(last)) {
                if (occursOn(day)) return day
                day = day.plusDays(1)
            }
            return null
        }

        /** The latest occurrence on or before [to], or null if there hasn't been one yet. */
        fun lastOnOrBefore(to: LocalDate): LocalDate? {
            val earliest = maxOf(start, to.minusDays(horizonDays()))
            var day = to
            while (!day.isBefore(earliest)) {
                if (occursOn(day)) return day
                day = day.minusDays(1)
            }
            return null
        }

        /** The date of the [n]th occurrence, ignoring [endOn]; used to turn "after N times" into an end date. */
        fun nthOccurrence(n: Int): LocalDate? {
            val unbounded = copy(endOn = null)
            var count = 0
            var day = start
            val last = start.plusDays(MAX_COUNT_SCAN_DAYS)
            while (!day.isAfter(last)) {
                if (unbounded.occursOn(day) && ++count == n) return day
                day = day.plusDays(1)
            }
            return null
        }
    }

    const val LAST = 5
    private const val MAX_HORIZON_DAYS = 3700L
    private const val MAX_COUNT_SCAN_DAYS = 36_500L

    // ---- bitmask <-> weekdays (bit 0 = Monday ... bit 6 = Sunday) ----

    fun weekdaysToMask(days: Collection<DayOfWeek>): Int = days.fold(0) { mask, d -> mask or (1 shl (d.value - 1)) }

    fun maskToWeekdays(mask: Int?): Set<DayOfWeek> =
        if (mask == null) emptySet() else DayOfWeek.entries.filter { mask and (1 shl (it.value - 1)) != 0 }.toSet()

    // ---- text ----

    private val EN = Locale.ENGLISH
    private val DAY_MONTH = DateTimeFormatter.ofPattern("d MMM", EN)
    private val DAY_MONTH_YEAR = DateTimeFormatter.ofPattern("d MMM yyyy", EN)

    private val ORDINALS = mapOf(1 to "first", 2 to "second", 3 to "third", 4 to "fourth", LAST to "last")

    /** "Every 2 weeks on Mon, Wed, until 1 Jan 2027" — or "3 times a week, any days" for a flexible routine. */
    fun describe(
        scheduleType: RoutineScheduleType,
        timesPerWeek: Int,
        recurrence: Recurrence?,
        endType: RoutineEndType,
        endCount: Int?,
    ): String {
        if (scheduleType == RoutineScheduleType.TIMES_PER_WEEK || recurrence == null) {
            return if (timesPerWeek == 1) "Once a week, any day" else "$timesPerWeek times a week, any days"
        }
        val end = when (endType) {
            RoutineEndType.NEVER -> ""
            RoutineEndType.ON_DATE -> recurrence.endOn?.let { ", until ${it.format(DAY_MONTH_YEAR)}" } ?: ""
            RoutineEndType.AFTER_COUNT -> endCount?.let { ", $it time${if (it == 1) "" else "s"}" } ?: ""
        }
        return describeRepeat(recurrence) + end
    }

    private fun describeRepeat(r: Recurrence): String {
        fun every(singular: String, plural: String) = if (r.interval == 1) "Every $singular" else "Every ${r.interval} $plural"
        return when (r.unit) {
            RecurUnit.DAY -> every("day", "days")
            RecurUnit.WEEK -> {
                val workdays = (1..5).map { DayOfWeek.of(it) }.toSet()
                when {
                    r.interval == 1 && r.weekdays.size == 7 -> "Every day"
                    r.interval == 1 && r.weekdays == workdays -> "Every weekday"
                    else -> "${every("week", "weeks")} on ${r.weekdays.sorted().joinToString(", ") { it.getDisplayName(TextStyle.SHORT, EN) }}"
                }
            }
            RecurUnit.MONTH -> {
                val prefix = every("month", "months")
                when (r.monthMode) {
                    MonthMode.DAY_OF_MONTH -> "$prefix on day ${r.monthDay}"
                    MonthMode.LAST_DAY -> "$prefix on the last day"
                    MonthMode.NTH_WEEKDAY -> "$prefix on the ${ORDINALS.getValue(r.nth)} ${r.weekday.getDisplayName(TextStyle.FULL, EN)}"
                }
            }
            RecurUnit.YEAR ->
                "${every("year", "years")} on ${java.time.Month.of(r.yearMonth).getDisplayName(TextStyle.FULL, EN)} ${r.monthDay}"
        }
    }

    class Digest(val title: String, val body: String)

    private const val MAX_LISTED = 4

    /** Null when nothing is open. */
    fun digest(open: List<RoutineStatusResponse>): Digest? {
        if (open.isEmpty()) return null
        val lines = open.map { label(it) }
        val shown = lines.take(MAX_LISTED)
        val more = lines.size - shown.size
        val body = (shown + listOfNotNull(if (more > 0) "+ $more more" else null)).joinToString("\n")
        return Digest("Routines: ${open.size} still open", body)
    }

    /** "Meditation · 10 min", "Gym · 1/3 this week", or "Pay rent · overdue since 1 Mar". */
    fun label(routine: RoutineStatusResponse): String = buildList {
        add(routine.name)
        routine.targetMinutes?.let { add("$it min") }
        if (routine.scheduleType == RoutineScheduleType.TIMES_PER_WEEK) {
            add("${routine.doneDaysThisWeek.size}/${routine.timesPerWeek} this week")
        }
        routine.overdueSince?.let { add("overdue since ${it.format(DAY_MONTH)}") }
    }.joinToString(" · ")
}
