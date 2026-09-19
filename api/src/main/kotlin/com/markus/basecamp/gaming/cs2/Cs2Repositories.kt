package com.markus.basecamp.gaming.cs2

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDate

interface RoutineItemRepository : JpaRepository<RoutineItem, Long> {
    fun findAllByUserIdOrderBySortOrderAscIdAsc(userId: Long): List<RoutineItem>
    fun findByIdAndUserId(id: Long, userId: Long): RoutineItem?

    /**
     * Returns 1 for exactly one caller per user (the one that must create the defaults), 0 for everyone else.
     * Concurrent first requests block on the primary key until the winner has committed the defaults.
     */
    @Modifying
    @Query(
        value = "insert into gaming.cs2_profile (user_id) values (:userId) on conflict do nothing",
        nativeQuery = true,
    )
    fun claimSeed(@Param("userId") userId: Long): Int
}

interface RoutineLogRepository : JpaRepository<RoutineLog, Long> {
    fun findAllByUserIdAndLogDate(userId: Long, logDate: LocalDate): List<RoutineLog>
    fun findAllByUserIdAndLogDateBetween(userId: Long, from: LocalDate, to: LocalDate): List<RoutineLog>
    fun findByItemIdAndLogDate(itemId: Long, logDate: LocalDate): RoutineLog?

    @Query("select distinct l.logDate from RoutineLog l where l.userId = :userId and l.logDate >= :from")
    fun trainedDays(@Param("userId") userId: Long, @Param("from") from: LocalDate): List<LocalDate>
}

interface SessionReviewRepository : JpaRepository<SessionReview, Long> {
    fun findTop30ByUserIdOrderByPlayedOnDescIdDesc(userId: Long): List<SessionReview>
    fun findByIdAndUserId(id: Long, userId: Long): SessionReview?
}

interface PrincipleRepository : JpaRepository<Principle, Long> {
    fun findAllByUserIdOrderByPinnedDescSortOrderAscIdAsc(userId: Long): List<Principle>
    fun findByIdAndUserId(id: Long, userId: Long): Principle?
}
