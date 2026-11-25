package com.markus.basecamp.logbook.fitness

import java.time.Duration
import java.time.Instant
import kotlin.math.ceil

/** The parts of training that are pure functions of their input, so they can be tested without a database. */
object TrainingRules {

    /** After the last logged set, a forgotten workout is assumed to have lasted this much longer (changing, stretching). */
    val AUTO_FINISH_GRACE: Duration = Duration.ofMinutes(5)

    /** "Bench  Press" and "bench press" are the same exercise. */
    fun nameKey(name: String): String = name.trim().lowercase().replace(Regex("\\s+"), " ")

    /** A workout still running when its limit has passed is considered forgotten. */
    fun isStale(startedAt: Instant, now: Instant, limit: Duration): Boolean = !startedAt.plus(limit).isAfter(now)

    /**
     * When a forgotten workout is taken to have ended: shortly after the last set that was logged, but never later
     * than the limit. Using the limit itself would credit hours that were never trained.
     */
    fun autoFinishAt(startedAt: Instant, lastSetAt: Instant?, limit: Duration): Instant {
        val cutoff = startedAt.plus(limit)
        val estimate = (lastSetAt ?: startedAt).plus(AUTO_FINISH_GRACE)
        return if (estimate.isBefore(cutoff)) estimate else cutoff
    }

    /** Whole minutes, rounded up, at least 1 (that is what a routine completion accepts). */
    fun minutes(startedAt: Instant, finishedAt: Instant): Int {
        val seconds = Duration.between(startedAt, finishedAt).seconds.coerceAtLeast(0)
        return ceil(seconds / 60.0).toInt().coerceIn(1, 1440)
    }

    fun volume(sets: List<SetValues>): Double = sets.sumOf { it.reps * (it.weightKg ?: 0.0) }

    /** Heavier is better, at equal weight more reps. Bodyweight counts as weight 0. */
    private val setOrder = compareBy<SetValues> { it.weightKg ?: 0.0 }.thenBy { it.reps }

    fun bestSet(sets: List<SetValues>): SetValues? = sets.maxWithOrNull(setOrder)

    data class PersonalBest(val exercise: String, val weightKg: Double?, val reps: Int)

    /**
     * An exercise is a personal best when its best set beats every set ever done before in the same exercise. The very
     * first time an exercise is done can't beat anything, so it isn't celebrated.
     * [today] is what was done in this workout, [before] everything from earlier workouts, both by exercise name key.
     */
    fun personalBests(today: List<Pair<String, List<SetValues>>>, before: Map<String, List<SetValues>>): List<PersonalBest> =
        today.mapNotNull { (name, sets) ->
            val best = bestSet(sets) ?: return@mapNotNull null
            val earlier = bestSet(before[nameKey(name)].orEmpty()) ?: return@mapNotNull null
            if (setOrder.compare(best, earlier) > 0) PersonalBest(name, best.weightKg, best.reps) else null
        }
}
