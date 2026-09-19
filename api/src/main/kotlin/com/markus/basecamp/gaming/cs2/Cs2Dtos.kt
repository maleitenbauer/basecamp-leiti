package com.markus.basecamp.gaming.cs2

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.LocalDate

// ---- routine ----

data class RoutineItemResponse(
    val id: Long,
    val title: String,
    val description: String?,
    val category: RoutineCategory,
    val targetMinutes: Int?,
    val active: Boolean,
)

data class RoutineItemStatus(
    val id: Long,
    val title: String,
    val description: String?,
    val category: RoutineCategory,
    val targetMinutes: Int?,
    val active: Boolean,
    val done: Boolean,
    val minutes: Int?,
)

data class DaySummary(val date: LocalDate, val doneCount: Int)

/** [streak] counts consecutive days (ending on the requested day, or the day before) with at least one item done. */
data class RoutineDayResponse(
    val date: LocalDate,
    val items: List<RoutineItemStatus>,
    val streak: Int,
    val history: List<DaySummary>,
)

data class CreateRoutineItemRequest(
    @field:NotBlank @field:Size(max = 200) val title: String,
    @field:Size(max = 2000) val description: String? = null,
    val category: RoutineCategory = RoutineCategory.PRACTICE,
    @field:Min(1) @field:Max(600) val targetMinutes: Int? = null,
)

data class UpdateRoutineItemRequest(
    @field:Size(min = 1, max = 200) val title: String? = null,
    @field:Size(max = 2000) val description: String? = null,
    val category: RoutineCategory? = null,
    @field:Min(1) @field:Max(600) val targetMinutes: Int? = null,
    val active: Boolean? = null,
)

data class SetDoneRequest(
    val done: Boolean,
    @field:Min(1) @field:Max(600) val minutes: Int? = null,
)

// ---- session reviews ----

data class ReviewResponse(
    val id: Long,
    val playedOn: LocalDate,
    val focus: Int,
    val movement: Int,
    val utility: Int,
    val notes: String?,
)

data class ReviewAverages(val focus: Double, val movement: Double, val utility: Double, val count: Int)

data class ReviewsResponse(val reviews: List<ReviewResponse>, val averages: ReviewAverages?)

data class CreateReviewRequest(
    val playedOn: LocalDate? = null,
    @field:Min(1) @field:Max(5) val focus: Int,
    @field:Min(1) @field:Max(5) val movement: Int,
    @field:Min(1) @field:Max(5) val utility: Int,
    @field:Size(max = 4000) val notes: String? = null,
)

// ---- principles ----

data class PrincipleResponse(
    val id: Long,
    val title: String,
    val body: String?,
    val category: PrincipleCategory,
    val pinned: Boolean,
)

data class CreatePrincipleRequest(
    @field:NotBlank @field:Size(max = 200) val title: String,
    @field:Size(max = 4000) val body: String? = null,
    val category: PrincipleCategory = PrincipleCategory.MINDSET,
)

data class UpdatePrincipleRequest(
    @field:Size(min = 1, max = 200) val title: String? = null,
    @field:Size(max = 4000) val body: String? = null,
    val category: PrincipleCategory? = null,
    val pinned: Boolean? = null,
)
