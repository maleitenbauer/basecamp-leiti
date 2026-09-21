package com.markus.basecamp.logbook.todos

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Instant

/** Every method takes the calling user's id and only ever touches that user's todos. */
@Service
@Transactional
class TodoService(
    private val todos: TodoRepository,
    private val reminders: TodoReminderService,
) {

    @Transactional(readOnly = true)
    fun list(userId: Long) = TodoListResponse(
        open = todos.findAllByUserIdAndDoneFalseOrderByDueDateAscIdAsc(userId).map { it.toResponse() },
        done = todos.findTop100ByUserIdAndDoneTrueOrderByDoneAtDesc(userId).map { it.toResponse() },
        doneTotal = todos.countByUserIdAndDoneTrue(userId),
    )

    fun create(userId: Long, request: CreateTodoRequest): TodoResponse {
        val todo = todos.save(
            Todo(
                userId = userId,
                title = request.title.trim(),
                note = request.note?.trim()?.ifEmpty { null },
                dueDate = request.dueDate,
            ),
        )
        // deadlines are what reminders are about, so make sure the user has (default) reminder settings
        if (request.dueDate != null) reminders.ensureSettings(userId)
        return todo.toResponse()
    }

    fun update(userId: Long, id: Long, request: UpdateTodoRequest): TodoResponse {
        val todo = find(userId, id)
        request.title?.let { todo.title = it.trim() }
        if (request.clearDueDate == true) todo.dueDate = null else request.dueDate?.let { todo.dueDate = it }
        request.note?.let { todo.note = it.trim().ifEmpty { null } }
        request.done?.let {
            if (it != todo.done) {
                todo.done = it
                todo.doneAt = if (it) Instant.now() else null
            }
        }
        todo.updatedAt = Instant.now()
        if (todo.dueDate != null) reminders.ensureSettings(userId)
        return todo.toResponse()
    }

    fun delete(userId: Long, id: Long) {
        todos.delete(find(userId, id))
    }

    private fun find(userId: Long, id: Long): Todo =
        todos.findByIdAndUserId(id, userId) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Todo not found")

    private fun Todo.toResponse() = TodoResponse(id!!, title, note, dueDate, done, doneAt, createdAt)
}
