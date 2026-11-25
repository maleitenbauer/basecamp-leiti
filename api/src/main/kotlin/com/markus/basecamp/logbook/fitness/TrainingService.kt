package com.markus.basecamp.logbook.fitness

import com.markus.basecamp.core.Notifier
import com.markus.basecamp.logbook.routine.RoutineRepository
import com.markus.basecamp.logbook.routine.RoutineRules
import com.markus.basecamp.logbook.routine.RoutineScheduleType
import com.markus.basecamp.logbook.routine.RoutineService
import com.markus.basecamp.logbook.routine.recurrence
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.server.ResponseStatusException
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Running a workout: start it (from a plan or empty), log sets, finish it. One running workout per user. A workout that
 * is still running after the cut-off (default 2 hours) is ended automatically, so a forgotten one never turns into a
 * five-hour "workout" in the history. Every method only ever touches the calling user's data.
 */
@Service
@Transactional
class TrainingService(
    private val planService: TrainingPlanService,
    private val plans: WorkoutPlanRepository,
    private val planExercises: WorkoutPlanExerciseRepository,
    private val sessions: WorkoutSessionRepository,
    private val exercises: WorkoutExerciseRepository,
    private val sets: WorkoutSetRepository,
    private val routines: RoutineRepository,
    private val routineService: RoutineService,
    private val notifier: Notifier,
    transactionManager: PlatformTransactionManager,
    @Value("\${basecamp.training.auto-finish-minutes:120}") autoFinishMinutes: Long,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val tx = TransactionTemplate(transactionManager)
    private val limit: Duration = Duration.ofMinutes(autoFinishMinutes)

    /** Everything the Training page needs in one go. [day] is the user's today, which decides which week "this week" is. */
    fun overview(userId: Long, day: LocalDate, now: Instant = Instant.now()): TrainingOverview {
        finishIfStale(userId, now)
        val planList = planService.list(userId)
        val history = sessions.findAllByUserIdAndFinishedAtIsNotNull(userId)
        return TrainingOverview(
            active = sessions.findFirstByUserIdAndFinishedAtIsNull(userId)?.let { response(it, withPrevious = true) },
            plans = planList,
            stats = stats(userId, day, planList, history),
            recent = summaries(history.sortedByDescending { it.startedAt }.take(RECENT)),
        )
    }

    fun start(userId: Long, request: StartWorkoutRequest, now: Instant = Instant.now()): WorkoutResponse {
        finishIfStale(userId, now)
        if (sessions.findFirstByUserIdAndFinishedAtIsNull(userId) != null) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "A workout is already running. Finish it first.")
        }
        val plan = request.planId?.let {
            plans.findByIdAndUserId(it, userId) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Plan not found")
        }

        val session = WorkoutSession(userId, plan?.name ?: "Workout", request.day, now)
        session.planId = plan?.id
        try {
            sessions.saveAndFlush(session)
        } catch (e: DataIntegrityViolationException) {
            // two starts at once: the database allows only one running workout per user
            throw ResponseStatusException(HttpStatus.CONFLICT, "A workout is already running. Finish it first.")
        }

        if (plan != null) {
            exercises.saveAll(
                planExercises.findAllByPlanIdInOrderBySortOrderAscIdAsc(listOf(plan.id!!)).mapIndexed { index, e ->
                    WorkoutExercise(userId, session.id!!, index, e.name).also {
                        it.targetSets = e.sets
                        it.targetReps = e.reps
                        it.targetWeightKg = e.weightKg
                        it.restSeconds = e.restSeconds
                    }
                },
            )
        }
        return response(session, withPrevious = true)
    }

    /** For the exercise you did instead of the planned one, or the one the plan didn't have. */
    fun addExercise(userId: Long, workoutId: Long, request: AddWorkoutExerciseRequest): WorkoutResponse {
        val session = running(userId, workoutId)
        val next = (exercises.findAllBySessionIdInOrderBySortOrderAscIdAsc(listOf(workoutId)).maxOfOrNull { it.sortOrder } ?: -1) + 1
        exercises.save(WorkoutExercise(userId, workoutId, next, request.name.trim()))
        return response(session, withPrevious = true)
    }

    fun logSet(userId: Long, workoutId: Long, exerciseId: Long, request: SetRequest, now: Instant = Instant.now()): WorkoutResponse {
        val session = running(userId, workoutId)
        val exercise = exercises.findByIdAndSessionId(exerciseId, workoutId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Exercise not found")
        sets.save(WorkoutSet(userId, exercise.id!!, request.reps, request.weightKg, now))
        return response(session, withPrevious = true)
    }

    fun updateSet(userId: Long, workoutId: Long, setId: Long, request: SetRequest): WorkoutResponse {
        val session = running(userId, workoutId)
        val set = ownedSet(userId, workoutId, setId)
        set.reps = request.reps
        set.weightKg = request.weightKg
        return response(session, withPrevious = true)
    }

    fun deleteSet(userId: Long, workoutId: Long, setId: Long): WorkoutResponse {
        val session = running(userId, workoutId)
        sets.delete(ownedSet(userId, workoutId, setId))
        return response(session, withPrevious = true)
    }

    fun finish(userId: Long, workoutId: Long, now: Instant = Instant.now()): FinishWorkoutResponse =
        complete(running(userId, workoutId), WorkoutEndedBy.MANUAL, now)

    /** Throws the workout away, running or finished. Anything it ticked off in a routine stays ticked off. */
    fun discard(userId: Long, workoutId: Long) {
        val session = sessions.findByIdAndUserId(workoutId, userId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Workout not found")
        sessions.delete(session)
    }

    /** The finished workouts whose day falls in the range, oldest first. Used by the weekly report. */
    @Transactional(readOnly = true)
    fun workoutsBetween(userId: Long, from: LocalDate, to: LocalDate): List<WorkoutSummary> =
        summaries(sessions.findAllByUserIdAndFinishedAtIsNotNullAndDayBetween(userId, from, to).sortedBy { it.startedAt })

    // ---- ending a workout ----

    /** Ends the user's running workout if it has gone past the cut-off. Also what keeps the page right if the timer is off. */
    private fun finishIfStale(userId: Long, now: Instant) {
        val session = sessions.findFirstByUserIdAndFinishedAtIsNull(userId) ?: return
        if (TrainingRules.isStale(session.startedAt, now, limit)) autoFinish(session)
    }

    /** Called every few minutes by [TrainingAutoFinishScheduler]. One workout's problem never blocks the others. */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun autoFinishStale(now: Instant = Instant.now()) {
        for (id in sessions.findAllByFinishedAtIsNullAndStartedAtBefore(now.minus(limit)).map { it.id!! }) {
            try {
                tx.executeWithoutResult {
                    sessions.findById(id).orElse(null)?.takeIf { it.finishedAt == null }?.let { autoFinish(it) }
                }
            } catch (e: Exception) {
                log.warn("Ending workout {} automatically failed: {}", id, e.message)
            }
        }
    }

    /**
     * Ends a forgotten workout shortly after the last set that was logged. Without a single set there's nothing to save
     * (and no reason to believe it happened), so it is dropped, and the notification says so.
     */
    private fun autoFinish(session: WorkoutSession) {
        val userId = session.userId
        val exerciseIds = exercises.findAllBySessionIdInOrderBySortOrderAscIdAsc(listOf(session.id!!)).map { it.id!! }
        val logged = if (exerciseIds.isEmpty()) emptyList() else sets.findAllByExerciseIdInOrderByIdAsc(exerciseIds)
        val name = session.name

        if (logged.isEmpty()) {
            sessions.delete(session)
            notifier.send(
                userId,
                "Workout dropped",
                "$name was still running after ${describe(limit)} with no sets logged, so it wasn't saved.",
                URL,
                CATEGORY,
            )
            return
        }

        val endedAt = TrainingRules.autoFinishAt(session.startedAt, logged.maxOf { it.doneAt }, limit)
        val result = complete(session, WorkoutEndedBy.AUTO, endedAt)
        notifier.send(
            userId,
            "Workout ended automatically",
            "$name was still running after ${describe(limit)}, so it was saved as ${result.summary.minutes} min, " +
                "ending shortly after your last set. Open it to check.",
            URL,
            CATEGORY,
        )
    }

    private fun complete(session: WorkoutSession, endedBy: WorkoutEndedBy, endedAt: Instant): FinishWorkoutResponse {
        val userId = session.userId
        session.finishedAt = endedAt
        session.endedBy = endedBy

        val exerciseList = exercises.findAllBySessionIdInOrderBySortOrderAscIdAsc(listOf(session.id!!))
        val setsByExercise = setsByExercise(exerciseList)
        val summary = summary(session, exerciseList, setsByExercise)

        val before = history(userId, exerciseList.map { it.nameKey }, excludeSessionId = session.id!!)
            .mapValues { (_, done) -> done.flatMap { (_, logged) -> logged.map { SetValues(it.reps, it.weightKg) } } }
        val personalBests = TrainingRules.personalBests(
            exerciseList.map { e -> e.name to setsByExercise[e.id!!].orEmpty().map { SetValues(it.reps, it.weightKg) } },
            before,
        ).map { PersonalBestResponse(it.exercise, it.weightKg, it.reps) }

        val routineName = session.planId
            ?.let { plans.findById(it).orElse(null) }
            ?.routineId
            ?.let { routines.findByIdAndUserId(it, userId) }
            ?.also { routineService.markDone(userId, it.id!!, session.day, summary.minutes) }
            ?.name

        val planList = planService.list(userId)
        val history = sessions.findAllByUserIdAndFinishedAtIsNotNull(userId)
        return FinishWorkoutResponse(summary, personalBests, routineName, stats(userId, session.day, planList, history))
    }

    // ---- reading ----

    private fun stats(userId: Long, day: LocalDate, planList: List<PlanResponse>, history: List<WorkoutSession>): TrainingStats {
        val weekStart = RoutineRules.weekStart(day)
        val weekEnd = weekStart.plusDays(6)
        val last = history.maxOfOrNull { it.day }
        return TrainingStats(
            weekStart = weekStart,
            workoutsThisWeek = history.count { !it.day.isBefore(weekStart) && !it.day.isAfter(weekEnd) },
            weeklyTarget = weeklyTarget(userId, planList, weekStart),
            daysSinceLast = last?.let { ChronoUnit.DAYS.between(it, day).toInt().coerceAtLeast(0) },
            totalWorkouts = history.size.toLong(),
        )
    }

    /** How often the user means to train: the sum over the routines their plans are linked to (each counted once). */
    private fun weeklyTarget(userId: Long, planList: List<PlanResponse>, weekStart: LocalDate): Int? {
        val routineIds = planList.mapNotNull { it.routineId }.distinct()
        if (routineIds.isEmpty()) return null
        val target = routines.findAllById(routineIds).filter { it.userId == userId }.map { routine ->
            if (routine.scheduleType == RoutineScheduleType.TIMES_PER_WEEK) {
                routine.timesPerWeek
            } else {
                routine.recurrence()?.let { rule -> (0L..6L).count { rule.occursOn(weekStart.plusDays(it)) } } ?: 0
            }
        }.sum()
        return target.takeIf { it > 0 }
    }

    private fun response(session: WorkoutSession, withPrevious: Boolean): WorkoutResponse {
        val exerciseList = exercises.findAllBySessionIdInOrderBySortOrderAscIdAsc(listOf(session.id!!))
        val setsByExercise = setsByExercise(exerciseList)
        val previous = if (withPrevious) {
            previousPerformance(session.userId, exerciseList.map { it.nameKey }, session.id!!)
        } else {
            emptyMap()
        }
        return WorkoutResponse(
            id = session.id!!,
            planId = session.planId,
            name = session.name,
            day = session.day,
            startedAt = session.startedAt,
            finishedAt = session.finishedAt,
            endedBy = session.endedBy,
            autoFinishAt = session.startedAt.plus(limit),
            exercises = exerciseList.map { e ->
                WorkoutExerciseResponse(
                    id = e.id!!,
                    name = e.name,
                    targetSets = e.targetSets,
                    targetReps = e.targetReps,
                    targetWeightKg = e.targetWeightKg,
                    restSeconds = e.restSeconds,
                    sets = setsByExercise[e.id!!].orEmpty().map { WorkoutSetResponse(it.id!!, it.reps, it.weightKg, it.doneAt) },
                    previous = previous[e.nameKey],
                )
            },
        )
    }

    private fun summaries(finished: List<WorkoutSession>): List<WorkoutSummary> {
        if (finished.isEmpty()) return emptyList()
        val exercisesBySession = exercises.findAllBySessionIdInOrderBySortOrderAscIdAsc(finished.map { it.id!! }).groupBy { it.sessionId }
        val setsByExercise = setsByExercise(exercisesBySession.values.flatten())
        return finished.map { summary(it, exercisesBySession[it.id!!].orEmpty(), setsByExercise) }
    }

    private fun summary(session: WorkoutSession, exerciseList: List<WorkoutExercise>, setsByExercise: Map<Long, List<WorkoutSet>>): WorkoutSummary {
        val done = exerciseList.flatMap { setsByExercise[it.id!!].orEmpty() }
        return WorkoutSummary(
            id = session.id!!,
            name = session.name,
            day = session.day,
            startedAt = session.startedAt,
            minutes = TrainingRules.minutes(session.startedAt, session.finishedAt ?: Instant.now()),
            exercises = exerciseList.count { setsByExercise[it.id!!].orEmpty().isNotEmpty() },
            sets = done.size,
            volumeKg = TrainingRules.volume(done.map { SetValues(it.reps, it.weightKg) }),
            endedBy = session.endedBy,
        )
    }

    private fun setsByExercise(exerciseList: List<WorkoutExercise>): Map<Long, List<WorkoutSet>> =
        if (exerciseList.isEmpty()) emptyMap() else sets.findAllByExerciseIdInOrderByIdAsc(exerciseList.map { it.id!! }).groupBy { it.exerciseId }

    /** Every finished workout (except [excludeSessionId]) in which each of the exercises was done, with what was done. */
    private fun history(userId: Long, nameKeys: Collection<String>, excludeSessionId: Long): Map<String, List<Pair<WorkoutSession, List<WorkoutSet>>>> {
        if (nameKeys.isEmpty()) return emptyMap()
        val past = exercises.findAllByUserIdAndNameKeyIn(userId, nameKeys.toSet()).filter { it.sessionId != excludeSessionId }
        if (past.isEmpty()) return emptyMap()

        val finished = sessions.findAllById(past.map { it.sessionId }.distinct()).filter { it.finishedAt != null }.associateBy { it.id!! }
        val usable = past.filter { it.sessionId in finished }
        val setsByExercise = setsByExercise(usable)
        return usable.groupBy { it.nameKey }.mapValues { (_, list) -> list.map { finished.getValue(it.sessionId) to setsByExercise[it.id!!].orEmpty() } }
    }

    private fun previousPerformance(userId: Long, nameKeys: Collection<String>, currentSessionId: Long): Map<String, PreviousPerformance> =
        history(userId, nameKeys, currentSessionId).mapNotNull { (key, done) ->
            done.filter { (_, logged) -> logged.isNotEmpty() }
                .maxByOrNull { (session, _) -> session.startedAt }
                ?.let { (session, logged) -> key to PreviousPerformance(session.day, logged.map { SetValues(it.reps, it.weightKg) }) }
        }.toMap()

    private fun running(userId: Long, workoutId: Long): WorkoutSession {
        val session = sessions.findByIdAndUserId(workoutId, userId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Workout not found")
        if (session.finishedAt != null) throw ResponseStatusException(HttpStatus.CONFLICT, "This workout is already finished")
        return session
    }

    private fun ownedSet(userId: Long, workoutId: Long, setId: Long): WorkoutSet {
        val set = sets.findByIdAndUserId(setId, userId) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Set not found")
        exercises.findByIdAndSessionId(set.exerciseId, workoutId) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Set not found")
        return set
    }

    private fun describe(duration: Duration): String {
        val minutes = duration.toMinutes()
        return if (minutes >= 60 && minutes % 60 == 0L) "${minutes / 60} h" else "$minutes min"
    }

    private companion object {
        const val RECENT = 8
        const val URL = "/lifestyle/fitness/training"
        const val CATEGORY = "workout"
    }
}

@Component
class TrainingAutoFinishScheduler(private val training: TrainingService) {

    @Scheduled(fixedDelay = 300_000L, initialDelay = 60_000L)
    fun tick() = training.autoFinishStale()
}
