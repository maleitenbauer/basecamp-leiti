package com.markus.basecamp.core.notifications

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

interface AppNotificationRepository : JpaRepository<AppNotification, Long> {
    fun findTop30ByUserIdOrderByCreatedAtDescIdDesc(userId: Long): List<AppNotification>
    fun countByUserIdAndReadAtIsNull(userId: Long): Long
    fun findByIdAndUserId(id: Long, userId: Long): AppNotification?

    @Modifying
    @Query("update AppNotification n set n.readAt = :now where n.userId = :userId and n.readAt is null")
    fun markAllRead(@Param("userId") userId: Long, @Param("now") now: Instant): Int

    @Modifying
    @Query("delete from AppNotification n where n.createdAt < :before")
    fun deleteOlderThan(@Param("before") before: Instant): Int
}

interface PushSubscriptionRepository : JpaRepository<PushSubscription, Long> {
    fun findAllByUserIdOrderByCreatedAtDesc(userId: Long): List<PushSubscription>
    fun findByEndpoint(endpoint: String): PushSubscription?
    fun findByIdAndUserId(id: Long, userId: Long): PushSubscription?
}

interface NotificationPrefsRepository : JpaRepository<NotificationPrefs, Long>
