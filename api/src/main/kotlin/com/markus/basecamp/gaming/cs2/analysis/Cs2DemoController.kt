package com.markus.basecamp.gaming.cs2.analysis

import com.markus.basecamp.core.CurrentUserProvider
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

/** Gaming > Counter-Strike 2 > Analysis. Everything is scoped to the signed-in user. */
@RestController
@RequestMapping("/api/gaming/cs2/analysis")
class Cs2DemoController(
    private val service: Cs2DemoService,
    private val currentUser: CurrentUserProvider,
) {
    private val userId: Long get() = currentUser.get().id

    @GetMapping("/available")
    fun available() = mapOf("available" to service.analysisAvailable)

    @GetMapping("/demos")
    fun listDemos() = service.list(userId)

    @PostMapping("/demos")
    @ResponseStatus(HttpStatus.CREATED)
    fun uploadDemo(@RequestParam("file") file: MultipartFile) = service.upload(userId, file)

    @GetMapping("/demos/{id}")
    fun getAnalysis(@PathVariable id: Long) = service.getAnalysis(userId, id)

    @GetMapping("/demos/{id}/rounds/{round}/positions")
    fun getRoundPositions(@PathVariable id: Long, @PathVariable round: Int) = service.getRoundPositions(userId, id, round)

    @PostMapping("/demos/{id}/retry")
    fun retry(@PathVariable id: Long) = service.retry(userId, id)

    @DeleteMapping("/demos/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteDemo(@PathVariable id: Long) = service.delete(userId, id)
}
