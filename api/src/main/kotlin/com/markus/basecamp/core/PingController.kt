package com.markus.basecamp.core

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

data class PingResponse(val app: String, val status: String, val time: Instant)

@RestController
class PingController {

    @GetMapping("/api/ping")
    fun ping() = PingResponse(app = "basecamp", status = "ok", time = Instant.now())
}
