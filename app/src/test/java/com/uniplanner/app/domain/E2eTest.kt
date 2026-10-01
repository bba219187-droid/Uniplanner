package com.uniplanner.app.domain

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class E2eTest {
    private fun hex(s: String) = s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    @Test
    fun hkdfMatchesRfc5869() {
        val okm = E2e.hkdf(
            hex("0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b"),
            hex("000102030405060708090a0b0c"),
            hex("f0f1f2f3f4f5f6f7f8f9"),
            42,
        )
        assertArrayEquals(hex("3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf34007208d5b887185865"), okm)
    }

    @Test
    fun bothSidesOpenTheWrappedChatKey() {
        val ana = E2e.newIdentity()
        val rui = E2e.newIdentity()
        val key = E2e.newChatKey()
        val wrapped = E2e.wrap(key, ana.private, rui.public, "chat1|k1")
        assertArrayEquals(key, E2e.unwrap(wrapped, rui.private, ana.public, "chat1|k1"))
    }

    @Test
    fun anotherPersonCannotUnwrap() {
        val ana = E2e.newIdentity()
        val rui = E2e.newIdentity()
        val eve = E2e.newIdentity()
        val wrapped = E2e.wrap(E2e.newChatKey(), ana.private, rui.public, "c")
        assertTrue(runCatching { E2e.unwrap(wrapped, eve.private, ana.public, "c") }.isFailure)
    }

    @Test
    fun messageIsBoundToItsChat() {
        val key = E2e.newChatKey()
        val sealed = E2e.sealText(key, "Olá 👋", "chatA")
        assertEquals("Olá 👋", E2e.openText(key, sealed, "chatA"))
        assertTrue(runCatching { E2e.openText(key, sealed, "chatB") }.isFailure)
        assertNotEquals(sealed, E2e.sealText(key, "Olá 👋", "chatA"))
    }

    @Test
    fun keysSurviveEncoding() {
        val me = E2e.newIdentity()
        val pub = E2e.publicKey(E2e.encodePublic(me.public))
        val priv = E2e.privateKey(me.private.encoded)
        val other = E2e.newIdentity()
        val k = E2e.newChatKey()
        assertArrayEquals(k, E2e.unwrap(E2e.wrap(k, other.private, pub, "x"), priv, other.public, "x"))
        assertEquals(16, E2e.fingerprint(E2e.encodePublic(me.public)).length)
    }

    @Test
    fun pinLocksThePrivateKey() {
        val secret = E2e.newIdentity().private.encoded
        val locked = E2e.lockWithPin(secret, "123456", iterations = 1000)
        assertArrayEquals(secret, E2e.unlockWithPin(locked, "123456"))
        assertNull(E2e.unlockWithPin(locked, "654321"))
    }
}
