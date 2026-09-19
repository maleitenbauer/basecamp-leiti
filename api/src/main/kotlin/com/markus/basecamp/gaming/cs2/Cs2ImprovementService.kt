package com.markus.basecamp.gaming.cs2

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDate

/** Every method takes the calling user's id and only ever touches that user's rows. */
@Service
@Transactional
class Cs2ImprovementService(
    private val items: RoutineItemRepository,
    private val logs: RoutineLogRepository,
    private val reviews: SessionReviewRepository,
    private val principles: PrincipleRepository,
) {

    // ---- routine ----

    fun routine(userId: Long, date: LocalDate): RoutineDayResponse {
        ensureSeeded(userId)
        val allItems = items.findAllByUserIdOrderBySortOrderAscIdAsc(userId)
        val today = logs.findAllByUserIdAndLogDate(userId, date).associateBy { it.itemId }
        val recent = logs.findAllByUserIdAndLogDateBetween(userId, date.minusDays(13), date)

        val history = (13 downTo 0).map { back ->
            val day = date.minusDays(back.toLong())
            DaySummary(day, recent.count { it.logDate == day })
        }
        return RoutineDayResponse(
            date = date,
            items = allItems.map {
                val log = today[it.id]
                RoutineItemStatus(
                    id = it.id!!,
                    title = it.title,
                    description = it.description,
                    category = it.category,
                    targetMinutes = it.targetMinutes,
                    active = it.active,
                    done = log != null,
                    minutes = log?.minutes,
                )
            },
            streak = streak(logs.trainedDays(userId, date.minusDays(400)), date),
            history = history,
        )
    }

    fun createItem(userId: Long, request: CreateRoutineItemRequest): RoutineItemResponse {
        ensureSeeded(userId)
        val next = (items.findAllByUserIdOrderBySortOrderAscIdAsc(userId).maxOfOrNull { it.sortOrder } ?: 0) + 1
        return items.save(
            RoutineItem(
                userId = userId,
                title = request.title.trim(),
                description = request.description.clean(),
                category = request.category,
                targetMinutes = request.targetMinutes,
                sortOrder = next,
            ),
        ).toResponse()
    }

    fun updateItem(userId: Long, id: Long, request: UpdateRoutineItemRequest): RoutineItemResponse {
        val item = findItem(userId, id)
        request.title?.let { item.title = it.trim() }
        request.description?.let { item.description = it.clean() }
        request.category?.let { item.category = it }
        request.targetMinutes?.let { item.targetMinutes = it }
        request.active?.let { item.active = it }
        return item.toResponse()
    }

    fun deleteItem(userId: Long, id: Long) {
        items.delete(findItem(userId, id))
    }

    fun setDone(userId: Long, itemId: Long, date: LocalDate, request: SetDoneRequest) {
        findItem(userId, itemId)
        val existing = logs.findByItemIdAndLogDate(itemId, date)
        if (request.done) {
            if (existing == null) {
                logs.save(RoutineLog(userId = userId, itemId = itemId, logDate = date, minutes = request.minutes))
            } else if (request.minutes != null) {
                existing.minutes = request.minutes
            }
        } else if (existing != null) {
            logs.delete(existing)
        }
    }

    // ---- session reviews ----

    fun listReviews(userId: Long): ReviewsResponse {
        val list = reviews.findTop30ByUserIdOrderByPlayedOnDescIdDesc(userId)
        val latest = list.take(10)
        val averages = if (latest.isEmpty()) null else ReviewAverages(
            focus = latest.map { it.focus }.average(),
            movement = latest.map { it.movement }.average(),
            utility = latest.map { it.utility }.average(),
            count = latest.size,
        )
        return ReviewsResponse(list.map { it.toResponse() }, averages)
    }

    fun createReview(userId: Long, request: CreateReviewRequest): ReviewResponse =
        reviews.save(
            SessionReview(
                userId = userId,
                playedOn = request.playedOn ?: LocalDate.now(),
                focus = request.focus,
                movement = request.movement,
                utility = request.utility,
                notes = request.notes.clean(),
            ),
        ).toResponse()

    fun deleteReview(userId: Long, id: Long) {
        reviews.delete(
            reviews.findByIdAndUserId(id, userId)
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Review not found"),
        )
    }

    // ---- principles ----

    fun listPrinciples(userId: Long): List<PrincipleResponse> {
        ensureSeeded(userId)
        return principles.findAllByUserIdOrderByPinnedDescSortOrderAscIdAsc(userId).map { it.toResponse() }
    }

    fun createPrinciple(userId: Long, request: CreatePrincipleRequest): PrincipleResponse {
        ensureSeeded(userId)
        val next = (principles.findAllByUserIdOrderByPinnedDescSortOrderAscIdAsc(userId).maxOfOrNull { it.sortOrder } ?: 0) + 1
        return principles.save(
            Principle(
                userId = userId,
                title = request.title.trim(),
                body = request.body.clean(),
                category = request.category,
                sortOrder = next,
            ),
        ).toResponse()
    }

    fun updatePrinciple(userId: Long, id: Long, request: UpdatePrincipleRequest): PrincipleResponse {
        val principle = findPrinciple(userId, id)
        request.title?.let { principle.title = it.trim() }
        request.body?.let { principle.body = it.clean() }
        request.category?.let { principle.category = it }
        request.pinned?.let { principle.pinned = it }
        return principle.toResponse()
    }

    fun deletePrinciple(userId: Long, id: Long) {
        principles.delete(findPrinciple(userId, id))
    }

    // ---- helpers ----

    /** Creates the default routine and principles the first time a user opens this page. */
    private fun ensureSeeded(userId: Long) {
        if (items.claimSeed(userId) == 0) return
        Cs2Defaults.routine.forEachIndexed { index, d ->
            items.save(RoutineItem(userId, d.title, d.description, d.category, d.targetMinutes, index + 1))
        }
        Cs2Defaults.principles.forEachIndexed { index, d ->
            principles.save(Principle(userId, d.title, d.body, d.category, d.pinned, index + 1))
        }
    }

    private fun streak(trainedDays: List<LocalDate>, day: LocalDate): Int {
        val days = trainedDays.toSet()
        var cursor = if (day in days) day else day.minusDays(1)
        var count = 0
        while (cursor in days) {
            count++
            cursor = cursor.minusDays(1)
        }
        return count
    }

    private fun findItem(userId: Long, id: Long): RoutineItem =
        items.findByIdAndUserId(id, userId) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Routine item not found")

    private fun findPrinciple(userId: Long, id: Long): Principle =
        principles.findByIdAndUserId(id, userId) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Principle not found")

    private fun String?.clean(): String? = this?.trim()?.ifEmpty { null }

    private fun RoutineItem.toResponse() =
        RoutineItemResponse(id!!, title, description, category, targetMinutes, active)

    private fun SessionReview.toResponse() = ReviewResponse(id!!, playedOn, focus, movement, utility, notes)

    private fun Principle.toResponse() = PrincipleResponse(id!!, title, body, category, pinned)
}
