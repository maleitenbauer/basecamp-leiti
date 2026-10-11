package com.markus.basecamp.core.report

import com.markus.basecamp.core.CurrentUserProvider
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

/** The weekly report for the signed-in user. Every module's part comes from its own WeeklyReportContributor. */
@RestController
@RequestMapping("/api/reports")
class WeeklyReportController(
    private val service: WeeklyReportService,
    private val currentUser: CurrentUserProvider,
) {
    private val userId: Long get() = currentUser.get().id

    @GetMapping("/weekly")
    fun weekly(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) weekStart: LocalDate?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) today: LocalDate?,
    ) = service.weekly(userId, weekStart, today)
}
