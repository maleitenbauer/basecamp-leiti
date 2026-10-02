package com.markus.basecamp.gaming.cs2.analysis

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant

enum class DemoStatus { UPLOADED, PARSING, READY, FAILED }

@Entity
@Table(name = "cs2_demo", schema = "gaming")
class Cs2Demo(
    @Column(nullable = false, updatable = false)
    val userId: Long,

    @Column(nullable = false, length = 255)
    var originalFilename: String,

    @Column(nullable = false)
    var sizeBytes: Long,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @Column(length = 64)
    var map: String? = null

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: DemoStatus = DemoStatus.UPLOADED

    var errorMessage: String? = null

    @Column(nullable = false, updatable = false)
    var uploadedAt: Instant = Instant.now()

    var parsedAt: Instant? = null
}

@Entity
@Table(name = "cs2_demo_round", schema = "gaming")
class Cs2DemoRound(
    @Column(nullable = false, updatable = false)
    val demoId: Long,

    @Column(nullable = false)
    val roundNumber: Int,

    @Column(length = 4)
    val winnerTeam: String?,

    @Column(nullable = false)
    val ctScore: Int,

    @Column(nullable = false)
    val tScore: Int,

    val freezeTimeEndTick: Int?,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
}

@Entity
@Table(name = "cs2_demo_kill", schema = "gaming")
class Cs2DemoKill(
    @Column(nullable = false, updatable = false)
    val demoId: Long,

    @Column(nullable = false)
    val roundNumber: Int,

    @Column(nullable = false)
    val tick: Int,

    @Column(length = 20)
    val attackerSteamId: String?,
    @Column(length = 64)
    val attackerName: String?,
    @Column(length = 4)
    val attackerTeam: String?,
    // Explicit column names: Hibernate's camelCase->snake_case naming strategy only inserts an underscore before
    // an uppercase letter that has a following character, so a trailing single capital (the X/Y/Z here) is missed
    // and would otherwise map to "attackerx" instead of "attacker_x".
    @Column(name = "attacker_x")
    val attackerX: Double?,
    @Column(name = "attacker_y")
    val attackerY: Double?,
    @Column(name = "attacker_z")
    val attackerZ: Double?,

    @Column(length = 20)
    val victimSteamId: String?,
    @Column(length = 64)
    val victimName: String?,
    @Column(length = 4)
    val victimTeam: String?,
    @Column(name = "victim_x", nullable = false)
    val victimX: Double,
    @Column(name = "victim_y", nullable = false)
    val victimY: Double,
    @Column(name = "victim_z", nullable = false)
    val victimZ: Double,

    @Column(nullable = false, length = 64)
    val weapon: String,
    @Column(nullable = false)
    val headshot: Boolean,
    @Column(nullable = false)
    val wallbang: Boolean,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
}

@Entity
@Table(name = "cs2_demo_grenade", schema = "gaming")
class Cs2DemoGrenade(
    @Column(nullable = false, updatable = false)
    val demoId: Long,

    @Column(nullable = false)
    val roundNumber: Int,

    @Column(nullable = false, length = 20)
    val type: String,

    @Column(length = 20)
    val throwerSteamId: String?,
    @Column(length = 64)
    val throwerName: String?,
    @Column(length = 4)
    val throwerTeam: String?,

    // Explicit names needed for the same trailing-single-capital reason as Cs2DemoKill's attacker/victim X/Y/Z.
    @Column(name = "throw_x")
    val throwX: Double?,
    @Column(name = "throw_y")
    val throwY: Double?,
    @Column(name = "throw_z")
    val throwZ: Double?,
    @Column(name = "detonate_x")
    val detonateX: Double?,
    @Column(name = "detonate_y")
    val detonateY: Double?,
    @Column(name = "detonate_z")
    val detonateZ: Double?,

    /**
     * Raw JSON text `[{x,y,z}, ...]`, the flight path for a future animated replay. Plain text rather than jsonb:
     * nothing queries inside it in SQL, it is only ever read back whole (see Cs2DemoDtos.kt for the (de)serializer).
     * No `@Lob`: on Postgres that maps a String to an `oid` large-object column, not `text` — the migration's
     * `text` column matches a plain (unannotated-length) String mapping instead.
     */
    @Column(nullable = false, columnDefinition = "text")
    val trajectory: String,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
}

@Entity
@Table(name = "cs2_demo_position", schema = "gaming")
class Cs2DemoPosition(
    @Column(nullable = false, updatable = false)
    val demoId: Long,

    @Column(nullable = false)
    val roundNumber: Int,

    @Column(nullable = false)
    val tick: Int,

    @Column(nullable = false, length = 20)
    val steamId: String,
    @Column(nullable = false, length = 64)
    val name: String,
    @Column(nullable = false, length = 4)
    val team: String,

    @Column(nullable = false)
    val x: Double,
    @Column(nullable = false)
    val y: Double,
    @Column(nullable = false)
    val z: Double,
    @Column(nullable = false)
    val health: Int,
    @Column(nullable = false)
    val alive: Boolean,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
}

interface Cs2DemoRepository : JpaRepository<Cs2Demo, Long> {
    fun findAllByUserIdOrderByUploadedAtDesc(userId: Long): List<Cs2Demo>
    fun findByIdAndUserId(id: Long, userId: Long): Cs2Demo?
}

interface Cs2DemoRoundRepository : JpaRepository<Cs2DemoRound, Long> {
    fun findAllByDemoIdOrderByRoundNumberAsc(demoId: Long): List<Cs2DemoRound>
    fun countByDemoId(demoId: Long): Long
    fun deleteAllByDemoId(demoId: Long)
}

interface Cs2DemoKillRepository : JpaRepository<Cs2DemoKill, Long> {
    fun findAllByDemoIdOrderByRoundNumberAscTickAsc(demoId: Long): List<Cs2DemoKill>
    fun countByDemoId(demoId: Long): Long
    fun deleteAllByDemoId(demoId: Long)
}

interface Cs2DemoGrenadeRepository : JpaRepository<Cs2DemoGrenade, Long> {
    fun findAllByDemoIdOrderByRoundNumberAsc(demoId: Long): List<Cs2DemoGrenade>
    fun deleteAllByDemoId(demoId: Long)
}

interface Cs2DemoPositionRepository : JpaRepository<Cs2DemoPosition, Long> {
    fun findAllByDemoIdAndRoundNumberOrderByTickAsc(demoId: Long, roundNumber: Int): List<Cs2DemoPosition>
    fun deleteAllByDemoId(demoId: Long)
}
