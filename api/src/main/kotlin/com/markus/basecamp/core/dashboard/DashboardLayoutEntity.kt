package com.markus.basecamp.core.dashboard

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant

/** One row per user: their dashboard arrangement as a JSON document (see DashboardLayoutService). */
@Entity
@Table(name = "dashboard_layout", schema = "core")
class DashboardLayoutRow(
    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    val userId: Long,

    @Column(nullable = false)
    var layout: String,
) {
    @Column(nullable = false)
    var updatedAt: Instant = Instant.now()
}

interface DashboardLayoutRepository : JpaRepository<DashboardLayoutRow, Long>
