package com.markus.basecamp.logbook.todos

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalTime

class TodoReminderRulesTest {

    private val today = LocalDate.of(2026, 3, 10)
    private val eight = LocalTime.of(8, 0)

    @Test
    fun `not due before the chosen time`() {
        assertFalse(TodoReminderRules.isDue(today, LocalTime.of(7, 59), eight, null))
    }

    @Test
    fun `due exactly at and after the chosen time`() {
        assertTrue(TodoReminderRules.isDue(today, eight, eight, null))
        assertTrue(TodoReminderRules.isDue(today, LocalTime.of(21, 30), eight, null)) // server was down at 08:00
    }

    @Test
    fun `sent at most once per local day`() {
        assertFalse(TodoReminderRules.isDue(today, LocalTime.of(12, 0), eight, today))
        assertTrue(TodoReminderRules.isDue(today, LocalTime.of(12, 0), eight, today.minusDays(1)))
    }

    @Test
    fun `nothing to remind about gives no digest`() {
        assertNull(TodoReminderRules.digest(emptyList(), emptyList()))
    }

    @Test
    fun `digest counts and lists overdue first`() {
        val digest = TodoReminderRules.digest(dueToday = listOf("Call bank"), overdue = listOf("Pay rent", "Renew passport"))
        assertNotNull(digest)
        assertEquals("Todos: 1 due today, 2 overdue", digest!!.title)
        assertEquals("Overdue: Pay rent\nOverdue: Renew passport\nToday: Call bank", digest.body)
    }

    @Test
    fun `digest with only one kind`() {
        assertEquals("Todos: 2 due today", TodoReminderRules.digest(listOf("a", "b"), emptyList())!!.title)
        assertEquals("Todos: 1 overdue", TodoReminderRules.digest(emptyList(), listOf("a"))!!.title)
    }

    @Test
    fun `long digests are shortened`() {
        val digest = TodoReminderRules.digest(dueToday = (1..6).map { "Task $it" }, overdue = emptyList())!!
        assertEquals(5, digest.body.lines().size) // 4 listed + "+ 2 more"
        assertTrue(digest.body.endsWith("+ 2 more"))
    }
}
