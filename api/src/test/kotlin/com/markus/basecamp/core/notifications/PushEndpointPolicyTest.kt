package com.markus.basecamp.core.notifications

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PushEndpointPolicyTest {

    @Test
    fun `real browser push services are accepted`() {
        listOf(
            "https://fcm.googleapis.com/fcm/send/abc",
            "https://updates.push.services.mozilla.com/wpush/v2/abc",
            "https://web.push.apple.com/abc",
            "https://wns2-par02p.notify.windows.com/w/?token=abc",
        ).forEach { assertTrue(PushEndpointPolicy.isAllowed(it), it) }
    }

    @Test
    fun `anything else is rejected so the server cannot be used to reach arbitrary addresses`() {
        listOf(
            "http://fcm.googleapis.com/fcm/send/abc", // not https
            "https://localhost/steal",
            "https://127.0.0.1:8080/admin",
            "https://169.254.169.254/latest/meta-data",
            "https://evil.example.com/fcm.googleapis.com",
            "https://fcm.googleapis.com.evil.example.com/x", // suffix trick
            "https://notfcm.googleapis.com.evil.io/x",
            "https://user:pw@fcm.googleapis.com/x",
            "https://fcm.googleapis.com:8443/x",
            "not a url",
            "",
        ).forEach { assertFalse(PushEndpointPolicy.isAllowed(it), it) }
    }
}
