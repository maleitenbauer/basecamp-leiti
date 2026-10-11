package com.markus.basecamp.gaming.cs2

import com.markus.basecamp.core.ReportStat
import com.markus.basecamp.core.ReportWeek
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZoneOffset

/** 2026-03-02 is a Monday. */
class Cs2PracticeReportTest {

    private val monday = LocalDate.of(2026, 3, 2)
    private val week = ReportWeek(monday, monday.plusDays(2), ZoneOffset.UTC) // up to Wednesday: three days so far

    private fun log(itemId: Long, dayOffset: Long, minutes: Int? = null) = RoutineLog(1, itemId, monday.plusDays(dayOffset), minutes)

    private fun review(focus: Int, movement: Int, utility: Int) = SessionReview(1, monday, focus, movement, utility)

    @Test
    fun `every active item is meant to be done every day, and the score is how much of that happened`() {
        // two active items over three days is six to do; item 1 on Mon, Tue and Wed, item 2 on Tue (logged twice)
        val logs = listOf(log(1, 0, 10), log(1, 1), log(2, 1, 20), log(2, 1), log(1, 2))

        val section = Cs2PracticeReport.build(setOf(1, 2), logs, emptyList(), week)!!

        assertEquals("67% of practice items done", section.headline)
        assertEquals(4.0 / 6, section.score!!, 1e-9)
        assertEquals(
            listOf(
                ReportStat("Practice done", "4 of 6", "items, every day"),
                ReportStat("Days practised", "3 of 3"),
                ReportStat("Time practised", "30 min"),
            ),
            section.stats,
        )
    }

    @Test
    fun `items that are no longer active don't count towards the score`() {
        val section = Cs2PracticeReport.build(setOf(1), listOf(log(1, 0), log(99, 0), log(99, 1)), emptyList(), week)!!

        assertEquals("33% of practice items done", section.headline) // 1 of 3
    }

    @Test
    fun `session ratings are averaged`() {
        val reviews = listOf(review(4, 3, 5), review(5, 3, 4))

        val section = Cs2PracticeReport.build(setOf(1), listOf(log(1, 0)), reviews, week)!!

        assertTrue(section.stats.contains(ReportStat("Session reviews", "2")))
        assertEquals(listOf("Session ratings (of 5): focus 4.5, movement 3.0, utility 4.5"), section.highlights)
    }

    @Test
    fun `with no practice routine, reviews alone still make a section, without a score`() {
        val section = Cs2PracticeReport.build(emptySet(), emptyList(), listOf(review(4, 4, 4), review(3, 3, 3)), week)!!

        assertEquals("2 session reviews", section.headline)
        assertNull(section.score)
        assertTrue(section.stats.none { it.label == "Practice done" })
    }

    @Test
    fun `the chart counts items done each day, with days still to come left empty`() {
        val section = Cs2PracticeReport.build(setOf(1, 2), listOf(log(1, 0), log(2, 0), log(1, 1)), emptyList(), week)!!

        val bars = section.bars!!.bars
        assertEquals(listOf(2.0, 1.0, 0.0), bars.take(3).map { it.value })
        assertTrue(bars.drop(3).all { it.future })
    }

    @Test
    fun `nothing set up and nothing logged leaves the section out`() {
        assertNull(Cs2PracticeReport.build(emptySet(), emptyList(), emptyList(), week))
    }
}
