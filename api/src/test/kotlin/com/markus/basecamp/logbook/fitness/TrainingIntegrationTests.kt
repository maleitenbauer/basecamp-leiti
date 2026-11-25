package com.markus.basecamp.logbook.fitness

import com.markus.basecamp.logbook.routine.RoutineRequest
import com.markus.basecamp.logbook.routine.RoutineScheduleType
import com.markus.basecamp.logbook.routine.RoutineService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
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
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/**
 * Runs the whole app against a real Postgres, so the V14 migration and Hibernate schema validation are exercised too.
 * Skipped automatically where Docker is unavailable (e.g. local Windows).
 *
 * Calendar facts (2026): Mar 2 is a Monday.
 */
@SpringBootTest(
    properties = [
        "basecamp.bootstrap-admin.username=admin",
        "basecamp.bootstrap-admin.password=correct-horse-battery",
        "basecamp.scheduling.enabled=false",
    ],
)
@Testcontainers(disabledWithoutDocker = true)
class TrainingIntegrationTests {

    companion object {
        @Container
        @ServiceConnection
        @JvmStatic
        val postgres = PostgreSQLContainer<Nothing>("postgres:17")

        private val MONDAY = LocalDate.of(2026, 3, 2)
    }

    @Autowired
    lateinit var plans: TrainingPlanService

    @Autowired
    lateinit var training: TrainingService

    @Autowired
    lateinit var routines: RoutineService

    @Autowired
    lateinit var jdbc: JdbcTemplate

    private fun newUser(name: String): Long = jdbc.queryForObject(
        "insert into core.app_user (username, password_hash, role) values (?, 'x', 'USER') returning id",
        Long::class.java,
        name,
    )!!

    private fun gymRoutine(userId: Long, timesPerWeek: Int = 3) = routines.create(
        userId,
        RoutineRequest(name = "Gym", scheduleType = RoutineScheduleType.TIMES_PER_WEEK, timesPerWeek = timesPerWeek, targetMinutes = 60),
    )

    private fun pushPlan(userId: Long, routineId: Long? = null) = plans.create(
        userId,
        PlanRequest(
            name = "Push day",
            routineId = routineId,
            exercises = listOf(
                PlanExerciseRequest("Bench Press", sets = 3, reps = 8, weightKg = 60.0, restSeconds = 90),
                PlanExerciseRequest("Overhead Press", sets = 3, reps = 8, weightKg = 40.0),
            ),
        ),
    )

    /** [minutes] after 17:00 UTC on Monday, March 2. */
    private fun at(minutes: Long): Instant = Instant.parse("2026-03-02T17:00:00Z").plus(Duration.ofMinutes(minutes))

    @Test
    fun `a plan keeps its exercises in order and can be edited and deleted`() {
        val user = newUser("plan-user")
        val id = pushPlan(user)

        val created = training.overview(user, MONDAY).plans.single()
        assertEquals(listOf("Bench Press", "Overhead Press"), created.exercises.map { it.name })
        assertEquals(0, created.timesDone)

        plans.update(user, id, PlanRequest(name = "Push v2", exercises = listOf(PlanExerciseRequest("Dips", 4, 10))))
        val edited = training.overview(user, MONDAY).plans.single()
        assertEquals("Push v2", edited.name)
        assertEquals(listOf("Dips"), edited.exercises.map { it.name })

        plans.delete(user, id)
        assertTrue(training.overview(user, MONDAY).plans.isEmpty())
    }

    @Test
    fun `a plan can only be linked to the users own routine, and nobody else can touch it`() {
        val owner = newUser("plan-owner")
        val other = newUser("plan-other")
        val foreignRoutine = gymRoutine(other)

        assertThrows<ResponseStatusException> { pushPlan(owner, routineId = foreignRoutine) }

        val plan = pushPlan(owner)
        assertThrows<ResponseStatusException> { plans.delete(other, plan) }
        assertThrows<ResponseStatusException> { training.start(other, StartWorkoutRequest(plan, MONDAY), at(0)) }
    }

    @Test
    fun `doing a workout from a plan logs sets, finishes, and ticks the linked routine off`() {
        val user = newUser("lifter")
        val routine = gymRoutine(user)
        val plan = pushPlan(user, routine)

        val workout = training.start(user, StartWorkoutRequest(plan, MONDAY), at(0))
        assertEquals("Push day", workout.name)
        assertEquals(listOf("Bench Press", "Overhead Press"), workout.exercises.map { it.name })
        assertEquals(at(120), workout.autoFinishAt)

        val bench = workout.exercises.first().id
        training.logSet(user, workout.id, bench, SetRequest(8, 60.0), at(5))
        val withSets = training.logSet(user, workout.id, bench, SetRequest(8, 60.0), at(9))
        assertEquals(2, withSets.exercises.first().sets.size)

        val result = training.finish(user, workout.id, at(52))

        assertEquals(52, result.summary.minutes)
        assertEquals(2, result.summary.sets)
        assertEquals(960.0, result.summary.volumeKg, 0.001)
        assertEquals("Gym", result.routineCompleted)
        assertEquals(1, result.stats.workoutsThisWeek)
        assertEquals(3, result.stats.weeklyTarget)
        assertTrue(result.personalBests.isEmpty()) // the first time can not beat anything

        val gym = routines.overview(user, MONDAY).routines.single()
        assertEquals(listOf(MONDAY), gym.doneDaysThisWeek)
        assertEquals(52, gym.minutesToday)

        assertNull(training.overview(user, MONDAY).active)
        assertEquals(1, training.overview(user, MONDAY).plans.single().timesDone)
    }

    @Test
    fun `the next workout shows what was done last time, and a heavier set is a personal best`() {
        val user = newUser("progressing")
        val plan = pushPlan(user)

        val first = training.start(user, StartWorkoutRequest(plan, MONDAY), at(0))
        training.logSet(user, first.id, first.exercises.first().id, SetRequest(8, 60.0), at(5))
        training.finish(user, first.id, at(40))

        val second = training.start(user, StartWorkoutRequest(plan, MONDAY.plusDays(2)), at(48 * 60))
        val previous = second.exercises.first().previous
        assertNotNull(previous)
        assertEquals(MONDAY, previous!!.day)
        assertEquals(listOf(SetValues(8, 60.0)), previous.sets)
        assertNull(second.exercises[1].previous) // overhead press was never logged

        training.logSet(user, second.id, second.exercises.first().id, SetRequest(8, 62.5), at(48 * 60 + 6))
        val result = training.finish(user, second.id, at(48 * 60 + 45))

        assertEquals(listOf(PersonalBestResponse("Bench Press", 62.5, 8)), result.personalBests)
    }

    @Test
    fun `only one workout can run at a time, and sets can be fixed while it runs`() {
        val user = newUser("busy")
        val plan = pushPlan(user)
        val workout = training.start(user, StartWorkoutRequest(plan, MONDAY), at(0))

        val conflict = assertThrows<ResponseStatusException> { training.start(user, StartWorkoutRequest(null, MONDAY), at(10)) }
        assertEquals(409, conflict.statusCode.value())

        val bench = workout.exercises.first().id
        val logged = training.logSet(user, workout.id, bench, SetRequest(5, 60.0), at(3)).exercises.first().sets.single()
        val fixed = training.updateSet(user, workout.id, logged.id, SetRequest(6, 60.0)).exercises.first().sets.single()
        assertEquals(6, fixed.reps)
        assertTrue(training.deleteSet(user, workout.id, logged.id).exercises.first().sets.isEmpty())

        val added = training.addExercise(user, workout.id, AddWorkoutExerciseRequest("Lateral Raise"))
        assertEquals("Lateral Raise", added.exercises.last().name)

        training.finish(user, workout.id, at(30))
        assertThrows<ResponseStatusException> { training.logSet(user, workout.id, bench, SetRequest(5, 60.0), at(31)) }
        assertNotNull(training.start(user, StartWorkoutRequest(null, MONDAY), at(60)))
    }

    @Test
    fun `a workout left running is ended shortly after its last set, with the routine ticked off`() {
        val user = newUser("forgetful")
        val routine = gymRoutine(user)
        val plan = pushPlan(user, routine)
        val workout = training.start(user, StartWorkoutRequest(plan, MONDAY), at(0))
        training.logSet(user, workout.id, workout.exercises.first().id, SetRequest(8, 60.0), at(50))

        training.autoFinishStale(at(100)) // not yet: only 100 minutes in
        assertNotNull(training.overview(user, MONDAY, at(100)).active)

        training.autoFinishStale(at(180))

        val overview = training.overview(user, MONDAY, at(181))
        assertNull(overview.active)
        val saved = overview.recent.single()
        assertEquals(WorkoutEndedBy.AUTO, saved.endedBy)
        assertEquals(55, saved.minutes) // last set at 50 min plus 5 min, not the 2 hours it was left running
        assertEquals(listOf(MONDAY), routines.overview(user, MONDAY).routines.single().doneDaysThisWeek)
    }

    @Test
    fun `a forgotten workout with nothing logged is dropped instead of saved`() {
        val user = newUser("never-trained")
        val routine = gymRoutine(user)
        val plan = pushPlan(user, routine)
        training.start(user, StartWorkoutRequest(plan, MONDAY), at(0))

        // the page itself also ends it, even if the timer never ran
        val overview = training.overview(user, MONDAY, at(200))

        assertNull(overview.active)
        assertTrue(overview.recent.isEmpty())
        assertEquals(0, overview.stats.totalWorkouts)
        assertTrue(routines.overview(user, MONDAY).routines.single().doneDaysThisWeek.isEmpty())
    }

    @Test
    fun `deleting a plan keeps the workouts done from it`() {
        val user = newUser("tidy")
        val plan = pushPlan(user)
        val workout = training.start(user, StartWorkoutRequest(plan, MONDAY), at(0))
        training.logSet(user, workout.id, workout.exercises.first().id, SetRequest(8, 60.0), at(5))
        training.finish(user, workout.id, at(30))

        plans.delete(user, plan)

        val kept = training.overview(user, MONDAY).recent.single()
        assertEquals("Push day", kept.name)
        assertEquals(1, kept.sets)
    }
}
