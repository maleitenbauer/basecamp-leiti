package com.markus.basecamp.logbook.todos

import com.markus.basecamp.core.Notifier
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.server.ResponseStatusException
import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** Configurable daily reminder: todos due today and overdue ones, delivered through the shared [Notifier]. */
@Service
@Transactional
class TodoReminderService(
    private val settings: TodoReminderSettingsRepository,
    private val todos: TodoRepository,
    private val notifier: Notifier,
    transactionManager: PlatformTransactionManager,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val tx = TransactionTemplate(transactionManager)

    fun ensureSettings(userId: Long): TodoReminderSettings =
        settings.findById(userId).orElseGet { settings.save(TodoReminderSettings(userId)) }

    fun get(userId: Long): ReminderSettingsResponse = ensureSettings(userId).toResponse(userId)

    fun update(userId: Long, request: UpdateReminderSettingsRequest): ReminderSettingsResponse {
        request.timezone?.let {
            val zone = try {
                ZoneId.of(it)
            } catch (e: DateTimeException) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown time zone '$it'")
            }
            notifier.setZone(userId, zone)
        }
        val row = ensureSettings(userId)
        row.enabled = request.enabled
        row.remindAt = LocalTime.parse(request.remindAt)
        row.notifyDueToday = request.notifyDueToday
        row.notifyOverdue = request.notifyOverdue
        row.lastSentOn = null // a changed time should apply today, even if today's reminder already went out
        return row.toResponse(userId)
    }

    /** Sends the digest right now (for testing the setup). Respects the two "notify about" switches, ignores the clock. */
    fun sendNow(userId: Long): SendNowResponse {
        val row = ensureSettings(userId)
        val today = ZonedDateTime.now(notifier.zoneOf(userId)).toLocalDate()
        val digest = deliver(row, today)
        return if (digest != null) SendNowResponse(true, digest.title) else SendNowResponse(false, "Nothing is due today or overdue.")
    }

    /** Called every minute by [TodoReminderScheduler]. One user's problem never blocks the others. */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun runDue(now: Instant = Instant.now()) {
        for (userId in settings.findAllByEnabledTrue().map { it.userId }) {
            try {
                tx.executeWithoutResult { sendIfDue(userId, now) }
            } catch (e: Exception) {
                log.warn("Todo reminder for user {} failed: {}", userId, e.message)
            }
        }
    }

    private fun sendIfDue(userId: Long, now: Instant) {
        val row = settings.findById(userId).orElse(null) ?: return
        val local = ZonedDateTime.ofInstant(now, notifier.zoneOf(userId))
        if (!TodoReminderRules.isDue(local.toLocalDate(), local.toLocalTime(), row.remindAt, row.lastSentOn)) return
        // mark first: a reminder must never be sent twice, even if delivery fails halfway
        row.lastSentOn = local.toLocalDate()
        deliver(row, local.toLocalDate())
    }

    private fun deliver(row: TodoReminderSettings, today: LocalDate): TodoReminderRules.Digest? {
        val dueToday = if (row.notifyDueToday) {
            todos.findAllByUserIdAndDoneFalseAndDueDate(row.userId, today).map { it.title }
        } else {
            emptyList()
        }
        val overdue = if (row.notifyOverdue) {
            todos.findAllByUserIdAndDoneFalseAndDueDateLessThan(row.userId, today).sortedBy { it.dueDate }.map { it.title }
        } else {
            emptyList()
        }
        val digest = TodoReminderRules.digest(dueToday, overdue) ?: return null
        notifier.send(row.userId, digest.title, digest.body, "/logbook/todos", "todo-reminder")
        return digest
    }

    private fun TodoReminderSettings.toResponse(userId: Long) = ReminderSettingsResponse(
        enabled = enabled,
        remindAt = remindAt.format(DateTimeFormatter.ofPattern("HH:mm")),
        notifyDueToday = notifyDueToday,
        notifyOverdue = notifyOverdue,
        timezone = notifier.zoneOf(userId).id,
    )
}

@Component
class TodoReminderScheduler(private val reminders: TodoReminderService) {

    @Scheduled(fixedDelay = 60_000L, initialDelay = 30_000L)
    fun tick() = reminders.runDue()
}
