package com.markus.basecamp.logbook.fitness

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.web.server.ResponseStatusException
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.LocalDate

/**
 * Runs the whole app against a real Postgres, so the V10 migration and Hibernate schema validation are exercised
 * too. Skipped automatically where Docker is unavailable (e.g. local Windows). The Open Food Facts lookup itself is
 * not called here (no network in tests); its parsing is covered by OpenFoodFactsParsingTest.
 */
@SpringBootTest(
    properties = [
        "basecamp.bootstrap-admin.username=admin",
        "basecamp.bootstrap-admin.password=correct-horse-battery",
        "basecamp.scheduling.enabled=false",
    ],
)
@Testcontainers(disabledWithoutDocker = true)
class NutritionIntegrationTests {

    companion object {
        @Container
        @ServiceConnection
        @JvmStatic
        val postgres = PostgreSQLContainer<Nothing>("postgres:17")
    }

    @Autowired
    lateinit var nutrition: NutritionService

    @Autowired
    lateinit var jdbc: JdbcTemplate

    private fun newUser(name: String): Long = jdbc.queryForObject(
        "insert into core.app_user (username, password_hash, role) values (?, 'x', 'USER') returning id",
        Long::class.java,
        name,
    )!!

    private fun food(userId: Long, name: String = "Oats", kcalPer100g: Double = 370.0, portionGrams: Double? = null) =
        nutrition.createFood(userId, CreateFoodRequest(name = name, kcalPer100g = kcalPer100g, portionGrams = portionGrams))

    @Test
    fun `a goal can be set and changed, and the day shows what is left`() {
        val userId = newUser("goal-user")
        val date = LocalDate.of(2026, 3, 1)
        assertNull(nutrition.goal(userId).dailyKcal)
        assertNull(nutrition.day(userId, date).remainingKcal)

        nutrition.setGoal(userId, SetNutritionGoalRequest(2000))
        nutrition.setGoal(userId, SetNutritionGoalRequest(2200))
        assertEquals(2200, nutrition.goal(userId).dailyKcal)

        nutrition.addEntry(userId, AddMealEntryRequest(day = date, kcal = 500, name = "Lunch"))
        val day = nutrition.day(userId, date)
        assertEquals(500, day.consumedKcal)
        assertEquals(1700, day.remainingKcal)
    }

    @Test
    fun `going over the goal gives a negative remainder`() {
        val userId = newUser("over-user")
        val date = LocalDate.of(2026, 3, 2)
        nutrition.setGoal(userId, SetNutritionGoalRequest(1500))
        nutrition.addEntry(userId, AddMealEntryRequest(day = date, kcal = 1800))

        assertEquals(-300, nutrition.day(userId, date).remainingKcal)
    }

    @Test
    fun `an entry from a food and an amount gets its kcal calculated, and the food remembers the amount`() {
        val userId = newUser("food-user")
        val date = LocalDate.of(2026, 3, 3)
        val oats = food(userId, kcalPer100g = 370.0)

        val entry = nutrition.addEntry(userId, AddMealEntryRequest(day = date, foodId = oats.id, grams = 60.0))

        assertEquals(222, entry.kcal)
        assertEquals("Oats", entry.name)
        val reloaded = nutrition.listFoods(userId).single { it.id == oats.id }
        assertEquals(1, reloaded.useCount)
        assertEquals(60.0, reloaded.lastGrams)
    }

    @Test
    fun `a food with a portion size can be added without an amount, but one without cannot`() {
        val userId = newUser("portion-user")
        val date = LocalDate.of(2026, 3, 4)
        val bar = food(userId, name = "Protein bar", kcalPer100g = 400.0, portionGrams = 50.0)
        val plain = food(userId, name = "Rice", kcalPer100g = 130.0)

        assertEquals(200, nutrition.addEntry(userId, AddMealEntryRequest(day = date, foodId = bar.id)).kcal)
        assertThrows<ResponseStatusException> { nutrition.addEntry(userId, AddMealEntryRequest(day = date, foodId = plain.id)) }
    }

    @Test
    fun `a quick entry needs kcal and gets a default name`() {
        val userId = newUser("quick-user")
        val date = LocalDate.of(2026, 3, 5)

        assertEquals("Quick add", nutrition.addEntry(userId, AddMealEntryRequest(day = date, kcal = 250)).name)
        assertThrows<ResponseStatusException> { nutrition.addEntry(userId, AddMealEntryRequest(day = date)) }
    }

    @Test
    fun `changing the grams of a food entry recalculates it, an explicit kcal wins, and delete removes it`() {
        val userId = newUser("edit-user")
        val date = LocalDate.of(2026, 3, 6)
        val oats = food(userId, kcalPer100g = 400.0)
        val entry = nutrition.addEntry(userId, AddMealEntryRequest(day = date, foodId = oats.id, grams = 50.0))
        assertEquals(200, entry.kcal)

        val doubled = nutrition.updateEntry(userId, entry.id, UpdateMealEntryRequest(grams = 100.0))
        assertEquals(400, doubled.kcal)

        val overridden = nutrition.updateEntry(userId, entry.id, UpdateMealEntryRequest(grams = 10.0, kcal = 999))
        assertEquals(999, overridden.kcal)

        nutrition.deleteEntry(userId, entry.id)
        assertTrue(nutrition.day(userId, date).entries.isEmpty())
    }

    @Test
    fun `entries keep their snapshot when the food is edited or deleted`() {
        val userId = newUser("snapshot-user")
        val date = LocalDate.of(2026, 3, 7)
        val oats = food(userId, kcalPer100g = 400.0)
        nutrition.addEntry(userId, AddMealEntryRequest(day = date, foodId = oats.id, grams = 50.0))

        nutrition.updateFood(userId, oats.id, UpdateFoodRequest(kcalPer100g = 800.0))
        assertEquals(200, nutrition.day(userId, date).entries.single().kcal)

        nutrition.deleteFood(userId, oats.id)
        val kept = nutrition.day(userId, date).entries.single()
        assertEquals(200, kept.kcal)
        assertNull(kept.foodId)
    }

    @Test
    fun `saving a barcode twice returns the same food`() {
        val userId = newUser("barcode-user")
        val request = CreateFoodRequest(
            name = "Skyr", brand = "Arla", kcalPer100g = 62.0, barcode = "5760466000000", source = FoodSource.OPEN_FOOD_FACTS,
        )

        val first = nutrition.createFood(userId, request)
        val second = nutrition.createFood(userId, request)

        assertEquals(first.id, second.id)
        assertEquals(1, nutrition.listFoods(userId).size)
    }

    @Test
    fun `users cannot see or touch each other's data`() {
        val owner = newUser("owner-user")
        val stranger = newUser("stranger-user")
        val date = LocalDate.of(2026, 3, 8)
        nutrition.setGoal(owner, SetNutritionGoalRequest(2000))
        val oats = food(owner)
        val entry = nutrition.addEntry(owner, AddMealEntryRequest(day = date, foodId = oats.id, grams = 100.0))

        assertNull(nutrition.goal(stranger).dailyKcal)
        assertTrue(nutrition.listFoods(stranger).isEmpty())
        assertTrue(nutrition.day(stranger, date).entries.isEmpty())
        assertThrows<ResponseStatusException> { nutrition.updateEntry(stranger, entry.id, UpdateMealEntryRequest(kcal = 1)) }
        assertThrows<ResponseStatusException> { nutrition.deleteEntry(stranger, entry.id) }
        assertThrows<ResponseStatusException> { nutrition.addEntry(stranger, AddMealEntryRequest(day = date, foodId = oats.id, grams = 1.0)) }
        assertThrows<ResponseStatusException> { nutrition.deleteFood(stranger, oats.id) }
        assertNotEquals(0, nutrition.day(owner, date).consumedKcal)
    }

    @Test
    fun `an online search rejects a too-short query before calling out`() {
        assertThrows<ResponseStatusException> { nutrition.searchOnline(" a ") }
        assertEquals(400, assertThrows<ResponseStatusException> { nutrition.searchOnline("x") }.statusCode.value())
    }
}
