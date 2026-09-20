package com.markus.basecamp.gaming.cs2.matches

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant

/** One row per user: which FACEIT account and Steam account to read matches for. */
@Entity
@Table(name = "cs2_match_profile", schema = "gaming")
class MatchProfile(
    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    val userId: Long,

    var faceitNickname: String? = null,

    var faceitPlayerId: String? = null,

    @Column(name = "steam64_id")
    var steam64Id: String? = null,
) {
    @Column(nullable = false)
    var updatedAt: Instant = Instant.now()
}

interface MatchProfileRepository : JpaRepository<MatchProfile, Long>
