package com.markus.basecamp.core

import com.markus.basecamp.core.auth.AppUserDetails
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component

data class CurrentUser(val id: Long, val username: String, val role: Role)

/**
 * The only way other modules learn who is calling. Scope module data with [CurrentUser.id]
 * (e.g. a `user_id` column referencing core.app_user) so several people can share one hub.
 */
@Component
class CurrentUserProvider {

    fun get(): CurrentUser {
        val principal = SecurityContextHolder.getContext().authentication?.principal as? AppUserDetails
            ?: throw IllegalStateException("No authenticated user in the current request")
        return CurrentUser(principal.id, principal.username, principal.role)
    }
}
