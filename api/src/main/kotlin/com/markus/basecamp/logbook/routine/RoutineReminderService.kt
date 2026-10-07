package com.markus.basecamp.logbook.routine

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

/** Configurable daily reminder of the routines still open, delivered through the shared [Notifier]. */
@Service
@Transactional
class RoutineReminderService(
    private val settings: RoutineReminderSettingsRepository,
    private val routines: RoutineService,
    private val notifier: Notifier,
    transactionManager: PlatformTransactionManager,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val tx = TransactionTemplate(transactionManager)

    fun ensureSettings(userId: Long): RoutineReminderSettings =
        settings.findById(userId).orElseGet { settings.save(RoutineReminderSettings(userId)) }

    fun get(userId: Long): RoutineReminderResponse = ensureSettings(userId).toResponse(userId)

    fun update(userId: Long, request: UpdateRoutineReminderRequest): RoutineReminderResponse {
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
        row.lastSentOn = null // a changed time should apply today, even if today's reminder already went out
        return row.toResponse(userId)
    }

    /** Sends the reminder right now (for testing the setup). Ignores the clock and whether one already went out today. */
    fun sendNow(userId: Long): RoutineSendNowResponse {
        val today = ZonedDateTime.now(notifier.zoneOf(userId)).toLocalDate()
        val digest = deliver(userId, today)
        return if (digest != null) RoutineSendNowResponse(true, digest.title) else RoutineSendNowResponse(false, "Nothing is open today.")
    }

    /** Called every minute by [RoutineReminderScheduler]. One user's problem never blocks the others. */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun runDue(now: Instant = Instant.now()) {
        for (userId in settings.findAllByEnabledTrue().map { it.userId }) {
            try {
                tx.executeWithoutResult { sendIfDue(userId, now) }
            } catch (e: Exception) {
                log.warn("Routine reminder for user {} failed: {}", userId, e.message)
            }
        }
    }

    private fun sendIfDue(userId: Long, now: Instant) {
        val row = settings.findById(userId).orElse(null) ?: return
        val local = ZonedDateTime.ofInstant(now, notifier.zoneOf(userId))
        if (!RoutineRules.isDue(local.toLocalDate(), local.toLocalTime(), row.remindAt, row.lastSentOn)) return
        // mark first: a reminder must never be sent twice, even if delivery fails halfway
        row.lastSentOn = local.toLocalDate()
        deliver(userId, local.toLocalDate())
    }

    private fun deliver(userId: Long, today: LocalDate): RoutineRules.Digest? {
        val open = routines.statuses(userId, today).filter { it.open }
        val digest = RoutineRules.digest(open) ?: return null
        notifier.send(userId, digest.title, digest.body, "/lifestyle/routine", "routine-reminder")
        return digest
    }

    private fun RoutineReminderSettings.toResponse(userId: Long) = RoutineReminderResponse(
        enabled = enabled,
        remindAt = remindAt.format(DateTimeFormatter.ofPattern("HH:mm")),
        timezone = notifier.zoneOf(userId).id,
    )
}

@Component
class RoutineReminderScheduler(private val reminders: RoutineReminderService) {

    @Scheduled(fixedDelay = 60_000L, initialDelay = 30_000L)
    fun tick() = reminders.runDue()
}
