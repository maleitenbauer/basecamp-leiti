package com.markus.basecamp.core.auth

import org.springframework.context.event.EventListener
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent
import org.springframework.security.authentication.event.AuthenticationSuccessEvent
import org.springframework.stereotype.Component

/**
 * Brute-force protection: 5 wrong passwords lock the account for 15 minutes. While locked, the
 * authentication provider rejects logins before checking the password, so the counter cannot grow.
 */
@Component
class LoginAttemptListener(private val users: UserService) {

    @EventListener
    fun onBadCredentials(event: AuthenticationFailureBadCredentialsEvent) {
        users.recordFailure(event.authentication.name)
    }

    @EventListener
    fun onSuccess(event: AuthenticationSuccessEvent) {
        users.recordSuccess(event.authentication.name)
    }
}
