package com.markus.basecamp.core.notifications

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.util.Base64

class WebPushSenderKeyCheckTest {

    /** A fresh VAPID pair in the same format scripts/generate-vapid-keys.mjs prints (base64url, no padding). */
    private fun newPair(): Pair<String, String> {
        val generator = KeyPairGenerator.getInstance("EC")
        generator.initialize(ECGenParameterSpec("secp256r1"))
        val pair = generator.generateKeyPair()
        val publicKey = pair.public as ECPublicKey
        val privateKey = pair.private as ECPrivateKey

        fun fixed32(value: BigInteger): ByteArray {
            val bytes = value.toByteArray().let { if (it.size > 32) it.copyOfRange(it.size - 32, it.size) else it }
            return ByteArray(32 - bytes.size) + bytes
        }
        val encoder = Base64.getUrlEncoder().withoutPadding()
        val point = byteArrayOf(0x04) + fixed32(publicKey.w.affineX) + fixed32(publicKey.w.affineY)
        return encoder.encodeToString(point) to encoder.encodeToString(fixed32(privateKey.s))
    }

    private val subject = "mailto:me@example.com"

    @Test
    fun `a correct pair is accepted`() {
        val (pub, priv) = newPair()
        assertNull(WebPushSender(pub, priv, subject).keyProblem)
    }

    @Test
    fun `surrounding whitespace from a copied env value is tolerated`() {
        val (pub, priv) = newPair()
        assertNull(WebPushSender(" $pub\n", "$priv\r\n", " $subject ").keyProblem)
    }

    @Test
    fun `push counts as not configured without keys, and that is not reported as a problem`() {
        val sender = WebPushSender("", "", "")
        assertNull(sender.keyProblem)
        assertTrue(!sender.configured)
    }

    @Test
    fun `a truncated public key is reported with its length`() {
        val (pub, priv) = newPair()
        val problem = WebPushSender(pub.dropLast(6), priv, subject).keyProblem
        assertNotNull(problem)
        assertTrue(problem!!.contains("87") && problem.contains("VAPID_PUBLIC_KEY"), problem)
    }

    @Test
    fun `a truncated private key is reported`() {
        val (pub, priv) = newPair()
        val problem = WebPushSender(pub, priv.dropLast(3), subject).keyProblem
        assertNotNull(problem)
        assertTrue(problem!!.contains("43") && problem.contains("VAPID_PRIVATE_KEY"), problem)
    }

    @Test
    fun `a public key from one run and a private key from another are reported`() {
        val (pubA, _) = newPair()
        val (_, privB) = newPair()
        val problem = WebPushSender(pubA, privB, subject).keyProblem
        assertNotNull(problem)
        assertTrue(problem!!.contains("do not belong together"), problem)
    }

    @Test
    fun `a subject that is neither mailto nor https is reported`() {
        val (pub, priv) = newPair()
        val problem = WebPushSender(pub, priv, "me@example.com").keyProblem
        assertNotNull(problem)
        assertTrue(problem!!.contains("mailto:"), problem)
    }
}
