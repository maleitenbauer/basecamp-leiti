package com.markus.basecamp.core

import jakarta.servlet.http.Cookie
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

/**
 * Full application against a real Postgres: bootstrap admin, login, session cookie, CSRF, admin-only
 * endpoints and lockout. Skipped automatically where Docker is unavailable (e.g. local Windows).
 */
@SpringBootTest(
    properties = [
        "basecamp.bootstrap-admin.username=admin",
        "basecamp.bootstrap-admin.password=correct-horse-battery",
    ],
)
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class AuthFlowIntegrationTests {

    companion object {
        @Container
        @ServiceConnection
        @JvmStatic
        val postgres = PostgreSQLContainer<Nothing>("postgres:17")
    }

    @Autowired
    lateinit var mvc: MockMvc

    private fun login(username: String, password: String): ResultActions =
        mvc.perform(post("/api/auth/login").with(csrf()).param("username", username).param("password", password))

    private fun sessionCookie(result: ResultActions): Cookie =
        checkNotNull(result.andReturn().response.getCookie("BASECAMP_SESSION")) { "no session cookie issued" }

    @Test
    fun `anonymous requests are rejected but health and ping are public`() {
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized)
        mvc.perform(get("/api/ping")).andExpect(status().isOk)
        mvc.perform(get("/actuator/health")).andExpect(status().isOk)
    }

    @Test
    fun `login without csrf token is rejected`() {
        mvc.perform(post("/api/auth/login").param("username", "admin").param("password", "correct-horse-battery"))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `login issues a session cookie that identifies the user`() {
        val cookie = sessionCookie(login("admin", "correct-horse-battery").andExpect(status().isNoContent))

        mvc.perform(get("/api/auth/me").cookie(cookie))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.username").value("admin"))
            .andExpect(jsonPath("$.role").value("ADMIN"))
    }

    @Test
    fun `wrong password is rejected`() {
        login("admin", "definitely-wrong-password").andExpect(status().isUnauthorized)
    }

    @Test
    fun `admin creates a user who cannot use admin endpoints, and repeated failures lock the account`() {
        val adminCookie = sessionCookie(login("admin", "correct-horse-battery").andExpect(status().isNoContent))

        mvc.perform(
            post("/api/admin/users").cookie(adminCookie).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"username":"guest","password":"another-long-pass","role":"USER"}"""),
        ).andExpect(status().isCreated)

        val guestCookie = sessionCookie(login("guest", "another-long-pass").andExpect(status().isNoContent))
        mvc.perform(get("/api/admin/users").cookie(guestCookie)).andExpect(status().isForbidden)
        mvc.perform(get("/api/admin/users").cookie(adminCookie)).andExpect(status().isOk)

        repeat(5) { login("guest", "wrong-wrong-wrong").andExpect(status().isUnauthorized) }
        // now locked: even the correct password is refused
        login("guest", "another-long-pass").andExpect(status().isUnauthorized)
    }
}
