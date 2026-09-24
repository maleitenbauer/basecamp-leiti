package com.markus.basecamp.core.notifications

import nl.martijndwars.webpush.Encoding
import nl.martijndwars.webpush.Notification
import nl.martijndwars.webpush.PushService
import org.apache.http.util.EntityUtils
import org.bouncycastle.jce.ECNamedCurveTable
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.math.BigInteger
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse.BodyHandlers
import java.security.Security
import java.time.Duration
import java.util.Base64

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
    private val httpClient: HttpClient by lazy { HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build() }

    val configured: Boolean get() = publicKey.isNotBlank() && privateKey.isNotBlank() && subject.isNotBlank()

    /**
     * Null when the keys look right and belong together. Push services answer a bad key with a vague HTTP 403 (FCM even
     * blames a "crypto-key header"), so this says what is actually wrong: wrong length, mixed-up pair, bad subject.
     */
    val keyProblem: String? by lazy { checkKeys() }

    private fun checkKeys(): String? {
        if (!configured) return null
        val pub = runCatching { Base64.getUrlDecoder().decode(publicKey.trim()) }.getOrNull()
            ?: return "VAPID_PUBLIC_KEY is not valid base64url text; it must be 87 characters and has ${publicKey.trim().length} (cut off when copying?)"
        val priv = runCatching { Base64.getUrlDecoder().decode(privateKey.trim()) }.getOrNull()
            ?: return "VAPID_PRIVATE_KEY is not valid base64url text; it must be 43 characters and has ${privateKey.trim().length} (cut off when copying?)"
        if (pub.size != 65 || pub[0] != 0x04.toByte()) {
            return "VAPID_PUBLIC_KEY must be 87 characters long, but it has ${publicKey.trim().length} (cut off when copying?)"
        }
        if (priv.size != 32) {
            return "VAPID_PRIVATE_KEY must be 43 characters long, but it has ${privateKey.trim().length} (cut off when copying?)"
        }
        val subjectText = subject.trim()
        if (!subjectText.startsWith("mailto:") && !subjectText.startsWith("https://")) {
            return "VAPID_SUBJECT must start with mailto: or https://"
        }
        Security.addProvider(BouncyCastleProvider())
        val curve = ECNamedCurveTable.getParameterSpec("secp256r1")
        val derivedPublic = curve.g.multiply(BigInteger(1, priv)).normalize().getEncoded(false)
        if (!derivedPublic.contentEquals(pub)) {
            return "VAPID_PUBLIC_KEY and VAPID_PRIVATE_KEY do not belong together (they come from different generator runs)"
        }
        return null
    }

    private val service: PushService? by lazy {
        if (!configured) {
            null
        } else {
            Security.addProvider(BouncyCastleProvider())
            PushService(publicKey.trim(), privateKey.trim(), subject.trim())
        }
    }

    fun send(subscription: PushSubscription, payloadJson: String): Outcome {
        keyProblem?.let { return Outcome(Result.FAILED, "The server's push keys are wrong: $it") }
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
            // We let the library build the request (correct encryption + VAPID JWT) but send it ourselves, because
            // its Crypto-Key header is unpadded base64url and Chrome/FCM has been reported to reject exactly that
            // with this same "crypto-key header had invalid format" 403 (web-push-libs/webpush-java#212, confirmed
            // fix: pad it). We tried both of the library's own encodings unmodified first; both failed identically,
            // which pointed at this header rather than the endpoint rewrite AES128GCM also does.
            val apachePost = push.preparePost(notification, Encoding.AES128GCM)
            apachePost.getFirstHeader("Crypto-Key")?.let { header ->
                apachePost.removeHeaders("Crypto-Key")
                apachePost.addHeader("Crypto-Key", padBase64Segments(header.value))
            }

            // headers java.net.http.HttpRequest refuses to set itself (it computes/manages these)
            val restrictedHeaders = setOf("connection", "content-length", "expect", "host", "upgrade")
            val requestBuilder = HttpRequest.newBuilder(URI.create(apachePost.uri.toString()))
            for (header in apachePost.allHeaders) {
                if (header.name.lowercase() !in restrictedHeaders) requestBuilder.header(header.name, header.value)
            }
            val body = apachePost.entity?.let { EntityUtils.toByteArray(it) } ?: ByteArray(0)
            requestBuilder.POST(HttpRequest.BodyPublishers.ofByteArray(body))

            val response = httpClient.send(requestBuilder.build(), BodyHandlers.ofString())
            val status = response.statusCode()
            when (status) {
                in 200..299 -> Outcome(Result.DELIVERED, "accepted by the push service (HTTP $status)")
                404, 410 -> Outcome(Result.GONE, "the browser dropped this device (HTTP $status), enable push again")
                else -> {
                    val body2 = response.body().orEmpty().take(200)
                    log.warn("Push service answered HTTP {} for subscription {}: {}", status, subscription.id, body2)
                    Outcome(Result.FAILED, "the push service answered HTTP $status $body2".trim())
                }
            }
        } catch (e: Exception) {
            log.warn("Push to subscription {} failed: {}", subscription.id, e.toString())
            Outcome(Result.FAILED, e.message ?: e.javaClass.simpleName)
        }
    }

    /** "p256ecdsa=XXXX" or "dh=YYYY;p256ecdsa=XXXX" -> the same, with each base64url value padded to a multiple of 4. */
    private fun padBase64Segments(headerValue: String): String =
        headerValue.split(";").joinToString(";") { segment ->
            val separator = segment.indexOf('=')
            if (separator < 0) return@joinToString segment
            val name = segment.substring(0, separator)
            val value = segment.substring(separator + 1)
            "$name=$value${"=".repeat((4 - value.length % 4) % 4)}"
        }
}
