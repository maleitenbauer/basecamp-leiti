package com.markus.basecamp.logbook.todos

import java.time.LocalDate
import java.time.LocalTime

/** Pure decisions and texts for the daily reminder, kept free of Spring so they are easy to test. */
object TodoReminderRules {

    /** A reminder goes out once per local day, at or after the chosen time (also when the server was down at that time). */
    fun isDue(localDate: LocalDate, localTime: LocalTime, remindAt: LocalTime, lastSentOn: LocalDate?): Boolean =
        lastSentOn != localDate && !localTime.isBefore(remindAt)

    class Digest(val title: String, val body: String)

    private const val MAX_LISTED = 4

    /** Null when there is nothing to remind about. Overdue items are listed first. */
    fun digest(dueToday: List<String>, overdue: List<String>): Digest? {
        if (dueToday.isEmpty() && overdue.isEmpty()) return null

        val parts = buildList {
            if (dueToday.isNotEmpty()) add("${dueToday.size} due today")
            if (overdue.isNotEmpty()) add("${overdue.size} overdue")
        }
        val lines = overdue.map { "Overdue: $it" } + dueToday.map { "Today: $it" }
        val shown = lines.take(MAX_LISTED)
        val more = lines.size - shown.size
        val body = (shown + listOfNotNull(if (more > 0) "+ $more more" else null)).joinToString("\n")

        return Digest("Todos: ${parts.joinToString(", ")}", body)
    }
}
