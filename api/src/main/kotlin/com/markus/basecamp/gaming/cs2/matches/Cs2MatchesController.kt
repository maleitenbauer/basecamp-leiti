package com.markus.basecamp.gaming.cs2.matches

import com.markus.basecamp.core.CurrentUserProvider
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/** Gaming > Counter-Strike 2 > Matches. Everything is scoped to the signed-in user. */
@RestController
@RequestMapping("/api/gaming/cs2/matches")
class Cs2MatchesController(
    private val service: Cs2MatchesService,
    private val currentUser: CurrentUserProvider,
) {
    private val userId: Long get() = currentUser.get().id

    @GetMapping("/settings")
    fun settings() = service.settings(userId)

    @PutMapping("/settings")
    fun saveSettings(@Valid @RequestBody request: UpdateMatchSettingsRequest) = service.saveSettings(userId, request)

    @GetMapping("/faceit")
    fun faceit(@RequestParam(defaultValue = "false") refresh: Boolean) = service.faceitMatches(userId, refresh)

    @GetMapping("/leetify")
    fun leetify(@RequestParam(defaultValue = "false") refresh: Boolean) = service.leetifyMatches(userId, refresh)
}
