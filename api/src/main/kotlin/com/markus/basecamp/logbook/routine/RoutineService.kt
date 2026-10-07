package com.markus.basecamp.logbook.routine

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.DayOfWeek
import java.time.LocalDate

/** Every method takes the calling user's id and only ever touches that user's routines and completions. */
@Service
@Transactional
class RoutineService(
    private val routines: RoutineRepository,
    private val completions: RoutineCompletionRepository,
) {

    @Transactional(readOnly = true)
    fun overview(userId: Long, day: LocalDate): RoutineOverviewResponse {
        val statuses = statuses(userId, day)
        return RoutineOverviewResponse(day, RoutineRules.weekStart(day), statuses, statuses.count { it.open })
    }

    /** Every routine with its state on [day] and in the Monday-Sunday week around it. Also used by the reminder. */
    @Transactional(readOnly = true)
    fun statuses(userId: Long, day: LocalDate): List<RoutineStatusResponse> {
        val weekStart = RoutineRules.weekStart(day)
        val thisWeek = completions.findAllByUserIdAndDayBetween(userId, weekStart, weekStart.plusDays(6))
            .groupBy { it.routineId }
        return routines.findAllByUserIdOrderByIdAsc(userId).map { routine -> status(routine, day, thisWeek[routine.id!!].orEmpty()) }
    }

    private fun status(routine: Routine, day: LocalDate, doneThisWeek: List<RoutineCompletion>): RoutineStatusResponse {
        val today = doneThisWeek.firstOrNull { it.day == day }
        val recurrence = routine.recurrence()
        val dueToday = recurrence?.occursOn(day) ?: false

        // for a carry-over routine: the latest occurrence on or before today, which stays open until it's been done
        val lastDue = if (routine.carryOver) recurrence?.lastOnOrBefore(day) else null

        val open = when {
            today != null -> false
            recurrence == null -> RoutineRules.isOpenTimesPerWeek(routine.timesPerWeek, false, doneThisWeek.size)
            lastDue != null -> !completions.existsByRoutineIdAndDayBetween(routine.id!!, lastDue, day)
            else -> dueToday
        }

        val state = when {
            open -> RoutineState.OPEN
            today != null || recurrence == null -> RoutineState.DONE
            else -> RoutineState.NOT_DUE
        }

        return RoutineStatusResponse(
            id = routine.id!!,
            name = routine.name,
            note = routine.note,
            scheduleType = routine.scheduleType,
            timesPerWeek = routine.timesPerWeek,
            targetMinutes = routine.targetMinutes,
            carryOver = routine.carryOver,
            summary = RoutineRules.describe(routine.scheduleType, routine.timesPerWeek, recurrence, routine.endType, routine.endCount),
            recurrence = recurrence?.let { routine.toRecurrenceResponse(it) },
            state = state,
            open = open,
            doneToday = today != null,
            minutesToday = today?.minutes,
            doneDaysThisWeek = doneThisWeek.map { it.day }.sorted(),
            dueToday = dueToday,
            nextDue = if (recurrence != null && !dueToday) recurrence.nextOnOrAfter(day.plusDays(1)) else null,
            overdueSince = if (open && lastDue != null && lastDue.isBefore(day)) lastDue else null,
        )
    }

    fun create(userId: Long, request: RoutineRequest): Long {
        val routine = Routine(userId, request.name.trim())
        apply(routine, request)
        return routines.save(routine).id!!
    }

    fun update(userId: Long, id: Long, request: RoutineRequest) {
        apply(findRoutine(userId, id), request)
    }

    fun delete(userId: Long, id: Long) {
        routines.delete(findRoutine(userId, id))
    }

    /**
     * Marks the routine done on [day]. Doing it again on the same day just updates the minutes. For a routine with a
     * time, the minutes default to its target; routines without a time never store any.
     */
    fun markDone(userId: Long, id: Long, day: LocalDate, minutes: Int?) {
        val routine = findRoutine(userId, id)
        val spent = if (routine.targetMinutes == null) null else minutes ?: routine.targetMinutes
        val existing = completions.findByRoutineIdAndDay(id, day)
        if (existing != null) {
            if (minutes != null) existing.minutes = spent
        } else {
            completions.save(RoutineCompletion(userId, id, day, spent))
        }
    }

    /** Idempotent: undoing something that isn't marked done is fine. */
    fun undo(userId: Long, id: Long, day: LocalDate) {
        findRoutine(userId, id)
        completions.findByRoutineIdAndDay(id, day)?.let { completions.delete(it) }
    }

    // ---- saving ----

    private fun apply(routine: Routine, request: RoutineRequest) {
        routine.name = request.name.trim()
        routine.note = request.note?.trim()?.takeIf { it.isNotEmpty() }
        routine.targetMinutes = request.targetMinutes
        routine.scheduleType = request.scheduleType

        if (request.scheduleType == RoutineScheduleType.TIMES_PER_WEEK) {
            routine.timesPerWeek = request.timesPerWeek
            routine.carryOver = false
            clearRecurrence(routine)
        } else {
            val recurrence = request.recurrence
                ?: throw bad("Pick when this routine repeats")
            routine.timesPerWeek = 1
            routine.carryOver = request.carryOver
            applyRecurrence(routine, recurrence)
        }
    }

    private fun clearRecurrence(routine: Routine) {
        routine.recurUnit = null
        routine.recurInterval = 1
        routine.recurWeekdays = null
        routine.monthMode = null
        routine.monthDay = null
        routine.monthNth = null
        routine.monthWeekday = null
        routine.yearMonth = null
        routine.startDate = null
        routine.endType = RoutineEndType.NEVER
        routine.endOn = null
        routine.endCount = null
    }

    /** Fills in whatever the request left out from the start date (like a calendar), validates, and stores it flat. */
    private fun applyRecurrence(routine: Routine, request: RecurrenceRequest) {
        clearRecurrence(routine)
        val start = request.startDate
        routine.startDate = start
        routine.recurUnit = request.unit
        routine.recurInterval = request.interval

        when (request.unit) {
            RecurUnit.DAY -> Unit
            RecurUnit.WEEK -> {
                val days = request.weekdays.distinct()
                if (days.any { it !in 1..7 }) throw bad("Weekdays must be 1 (Monday) to 7 (Sunday)")
                val chosen = days.map { DayOfWeek.of(it) }.ifEmpty { listOf(start.dayOfWeek) }
                routine.recurWeekdays = RoutineRules.weekdaysToMask(chosen)
            }
            RecurUnit.MONTH -> {
                routine.monthMode = request.monthMode
                when (request.monthMode) {
                    MonthMode.DAY_OF_MONTH -> routine.monthDay = request.monthDay ?: start.dayOfMonth
                    MonthMode.LAST_DAY -> Unit
                    MonthMode.NTH_WEEKDAY -> {
                        routine.monthNth = request.nth ?: RoutineRules.nthWeekdayOfMonth(start)
                        routine.monthWeekday = request.weekday ?: start.dayOfWeek.value
                    }
                }
            }
            RecurUnit.YEAR -> {
                routine.yearMonth = request.yearMonth ?: start.monthValue
                routine.monthDay = request.monthDay ?: start.dayOfMonth
            }
        }

        routine.endType = request.endType
        when (request.endType) {
            RoutineEndType.NEVER -> Unit
            RoutineEndType.ON_DATE -> {
                val end = request.endDate ?: throw bad("Pick the date it ends on")
                if (end.isBefore(start)) throw bad("The end date can't be before the start date")
                routine.endOn = end
            }
            RoutineEndType.AFTER_COUNT -> {
                val count = request.endCount ?: throw bad("Say after how many times it ends")
                routine.endCount = count
                // stored as the date of the last occurrence, so everything else only has to deal with an end date
                routine.endOn = routine.recurrence()!!.nthOccurrence(count)
                    ?: throw bad("That schedule doesn't occur that many times")
            }
        }
    }

    private fun Routine.toRecurrenceResponse(r: RoutineRules.Recurrence) = RecurrenceResponse(
        unit = r.unit,
        interval = r.interval,
        weekdays = r.weekdays.map { it.value }.sorted(),
        monthMode = r.monthMode,
        monthDay = r.monthDay,
        nth = r.nth,
        weekday = r.weekday.value,
        yearMonth = r.yearMonth,
        startDate = r.start,
        endType = endType,
        endDate = if (endType == RoutineEndType.ON_DATE) endOn else null,
        endCount = if (endType == RoutineEndType.AFTER_COUNT) endCount else null,
    )

    private fun bad(message: String) = ResponseStatusException(HttpStatus.BAD_REQUEST, message)

    private fun findRoutine(userId: Long, id: Long): Routine =
        routines.findByIdAndUserId(id, userId) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Routine not found")
}

/** The routine's stored fields as a rule, or null for a "N times a week" routine. Missing parts default from the start date. */
internal fun Routine.recurrence(): RoutineRules.Recurrence? {
    if (scheduleType != RoutineScheduleType.RECURRING) return null
    val start = startDate ?: return null
    return RoutineRules.Recurrence(
        unit = recurUnit ?: RecurUnit.DAY,
        interval = recurInterval,
        weekdays = RoutineRules.maskToWeekdays(recurWeekdays).ifEmpty { setOf(start.dayOfWeek) },
        monthMode = monthMode ?: MonthMode.DAY_OF_MONTH,
        monthDay = monthDay ?: start.dayOfMonth,
        nth = monthNth ?: RoutineRules.nthWeekdayOfMonth(start),
        weekday = monthWeekday?.let { DayOfWeek.of(it) } ?: start.dayOfWeek,
        yearMonth = yearMonth ?: start.monthValue,
        start = start,
        endOn = endOn,
    )
}
