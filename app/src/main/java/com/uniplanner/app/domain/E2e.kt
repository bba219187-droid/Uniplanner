package com.uniplanner.app.domain

import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * End-to-end encryption for chats, with only the Java crypto every Android phone has.
 *
 * Each account has an EC P-256 key pair; the public half is on the profile. Each conversation has
 * a random AES-256 key, sent to every member "wrapped": encrypted with a key that only the sender
 * and that member can work out (ECDH, then HKDF). Messages are AES-GCM with the conversation id as
 * associated data, so a message cannot be moved to another chat. The private key can be kept in
 * the account locked with a PIN (PBKDF2), to come back after clearing the app's data.
 */
object E2e {
    private val random = SecureRandom()
    private val b64 = Base64.getEncoder()
    private val unb64 = Base64.getDecoder()
    private const val IV = 12
    private const val TAG_BITS = 128
    const val PIN_ITERATIONS = 310_000

    fun encode(bytes: ByteArray): String = b64.encodeToString(bytes)
    fun decode(text: String): ByteArray = unb64.decode(text)

    fun newIdentity(): KeyPair = KeyPairGenerator.getInstance("EC").apply {
        initialize(ECGenParameterSpec("secp256r1"), random)
    }.generateKeyPair()

    fun publicKey(encoded: String): PublicKey = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(decode(encoded)))
    fun privateKey(pkcs8: ByteArray): PrivateKey = KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(pkcs8))
    fun encodePublic(key: PublicKey): String = encode(key.encoded)

    /** A short id for a public key, to tell which key a wrapped copy was made for. */
    fun fingerprint(publicKey: String): String =
        MessageDigest.getInstance("SHA-256").digest(decode(publicKey)).take(8).joinToString("") { "%02x".format(it) }

    fun randomBytes(n: Int): ByteArray = ByteArray(n).also(random::nextBytes)
    fun newChatKey(): ByteArray = randomBytes(32)
    fun newKeyId(): String = randomBytes(6).joinToString("") { "%02x".format(it) }

    /** AES-GCM; the result is the random IV followed by the ciphertext. */
    fun seal(key: ByteArray, plain: ByteArray, aad: String): ByteArray {
        val iv = randomBytes(IV)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, iv))
        c.updateAAD(aad.toByteArray())
        return iv + c.doFinal(plain)
    }

    /** Opens [seal]'s output; throws when the key, the data or the associated data is wrong. */
    fun open(key: ByteArray, sealed: ByteArray, aad: String): ByteArray {
        require(sealed.size > IV) { "Too short" }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, sealed, 0, IV))
        c.updateAAD(aad.toByteArray())
        return c.doFinal(sealed, IV, sealed.size - IV)
    }

    fun sealText(key: ByteArray, text: String, aad: String): String = encode(seal(key, text.toByteArray(), aad))
    fun openText(key: ByteArray, sealed: String, aad: String): String = String(open(key, decode(sealed), aad))

    /** The key shared by two people: ECDH, then HKDF-SHA256 bound to what it is for. */
    private fun shared(mine: PrivateKey, theirs: PublicKey, info: String): ByteArray {
        val secret = KeyAgreement.getInstance("ECDH").apply {
            init(mine)
            doPhase(theirs, true)
        }.generateSecret()
        return hkdf(secret, "uniplanner-e2e-v1".toByteArray(), info.toByteArray(), 32)
    }

    fun hkdf(ikm: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(salt, "HmacSHA256"))
        val prk = mac.doFinal(ikm)
        mac.init(SecretKeySpec(prk, "HmacSHA256"))
        var block = ByteArray(0)
        val out = java.io.ByteArrayOutputStream()
        var i = 1
        while (out.size() < length) {
            mac.update(block)
            mac.update(info)
            mac.update(i.toByte())
            block = mac.doFinal()
            out.write(block)
            i++
        }
        return out.toByteArray().copyOf(length)
    }

    /** A conversation key for one member, readable only with their private key and the sender's public key. */
    fun wrap(chatKey: ByteArray, mine: PrivateKey, theirs: PublicKey, context: String): String =
        encode(seal(shared(mine, theirs, "wrap|$context"), chatKey, context))

    fun unwrap(wrapped: String, mine: PrivateKey, theirs: PublicKey, context: String): ByteArray =
        open(shared(mine, theirs, "wrap|$context"), decode(wrapped), context)

    /** The private key locked with a PIN, as "salt.iterations.sealed". */
    fun lockWithPin(pkcs8: ByteArray, pin: String, iterations: Int = PIN_ITERATIONS): String {
        val salt = randomBytes(16)
        return listOf(encode(salt), iterations.toString(), encode(seal(pinKey(pin, salt, iterations), pkcs8, "pin"))).joinToString(".")
    }

    /** The private key, or null when the PIN is wrong. */
    fun unlockWithPin(locked: String, pin: String): ByteArray? = runCatching {
        val (salt, iterations, sealed) = locked.split('.')
        open(pinKey(pin, decode(salt), iterations.toInt()), decode(sealed), "pin")
    }.getOrNull()

    private fun pinKey(pin: String, salt: ByteArray, iterations: Int): ByteArray =
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(PBEKeySpec(pin.toCharArray(), salt, iterations, 256)).encoded
}
