package com.markus.basecamp.core.auth

import com.markus.basecamp.core.Role
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.time.Instant

// bcrypt only uses the first 72 bytes, so longer passwords would be silently truncated.
private const val MIN_PASSWORD = 12
private const val MAX_PASSWORD = 72

data class MeResponse(val id: Long, val username: String, val role: Role)

data class ChangePasswordRequest(
    @field:NotBlank val currentPassword: String,
    @field:Size(min = MIN_PASSWORD, max = MAX_PASSWORD) val newPassword: String,
)

data class CreateUserRequest(
    @field:Pattern(regexp = "^[A-Za-z0-9._-]{3,64}$", message = "3-64 characters: letters, digits, . _ -")
    val username: String,
    @field:Size(min = MIN_PASSWORD, max = MAX_PASSWORD) val password: String,
    val role: Role = Role.USER,
)

data class UpdateUserRequest(
    val enabled: Boolean? = null,
    val role: Role? = null,
    @field:Size(min = MIN_PASSWORD, max = MAX_PASSWORD) val newPassword: String? = null,
)

data class UserResponse(
    val id: Long,
    val username: String,
    val role: Role,
    val enabled: Boolean,
    val locked: Boolean,
    val lastLoginAt: Instant?,
    val createdAt: Instant,
)
