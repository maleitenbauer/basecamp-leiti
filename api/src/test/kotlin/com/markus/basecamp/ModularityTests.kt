package com.markus.basecamp

import org.junit.jupiter.api.Test
import org.springframework.modulith.core.ApplicationModules

class ModularityTests {

    private val modules = ApplicationModules.of(BasecampApplication::class.java)

    @Test
    fun `module boundaries are respected`() {
        modules.verify()
    }
}
