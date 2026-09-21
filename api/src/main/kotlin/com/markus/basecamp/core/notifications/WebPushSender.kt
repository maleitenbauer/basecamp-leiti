package com.markus.basecamp.core.notifications

import nl.martijndwars.webpush.Notification
import nl.martijndwars.webpush.PushService
import org.apache.http.util.EntityUtils
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.security.Security

/** Sends one encrypted Web Push message. Does nothing (and reports [configured] = false) without VAPID keys. */
@Component
class WebPushSender(
    @Value("\${basecamp.push.vapid-public-key}") val publicKey: String,
    @Value("\${basecamp.push.vapid-private-key}") private val privateKey: String,
    @Value("\${basecamp.push.subject}") private val subject: String,
) {
    enum class Result { DELIVERED, GONE, FAILED }

    /** [detail] is shown to the user in the notification settings, so it says what the push service answered. */
    class Outcome(val result: Result, val detail: String)

    private val log = LoggerFactory.getLogger(javaClass)

    val configured: Boolean get() = publicKey.isNotBlank() && privateKey.isNotBlank() && subject.isNotBlank()

    private val service: PushService? by lazy {
        if (!configured) {
            null
        } else {
            Security.addProvider(BouncyCastleProvider())
            PushService(publicKey, privateKey, subject)
        }
    }

    fun send(subscription: PushSubscription, payloadJson: String): Outcome {
        val push: PushService? = try {
            service
        } catch (e: Exception) {
            log.warn("The VAPID keys could not be loaded: {}", e.message)
            return Outcome(Result.FAILED, "The server's VAPID keys are invalid (${e.message})")
        }
        if (push == null) return Outcome(Result.FAILED, "Push is not configured on the server")

        // defence in depth: endpoints are checked when saved, and again before every request
        if (!PushEndpointPolicy.isAllowed(subscription.endpoint)) {
            return Outcome(Result.GONE, "This device uses a push service that is not supported")
        }
        return try {
            val notification = Notification(
                subscription.endpoint,
                subscription.p256dh,
                subscription.auth,
                payloadJson.toByteArray(Charsets.UTF_8),
            )
            val response = push.send(notification)
            val status = response.statusLine.statusCode
            when (status) {
                in 200..299 -> Outcome(Result.DELIVERED, "accepted by the push service (HTTP $status)")
                404, 410 -> Outcome(Result.GONE, "the browser dropped this device (HTTP $status), enable push again")
                else -> {
                    val body = runCatching { EntityUtils.toString(response.entity) }.getOrNull().orEmpty().take(200)
                    log.warn("Push service answered HTTP {} for subscription {}: {}", status, subscription.id, body)
                    Outcome(Result.FAILED, "the push service answered HTTP $status $body".trim())
                }
            }
        } catch (e: Exception) {
            log.warn("Push to subscription {} failed: {}", subscription.id, e.toString())
            Outcome(Result.FAILED, e.message ?: e.javaClass.simpleName)
        }
    }
}
