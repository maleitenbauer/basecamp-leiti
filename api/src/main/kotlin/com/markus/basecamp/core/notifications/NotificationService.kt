package com.markus.basecamp.core.notifications

import com.fasterxml.jackson.databind.ObjectMapper
import com.markus.basecamp.core.Notifier
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.DateTimeException
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

@Service
@Transactional
class NotificationService(
    private val notifications: AppNotificationRepository,
    private val subscriptions: PushSubscriptionRepository,
    private val prefs: NotificationPrefsRepository,
    private val push: WebPushSender,
    private val mapper: ObjectMapper,
) : Notifier {

    private val log = LoggerFactory.getLogger(javaClass)

    // ---- Notifier (used by other modules) ----

    override fun send(userId: Long, title: String, body: String?, url: String?, category: String) {
        notifications.save(AppNotification(userId, title.take(200), body, url?.take(500), category))
        pushToDevices(userId, title, body, url)
    }

    @Transactional(readOnly = true)
    override fun zoneOf(userId: Long): ZoneId =
        prefs.findById(userId).map { runCatching { ZoneId.of(it.timezone) }.getOrDefault(ZoneId.of("UTC")) }
            .orElse(ZoneId.of("UTC"))

    override fun setZone(userId: Long, zone: ZoneId) {
        val row = prefs.findById(userId).orElseGet { NotificationPrefs(userId) }
        row.timezone = zone.id
        prefs.save(row)
    }

    // ---- bell / settings / devices (used by the controller) ----

    @Transactional(readOnly = true)
    fun list(userId: Long): NotificationsResponse =
        NotificationsResponse(
            unread = notifications.countByUserIdAndReadAtIsNull(userId),
            items = notifications.findTop30ByUserIdOrderByCreatedAtDescIdDesc(userId).map {
                NotificationItem(it.id!!, it.title, it.body, it.url, it.category, it.createdAt, it.readAt != null)
            },
        )

    fun markAllRead(userId: Long) {
        notifications.markAllRead(userId, Instant.now())
    }

    fun markRead(userId: Long, id: Long) {
        val notification = notifications.findByIdAndUserId(id, userId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found")
        if (notification.readAt == null) notification.readAt = Instant.now()
    }

    @Transactional(readOnly = true)
    fun config(userId: Long) = NotificationConfigResponse(
        pushAvailable = push.configured,
        vapidPublicKey = push.publicKey.takeIf { push.configured },
        timezone = zoneOf(userId).id,
        devices = subscriptions.findAllByUserIdOrderByCreatedAtDesc(userId).map {
            DeviceResponse(it.id!!, deviceLabel(it.userAgent), it.endpoint, it.createdAt, it.lastUsedAt)
        },
    )

    fun updateTimezone(userId: Long, timezone: String) {
        val zone = try {
            ZoneId.of(timezone)
        } catch (e: DateTimeException) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown time zone '$timezone'")
        }
        setZone(userId, zone)
    }

    fun subscribe(userId: Long, request: SubscribeRequest) {
        if (!push.configured) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Push is not set up on the server (VAPID keys are missing)")
        }
        if (!PushEndpointPolicy.isAllowed(request.endpoint)) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported push service")
        }
        // the same browser can only belong to one user: re-registering an endpoint takes it over
        val existing = subscriptions.findByEndpoint(request.endpoint)
        if (existing != null) {
            existing.userId = userId
            existing.p256dh = request.p256dh
            existing.auth = request.auth
            existing.userAgent = request.userAgent
        } else {
            subscriptions.save(PushSubscription(userId, request.endpoint, request.p256dh, request.auth, request.userAgent))
        }
    }

    fun unsubscribe(userId: Long, id: Long) {
        subscriptions.delete(
            subscriptions.findByIdAndUserId(id, userId)
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Device not found"),
        )
    }

    fun sendTest(userId: Long) {
        send(userId, "Test notification", "If you can read this, notifications work on this device.", "/", "test")
    }

    /** Old notifications are not kept forever. */
    @Scheduled(cron = "0 30 3 * * *")
    fun cleanUp() {
        val removed = notifications.deleteOlderThan(Instant.now().minus(Duration.ofDays(60)))
        if (removed > 0) log.info("Removed {} old notifications", removed)
    }

    private fun pushToDevices(userId: Long, title: String, body: String?, url: String?) {
        if (!push.configured) return
        val devices = subscriptions.findAllByUserIdOrderByCreatedAtDesc(userId)
        if (devices.isEmpty()) return
        val payload = mapper.writeValueAsString(mapOf("title" to title, "body" to (body ?: ""), "url" to (url ?: "/")))
        for (device in devices) {
            when (push.send(device, payload)) {
                WebPushSender.Result.DELIVERED -> device.lastUsedAt = Instant.now()
                WebPushSender.Result.GONE -> subscriptions.delete(device)
                WebPushSender.Result.FAILED -> Unit
            }
        }
    }

    private fun deviceLabel(userAgent: String?): String {
        val ua = userAgent.orEmpty()
        val system = when {
            "Android" in ua -> "Android"
            "iPhone" in ua || "iPad" in ua -> "iPhone / iPad"
            "Windows" in ua -> "Windows"
            "Mac OS" in ua -> "Mac"
            "Linux" in ua -> "Linux"
            else -> "Device"
        }
        val browser = when {
            "Edg/" in ua -> "Edge"
            "Firefox/" in ua -> "Firefox"
            "Chrome/" in ua -> "Chrome"
            "Safari/" in ua -> "Safari"
            else -> ""
        }
        return if (browser.isEmpty()) system else "$system · $browser"
    }
}
