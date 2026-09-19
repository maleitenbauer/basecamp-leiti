package com.markus.basecamp.core.auth

import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class AppUserDetailsService(private val users: AppUserRepository) : UserDetailsService {

    @Transactional(readOnly = true)
    override fun loadUserByUsername(username: String): UserDetails {
        val user = users.findByUsername(username.trim().lowercase())
            ?: throw UsernameNotFoundException("Unknown user")
        return AppUserDetails(
            id = user.id!!,
            username = user.username,
            passwordHash = user.passwordHash,
            role = user.role,
            enabled = user.enabled,
            accountNonLocked = user.lockedUntil?.isBefore(Instant.now()) ?: true,
        )
    }
}
