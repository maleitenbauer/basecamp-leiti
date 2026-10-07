package com.markus.basecamp.logbook.routine

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/** RECURRING: due on specific days, like a calendar event. TIMES_PER_WEEK: N times a week, on whichever days. */
enum class RoutineScheduleType { RECURRING, TIMES_PER_WEEK }

enum class RecurUnit { DAY, WEEK, MONTH, YEAR }

enum class MonthMode { DAY_OF_MONTH, LAST_DAY, NTH_WEEKDAY }

enum class RoutineEndType { NEVER, ON_DATE, AFTER_COUNT }

@Entity
@Table(name = "routine", schema = "logbook")
class Routine(
    @Column(nullable = false, updatable = false)
    val userId: Long,

    @Column(nullable = false, length = 200)
    var name: String,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @Column(length = 500)
    var note: String? = null

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var scheduleType: RoutineScheduleType = RoutineScheduleType.RECURRING

    /** Only meaningful for TIMES_PER_WEEK. */
    @Column(nullable = false)
    var timesPerWeek: Int = 1

    /** Null for routines that are simply checked off rather than timed. */
    var targetMinutes: Int? = null

    // ---- recurrence (RECURRING only; see RoutineRules.Recurrence for what these mean) ----

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    var recurUnit: RecurUnit? = null

    @Column(nullable = false)
    var recurInterval: Int = 1

    /** WEEK: bitmask of weekdays, bit 0 = Monday ... bit 6 = Sunday. */
    var recurWeekdays: Int? = null

    @Enumerated(EnumType.STRING)
    @Column(length = 15)
    var monthMode: MonthMode? = null

    /** MONTH's day of the month, and YEAR's day. */
    var monthDay: Int? = null

    var monthNth: Int? = null

    /** ISO: 1 = Monday ... 7 = Sunday. */
    var monthWeekday: Int? = null

    var yearMonth: Int? = null

    var startDate: LocalDate? = null

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    var endType: RoutineEndType = RoutineEndType.NEVER

    /** The last day it can occur. For AFTER_COUNT it's the date of the last occurrence, worked out when saved. */
    var endOn: LocalDate? = null

    var endCount: Int? = null

    /** An unfinished occurrence stays open until it's done, instead of lapsing at the end of its day. */
    @Column(nullable = false)
    var carryOver: Boolean = false

    @Column(nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()
}

@Entity
@Table(name = "routine_completion", schema = "logbook")
class RoutineCompletion(
    @Column(nullable = false, updatable = false)
    val userId: Long,

    @Column(nullable = false, updatable = false)
    val routineId: Long,

    @Column(nullable = false, updatable = false)
    val day: LocalDate,

    var minutes: Int?,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @Column(nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()
}

/** One row per user, created lazily. Reminders run in the user's time zone (see Notifier.zoneOf). */
@Entity
@Table(name = "routine_reminder_settings", schema = "logbook")
class RoutineReminderSettings(
    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    val userId: Long,

    @Column(nullable = false)
    var enabled: Boolean = true,

    @Column(nullable = false)
    var remindAt: LocalTime = LocalTime.of(16, 0),

    var lastSentOn: LocalDate? = null,
)

interface RoutineRepository : JpaRepository<Routine, Long> {
    fun findAllByUserIdOrderByIdAsc(userId: Long): List<Routine>
    fun findByIdAndUserId(id: Long, userId: Long): Routine?
}

interface RoutineCompletionRepository : JpaRepository<RoutineCompletion, Long> {
    fun findAllByUserIdAndDayBetween(userId: Long, from: LocalDate, to: LocalDate): List<RoutineCompletion>
    fun findByRoutineIdAndDay(routineId: Long, day: LocalDate): RoutineCompletion?
    fun existsByRoutineIdAndDayBetween(routineId: Long, from: LocalDate, to: LocalDate): Boolean
}

interface RoutineReminderSettingsRepository : JpaRepository<RoutineReminderSettings, Long> {
    fun findAllByEnabledTrue(): List<RoutineReminderSettings>
}
