package com.markus.basecamp.logbook.fitness

import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant
import java.time.LocalDate

data class NutritionGoalResponse(val dailyKcal: Int?)

data class SetNutritionGoalRequest(
    @field:Min(500) @field:Max(20000) val dailyKcal: Int,
)

data class MealEntryResponse(
    val id: Long,
    val foodId: Long?,
    val name: String,
    val grams: Double?,
    val kcal: Int,
    val eatenAt: Instant,
)

/** [remainingKcal] goes negative once the goal is exceeded; null when no goal is set. */
data class NutritionDayResponse(
    val date: LocalDate,
    val goalKcal: Int?,
    val consumedKcal: Int,
    val remainingKcal: Int?,
    val entries: List<MealEntryResponse>,
)

data class FoodResponse(
    val id: Long,
    val name: String,
    val brand: String?,
    val kcalPer100g: Double,
    val portionGrams: Double?,
    val portionLabel: String?,
    val barcode: String?,
    val source: FoodSource,
    val lastGrams: Double?,
    val useCount: Int,
    val lastUsedAt: Instant?,
)

data class CreateFoodRequest(
    @field:NotBlank @field:Size(max = 200) val name: String,
    @field:Size(max = 200) val brand: String? = null,
    @field:DecimalMin("0") @field:DecimalMax("1000") val kcalPer100g: Double,
    @field:DecimalMin("0.1") @field:DecimalMax("5000") val portionGrams: Double? = null,
    @field:Size(max = 60) val portionLabel: String? = null,
    @field:Size(max = 32) val barcode: String? = null,
    val source: FoodSource = FoodSource.MANUAL,
)

data class UpdateFoodRequest(
    @field:Size(min = 1, max = 200) val name: String? = null,
    @field:Size(max = 200) val brand: String? = null,
    @field:DecimalMin("0") @field:DecimalMax("1000") val kcalPer100g: Double? = null,
    @field:DecimalMin("0.1") @field:DecimalMax("5000") val portionGrams: Double? = null,
    @field:Size(max = 60) val portionLabel: String? = null,
)

/**
 * Either [foodId] (+ [grams], defaulting to the food's portion size) or a plain [kcal] value with an optional
 * [name]. [day] is the calendar day as the user sees it; [eatenAt] defaults to now.
 */
data class AddMealEntryRequest(
    val day: LocalDate,
    val eatenAt: Instant? = null,
    val foodId: Long? = null,
    @field:DecimalMin("0.1") @field:DecimalMax("20000") val grams: Double? = null,
    @field:Size(max = 200) val name: String? = null,
    @field:Min(0) @field:Max(20000) val kcal: Int? = null,
)

/** Changing [grams] on a food-based entry recalculates its kcal, unless [kcal] is given explicitly too. */
data class UpdateMealEntryRequest(
    @field:DecimalMin("0.1") @field:DecimalMax("20000") val grams: Double? = null,
    @field:Min(0) @field:Max(20000) val kcal: Int? = null,
    val eatenAt: Instant? = null,
    val day: LocalDate? = null,
)

/** A product found on Open Food Facts; not stored until the user picks it. */
data class OnlineFoodResponse(
    val code: String?,
    val name: String,
    val brand: String?,
    val kcalPer100g: Double,
)
