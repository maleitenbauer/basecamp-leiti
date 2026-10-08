package com.markus.basecamp.core.dashboard

import com.markus.basecamp.core.CurrentUserProvider
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * What is on the home dashboard is decided by each module's frontend widget; this only remembers how the signed-in
 * user arranged it.
 */
@RestController
@RequestMapping("/api/dashboard")
class DashboardController(
    private val service: DashboardLayoutService,
    private val currentUser: CurrentUserProvider,
) {
    private val userId: Long get() = currentUser.get().id

    @GetMapping("/layout")
    fun layout() = service.get(userId)

    @PutMapping("/layout")
    fun saveLayout(@RequestBody request: DashboardLayoutDto) = service.save(userId, request)
}

/**
 * [order] is the widget ids in the user's order, [hidden] the ones they turned off. Widgets in neither are new to the
 * user and appear by default, after the ones they have placed.
 */
data class DashboardLayoutDto(
    val order: List<String> = emptyList(),
    val hidden: List<String> = emptyList(),
)
