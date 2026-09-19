package com.markus.basecamp.core.auth

import com.markus.basecamp.core.Role
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.User

/**
 * Principal stored in the (serialized) session. Extends Spring's [User] so credentials are erased
 * after login and the password hash never ends up in the session table.
 */
class AppUserDetails(
    val id: Long,
    username: String,
    passwordHash: String,
    val role: Role,
    enabled: Boolean,
    accountNonLocked: Boolean,
) : User(username, passwordHash, enabled, true, true, accountNonLocked, listOf(SimpleGrantedAuthority("ROLE_${role.name}"))) {

    companion object {
        private const val serialVersionUID = 1L
    }
}
