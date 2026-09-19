package com.markus.basecamp.core.auth

import com.markus.basecamp.core.Role
import org.springframework.http.HttpStatus
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.session.FindByIndexNameSessionRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Duration
import java.time.Instant

@Service
@Transactional
class UserService(
    private val users: AppUserRepository,
    private val encoder: PasswordEncoder,
    private val sessions: FindByIndexNameSessionRepository<*>,
) {

    companion object {
        const val MAX_FAILED_ATTEMPTS = 5
        val LOCK_DURATION: Duration = Duration.ofMinutes(15)
    }

    @Transactional(readOnly = true)
    fun count(): Long = users.count()

    @Transactional(readOnly = true)
    fun list(): List<UserResponse> = users.findAllByOrderByUsernameAsc().map { it.toResponse() }

    fun create(request: CreateUserRequest): UserResponse {
        require(request.password.length in 12..72) { "Password must be 12-72 characters" }
        val username = request.username.trim().lowercase()
        if (users.existsByUsername(username)) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Username '$username' is already taken")
        }
        val user = users.save(AppUser(username, encoder.encode(request.password)!!, request.role))
        return user.toResponse()
    }

    fun update(id: Long, request: UpdateUserRequest, actingUserId: Long): UserResponse {
        val user = users.findById(id).orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "User not found") }
        if (id == actingUserId && (request.enabled == false || (request.role != null && request.role != Role.ADMIN))) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "You cannot disable or demote yourself")
        }
        var revokeSessions = false
        request.enabled?.let {
            if (!it) revokeSessions = true
            user.enabled = it
            if (it) unlock(user)
        }
        request.role?.let {
            if (it != user.role) revokeSessions = true
            user.role = it
        }
        request.newPassword?.let {
            user.passwordHash = encoder.encode(it)!!
            unlock(user)
            revokeSessions = true
        }
        if (revokeSessions) revokeSessions(user.username)
        return user.toResponse()
    }

    fun changeOwnPassword(userId: Long, currentPassword: String, newPassword: String, keepSessionId: String?) {
        val user = users.findById(userId).orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "User not found") }
        if (!encoder.matches(currentPassword, user.passwordHash)) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Current password is incorrect")
        }
        user.passwordHash = encoder.encode(newPassword)!!
        revokeSessions(user.username, keepSessionId)
    }

    /** Called by [LoginAttemptListener]; unknown usernames are ignored on purpose. */
    fun recordFailure(username: String) {
        val user = users.findByUsername(username.trim().lowercase()) ?: return
        user.failedAttempts += 1
        if (user.failedAttempts >= MAX_FAILED_ATTEMPTS) {
            user.lockedUntil = Instant.now().plus(LOCK_DURATION)
            user.failedAttempts = 0
        }
    }

    fun recordSuccess(username: String) {
        val user = users.findByUsername(username.trim().lowercase()) ?: return
        user.failedAttempts = 0
        user.lockedUntil = null
        user.lastLoginAt = Instant.now()
    }

    private fun unlock(user: AppUser) {
        user.failedAttempts = 0
        user.lockedUntil = null
    }

    private fun revokeSessions(username: String, keepSessionId: String? = null) {
        sessions.findByPrincipalName(username).keys
            .filter { it != keepSessionId }
            .forEach { sessions.deleteById(it) }
    }

    private fun AppUser.toResponse() = UserResponse(
        id = id!!,
        username = username,
        role = role,
        enabled = enabled,
        locked = lockedUntil?.isAfter(Instant.now()) ?: false,
        lastLoginAt = lastLoginAt,
        createdAt = createdAt,
    )
}
