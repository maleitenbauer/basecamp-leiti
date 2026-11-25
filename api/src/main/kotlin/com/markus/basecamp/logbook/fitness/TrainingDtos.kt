package com.markus.basecamp.logbook.fitness

import jakarta.validation.Valid
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant
import java.time.LocalDate

// ---- plans ----

data class PlanExerciseRequest(
    @field:NotBlank @field:Size(max = 200) val name: String,
    @field:Min(1) @field:Max(20) val sets: Int = 3,
    @field:Min(1) @field:Max(200) val reps: Int = 10,
    /** Null means bodyweight / "pick the weight on the day". */
    @field:DecimalMin("0") @field:DecimalMax("1000") val weightKg: Double? = null,
    @field:Min(0) @field:Max(900) val restSeconds: Int? = null,
    @field:Size(max = 300) val note: String? = null,
)

/** Create and (full) update share this shape; an update replaces the whole exercise list. */
data class PlanRequest(
    @field:NotBlank @field:Size(max = 200) val name: String,
    @field:Size(max = 500) val note: String? = null,
    /** A routine of the same user: finishing a workout from this plan ticks it off for that day. */
    val routineId: Long? = null,
    @field:Valid @field:Size(max = 40) val exercises: List<PlanExerciseRequest> = emptyList(),
)

data class PlanExerciseResponse(
    val id: Long,
    val name: String,
    val sets: Int,
    val reps: Int,
    val weightKg: Double?,
    val restSeconds: Int?,
    val note: String?,
)

data class PlanResponse(
    val id: Long,
    val name: String,
    val note: String?,
    val routineId: Long?,
    val routineName: String?,
    val exercises: List<PlanExerciseResponse>,
    val timesDone: Int,
    val lastDoneOn: LocalDate?,
)

// ---- workouts ----

/** [planId] null starts an empty workout to fill in as you go. [day] is the user's calendar day, as their browser sees it. */
data class StartWorkoutRequest(
    val planId: Long? = null,
    val day: LocalDate,
)

data class SetRequest(
    @field:Min(1) @field:Max(500) val reps: Int,
    @field:DecimalMin("0") @field:DecimalMax("1000") val weightKg: Double? = null,
)

data class AddWorkoutExerciseRequest(
    @field:NotBlank @field:Size(max = 200) val name: String,
)

data class WorkoutSetResponse(val id: Long, val reps: Int, val weightKg: Double?, val doneAt: Instant)

data class SetValues(val reps: Int, val weightKg: Double?)

/** What was done the last time this exercise was done in a finished workout. */
data class PreviousPerformance(val day: LocalDate, val sets: List<SetValues>)

data class WorkoutExerciseResponse(
    val id: Long,
    val name: String,
    val targetSets: Int?,
    val targetReps: Int?,
    val targetWeightKg: Double?,
    val restSeconds: Int?,
    val sets: List<WorkoutSetResponse>,
    /** Only filled in while the workout is running. */
    val previous: PreviousPerformance?,
)

data class WorkoutResponse(
    val id: Long,
    val planId: Long?,
    val name: String,
    val day: LocalDate,
    val startedAt: Instant,
    val finishedAt: Instant?,
    val endedBy: WorkoutEndedBy?,
    /** When a running workout is ended automatically if nothing happens before then. */
    val autoFinishAt: Instant,
    val exercises: List<WorkoutExerciseResponse>,
)

data class WorkoutSummary(
    val id: Long,
    val name: String,
    val day: LocalDate,
    val startedAt: Instant,
    val minutes: Int,
    val exercises: Int,
    val sets: Int,
    val volumeKg: Double,
    val endedBy: WorkoutEndedBy?,
)

data class TrainingStats(
    val weekStart: LocalDate,
    val workoutsThisWeek: Int,
    /** From the routines the plans are linked to; null when no plan is linked to a routine. */
    val weeklyTarget: Int?,
    val daysSinceLast: Int?,
    val totalWorkouts: Long,
)

data class TrainingOverview(
    val active: WorkoutResponse?,
    val plans: List<PlanResponse>,
    val stats: TrainingStats,
    val recent: List<WorkoutSummary>,
)

data class PersonalBestResponse(val exercise: String, val weightKg: Double?, val reps: Int)

data class FinishWorkoutResponse(
    val summary: WorkoutSummary,
    val personalBests: List<PersonalBestResponse>,
    /** The name of the routine that was ticked off for the day, if the plan is linked to one. */
    val routineCompleted: String?,
    val stats: TrainingStats,
)
