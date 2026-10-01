package com.uniplanner.app.online

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.google.firebase.Firebase
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
import com.uniplanner.app.domain.E2e
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import java.security.PrivateKey

/** Where this phone stands with end-to-end encryption. */
enum class E2eState { OFF, NEEDS_NEW_PIN, NEEDS_PIN, READY }

/**
 * The phone side of [E2e]: this account's key pair, its PIN-locked copy in the account, and the
 * keys of each conversation.
 *
 * A conversation key travels inside the conversation's own message list, as documents named
 * key_{keyId}_{member}_{fingerprint} without "createdAt", so the chat never lists them and the
 * existing rules (members read, authors create) already cover them. A member who gets a new key
 * pair (a new phone without the PIN) gets the keys wrapped again the next time someone writes.
 */
object ChatCrypto {
    private const val PREFS = "e2e"
    private val _state = MutableStateFlow(E2eState.OFF)
    val state: StateFlow<E2eState> = _state.asStateFlow()

    private val lock = Mutex()
    @Volatile private var uid: String? = null
    @Volatile private var privateKey: PrivateKey? = null
    @Volatile private var publicKey: String? = null
    private val chatKeys = HashMap<String, MutableMap<String, ByteArray>>()
    private val sharedAt = HashMap<String, Long>()
    private val publicKeys = HashMap<String, String?>()

    private val db get() = Firebase.firestore
    private fun backup(uid: String) = db.collection("users").document(uid).collection("private").document("keys")

    private fun prefs(ctx: Context) = EncryptedSharedPreferences.create(
        PREFS,
        MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
        ctx.applicationContext,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    val myFingerprint: String? get() = publicKey?.let(E2e::fingerprint)

    /** Loads this account's keys, or says a PIN is needed to make or bring them back. */
    suspend fun prepare(ctx: Context, account: String) = lock.withLock {
        if (uid == account && privateKey != null) return@withLock
        forget()
        uid = account
        val p = prefs(ctx)
        val priv = p.getString("priv_$account", null)
        val pub = p.getString("pub_$account", null)
        if (priv != null && pub != null) {
            privateKey = E2e.privateKey(E2e.decode(priv))
            publicKey = pub
            publish(account, pub)
            _state.value = E2eState.READY
            return@withLock
        }
        val saved = runCatching { backup(account).get().await() }.getOrNull()
        _state.value = if (saved?.getString("locked") != null) E2eState.NEEDS_PIN else E2eState.NEEDS_NEW_PIN
    }

    /** Makes a new key pair and keeps a copy in the account, locked with [pin]. Old messages to the old key stay unreadable. */
    suspend fun createWithPin(ctx: Context, pin: String) = lock.withLock {
        val account = uid ?: return@withLock
        val pair = E2e.newIdentity()
        val pub = E2e.encodePublic(pair.public)
        backup(account).set(mapOf("locked" to E2e.lockWithPin(pair.private.encoded, pin), "publicKey" to pub)).await()
        keep(ctx, account, pair.private.encoded, pub)
    }

    /** Brings the key pair back from the account; false when the PIN is wrong. */
    suspend fun restoreWithPin(ctx: Context, pin: String): Boolean = lock.withLock {
        val account = uid ?: return@withLock false
        val saved = backup(account).get().await()
        val locked = saved.getString("locked") ?: return@withLock false
        val pub = saved.getString("publicKey") ?: return@withLock false
        val priv = E2e.unlockWithPin(locked, pin) ?: return@withLock false
        keep(ctx, account, priv, pub)
        true
    }

    private suspend fun keep(ctx: Context, account: String, priv: ByteArray, pub: String) {
        prefs(ctx).edit().putString("priv_$account", E2e.encode(priv)).putString("pub_$account", pub).commit()
        privateKey = E2e.privateKey(priv)
        publicKey = pub
        chatKeys.clear()
        sharedAt.clear()
        publish(account, pub)
        _state.value = E2eState.READY
    }

    private suspend fun publish(account: String, pub: String) {
        if (publicKeys[account] == pub) return
        runCatching {
            db.collection("users").document(account).set(mapOf("publicKey" to pub), SetOptions.merge()).await()
            publicKeys[account] = pub
        }
    }

    /** After signing out. */
    fun forget() {
        uid = null
        privateKey = null
        publicKey = null
        chatKeys.clear()
        sharedAt.clear()
        publicKeys.clear()
        _state.value = E2eState.OFF
    }

    private fun aad(chatId: String, keyId: String, member: String) = "$chatId|$keyId|$member"

    /** Every key of a conversation this phone can open, by key id. Reads the server only for an unknown key id. */
    suspend fun keys(kind: ChatKind, chatId: String, needed: Set<String> = emptySet()): Map<String, ByteArray> {
        val me = uid ?: return emptyMap()
        val mine = privateKey ?: return emptyMap()
        val fp = myFingerprint ?: return emptyMap()
        synchronized(chatKeys) {
            val known = chatKeys[chatId]
            if (known != null && known.keys.containsAll(needed)) return known.toMap()
        }
        val docs = chatMessages(kind, chatId).whereEqualTo("keyFor", me).get().await().documents
        val found = HashMap<String, ByteArray>()
        for (d in docs) {
            if (d.getString("forFp") != fp) continue
            val kid = d.getString("kid") ?: continue
            val from = d.getString("fromPub") ?: continue
            val wrapped = d.getString("wrapped") ?: continue
            runCatching { E2e.unwrap(wrapped, mine, E2e.publicKey(from), aad(chatId, kid, me)) }.getOrNull()?.let { found[kid] = it }
        }
        synchronized(chatKeys) {
            val all = chatKeys.getOrPut(chatId) { HashMap() }
            all.putAll(found)
            return all.toMap()
        }
    }

    fun cached(chatId: String): Map<String, ByteArray> = synchronized(chatKeys) { chatKeys[chatId]?.toMap().orEmpty() }

    /**
     * The key to write with, made the first time. Every member with a public key gets a copy of
     * every key this phone holds; null when this phone has no key pair yet.
     */
    suspend fun keyForSending(kind: ChatKind, chatId: String): Pair<String, ByteArray>? {
        val me = uid ?: return null
        val mine = privateKey ?: return null
        val myPub = publicKey ?: return null
        var held = keys(kind, chatId)
        val now = System.currentTimeMillis()
        val recent = synchronized(sharedAt) { (sharedAt[chatId] ?: 0) > now - 60_000 }
        if (held.isNotEmpty() && recent) return held.entries.minBy { it.key }.toPair()

        val conv = conversationOf(kind, chatId).get().await()
        val members = (conv.get("members") as? List<*>)?.filterIsInstance<String>().orEmpty().ifEmpty { listOf(me) }
        if (held.isEmpty()) {
            val kid = E2e.newKeyId()
            held = mapOf(kid to E2e.newChatKey())
            synchronized(chatKeys) { chatKeys.getOrPut(chatId) { HashMap() }.putAll(held) }
        }
        val col = chatMessages(kind, chatId)
        val existing = col.whereEqualTo("kind", "key").get().await().documents.map { it.id }.toSet()
        val batch = db.batch()
        var writes = 0
        for (member in members) {
            val theirPub = (if (member == me) myPub else publicKeyOf(member)) ?: continue
            val theirFp = E2e.fingerprint(theirPub)
            for ((kid, key) in held) {
                val docId = "key_${kid}_${member}_$theirFp"
                if (docId in existing) continue
                batch.set(
                    col.document(docId),
                    mapOf(
                        "kind" to "key",
                        "authorId" to me,
                        "keyFor" to member,
                        "forFp" to theirFp,
                        "fromPub" to myPub,
                        "kid" to kid,
                        "wrapped" to E2e.wrap(key, mine, E2e.publicKey(theirPub), aad(chatId, kid, member)),
                    ),
                )
                writes++
            }
        }
        if (writes > 0) batch.commit().await()
        synchronized(sharedAt) { sharedAt[chatId] = now }
        return held.entries.minBy { it.key }.toPair()
    }

    private suspend fun publicKeyOf(member: String): String? {
        synchronized(publicKeys) { if (publicKeys.containsKey(member)) return publicKeys[member] }
        val pub = runCatching { db.collection("users").document(member).get().await().getString("publicKey") }.getOrNull()
        synchronized(publicKeys) { publicKeys[member] = pub }
        return pub
    }

    // --- Sealing message fields ---------------------------------------------

    /** Fields sealed with the conversation key, as stored in a message's "enc". */
    fun sealFields(chatId: String, key: Pair<String, ByteArray>, fields: Map<String, Any>): Map<String, Any> =
        mapOf("kid" to key.first, "enc" to E2e.sealText(key.second, JSONObject(fields).toString(), "$chatId|msg"))

    fun openFields(chatId: String, kid: String?, enc: String?): JSONObject? {
        if (kid == null || enc == null) return null
        val key = cached(chatId)[kid] ?: return null
        return runCatching { JSONObject(E2e.openText(key, enc, "$chatId|msg")) }.getOrNull()
    }

    fun sealBytes(chatId: String, key: Pair<String, ByteArray>, bytes: ByteArray, what: String): ByteArray =
        E2e.seal(key.second, bytes, "$chatId|$what")

    fun openBytes(chatId: String, kid: String?, bytes: ByteArray, what: String): ByteArray? {
        val key = kid?.let { cached(chatId)[it] } ?: return null
        return runCatching { E2e.open(key, bytes, "$chatId|$what") }.getOrNull()
    }

    /** The chat list's preview: "e1:{keyId}:{sealed}". */
    fun sealPreview(chatId: String, key: Pair<String, ByteArray>, text: String): String =
        "$PREVIEW${key.first}:${E2e.sealText(key.second, text, "$chatId|preview")}"

    fun isSealedPreview(text: String) = text.startsWith(PREVIEW)

    suspend fun openPreview(kind: ChatKind, chatId: String, text: String): String? {
        if (!isSealedPreview(text)) return text
        val rest = text.removePrefix(PREVIEW)
        val kid = rest.substringBefore(':')
        val key = runCatching { keys(kind, chatId, setOf(kid))[kid] }.getOrNull() ?: return null
        return runCatching { E2e.openText(key, rest.substringAfter(':'), "$chatId|preview") }.getOrNull()
    }

    private const val PREVIEW = "e1:"
}
