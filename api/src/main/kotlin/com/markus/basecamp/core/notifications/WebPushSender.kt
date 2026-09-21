package com.markus.basecamp.core.notifications

import nl.martijndwars.webpush.Notification
import nl.martijndwars.webpush.PushService
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

    fun send(subscription: PushSubscription, payloadJson: String): Result {
        val push = service ?: return Result.FAILED
        // defence in depth: endpoints are checked when saved, and again before every request
        if (!PushEndpointPolicy.isAllowed(subscription.endpoint)) return Result.GONE
        return try {
            val notification = Notification(
                subscription.endpoint,
                subscription.p256dh,
                subscription.auth,
                payloadJson.toByteArray(Charsets.UTF_8),
            )
            val status = push.send(notification).statusLine.statusCode
            when (status) {
                in 200..299 -> Result.DELIVERED
                404, 410 -> Result.GONE // the browser dropped the subscription
                else -> {
                    log.warn("Push service answered HTTP {} for subscription {}", status, subscription.id)
                    Result.FAILED
                }
            }
        } catch (e: Exception) {
            log.warn("Push to subscription {} failed: {}", subscription.id, e.message)
            Result.FAILED
        }
    }
}
