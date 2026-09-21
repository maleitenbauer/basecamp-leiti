package com.markus.basecamp.logbook.todos

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

@Entity
@Table(name = "todo", schema = "logbook")
class Todo(
    @Column(nullable = false, updatable = false)
    val userId: Long,

    @Column(nullable = false, length = 300)
    var title: String,

    var note: String? = null,

    var dueDate: LocalDate? = null,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @Column(nullable = false)
    var done: Boolean = false

    var doneAt: Instant? = null

    @Column(nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()

    @Column(nullable = false)
    var updatedAt: Instant = Instant.now()
}

/** One row per user, created lazily. Reminders run in the user's time zone (see Notifier.zoneOf). */
@Entity
@Table(name = "todo_reminder_settings", schema = "logbook")
class TodoReminderSettings(
    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    val userId: Long,

    @Column(nullable = false)
    var enabled: Boolean = true,

    @Column(nullable = false)
    var remindAt: LocalTime = LocalTime.of(8, 0),

    @Column(nullable = false)
    var notifyDueToday: Boolean = true,

    @Column(nullable = false)
    var notifyOverdue: Boolean = true,

    var lastSentOn: LocalDate? = null,
)

interface TodoRepository : JpaRepository<Todo, Long> {
    fun findAllByUserIdAndDoneFalseOrderByDueDateAscIdAsc(userId: Long): List<Todo>
    fun findTop100ByUserIdAndDoneTrueOrderByDoneAtDesc(userId: Long): List<Todo>
    fun countByUserIdAndDoneTrue(userId: Long): Long
    fun findByIdAndUserId(id: Long, userId: Long): Todo?
    fun findAllByUserIdAndDoneFalseAndDueDate(userId: Long, dueDate: LocalDate): List<Todo>
    fun findAllByUserIdAndDoneFalseAndDueDateLessThan(userId: Long, before: LocalDate): List<Todo>
}

interface TodoReminderSettingsRepository : JpaRepository<TodoReminderSettings, Long> {
    fun findAllByEnabledTrue(): List<TodoReminderSettings>
}
