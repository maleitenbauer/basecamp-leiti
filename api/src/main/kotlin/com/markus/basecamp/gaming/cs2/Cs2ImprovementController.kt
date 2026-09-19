package com.markus.basecamp.gaming.cs2

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

/** Gaming > Counter-Strike 2 > Improvement. The caller's identity always comes from the session. */
@RestController
@RequestMapping("/api/gaming/cs2/improvement")
class Cs2ImprovementController(
    private val service: Cs2ImprovementService,
    private val currentUser: CurrentUserProvider,
) {
    private val userId: Long get() = currentUser.get().id

    // routine

    @GetMapping("/routine")
    fun routine(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) date: LocalDate?) =
        service.routine(userId, date ?: LocalDate.now())

    @PostMapping("/routine/items")
    @ResponseStatus(HttpStatus.CREATED)
    fun createItem(@Valid @RequestBody request: CreateRoutineItemRequest) = service.createItem(userId, request)

    @PatchMapping("/routine/items/{id}")
    fun updateItem(@PathVariable id: Long, @Valid @RequestBody request: UpdateRoutineItemRequest) =
        service.updateItem(userId, id, request)

    @DeleteMapping("/routine/items/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteItem(@PathVariable id: Long) = service.deleteItem(userId, id)

    @PutMapping("/routine/items/{id}/days/{date}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun setDone(
        @PathVariable id: Long,
        @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) date: LocalDate,
        @Valid @RequestBody request: SetDoneRequest,
    ) = service.setDone(userId, id, date, request)

    // session reviews

    @GetMapping("/reviews")
    fun reviews() = service.listReviews(userId)

    @PostMapping("/reviews")
    @ResponseStatus(HttpStatus.CREATED)
    fun createReview(@Valid @RequestBody request: CreateReviewRequest) = service.createReview(userId, request)

    @DeleteMapping("/reviews/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteReview(@PathVariable id: Long) = service.deleteReview(userId, id)

    // principles

    @GetMapping("/principles")
    fun principles() = service.listPrinciples(userId)

    @PostMapping("/principles")
    @ResponseStatus(HttpStatus.CREATED)
    fun createPrinciple(@Valid @RequestBody request: CreatePrincipleRequest) = service.createPrinciple(userId, request)

    @PatchMapping("/principles/{id}")
    fun updatePrinciple(@PathVariable id: Long, @Valid @RequestBody request: UpdatePrincipleRequest) =
        service.updatePrinciple(userId, id, request)

    @DeleteMapping("/principles/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deletePrinciple(@PathVariable id: Long) = service.deletePrinciple(userId, id)
}
