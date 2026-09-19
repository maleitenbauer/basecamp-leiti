package com.markus.basecamp.core.auth

import jakarta.servlet.http.HttpSession
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/** Login and logout themselves are handled by the security filter chain (see SecurityConfig). */
@RestController
@RequestMapping("/api/auth")
class AuthController(private val users: UserService) {

    @GetMapping("/me")
    fun me(@AuthenticationPrincipal user: AppUserDetails) = MeResponse(user.id, user.username, user.role)

    @PostMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun changePassword(
        @AuthenticationPrincipal user: AppUserDetails,
        @Valid @RequestBody request: ChangePasswordRequest,
        session: HttpSession,
    ) {
        // other devices are signed out, this one stays
        users.changeOwnPassword(user.id, request.currentPassword, request.newPassword, session.id)
    }
}
