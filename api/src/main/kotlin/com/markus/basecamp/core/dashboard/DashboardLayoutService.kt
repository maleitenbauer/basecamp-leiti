package com.markus.basecamp.core.dashboard

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Instant

private const val MAX_WIDGETS = 200
private val WIDGET_ID = Regex("^[A-Za-z0-9._-]{1,100}$")

/**
 * Stores each user's dashboard layout. The server deliberately knows nothing about which widgets exist — the ids come
 * from the frontend's registry, so a new module's tile needs no change here. It only guarantees the stored document is
 * well-formed and bounded.
 */
@Service
@Transactional
class DashboardLayoutService(
    private val layouts: DashboardLayoutRepository,
    private val mapper: ObjectMapper,
) {

    /** The saved layout, or an empty one (meaning "all defaults") for a user who never changed it. */
    @Transactional(readOnly = true)
    fun get(userId: Long): DashboardLayoutDto =
        layouts.findById(userId)
            .map { row -> runCatching { mapper.readValue<DashboardLayoutDto>(row.layout) }.getOrDefault(DashboardLayoutDto()) }
            .orElse(DashboardLayoutDto())

    fun save(userId: Long, request: DashboardLayoutDto): DashboardLayoutDto {
        val clean = DashboardLayoutDto(order = validated(request.order), hidden = validated(request.hidden))
        val json = mapper.writeValueAsString(clean)
        val row = layouts.findById(userId).orElse(null)
        if (row != null) {
            row.layout = json
            row.updatedAt = Instant.now()
        } else {
            layouts.save(DashboardLayoutRow(userId, json))
        }
        return clean
    }

    /** Duplicates collapse to the first occurrence; malformed ids and absurd sizes are rejected. */
    private fun validated(ids: List<String>): List<String> {
        if (ids.size > MAX_WIDGETS) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Too many dashboard widgets")
        val trimmed = ids.map { it.trim() }
        if (trimmed.any { !WIDGET_ID.matches(it) }) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid widget id")
        }
        return trimmed.distinct()
    }
}
