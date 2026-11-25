package com.markus.basecamp.logbook.fitness

import com.markus.basecamp.core.CurrentUserProvider
import jakarta.validation.Valid
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

/** Lifestyle > Fitness > Training. The caller's identity always comes from the session. */
@RestController
@RequestMapping("/api/logbook/fitness/training")
class TrainingController(
    private val plans: TrainingPlanService,
    private val training: TrainingService,
    private val currentUser: CurrentUserProvider,
) {
    private val userId: Long get() = currentUser.get().id

    @GetMapping
    fun overview(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) date: LocalDate) = training.overview(userId, date)

    // ---- plans ----

    @PostMapping("/plans")
    @ResponseStatus(HttpStatus.CREATED)
    fun createPlan(@Valid @RequestBody request: PlanRequest) = mapOf("id" to plans.create(userId, request))

    @PutMapping("/plans/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun updatePlan(@PathVariable id: Long, @Valid @RequestBody request: PlanRequest) = plans.update(userId, id, request)

    @DeleteMapping("/plans/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deletePlan(@PathVariable id: Long) = plans.delete(userId, id)

    // ---- workouts ----

    @PostMapping("/workouts")
    @ResponseStatus(HttpStatus.CREATED)
    fun start(@Valid @RequestBody request: StartWorkoutRequest) = training.start(userId, request)

    @PostMapping("/workouts/{id}/exercises")
    @ResponseStatus(HttpStatus.CREATED)
    fun addExercise(@PathVariable id: Long, @Valid @RequestBody request: AddWorkoutExerciseRequest) =
        training.addExercise(userId, id, request)

    @PostMapping("/workouts/{id}/exercises/{exerciseId}/sets")
    @ResponseStatus(HttpStatus.CREATED)
    fun logSet(@PathVariable id: Long, @PathVariable exerciseId: Long, @Valid @RequestBody request: SetRequest) =
        training.logSet(userId, id, exerciseId, request)

    @PatchMapping("/workouts/{id}/sets/{setId}")
    fun updateSet(@PathVariable id: Long, @PathVariable setId: Long, @Valid @RequestBody request: SetRequest) =
        training.updateSet(userId, id, setId, request)

    @DeleteMapping("/workouts/{id}/sets/{setId}")
    fun deleteSet(@PathVariable id: Long, @PathVariable setId: Long) = training.deleteSet(userId, id, setId)

    @PostMapping("/workouts/{id}/finish")
    fun finish(@PathVariable id: Long) = training.finish(userId, id)

    @DeleteMapping("/workouts/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun discard(@PathVariable id: Long) = training.discard(userId, id)
}
