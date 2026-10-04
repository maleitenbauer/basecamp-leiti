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

/** Lifestyle > Fitness > Nutrition. The caller's identity always comes from the session. */
@RestController
@RequestMapping("/api/logbook/fitness/nutrition")
class NutritionController(
    private val service: NutritionService,
    private val currentUser: CurrentUserProvider,
) {
    private val userId: Long get() = currentUser.get().id

    @GetMapping("/goal")
    fun goal() = service.goal(userId)

    @PutMapping("/goal")
    fun setGoal(@Valid @RequestBody request: SetNutritionGoalRequest) = service.setGoal(userId, request)

    @GetMapping("/day")
    fun day(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) date: LocalDate) = service.day(userId, date)

    @PostMapping("/entries")
    @ResponseStatus(HttpStatus.CREATED)
    fun addEntry(@Valid @RequestBody request: AddMealEntryRequest) = service.addEntry(userId, request)

    @PatchMapping("/entries/{id}")
    fun updateEntry(@PathVariable id: Long, @Valid @RequestBody request: UpdateMealEntryRequest) =
        service.updateEntry(userId, id, request)

    @DeleteMapping("/entries/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteEntry(@PathVariable id: Long) = service.deleteEntry(userId, id)

    @GetMapping("/foods")
    fun foods() = service.listFoods(userId)

    @GetMapping("/foods/online")
    fun searchOnline(@RequestParam q: String) = service.searchOnline(q)

    @PostMapping("/foods")
    @ResponseStatus(HttpStatus.CREATED)
    fun createFood(@Valid @RequestBody request: CreateFoodRequest) = service.createFood(userId, request)

    @PatchMapping("/foods/{id}")
    fun updateFood(@PathVariable id: Long, @Valid @RequestBody request: UpdateFoodRequest) =
        service.updateFood(userId, id, request)

    @DeleteMapping("/foods/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteFood(@PathVariable id: Long) = service.deleteFood(userId, id)
}
