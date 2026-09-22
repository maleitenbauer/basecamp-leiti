package com.markus.basecamp.core.notifications

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant

data class NotificationItem(
    val id: Long,
    val title: String,
    val body: String?,
    val url: String?,
    val category: String,
    val createdAt: Instant,
    val read: Boolean,
)

data class NotificationsResponse(val unread: Long, val items: List<NotificationItem>)

/** [endpoint] is only ever returned to the user who owns the device; the browser compares it to find "this device". */
data class DeviceResponse(val id: Long, val label: String, val endpoint: String, val createdAt: Instant, val lastUsedAt: Instant?)

data class NotificationConfigResponse(
    /** false until the server has VAPID keys */
    val pushAvailable: Boolean,
    /** Set when the server's VAPID keys are misconfigured; says what is wrong. Push cannot work until it is fixed. */
    val pushProblem: String?,
    val vapidPublicKey: String?,
    val timezone: String,
    val devices: List<DeviceResponse>,
)

/** What happened to one device when a notification was pushed. [detail] explains a failure in plain words. */
data class DeviceDelivery(val device: String, val delivered: Boolean, val detail: String)

data class TestResult(val pushAvailable: Boolean, val deviceCount: Int, val deliveries: List<DeviceDelivery>)

data class UpdateNotificationSettingsRequest(
    @field:NotBlank @field:Size(max = 64) val timezone: String,
)

data class SubscribeRequest(
    @field:NotBlank @field:Size(max = 2000) val endpoint: String,
    @field:NotBlank @field:Size(max = 255) val p256dh: String,
    @field:NotBlank @field:Size(max = 255) val auth: String,
    @field:Size(max = 255) val userAgent: String? = null,
)
