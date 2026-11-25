package com.markus.basecamp.logbook.fitness

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

class TrainingRulesTest {

    private val start = Instant.parse("2026-03-02T17:00:00Z")
    private val limit = Duration.ofHours(2)

    @Test
    fun `exercise names match regardless of case and spacing`() {
        assertEquals("bench press", TrainingRules.nameKey("  Bench   PRESS "))
        assertEquals(TrainingRules.nameKey("Squat"), TrainingRules.nameKey("squat"))
    }

    @Test
    fun `a workout is stale exactly when its limit has passed`() {
        assertFalse(TrainingRules.isStale(start, start.plus(Duration.ofMinutes(119)), limit))
        assertTrue(TrainingRules.isStale(start, start.plus(limit), limit))
        assertTrue(TrainingRules.isStale(start, start.plus(Duration.ofHours(5)), limit))
    }

    @Test
    fun `a forgotten workout ends shortly after the last set, not at the limit`() {
        val lastSet = start.plus(Duration.ofMinutes(50))
        assertEquals(lastSet.plus(Duration.ofMinutes(5)), TrainingRules.autoFinishAt(start, lastSet, limit))
    }

    @Test
    fun `the automatic end never goes past the limit`() {
        val lateSet = start.plus(Duration.ofMinutes(118))
        assertEquals(start.plus(limit), TrainingRules.autoFinishAt(start, lateSet, limit))
    }

    @Test
    fun `without any set the end is just after the start`() {
        assertEquals(start.plus(TrainingRules.AUTO_FINISH_GRACE), TrainingRules.autoFinishAt(start, null, limit))
    }

    @Test
    fun `minutes are rounded up and never zero`() {
        assertEquals(1, TrainingRules.minutes(start, start))
        assertEquals(1, TrainingRules.minutes(start, start.plusSeconds(1)))
        assertEquals(52, TrainingRules.minutes(start, start.plusSeconds(51 * 60 + 5)))
        assertEquals(60, TrainingRules.minutes(start, start.plusSeconds(3600)))
    }

    @Test
    fun `volume is reps times weight, with bodyweight counting as nothing`() {
        assertEquals(8 * 60.0 + 5 * 62.5, TrainingRules.volume(listOf(SetValues(8, 60.0), SetValues(5, 62.5))), 0.001)
        assertEquals(0.0, TrainingRules.volume(listOf(SetValues(15, null))), 0.001)
    }

    @Test
    fun `the best set is the heaviest, then the one with more reps`() {
        assertEquals(SetValues(5, 70.0), TrainingRules.bestSet(listOf(SetValues(10, 60.0), SetValues(5, 70.0), SetValues(3, 70.0))))
        assertEquals(SetValues(8, 70.0), TrainingRules.bestSet(listOf(SetValues(5, 70.0), SetValues(8, 70.0))))
        assertNull(TrainingRules.bestSet(emptyList()))
    }

    @Test
    fun `a heavier set than ever before is a personal best`() {
        val bests = TrainingRules.personalBests(
            today = listOf("Bench Press" to listOf(SetValues(5, 62.5))),
            before = mapOf("bench press" to listOf(SetValues(8, 60.0), SetValues(5, 60.0))),
        )
        assertEquals(listOf(TrainingRules.PersonalBest("Bench Press", 62.5, 5)), bests)
    }

    @Test
    fun `more reps at the same weight is a personal best too`() {
        val bests = TrainingRules.personalBests(
            today = listOf("Pull-up" to listOf(SetValues(9, null))),
            before = mapOf("pull-up" to listOf(SetValues(8, null))),
        )
        assertEquals(1, bests.size)
    }

    @Test
    fun `matching or missing a previous best is not one, and neither is a first time`() {
        val before = mapOf("squat" to listOf(SetValues(5, 100.0)))
        assertTrue(TrainingRules.personalBests(listOf("Squat" to listOf(SetValues(5, 100.0))), before).isEmpty())
        assertTrue(TrainingRules.personalBests(listOf("Squat" to listOf(SetValues(12, 90.0))), before).isEmpty())
        assertTrue(TrainingRules.personalBests(listOf("Deadlift" to listOf(SetValues(5, 120.0))), before).isEmpty())
        assertTrue(TrainingRules.personalBests(listOf("Squat" to emptyList()), before).isEmpty())
    }
}
