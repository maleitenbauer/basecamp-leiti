package com.markus.basecamp.logbook.routine

import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.time.LocalDate

/**
 * When a scheduled routine repeats, the way a calendar does it. Everything beyond [unit], [interval] and [startDate]
 * has a default taken from [startDate], so a bare "every 2 weeks from Monday" is a complete request:
 *  - WEEK: [weekdays] (ISO 1 = Monday ... 7 = Sunday), defaults to the start date's weekday.
 *  - MONTH: [monthMode] DAY_OF_MONTH on [monthDay] (a 31st in a shorter month falls on its last day), LAST_DAY,
 *    or NTH_WEEKDAY: [nth] (1-4, or 5 = the last) [weekday] of the month.
 *  - YEAR: on [yearMonth] / [monthDay]; a Feb 29 falls on Feb 28 in years without one.
 * The end is never, on [endDate], or after [endCount] occurrences.
 */
data class RecurrenceRequest(
    val unit: RecurUnit = RecurUnit.DAY,
    @field:Min(1) @field:Max(999) val interval: Int = 1,
    val weekdays: List<Int> = emptyList(),
    val monthMode: MonthMode = MonthMode.DAY_OF_MONTH,
    @field:Min(1) @field:Max(31) val monthDay: Int? = null,
    @field:Min(1) @field:Max(5) val nth: Int? = null,
    @field:Min(1) @field:Max(7) val weekday: Int? = null,
    @field:Min(1) @field:Max(12) val yearMonth: Int? = null,
    val startDate: LocalDate,
    val endType: RoutineEndType = RoutineEndType.NEVER,
    val endDate: LocalDate? = null,
    @field:Min(1) @field:Max(1000) val endCount: Int? = null,
)

/**
 * Create and (full) update share this shape. A RECURRING routine needs [recurrence]; a TIMES_PER_WEEK one uses
 * [timesPerWeek]. [targetMinutes] is null for routines that are simply checked off. [carryOver] keeps an unfinished
 * occurrence open (and in the reminder) until it's done; it only applies to RECURRING routines.
 */
data class RoutineRequest(
    @field:NotBlank @field:Size(max = 200) val name: String,
    @field:Size(max = 500) val note: String? = null,
    val scheduleType: RoutineScheduleType = RoutineScheduleType.RECURRING,
    @field:Min(1) @field:Max(7) val timesPerWeek: Int = 1,
    @field:Valid val recurrence: RecurrenceRequest? = null,
    val carryOver: Boolean = false,
    @field:Min(1) @field:Max(1440) val targetMinutes: Int? = null,
)

/** [minutes] defaults to the routine's target when omitted; ignored for routines without a time. */
data class MarkRoutineDoneRequest(
    @field:Min(1) @field:Max(1440) val minutes: Int? = null,
)

/** The recurrence with every default filled in, so the edit form can show exactly what's saved. */
data class RecurrenceResponse(
    val unit: RecurUnit,
    val interval: Int,
    val weekdays: List<Int>,
    val monthMode: MonthMode,
    val monthDay: Int,
    val nth: Int,
    val weekday: Int,
    val yearMonth: Int,
    val startDate: LocalDate,
    val endType: RoutineEndType,
    val endDate: LocalDate?,
    val endCount: Int?,
)

/** OPEN needs doing; DONE was done that day (or this week's target is met); NOT_DUE just isn't scheduled that day. */
enum class RoutineState { OPEN, DONE, NOT_DUE }

data class RoutineStatusResponse(
    val id: Long,
    val name: String,
    val note: String?,
    val scheduleType: RoutineScheduleType,
    /** Only meaningful for TIMES_PER_WEEK (1 otherwise). */
    val timesPerWeek: Int,
    val targetMinutes: Int?,
    val carryOver: Boolean,
    /** Human-readable schedule, e.g. "Every 2 weeks on Mon, Wed" or "3 times a week, any days". */
    val summary: String,
    /** Null for TIMES_PER_WEEK. */
    val recurrence: RecurrenceResponse?,
    val state: RoutineState,
    val open: Boolean,
    val doneToday: Boolean,
    val minutesToday: Int?,
    /** The days in the (Monday-Sunday) week containing the requested date on which this routine was done. */
    val doneDaysThisWeek: List<LocalDate>,
    /** A scheduled routine's occurrence falls on the requested day. */
    val dueToday: Boolean,
    /** When a scheduled routine isn't due on the requested day: its next occurrence after it. */
    val nextDue: LocalDate?,
    /** For a carry-over routine that's open from an earlier occurrence: the date that occurrence was due. */
    val overdueSince: LocalDate?,
)

data class RoutineOverviewResponse(
    val date: LocalDate,
    val weekStart: LocalDate,
    val routines: List<RoutineStatusResponse>,
    val openCount: Int,
)

data class RoutineReminderResponse(
    val enabled: Boolean,
    /** HH:mm in the user's time zone */
    val remindAt: String,
    val timezone: String,
)

data class UpdateRoutineReminderRequest(
    val enabled: Boolean,
    @field:Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$", message = "Use HH:mm, for example 16:00")
    val remindAt: String,
    /** IANA name such as Europe/Vienna; the browser reports it. Optional. */
    @field:Size(max = 64) val timezone: String? = null,
)

data class RoutineSendNowResponse(val sent: Boolean, val message: String)
