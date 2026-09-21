package com.markus.basecamp.core

import java.time.ZoneId

/**
 * The notification API for every module: call [send] and core delivers it to the user's in-app bell and to all
 * of their push-enabled devices. Modules never deal with subscriptions or push services themselves.
 */
interface Notifier {

    fun send(userId: Long, title: String, body: String?, url: String?, category: String)

    /** The user's time zone (UTC until they have saved one). Reminders are evaluated in this zone. */
    fun zoneOf(userId: Long): ZoneId

    fun setZone(userId: Long, zone: ZoneId)
}
