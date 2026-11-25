package com.markus.basecamp.logbook.fitness

import com.markus.basecamp.logbook.routine.RoutineRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

/** Workout plans: a named list of exercises, optionally linked to a routine. Only ever touches the caller's own plans. */
@Service
@Transactional
class TrainingPlanService(
    private val plans: WorkoutPlanRepository,
    private val planExercises: WorkoutPlanExerciseRepository,
    private val sessions: WorkoutSessionRepository,
    private val routines: RoutineRepository,
) {

    @Transactional(readOnly = true)
    fun list(userId: Long): List<PlanResponse> {
        val mine = plans.findAllByUserIdOrderByIdAsc(userId)
        if (mine.isEmpty()) return emptyList()

        val exercisesByPlan = planExercises.findAllByPlanIdInOrderBySortOrderAscIdAsc(mine.map { it.id!! }).groupBy { it.planId }
        val routineNames = routines.findAllByUserIdOrderByIdAsc(userId).associate { it.id!! to it.name }
        val usage = sessions.findAllByUserIdAndFinishedAtIsNotNull(userId).filter { it.planId != null }.groupBy { it.planId!! }

        return mine.map { plan ->
            val used = usage[plan.id!!].orEmpty()
            PlanResponse(
                id = plan.id!!,
                name = plan.name,
                note = plan.note,
                routineId = plan.routineId,
                routineName = plan.routineId?.let { routineNames[it] },
                exercises = exercisesByPlan[plan.id!!].orEmpty().map { it.toResponse() },
                timesDone = used.size,
                lastDoneOn = used.maxOfOrNull { it.day },
            )
        }
    }

    fun create(userId: Long, request: PlanRequest): Long {
        val plan = plans.save(WorkoutPlan(userId, request.name.trim()))
        apply(userId, plan, request)
        return plan.id!!
    }

    fun update(userId: Long, id: Long, request: PlanRequest) {
        val plan = find(userId, id)
        planExercises.deleteAllByPlanId(id)
        planExercises.flush()
        apply(userId, plan, request)
    }

    /** Past workouts keep their own copy of the plan's name and exercises; they just lose the link. */
    fun delete(userId: Long, id: Long) {
        plans.delete(find(userId, id))
    }

    private fun apply(userId: Long, plan: WorkoutPlan, request: PlanRequest) {
        plan.name = request.name.trim()
        plan.note = request.note?.trim()?.takeIf { it.isNotEmpty() }
        plan.routineId = request.routineId?.let {
            routines.findByIdAndUserId(it, userId)?.id ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown routine")
        }
        planExercises.saveAll(
            request.exercises.mapIndexed { index, e ->
                WorkoutPlanExercise(
                    planId = plan.id!!,
                    sortOrder = index,
                    name = e.name.trim(),
                    sets = e.sets,
                    reps = e.reps,
                    weightKg = e.weightKg,
                    restSeconds = e.restSeconds,
                    note = e.note?.trim()?.takeIf { it.isNotEmpty() },
                )
            },
        )
    }

    private fun WorkoutPlanExercise.toResponse() = PlanExerciseResponse(id!!, name, sets, reps, weightKg, restSeconds, note)

    private fun find(userId: Long, id: Long): WorkoutPlan =
        plans.findByIdAndUserId(id, userId) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Plan not found")
}
