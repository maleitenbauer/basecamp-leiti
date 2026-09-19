package com.markus.basecamp.core.auth

import com.markus.basecamp.core.Role
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component

/** Creates the first admin from configuration, but only while there are no users at all. */
@Component
class AdminBootstrap(
    private val users: UserService,
    @Value("\${basecamp.bootstrap-admin.username}") private val username: String,
    @Value("\${basecamp.bootstrap-admin.password}") private val password: String,
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun run(args: ApplicationArguments) {
        if (users.count() > 0) return
        check(password.isNotBlank()) {
            "No users exist yet. Set BASECAMP_ADMIN_PASSWORD (12-72 characters) to create the first admin."
        }
        users.create(CreateUserRequest(username, password, Role.ADMIN))
        log.info("Created initial admin '{}'. Change the password after the first login.", username.lowercase())
    }
}
