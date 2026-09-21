package com.markus.basecamp.core.notifications

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "notification", schema = "core")
class AppNotification(
    @Column(nullable = false, updatable = false)
    val userId: Long,

    @Column(nullable = false, length = 200)
    var title: String,

    var body: String? = null,

    @Column(length = 500)
    var url: String? = null,

    @Column(nullable = false, length = 40)
    var category: String = "general",
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @Column(nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()

    var readAt: Instant? = null
}

@Entity
@Table(name = "push_subscription", schema = "core")
class PushSubscription(
    @Column(nullable = false)
    var userId: Long,

    @Column(nullable = false)
    var endpoint: String,

    @Column(nullable = false, length = 255)
    var p256dh: String,

    @Column(nullable = false, length = 255)
    var auth: String,

    @Column(length = 255)
    var userAgent: String? = null,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @Column(nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()

    var lastUsedAt: Instant? = null
}

@Entity
@Table(name = "notification_prefs", schema = "core")
class NotificationPrefs(
    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    val userId: Long,

    @Column(nullable = false, length = 64)
    var timezone: String = "UTC",
)
