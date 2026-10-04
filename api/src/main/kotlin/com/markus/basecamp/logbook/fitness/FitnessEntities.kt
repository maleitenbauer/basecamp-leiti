package com.markus.basecamp.logbook.fitness

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
import java.time.LocalDate

enum class FoodSource { MANUAL, OPEN_FOOD_FACTS }

@Entity
@Table(name = "nutrition_goal", schema = "logbook")
class NutritionGoal(
    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    val userId: Long,

    @Column(nullable = false)
    var dailyKcal: Int,
) {
    @Column(nullable = false)
    var updatedAt: Instant = Instant.now()
}

@Entity
@Table(name = "food", schema = "logbook")
class Food(
    @Column(nullable = false, updatable = false)
    val userId: Long,

    @Column(nullable = false, length = 200)
    var name: String,

    @Column(length = 200)
    var brand: String?,

    @Column(name = "kcal_per_100g", nullable = false)
    var kcalPer100g: Double,

    var portionGrams: Double?,

    @Column(length = 60)
    var portionLabel: String?,

    @Column(length = 32)
    var barcode: String?,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    val source: FoodSource,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    var lastGrams: Double? = null

    @Column(nullable = false)
    var useCount: Int = 0

    var lastUsedAt: Instant? = null

    @Column(nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()

    /** "Skyr (Arla)" — what a meal entry stores as its name snapshot. */
    fun label(): String = if (brand.isNullOrBlank()) name else "$name ($brand)"
}

@Entity
@Table(name = "meal_entry", schema = "logbook")
class MealEntry(
    @Column(nullable = false, updatable = false)
    val userId: Long,

    @Column(nullable = false)
    var day: LocalDate,

    @Column(nullable = false)
    var eatenAt: Instant,

    var foodId: Long?,

    @Column(nullable = false, length = 200)
    var name: String,

    var grams: Double?,

    @Column(nullable = false)
    var kcal: Int,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @Column(nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()
}

interface NutritionGoalRepository : JpaRepository<NutritionGoal, Long>

interface FoodRepository : JpaRepository<Food, Long> {
    fun findAllByUserIdOrderByNameAsc(userId: Long): List<Food>
    fun findByIdAndUserId(id: Long, userId: Long): Food?
    fun findFirstByUserIdAndBarcode(userId: Long, barcode: String): Food?
}

interface MealEntryRepository : JpaRepository<MealEntry, Long> {
    fun findAllByUserIdAndDayOrderByEatenAtAscIdAsc(userId: Long, day: LocalDate): List<MealEntry>
    fun findByIdAndUserId(id: Long, userId: Long): MealEntry?
}
