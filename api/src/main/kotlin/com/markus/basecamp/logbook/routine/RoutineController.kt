package com.markus.basecamp.logbook.routine

import com.markus.basecamp.core.CurrentUserProvider
import jakarta.validation.Valid
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

/** Lifestyle > Routine. The caller's identity always comes from the session. */
@RestController
@RequestMapping("/api/logbook/routines")
class RoutineController(
    private val routines: RoutineService,
    private val reminders: RoutineReminderService,
    private val currentUser: CurrentUserProvider,
) {
    private val userId: Long get() = currentUser.get().id

    @GetMapping
    fun overview(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) date: LocalDate) =
        routines.overview(userId, date)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@Valid @RequestBody request: RoutineRequest) = mapOf("id" to routines.create(userId, request))

    @PutMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun update(@PathVariable id: Long, @Valid @RequestBody request: RoutineRequest) = routines.update(userId, id, request)

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable id: Long) = routines.delete(userId, id)

    @PutMapping("/{id}/completions/{date}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun markDone(
        @PathVariable id: Long,
        @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) date: LocalDate,
        @Valid @RequestBody request: MarkRoutineDoneRequest,
    ) = routines.markDone(userId, id, date, request.minutes)

    @DeleteMapping("/{id}/completions/{date}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun undo(
        @PathVariable id: Long,
        @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) date: LocalDate,
    ) = routines.undo(userId, id, date)

    @GetMapping("/reminder")
    fun reminder() = reminders.get(userId)

    @PutMapping("/reminder")
    fun updateReminder(@Valid @RequestBody request: UpdateRoutineReminderRequest) = reminders.update(userId, request)

    @PostMapping("/reminder/send-now")
    fun sendNow() = reminders.sendNow(userId)
}
