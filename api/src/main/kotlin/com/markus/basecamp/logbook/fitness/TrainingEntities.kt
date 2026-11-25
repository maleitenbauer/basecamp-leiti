package com.markus.basecamp.logbook.fitness

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant
import java.time.LocalDate

/** MANUAL: the user pressed finish. AUTO: it was still running past the cut-off and was ended for them. */
enum class WorkoutEndedBy { MANUAL, AUTO }

@Entity
@Table(name = "workout_plan", schema = "logbook")
class WorkoutPlan(
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

    /** Finishing a workout from this plan ticks this routine off for the day. */
    var routineId: Long? = null

    @Column(nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()
}

@Entity
@Table(name = "workout_plan_exercise", schema = "logbook")
class WorkoutPlanExercise(
    @Column(nullable = false, updatable = false)
    val planId: Long,

    @Column(nullable = false)
    var sortOrder: Int,

    @Column(nullable = false, length = 200)
    var name: String,

    @Column(nullable = false)
    var sets: Int,

    @Column(nullable = false)
    var reps: Int,

    var weightKg: Double?,

    var restSeconds: Int?,

    @Column(length = 300)
    var note: String?,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
}

@Entity
@Table(name = "workout_session", schema = "logbook")
class WorkoutSession(
    @Column(nullable = false, updatable = false)
    val userId: Long,

    @Column(nullable = false, length = 200)
    var name: String,

    @Column(nullable = false, updatable = false)
    val day: LocalDate,

    @Column(nullable = false, updatable = false)
    val startedAt: Instant,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    /** Null once the plan is deleted; the workout keeps its own copy of everything it needs. */
    var planId: Long? = null

    var finishedAt: Instant? = null

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    var endedBy: WorkoutEndedBy? = null

    @Column(nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()
}

@Entity
@Table(name = "workout_exercise", schema = "logbook")
class WorkoutExercise(
    @Column(nullable = false, updatable = false)
    val userId: Long,

    @Column(nullable = false, updatable = false)
    val sessionId: Long,

    @Column(nullable = false)
    var sortOrder: Int,

    @Column(nullable = false, length = 200)
    val name: String,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    /** Lower-cased name: how the same exercise is recognised across workouts. */
    @Column(nullable = false, length = 200)
    val nameKey: String = TrainingRules.nameKey(name)

    var targetSets: Int? = null

    var targetReps: Int? = null

    var targetWeightKg: Double? = null

    var restSeconds: Int? = null
}

@Entity
@Table(name = "workout_set", schema = "logbook")
class WorkoutSet(
    @Column(nullable = false, updatable = false)
    val userId: Long,

    @Column(nullable = false, updatable = false)
    val exerciseId: Long,

    @Column(nullable = false)
    var reps: Int,

    /** Null means bodyweight. */
    var weightKg: Double?,

    @Column(nullable = false, updatable = false)
    val doneAt: Instant,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @Column(nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()
}

interface WorkoutPlanRepository : JpaRepository<WorkoutPlan, Long> {
    fun findAllByUserIdOrderByIdAsc(userId: Long): List<WorkoutPlan>
    fun findByIdAndUserId(id: Long, userId: Long): WorkoutPlan?
    fun findAllByUserIdAndRoutineId(userId: Long, routineId: Long): List<WorkoutPlan>
}

interface WorkoutPlanExerciseRepository : JpaRepository<WorkoutPlanExercise, Long> {
    fun findAllByPlanIdInOrderBySortOrderAscIdAsc(planIds: Collection<Long>): List<WorkoutPlanExercise>
    fun deleteAllByPlanId(planId: Long)
}

interface WorkoutSessionRepository : JpaRepository<WorkoutSession, Long> {
    fun findByIdAndUserId(id: Long, userId: Long): WorkoutSession?
    fun findFirstByUserIdAndFinishedAtIsNull(userId: Long): WorkoutSession?
    fun findAllByFinishedAtIsNullAndStartedAtBefore(before: Instant): List<WorkoutSession>
    fun findAllByUserIdAndFinishedAtIsNotNullOrderByStartedAtDesc(userId: Long, page: Pageable): List<WorkoutSession>
    fun findAllByUserIdAndFinishedAtIsNotNullAndDayBetween(userId: Long, from: LocalDate, to: LocalDate): List<WorkoutSession>
    fun findAllByUserIdAndFinishedAtIsNotNull(userId: Long): List<WorkoutSession>
}

interface WorkoutExerciseRepository : JpaRepository<WorkoutExercise, Long> {
    fun findAllBySessionIdInOrderBySortOrderAscIdAsc(sessionIds: Collection<Long>): List<WorkoutExercise>
    fun findAllByUserIdAndNameKeyIn(userId: Long, nameKeys: Collection<String>): List<WorkoutExercise>
    fun findByIdAndSessionId(id: Long, sessionId: Long): WorkoutExercise?
}

interface WorkoutSetRepository : JpaRepository<WorkoutSet, Long> {
    fun findAllByExerciseIdInOrderByIdAsc(exerciseIds: Collection<Long>): List<WorkoutSet>
    fun findByIdAndUserId(id: Long, userId: Long): WorkoutSet?
}
