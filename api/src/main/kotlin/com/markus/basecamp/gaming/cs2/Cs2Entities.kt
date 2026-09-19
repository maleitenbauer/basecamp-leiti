package com.markus.basecamp.gaming.cs2

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.time.LocalDate

enum class RoutineCategory { WARMUP, PRACTICE }

enum class PrincipleCategory { MINDSET, MOVEMENT, UTILITY, AIM, PRACTICE, PREPARATION }

@Entity
@Table(name = "cs2_routine_item", schema = "gaming")
class RoutineItem(
    @Column(nullable = false, updatable = false)
    val userId: Long,

    @Column(nullable = false, length = 200)
    var title: String,

    var description: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var category: RoutineCategory = RoutineCategory.PRACTICE,

    var targetMinutes: Int? = null,

    @Column(nullable = false)
    var sortOrder: Int = 0,

    @Column(nullable = false)
    var active: Boolean = true,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @Column(nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()
}

@Entity
@Table(name = "cs2_routine_log", schema = "gaming")
class RoutineLog(
    @Column(nullable = false, updatable = false)
    val userId: Long,

    @Column(nullable = false, updatable = false)
    val itemId: Long,

    @Column(nullable = false, updatable = false)
    val logDate: LocalDate,

    var minutes: Int? = null,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @Column(nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()
}

@Entity
@Table(name = "cs2_session_review", schema = "gaming")
class SessionReview(
    @Column(nullable = false, updatable = false)
    val userId: Long,

    @Column(nullable = false)
    var playedOn: LocalDate,

    @Column(nullable = false)
    var focus: Int,

    @Column(nullable = false)
    var movement: Int,

    @Column(nullable = false)
    var utility: Int,

    var notes: String? = null,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @Column(nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()
}

@Entity
@Table(name = "cs2_principle", schema = "gaming")
class Principle(
    @Column(nullable = false, updatable = false)
    val userId: Long,

    @Column(nullable = false, length = 200)
    var title: String,

    var body: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var category: PrincipleCategory = PrincipleCategory.MINDSET,

    @Column(nullable = false)
    var pinned: Boolean = false,

    @Column(nullable = false)
    var sortOrder: Int = 0,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @Column(nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()
}
