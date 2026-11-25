package com.markus.basecamp.logbook.fitness

import com.markus.basecamp.core.ReportStat
import com.markus.basecamp.core.ReportWeek
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** 2026-03-02 is a Monday. */
class NutritionReportTest {

    private val monday = LocalDate.of(2026, 3, 2)
    private val week = ReportWeek(monday, monday.plusDays(3), ZoneOffset.UTC) // up to Thursday: four days so far

    private fun meal(dayOffset: Long, kcal: Int) =
        MealEntry(1, monday.plusDays(dayOffset), Instant.parse("2026-03-02T12:00:00Z"), null, "Meal", null, kcal)

    // Monday 2500 (two meals), Tuesday 1800, Wednesday 2600
    private val meals = listOf(meal(0, 2000), meal(0, 500), meal(1, 1800), meal(2, 2600))

    @Test
    fun `with a limit, the section says on how many logged days it was kept`() {
        val section = NutritionReport.build(meals, goal = 2200, week = week)!!

        assertEquals("Within your limit on 1 of 3 logged days", section.headline)
        assertEquals(1.0 / 3, section.score!!, 1e-9)
        assertEquals(
            listOf(
                ReportStat("Average", "2300 kcal", "per logged day"),
                ReportStat("Days logged", "3 of 4"),
                ReportStat("Daily limit", "2200 kcal"),
                ReportStat("Highest day", "2600 kcal", "Wed"),
            ),
            section.stats,
        )
        assertEquals(
            listOf("Over the limit on Mon: 2500 kcal (+300)", "Over the limit on Wed: 2600 kcal (+400)"),
            section.highlights,
        )
    }

    @Test
    fun `the chart shows each day's total with the limit as a line, and days not logged as empty`() {
        val section = NutritionReport.build(meals, goal = 2200, week = week)!!

        val chart = section.bars!!
        assertEquals(2200.0, chart.goal)
        assertEquals(listOf(2500.0, 1800.0, 2600.0, 0.0), chart.bars.take(4).map { it.value })
        assertEquals("–", chart.bars[3].display) // Thursday: nothing logged
        assertTrue(chart.bars.drop(4).all { it.future })
    }

    @Test
    fun `without a limit it just reports the average and gives no score`() {
        val section = NutritionReport.build(meals, goal = null, week = week)!!

        assertEquals("Averaging 2300 kcal on 3 logged days", section.headline)
        assertNull(section.score)
        assertNull(section.bars!!.goal)
        assertTrue(section.highlights.isEmpty())
        assertTrue(section.stats.none { it.label == "Daily limit" })
    }

    @Test
    fun `a single logged day reads naturally`() {
        val section = NutritionReport.build(listOf(meal(0, 1500)), goal = 2000, week = week)!!

        assertEquals("Within your limit on 1 of 1 logged day", section.headline)
        assertEquals(1.0, section.score)
    }

    @Test
    fun `days with nothing logged don't pull the average down, and no meals leaves the section out`() {
        val section = NutritionReport.build(listOf(meal(0, 2000)), goal = null, week = week)!!
        assertEquals("Averaging 2000 kcal on 1 logged day", section.headline) // not 2000 / 4

        assertNull(NutritionReport.build(emptyList(), goal = 2000, week = week))
    }
}
