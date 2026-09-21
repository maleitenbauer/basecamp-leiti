package com.markus.basecamp.logbook.todos

import com.markus.basecamp.core.CurrentUserProvider
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/** Logbook > Todos. The caller's identity always comes from the session. */
@RestController
@RequestMapping("/api/logbook/todos")
class TodoController(
    private val todos: TodoService,
    private val reminders: TodoReminderService,
    private val currentUser: CurrentUserProvider,
) {
    private val userId: Long get() = currentUser.get().id

    @GetMapping
    fun list() = todos.list(userId)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@Valid @RequestBody request: CreateTodoRequest) = todos.create(userId, request)

    @PatchMapping("/{id}")
    fun update(@PathVariable id: Long, @Valid @RequestBody request: UpdateTodoRequest) =
        todos.update(userId, id, request)

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable id: Long) = todos.delete(userId, id)

    @GetMapping("/reminders")
    fun reminderSettings() = reminders.get(userId)

    @PutMapping("/reminders")
    fun updateReminderSettings(@Valid @RequestBody request: UpdateReminderSettingsRequest) =
        reminders.update(userId, request)

    @PostMapping("/reminders/send-now")
    fun sendNow() = reminders.sendNow(userId)
}
