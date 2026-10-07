package com.markus.basecamp.logbook.routine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Calendar facts used below (2026): Mar 1 is a Sunday, so Mar 2 is a Monday and the Tuesdays of March are the
 * 3rd, 10th, 17th, 24th and 31st.
 */
class RecurrenceTest {

    private val start = LocalDate.of(2026, 3, 2) // a Monday

    private fun rule(
        unit: RecurUnit,
        interval: Int = 1,
        weekdays: Set<DayOfWeek> = setOf(DayOfWeek.MONDAY),
        monthMode: MonthMode = MonthMode.DAY_OF_MONTH,
        monthDay: Int = start.dayOfMonth,
        nth: Int = 1,
        weekday: DayOfWeek = DayOfWeek.MONDAY,
        yearMonth: Int = start.monthValue,
        from: LocalDate = start,
        endOn: LocalDate? = null,
    ) = RoutineRules.Recurrence(unit, interval, weekdays, monthMode, monthDay, nth, weekday, yearMonth, from, endOn)

    private fun d(y: Int, m: Int, day: Int) = LocalDate.of(y, m, day)

    @Test
    fun `every N days counts from the start date`() {
        val every3 = rule(RecurUnit.DAY, interval = 3)

        assertTrue(every3.occursOn(d(2026, 3, 2)))
        assertFalse(every3.occursOn(d(2026, 3, 4)))
        assertTrue(every3.occursOn(d(2026, 3, 5)))
        assertTrue(every3.occursOn(d(2026, 3, 8)))
        assertFalse(every3.occursOn(d(2026, 3, 1))) // before the start
    }

    @Test
    fun `every other week on Monday and Wednesday alternates weeks`() {
        val rule = rule(RecurUnit.WEEK, interval = 2, weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY))

        assertTrue(rule.occursOn(d(2026, 3, 2)))
        assertTrue(rule.occursOn(d(2026, 3, 4)))
        assertFalse(rule.occursOn(d(2026, 3, 9))) // the off week
        assertFalse(rule.occursOn(d(2026, 3, 11)))
        assertTrue(rule.occursOn(d(2026, 3, 16)))
        assertTrue(rule.occursOn(d(2026, 3, 18)))
        assertFalse(rule.occursOn(d(2026, 3, 17))) // right week, wrong weekday
    }

    @Test
    fun `a day of the month falls on the last day of shorter months`() {
        val on31st = rule(RecurUnit.MONTH, monthDay = 31, from = d(2026, 1, 31))

        assertTrue(on31st.occursOn(d(2026, 1, 31)))
        assertTrue(on31st.occursOn(d(2026, 2, 28)))
        assertFalse(on31st.occursOn(d(2026, 2, 27)))
        assertTrue(on31st.occursOn(d(2026, 4, 30)))
        assertTrue(on31st.occursOn(d(2028, 2, 29))) // leap year
        assertFalse(on31st.occursOn(d(2028, 2, 28)))
    }

    @Test
    fun `the last day of the month, whatever its length`() {
        val lastDay = rule(RecurUnit.MONTH, monthMode = MonthMode.LAST_DAY)

        assertTrue(lastDay.occursOn(d(2026, 3, 31)))
        assertTrue(lastDay.occursOn(d(2026, 4, 30)))
        assertFalse(lastDay.occursOn(d(2026, 2, 28))) // the last day of February, but before the start (Mar 2)
        assertFalse(lastDay.occursOn(d(2026, 3, 30)))
    }

    @Test
    fun `the nth weekday of the month, including the last one`() {
        val secondTuesday = rule(RecurUnit.MONTH, monthMode = MonthMode.NTH_WEEKDAY, nth = 2, weekday = DayOfWeek.TUESDAY)
        assertTrue(secondTuesday.occursOn(d(2026, 3, 10)))
        assertFalse(secondTuesday.occursOn(d(2026, 3, 3)))
        assertFalse(secondTuesday.occursOn(d(2026, 3, 17)))

        val lastTuesday = rule(
            RecurUnit.MONTH,
            monthMode = MonthMode.NTH_WEEKDAY,
            nth = RoutineRules.LAST,
            weekday = DayOfWeek.TUESDAY,
        )
        assertTrue(lastTuesday.occursOn(d(2026, 3, 31)))
        assertFalse(lastTuesday.occursOn(d(2026, 3, 24)))
        assertTrue(lastTuesday.occursOn(d(2026, 4, 28))) // April's Tuesdays: 7, 14, 21, 28
    }

    @Test
    fun `every N months counts from the start month`() {
        val everyTwoMonths = rule(RecurUnit.MONTH, interval = 2, monthDay = 15, from = d(2026, 1, 15))

        assertTrue(everyTwoMonths.occursOn(d(2026, 1, 15)))
        assertFalse(everyTwoMonths.occursOn(d(2026, 2, 15)))
        assertTrue(everyTwoMonths.occursOn(d(2026, 3, 15)))
        assertTrue(everyTwoMonths.occursOn(d(2026, 5, 15)))
    }

    @Test
    fun `yearly lands on the same month and day, and Feb 29 falls on Feb 28 in other years`() {
        val birthday = rule(RecurUnit.YEAR, yearMonth = 3, monthDay = 15, from = d(2026, 3, 15))
        assertTrue(birthday.occursOn(d(2027, 3, 15)))
        assertFalse(birthday.occursOn(d(2027, 3, 14)))

        val leapDay = rule(RecurUnit.YEAR, yearMonth = 2, monthDay = 29, from = d(2024, 2, 29))
        assertTrue(leapDay.occursOn(d(2024, 2, 29)))
        assertTrue(leapDay.occursOn(d(2025, 2, 28)))
        assertFalse(leapDay.occursOn(d(2025, 2, 27)))
        assertTrue(leapDay.occursOn(d(2028, 2, 29)))
        assertFalse(leapDay.occursOn(d(2028, 2, 28)))

        val everyOtherYear = rule(RecurUnit.YEAR, interval = 2, yearMonth = 2, monthDay = 29, from = d(2024, 2, 29))
        assertTrue(everyOtherYear.occursOn(d(2026, 2, 28)))
        assertFalse(everyOtherYear.occursOn(d(2025, 2, 28)))
    }

    @Test
    fun `nothing occurs before the start or after the end`() {
        val limited = rule(RecurUnit.DAY, endOn = d(2026, 3, 4))

        assertFalse(limited.occursOn(d(2026, 3, 1)))
        assertTrue(limited.occursOn(d(2026, 3, 4)))
        assertFalse(limited.occursOn(d(2026, 3, 5)))
    }

    @Test
    fun `next and last occurrence searches`() {
        val monthly = rule(RecurUnit.MONTH, monthDay = 15, from = d(2026, 1, 15))

        assertEquals(d(2026, 4, 15), monthly.nextOnOrAfter(d(2026, 3, 16)))
        assertEquals(d(2026, 3, 15), monthly.nextOnOrAfter(d(2026, 3, 15))) // "on or after" includes the day itself
        assertEquals(d(2026, 1, 15), monthly.nextOnOrAfter(d(2025, 6, 1))) // before the start: the first occurrence
        assertEquals(d(2026, 3, 15), monthly.lastOnOrBefore(d(2026, 4, 14)))
        assertNull(monthly.lastOnOrBefore(d(2026, 1, 14))) // none yet

        val ended = rule(RecurUnit.DAY, endOn = d(2026, 3, 4))
        assertNull(ended.nextOnOrAfter(d(2026, 3, 10)))
    }

    @Test
    fun `the Nth occurrence turns after-N-times into a date`() {
        assertEquals(d(2026, 3, 4), rule(RecurUnit.DAY).nthOccurrence(3))
        assertEquals(d(2026, 5, 2), rule(RecurUnit.MONTH, monthDay = 2).nthOccurrence(3)) // Mar 2, Apr 2, May 2
        assertEquals(
            d(2026, 3, 16),
            rule(RecurUnit.WEEK, interval = 2, weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY)).nthOccurrence(3),
        ) // Mar 2, Mar 4, Mar 16
    }

    @Test
    fun `weekdays round-trip through the bitmask`() {
        val days = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.SUNDAY)
        val mask = RoutineRules.weekdaysToMask(days)

        assertEquals(1 + 4 + 64, mask)
        assertEquals(days, RoutineRules.maskToWeekdays(mask))
        assertEquals(emptySet<DayOfWeek>(), RoutineRules.maskToWeekdays(null))
    }

    @Test
    fun `schedules are described like a calendar would`() {
        fun describe(rule: RoutineRules.Recurrence, endType: RoutineEndType = RoutineEndType.NEVER, count: Int? = null) =
            RoutineRules.describe(RoutineScheduleType.RECURRING, 1, rule, endType, count)

        assertEquals("Every day", describe(rule(RecurUnit.DAY)))
        assertEquals("Every 3 days", describe(rule(RecurUnit.DAY, interval = 3)))
        assertEquals("Every week on Mon", describe(rule(RecurUnit.WEEK)))
        assertEquals(
            "Every 2 weeks on Mon, Wed",
            describe(rule(RecurUnit.WEEK, interval = 2, weekdays = setOf(DayOfWeek.WEDNESDAY, DayOfWeek.MONDAY))),
        )
        assertEquals(
            "Every weekday",
            describe(rule(RecurUnit.WEEK, weekdays = (1..5).map { DayOfWeek.of(it) }.toSet())),
        )
        assertEquals("Every day", describe(rule(RecurUnit.WEEK, weekdays = DayOfWeek.entries.toSet())))
        assertEquals("Every month on day 15", describe(rule(RecurUnit.MONTH, monthDay = 15)))
        assertEquals("Every 3 months on the last day", describe(rule(RecurUnit.MONTH, interval = 3, monthMode = MonthMode.LAST_DAY)))
        assertEquals(
            "Every month on the second Tuesday",
            describe(rule(RecurUnit.MONTH, monthMode = MonthMode.NTH_WEEKDAY, nth = 2, weekday = DayOfWeek.TUESDAY)),
        )
        assertEquals(
            "Every month on the last Friday",
            describe(rule(RecurUnit.MONTH, monthMode = MonthMode.NTH_WEEKDAY, nth = RoutineRules.LAST, weekday = DayOfWeek.FRIDAY)),
        )
        assertEquals("Every year on March 15", describe(rule(RecurUnit.YEAR, yearMonth = 3, monthDay = 15)))
        assertEquals("Every 2 years on February 29", describe(rule(RecurUnit.YEAR, interval = 2, yearMonth = 2, monthDay = 29)))

        assertEquals("Every day, until 4 Mar 2026", describe(rule(RecurUnit.DAY, endOn = d(2026, 3, 4)), RoutineEndType.ON_DATE))
        assertEquals("Every day, 10 times", describe(rule(RecurUnit.DAY), RoutineEndType.AFTER_COUNT, 10))
        assertEquals("Every day, 1 time", describe(rule(RecurUnit.DAY), RoutineEndType.AFTER_COUNT, 1))
    }

    @Test
    fun `a flexible routine is described by its weekly count`() {
        assertEquals("Once a week, any day", RoutineRules.describe(RoutineScheduleType.TIMES_PER_WEEK, 1, null, RoutineEndType.NEVER, null))
        assertEquals("3 times a week, any days", RoutineRules.describe(RoutineScheduleType.TIMES_PER_WEEK, 3, null, RoutineEndType.NEVER, null))
    }
}
