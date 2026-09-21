package com.markus.basecamp.core.notifications

import java.net.URI

/**
 * The server POSTs to whatever endpoint a device registered, so an arbitrary URL would let a logged-in user make the
 * server call any address (SSRF). Only the real browser push services are accepted.
 */
object PushEndpointPolicy {

    /** Chrome / Edge on Android (FCM), Firefox, Safari / Apple, Windows (WNS). */
    private val ALLOWED_HOST_SUFFIXES = listOf(
        "fcm.googleapis.com",
        "push.services.mozilla.com",
        "push.apple.com",
        "notify.windows.com",
    )

    fun isAllowed(endpoint: String): Boolean {
        val uri = runCatching { URI(endpoint) }.getOrNull() ?: return false
        if (uri.scheme != "https" || uri.userInfo != null) return false
        if (uri.port != -1 && uri.port != 443) return false
        val host = uri.host?.lowercase() ?: return false
        return ALLOWED_HOST_SUFFIXES.any { host == it || host.endsWith(".$it") }
    }
}
