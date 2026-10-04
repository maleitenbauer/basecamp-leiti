package com.markus.basecamp.logbook.fitness

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.client.RestClientException
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.time.LocalDate
import kotlin.math.roundToInt

private val BARCODE = Regex("^\\d{8,14}$")

/** Every method takes the calling user's id and only ever touches that user's goal, foods and meal entries. */
@Service
@Transactional
class NutritionService(
    private val goals: NutritionGoalRepository,
    private val foods: FoodRepository,
    private val entries: MealEntryRepository,
    private val openFoodFacts: OpenFoodFactsClient,
) {

    // ---- daily goal ----

    @Transactional(readOnly = true)
    fun goal(userId: Long) = NutritionGoalResponse(goals.findById(userId).map { it.dailyKcal }.orElse(null))

    fun setGoal(userId: Long, request: SetNutritionGoalRequest): NutritionGoalResponse {
        val goal = goals.findById(userId).orElse(null)
        if (goal != null) {
            goal.dailyKcal = request.dailyKcal
            goal.updatedAt = Instant.now()
        } else {
            goals.save(NutritionGoal(userId, request.dailyKcal))
        }
        return NutritionGoalResponse(request.dailyKcal)
    }

    // ---- a day's log ----

    @Transactional(readOnly = true)
    fun day(userId: Long, date: LocalDate): NutritionDayResponse {
        val dayEntries = entries.findAllByUserIdAndDayOrderByEatenAtAscIdAsc(userId, date)
        val consumed = dayEntries.sumOf { it.kcal }
        val goal = goals.findById(userId).map { it.dailyKcal }.orElse(null)
        return NutritionDayResponse(
            date = date,
            goalKcal = goal,
            consumedKcal = consumed,
            remainingKcal = goal?.minus(consumed),
            entries = dayEntries.map { it.toResponse() },
        )
    }

    fun addEntry(userId: Long, request: AddMealEntryRequest): MealEntryResponse {
        val now = Instant.now()
        val entry = if (request.foodId != null) {
            val food = findFood(userId, request.foodId)
            val grams = request.grams ?: food.portionGrams
                ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter an amount in grams")
            food.useCount += 1
            food.lastUsedAt = now
            food.lastGrams = grams
            MealEntry(userId, request.day, request.eatenAt ?: now, food.id, food.label().take(200), grams, kcalFor(food, grams))
        } else {
            val kcal = request.kcal
                ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Pick a food or enter the calories")
            val name = request.name?.trim().takeUnless { it.isNullOrEmpty() } ?: "Quick add"
            MealEntry(userId, request.day, request.eatenAt ?: now, null, name, null, kcal)
        }
        return entries.save(entry).toResponse()
    }

    fun updateEntry(userId: Long, id: Long, request: UpdateMealEntryRequest): MealEntryResponse {
        val entry = findEntry(userId, id)
        request.grams?.let { grams ->
            entry.grams = grams
            val food = entry.foodId?.let { foods.findByIdAndUserId(it, userId) }
            if (food != null && request.kcal == null) entry.kcal = kcalFor(food, grams)
        }
        request.kcal?.let { entry.kcal = it }
        request.eatenAt?.let { entry.eatenAt = it }
        request.day?.let { entry.day = it }
        return entry.toResponse()
    }

    fun deleteEntry(userId: Long, id: Long) {
        entries.delete(findEntry(userId, id))
    }

    // ---- the user's food library ----

    @Transactional(readOnly = true)
    fun listFoods(userId: Long): List<FoodResponse> = foods.findAllByUserIdOrderByNameAsc(userId).map { it.toResponse() }

    /** Saving a barcode the user already has returns the existing food instead of failing or duplicating it. */
    fun createFood(userId: Long, request: CreateFoodRequest): FoodResponse {
        val barcode = request.barcode?.trim()?.takeIf { it.isNotEmpty() }
        if (barcode != null) foods.findFirstByUserIdAndBarcode(userId, barcode)?.let { return it.toResponse() }
        val food = Food(
            userId = userId,
            name = request.name.trim(),
            brand = request.brand?.trim()?.takeIf { it.isNotEmpty() },
            kcalPer100g = request.kcalPer100g,
            portionGrams = request.portionGrams,
            portionLabel = request.portionLabel?.trim()?.takeIf { it.isNotEmpty() },
            barcode = barcode,
            source = request.source,
        )
        return foods.save(food).toResponse()
    }

    fun updateFood(userId: Long, id: Long, request: UpdateFoodRequest): FoodResponse {
        val food = findFood(userId, id)
        request.name?.let { food.name = it.trim() }
        request.brand?.let { food.brand = it.trim().takeIf { b -> b.isNotEmpty() } }
        request.kcalPer100g?.let { food.kcalPer100g = it }
        request.portionGrams?.let { food.portionGrams = it }
        request.portionLabel?.let { food.portionLabel = it.trim().takeIf { l -> l.isNotEmpty() } }
        return food.toResponse()
    }

    /** Past meal entries keep their own name/kcal snapshot; only their link to the food is cleared. */
    fun deleteFood(userId: Long, id: Long) {
        foods.delete(findFood(userId, id))
    }

    // ---- Open Food Facts lookup ----

    /**
     * A barcode (8-14 digits) is looked up directly; anything else is a text search. Runs without a transaction so
     * a slow response from the other side never holds a database connection open.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun searchOnline(query: String): List<OnlineFoodResponse> {
        val q = query.trim()
        if (q.length < 2) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Type at least 2 characters")
        if (q.length > 100) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "That search is too long")
        return try {
            if (BARCODE.matches(q)) listOfNotNull(openFoodFacts.byBarcode(q)) else openFoodFacts.search(q)
        } catch (e: RestClientException) {
            throw ResponseStatusException(HttpStatus.BAD_GATEWAY, "Open Food Facts could not be reached. Try again in a moment.")
        }
    }

    private fun kcalFor(food: Food, grams: Double): Int = (food.kcalPer100g * grams / 100.0).roundToInt()

    private fun findFood(userId: Long, id: Long): Food =
        foods.findByIdAndUserId(id, userId) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Food not found")

    private fun findEntry(userId: Long, id: Long): MealEntry =
        entries.findByIdAndUserId(id, userId) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Meal entry not found")

    private fun MealEntry.toResponse() = MealEntryResponse(id!!, foodId, name, grams, kcal, eatenAt)

    private fun Food.toResponse() =
        FoodResponse(id!!, name, brand, kcalPer100g, portionGrams, portionLabel, barcode, source, lastGrams, useCount, lastUsedAt)
}
