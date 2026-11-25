package com.markus.basecamp.logbook.fitness

import com.markus.basecamp.core.ReportStat
import com.markus.basecamp.core.ReportWeek
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** 2026-03-02 is a Monday. */
class TrainingReportTest {

    private val monday = LocalDate.of(2026, 3, 2)
    private val week = ReportWeek(monday, monday.plusDays(3), ZoneOffset.UTC) // up to Thursday

    private fun workout(name: String, day: LocalDate, minutes: Int, sets: Int, volume: Double) = WorkoutSummary(
        id = 1,
        name = name,
        day = day,
        startedAt = Instant.parse("2026-03-02T17:00:00Z"),
        minutes = minutes,
        exercises = 3,
        sets = sets,
        volumeKg = volume,
        endedBy = WorkoutEndedBy.MANUAL,
    )

    @Test
    fun `a week without workouts has no section`() {
        assertNull(TrainingReport.build(emptyList(), week))
    }

    @Test
    fun `workouts are summed up and charted per day`() {
        val section = TrainingReport.build(
            listOf(
                workout("Push day", monday, 55, 18, 5400.0),
                workout("Pull day", monday.plusDays(2), 65, 20, 6100.4),
            ),
            week,
        )!!

        assertEquals("2 workouts, 2 h", section.headline)
        assertEquals(
            listOf(
                ReportStat("Workouts", "2"),
                ReportStat("Time trained", "2 h"),
                ReportStat("Sets", "38"),
                ReportStat("Weight moved", "11500 kg", "reps × weight"),
            ),
            section.stats,
        )
        assertEquals(listOf(55.0, 0.0, 65.0, 0.0), section.bars!!.bars.take(4).map { it.value })
        assertEquals(listOf("Push day on Mon: 55 min, 18 sets", "Pull day on Wed: 1 h 5 min, 20 sets"), section.highlights)
        assertNull(section.score)
    }

    @Test
    fun `one workout reads in the singular, and bodyweight-only training has no weight stat`() {
        val section = TrainingReport.build(listOf(workout("Calisthenics", monday, 40, 12, 0.0)), week)!!

        assertEquals("1 workout, 40 min", section.headline)
        assertEquals(listOf("Workouts", "Time trained", "Sets"), section.stats.map { it.label })
    }
}
