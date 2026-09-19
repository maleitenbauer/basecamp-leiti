package com.markus.basecamp.core.auth

import org.springframework.data.jpa.repository.JpaRepository

interface AppUserRepository : JpaRepository<AppUser, Long> {
    fun findByUsername(username: String): AppUser?
    fun existsByUsername(username: String): Boolean
    fun findAllByOrderByUsernameAsc(): List<AppUser>
}
