package com.markus.basecamp.logbook.todos

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.time.Instant
import java.time.LocalDate

data class TodoResponse(
    val id: Long,
    val title: String,
    val note: String?,
    val dueDate: LocalDate?,
    val done: Boolean,
    val doneAt: Instant?,
    val createdAt: Instant,
)

/** [done] holds the 100 most recently finished todos; [doneTotal] is the real count. */
data class TodoListResponse(val open: List<TodoResponse>, val done: List<TodoResponse>, val doneTotal: Long)

data class CreateTodoRequest(
    @field:NotBlank @field:Size(max = 300) val title: String,
    val dueDate: LocalDate? = null,
    @field:Size(max = 4000) val note: String? = null,
)

/** Partial update. Send [clearDueDate] = true to remove the deadline; an empty [note] removes the note. */
data class UpdateTodoRequest(
    @field:Size(min = 1, max = 300) val title: String? = null,
    val dueDate: LocalDate? = null,
    val clearDueDate: Boolean? = null,
    @field:Size(max = 4000) val note: String? = null,
    val done: Boolean? = null,
)

data class ReminderSettingsResponse(
    val enabled: Boolean,
    /** HH:mm in the user's time zone */
    val remindAt: String,
    val notifyDueToday: Boolean,
    val notifyOverdue: Boolean,
    val timezone: String,
)

data class UpdateReminderSettingsRequest(
    val enabled: Boolean,
    @field:Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$", message = "Use HH:mm, for example 08:00")
    val remindAt: String,
    val notifyDueToday: Boolean,
    val notifyOverdue: Boolean,
    /** IANA name such as Europe/Vienna; the browser reports it. Optional. */
    @field:Size(max = 64) val timezone: String? = null,
)

data class SendNowResponse(val sent: Boolean, val message: String)
