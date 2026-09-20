package com.markus.basecamp.gaming.cs2.matches

import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Tiny in-memory cache that only exists to spare the upstream rate limits when a page is reloaded a few times.
 * Nothing is written to disk or the database, and entries expire after [ttl].
 */
class TtlCache<T : Any>(private val ttl: Duration) {

    private class Entry<V>(val storedAt: Instant, val value: V)

    private val entries = ConcurrentHashMap<Long, Entry<T>>()

    fun get(key: Long): T? =
        entries[key]?.takeIf { Duration.between(it.storedAt, Instant.now()) < ttl }?.value

    fun put(key: Long, value: T) {
        entries[key] = Entry(Instant.now(), value)
    }

    fun evict(key: Long) {
        entries.remove(key)
    }
}
