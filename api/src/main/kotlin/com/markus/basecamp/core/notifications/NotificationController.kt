package com.markus.basecamp.core.notifications

import com.markus.basecamp.core.CurrentUserProvider
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/** The bell (in-app inbox) and the device / time zone settings. Always scoped to the signed-in user. */
@RestController
@RequestMapping("/api/notifications")
class NotificationController(
    private val service: NotificationService,
    private val currentUser: CurrentUserProvider,
) {
    private val userId: Long get() = currentUser.get().id

    @GetMapping
    fun list() = service.list(userId)

    @PostMapping("/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun readAll() = service.markAllRead(userId)

    @PostMapping("/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun read(@PathVariable id: Long) = service.markRead(userId, id)

    @GetMapping("/config")
    fun config() = service.config(userId)

    @PutMapping("/settings")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun settings(@Valid @RequestBody request: UpdateNotificationSettingsRequest) =
        service.updateTimezone(userId, request.timezone)

    @PostMapping("/subscriptions")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun subscribe(@Valid @RequestBody request: SubscribeRequest) = service.subscribe(userId, request)

    @DeleteMapping("/subscriptions/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun unsubscribe(@PathVariable id: Long) = service.unsubscribe(userId, id)

    @PostMapping("/test")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun test() = service.sendTest(userId)
}
