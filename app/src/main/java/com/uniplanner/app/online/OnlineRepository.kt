package com.uniplanner.app.online

import android.content.Context
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.Timestamp
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.tasks.await
import java.util.Date

data class Profile(
    val uid: String = "",
    val name: String = "",
    val university: String = "",
    val course: String = "",
    val level: String = "",
    val friendCode: String = "",
)

data class Friendship(
    val id: String,
    val otherUid: String,
    val otherName: String,
    val accepted: Boolean,
    /** True when the other person sent the request and it is waiting for me. */
    val incoming: Boolean,
    val last: LastMessage? = null,
)

/** The newest message of a conversation, kept on the conversation so the list needs one read. */
data class LastMessage(val text: String, val at: Long, val byUid: String, val byName: String)

data class ChatMessage(
    val id: String,
    val authorId: String,
    val authorName: String,
    val text: String,
    val createdAt: Long,
    /** Still on its way to the server. */
    val pending: Boolean,
    val type: MessageType = MessageType.TEXT,
    val attachment: Attachment? = null,
    val place: SharedPlace? = null,
)

enum class ChatKind { FRIEND, GROUP }

data class Group(
    val id: String,
    val name: String,
    val description: String,
    val inviteCode: String,
    val memberCount: Int,
    val ownerId: String,
    val last: LastMessage? = null,
)

data class Post(
    val id: String,
    val authorName: String,
    val text: String,
    val link: String,
    val createdAt: Long,
)

/** Online features need the Firebase config file; without it the app stays offline-only. */
object Online {
    fun isConfigured(context: Context): Boolean = FirebaseApp.getApps(context).isNotEmpty()

    /** Web client id generated from google-services.json, needed for Google sign-in. */
    fun googleClientId(context: Context): String? {
        val id = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        return if (id != 0) context.getString(id) else null
    }

    fun newCode(length: Int = 6): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..length).map { chars.random() }.joinToString("")
    }

    fun friendshipId(a: String, b: String): String = listOf(a, b).sorted().joinToString("_")
}

class OnlineRepository {
    private val auth: FirebaseAuth get() = Firebase.auth
    private val db: FirebaseFirestore get() = Firebase.firestore

    val uid: String? get() = auth.currentUser?.uid

    fun authState(): Flow<String?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser?.uid) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    suspend fun signIn(email: String, password: String) {
        val user = auth.signInWithEmailAndPassword(email.trim(), password).await().user ?: return
        // An account whose profile failed to save when it was created gets one now.
        runCatching { ensureProfile(user.uid, user.displayName ?: email.substringBefore('@')) }
    }

    /** The student's profile, created first if it is missing. */
    suspend fun loadProfile(uid: String): Profile {
        val user = auth.currentUser
        ensureProfile(uid, user?.displayName ?: user?.email?.substringBefore('@').orEmpty())
        val snap = db.collection("users").document(uid).get().await()
        return Profile(
            uid = uid,
            name = snap.getString("name").orEmpty(),
            university = snap.getString("university").orEmpty(),
            course = snap.getString("course").orEmpty(),
            level = snap.getString("level").orEmpty(),
            friendCode = snap.getString("friendCode").orEmpty(),
        )
    }

    suspend fun register(email: String, password: String, name: String) {
        val user = auth.createUserWithEmailAndPassword(email.trim(), password).await().user ?: return
        ensureProfile(user.uid, name)
    }

    suspend fun signInWithGoogle(idToken: String) {
        val user = auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await().user ?: return
        ensureProfile(user.uid, user.displayName.orEmpty())
    }

    suspend fun sendPasswordReset(email: String) {
        auth.sendPasswordResetEmail(email.trim()).await()
    }

    fun signOut() = auth.signOut()

    private suspend fun ensureProfile(uid: String, name: String) {
        val ref = db.collection("users").document(uid)
        if (!ref.get().await().exists()) {
            ref.set(
                mapOf(
                    "name" to name.trim(),
                    "university" to "",
                    "course" to "",
                    "level" to "",
                    "friendCode" to Online.newCode(),
                    "createdAt" to FieldValue.serverTimestamp(),
                ),
            ).await()
        }
    }

    fun profile(uid: String): Flow<Profile?> = callbackFlow {
        val reg = db.collection("users").document(uid).addSnapshotListener { snap, e ->
                if (e != null) {
                    close(e)
                    return@addSnapshotListener
                }
            if (snap == null || !snap.exists()) {
                trySend(null)
            } else {
                trySend(
                    Profile(
                        uid = uid,
                        name = snap.getString("name").orEmpty(),
                        university = snap.getString("university").orEmpty(),
                        course = snap.getString("course").orEmpty(),
                        level = snap.getString("level").orEmpty(),
                        friendCode = snap.getString("friendCode").orEmpty(),
                    ),
                )
            }
        }
        awaitClose { reg.remove() }
    }.retrying()

    suspend fun saveProfile(uid: String, name: String, university: String, course: String, level: String) {
        ensureProfile(uid, name)
        db.collection("users").document(uid).update(
            mapOf("name" to name.trim(), "university" to university.trim(), "course" to course.trim(), "level" to level),
        ).await()
    }

    // --- Friends -----------------------------------------------------------

    fun friendships(uid: String): Flow<List<Friendship>> = callbackFlow {
        val reg = db.collection("friendships").whereArrayContains("members", uid)
            .addSnapshotListener { snap, e ->
                if (e != null) {
                    close(e)
                    return@addSnapshotListener
                }
                val list = snap?.documents.orEmpty().mapNotNull { d ->
                    @Suppress("UNCHECKED_CAST")
                    val members = d.get("members") as? List<String> ?: return@mapNotNull null
                    val other = members.firstOrNull { it != uid } ?: return@mapNotNull null
                    @Suppress("UNCHECKED_CAST")
                    val names = d.get("names") as? Map<String, String> ?: emptyMap()
                    Friendship(
                        id = d.id,
                        otherUid = other,
                        otherName = names[other].orEmpty(),
                        accepted = d.getString("status") == "accepted",
                        incoming = d.getString("requestedBy") != uid,
                        last = d.lastMessage(),
                    )
                }
                trySend(list.sortedBy { it.otherName.lowercase() })
            }
        awaitClose { reg.remove() }
    }.retrying()

    /** Returns false when no student has that friend code. */
    suspend fun sendFriendRequest(me: Profile, code: String): Boolean {
        val found = db.collection("users").whereEqualTo("friendCode", code.trim().uppercase()).limit(1)
            .get().await().documents.firstOrNull() ?: return false
        if (found.id == me.uid) return false
        val ref = db.collection("friendships").document(Online.friendshipId(me.uid, found.id))
        // Already friends or already asked: nothing to do (rules only let the receiver change it).
        if (ref.get().await().exists()) return true
        ref.set(
            mapOf(
                "members" to listOf(me.uid, found.id),
                "names" to mapOf(me.uid to me.name, found.id to found.getString("name").orEmpty()),
                "requestedBy" to me.uid,
                "status" to "pending",
                "createdAt" to FieldValue.serverTimestamp(),
            ),
        ).await()
        return true
    }

    suspend fun acceptFriend(id: String) {
        db.collection("friendships").document(id).update("status", "accepted").await()
    }

    suspend fun removeFriend(id: String) {
        db.collection("friendships").document(id).delete().await()
    }

    // --- Groups ------------------------------------------------------------

    fun groups(uid: String): Flow<List<Group>> = callbackFlow {
        val reg = db.collection("groups").whereArrayContains("members", uid)
            .addSnapshotListener { snap, e ->
                if (e != null) {
                    close(e)
                    return@addSnapshotListener
                }
                trySend(
                    snap?.documents.orEmpty().map { d ->
                        Group(
                            id = d.id,
                            name = d.getString("name").orEmpty(),
                            description = d.getString("description").orEmpty(),
                            inviteCode = d.getString("inviteCode").orEmpty(),
                            memberCount = (d.get("members") as? List<*>)?.size ?: 0,
                            ownerId = d.getString("ownerId").orEmpty(),
                            last = d.lastMessage(),
                        )
                    }.sortedBy { it.name.lowercase() },
                )
            }
        awaitClose { reg.remove() }
    }.retrying()

    suspend fun createGroup(me: Profile, name: String, description: String) {
        db.collection("groups").add(
            mapOf(
                "name" to name.trim(),
                "description" to description.trim(),
                "ownerId" to me.uid,
                "members" to listOf(me.uid),
                "inviteCode" to Online.newCode(8),
                "createdAt" to FieldValue.serverTimestamp(),
            ),
        ).await()
    }

    /** Returns false when no group has that invite code. */
    suspend fun joinGroup(me: Profile, code: String): Boolean {
        val group = db.collection("groups").whereEqualTo("inviteCode", code.trim().uppercase()).limit(1)
            .get().await().documents.firstOrNull() ?: return false
        val members = group.get("members") as? List<*> ?: emptyList<Any>()
        if (me.uid !in members) group.reference.update("members", FieldValue.arrayUnion(me.uid)).await()
        return true
    }

    suspend fun leaveGroup(me: Profile, groupId: String) {
        db.collection("groups").document(groupId).update("members", FieldValue.arrayRemove(me.uid)).await()
    }

    fun posts(groupId: String): Flow<List<Post>> = callbackFlow {
        val reg = db.collection("groups").document(groupId).collection("posts")
            .orderBy("createdAt", Query.Direction.DESCENDING).limit(200)
            .addSnapshotListener { snap, e ->
                if (e != null) {
                    close(e)
                    return@addSnapshotListener
                }
                trySend(
                    snap?.documents.orEmpty().map { d ->
                        Post(
                            id = d.id,
                            authorName = d.getString("authorName").orEmpty(),
                            text = d.getString("text").orEmpty(),
                            link = d.getString("link").orEmpty(),
                            createdAt = d.getTimestamp("createdAt")?.toDate()?.time ?: System.currentTimeMillis(),
                        )
                    },
                )
            }
        awaitClose { reg.remove() }
    }.retrying()

    suspend fun addPost(me: Profile, groupId: String, text: String, link: String) {
        db.collection("groups").document(groupId).collection("posts").add(
            mapOf(
                "authorId" to me.uid,
                "authorName" to me.name,
                "text" to text.trim(),
                "link" to link.trim(),
                "createdAt" to FieldValue.serverTimestamp(),
            ),
        ).await()
    }

    // --- Chat --------------------------------------------------------------

    private fun conversation(kind: ChatKind, id: String) =
        if (kind == ChatKind.FRIEND) db.collection("friendships").document(id) else db.collection("groups").document(id)

    // Group messages live in "posts", where the first version of groups kept them.
    private fun messagesOf(kind: ChatKind, id: String) =
        conversation(kind, id).collection(if (kind == ChatKind.FRIEND) "messages" else "posts")

    fun messages(kind: ChatKind, id: String): Flow<List<ChatMessage>> = callbackFlow {
        val reg = messagesOf(kind, id).orderBy("createdAt", Query.Direction.DESCENDING).limit(300)
            .addSnapshotListener { snap, e ->
                if (e != null) {
                    close(e)
                    return@addSnapshotListener
                }
                trySend(
                    snap?.documents.orEmpty().map { d -> message(kind, id, d) },
                )
            }
        awaitClose { reg.remove() }
    }.retrying()

    /**
     * Any member can write any fields, so each one is read as the type it should have and a
     * malformed message shows as empty instead of stopping the chat.
     */
    private fun message(kind: ChatKind, chatId: String, d: DocumentSnapshot): ChatMessage {
        fun str(field: String) = d.get(field) as? String
        fun num(field: String) = d.get(field) as? Number
        val text = str("text").orEmpty()
        val link = str("link").orEmpty()
        val type = MessageType.of(str("type"))
        val blobId = Attachments.validBlobId(str("blobId"))
        val lat = num("lat")?.toDouble()
        val lng = num("lng")?.toDouble()
        // Estimated while it is on its way, the same estimate as the chat's newest-message time.
        val at = d.get("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) as? Timestamp
        return ChatMessage(
            id = d.id,
            authorId = str("authorId").orEmpty(),
            authorName = str("authorName").orEmpty(),
            text = if (link.isNotBlank() && link !in text) listOf(text, link).filter { it.isNotBlank() }.joinToString("\n") else text,
            createdAt = at?.toDate()?.time ?: System.currentTimeMillis(),
            pending = d.metadata.hasPendingWrites(),
            type = type,
            attachment = if ((type == MessageType.IMAGE || type == MessageType.FILE) && blobId != null) {
                Attachment(
                    blobId = blobId,
                    parts = (num("parts")?.toInt() ?: 1).coerceIn(1, Attachments.partsOf(Attachments.MAX_FILE_BYTES)),
                    fileName = str("fileName").orEmpty(),
                    size = num("fileSize")?.toLong() ?: 0L,
                    mime = str("mime") ?: "application/octet-stream",
                    width = num("width")?.toInt() ?: 0,
                    height = num("height")?.toInt() ?: 0,
                    thumb = (d.get("thumb") as? Blob)?.toBytes(),
                    cacheKey = Attachments.cacheKey(kind, chatId, blobId),
                )
            } else {
                null
            },
            place = if (type == MessageType.LOCATION && lat != null && lng != null && lat in -90.0..90.0 && lng in -180.0..180.0) {
                SharedPlace(lat, lng, text)
            } else {
                null
            },
        )
    }

    suspend fun sendMessage(me: Profile, kind: ChatKind, id: String, text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        post(me, kind, id, mapOf("text" to clean), preview = clean)
    }

    private suspend fun post(me: Profile, kind: ChatKind, id: String, fields: Map<String, Any>, preview: String) {
        write(me, kind, id, fields, preview).await()
    }

    /**
     * Writes a message and keeps it as the conversation's newest, in one go. Firestore keeps it on
     * the phone until it reaches the server, so callers need not wait for the returned task.
     */
    private fun write(me: Profile, kind: ChatKind, id: String, fields: Map<String, Any>, preview: String): Task<Void> {
        val batch = db.batch()
        val col = messagesOf(kind, id)
        batch.set(
            col.document(),
            mapOf(
                "authorId" to me.uid,
                "authorName" to me.name,
                "text" to "",
                "link" to "",
                "createdAt" to FieldValue.serverTimestamp(),
            ) + fields,
        )
        batch.update(
            conversation(kind, id),
            mapOf(
                "lastText" to preview.take(200),
                "lastAt" to FieldValue.serverTimestamp(),
                "lastBy" to me.uid,
                "lastName" to me.name,
            ),
        )
        return batch.commit()
    }

    /** A new id for an attachment's pieces, known before sending so the sender can keep a copy. */
    fun newBlobId(kind: ChatKind, id: String): String = messagesOf(kind, id).document().id

    /**
     * A photo or file. The pieces are written first and have no "createdAt", so the message list,
     * which is ordered by it, never shows them; the message comes last and points at them.
     */
    fun sendAttachment(me: Profile, kind: ChatKind, id: String, blobId: String, out: Outgoing, preview: String): Task<Void> {
        val col = messagesOf(kind, id)
        val parts = Attachments.partsOf(out.bytes.size)
        for (i in 0 until parts) {
            val from = i * Attachments.PART_BYTES
            val to = minOf(out.bytes.size, from + Attachments.PART_BYTES)
            // Not awaited: writes reach the server in order, so the pieces always land before the message.
            col.document(Attachments.partId(blobId, i)).set(
                mapOf("authorId" to me.uid, "part" to i, "data" to Blob.fromBytes(out.bytes.copyOfRange(from, to))),
            )
        }
        val fields = buildMap<String, Any> {
            put("type", out.type.key)
            put("blobId", blobId)
            put("parts", parts)
            put("fileName", out.fileName)
            put("fileSize", out.bytes.size.toLong())
            put("mime", out.mime)
            if (out.width > 0) put("width", out.width)
            if (out.height > 0) put("height", out.height)
            out.thumb?.let { put("thumb", Blob.fromBytes(it)) }
        }
        return write(me, kind, id, fields, preview)
    }

    fun sendPlace(me: Profile, kind: ChatKind, id: String, place: SharedPlace, preview: String): Task<Void> =
        write(me, kind, id, mapOf("type" to MessageType.LOCATION.key, "text" to place.name, "lat" to place.lat, "lng" to place.lng), preview)

    /** Downloads the pieces of a photo or file and puts them back together. */
    suspend fun download(kind: ChatKind, id: String, a: Attachment): ByteArray? {
        val col = messagesOf(kind, id)
        val pieces = (0 until a.parts).map { i ->
            (col.document(Attachments.partId(a.blobId, i)).get().await().get("data") as? Blob)?.toBytes() ?: return null
        }
        return pieces.fold(ByteArray(0)) { all, p -> all + p }
    }

    /**
     * Deletes a message and its pieces for everyone, leaving a "deleted" mark at the same time, as
     * messaging apps do. Messages cannot be edited, so the mark is a new message.
     */
    suspend fun deleteForEveryone(me: Profile, kind: ChatKind, id: String, m: ChatMessage, wasNewest: Boolean, deletedPreview: String) {
        val col = messagesOf(kind, id)
        val batch = db.batch()
        batch.delete(col.document(m.id))
        m.attachment?.let { a -> for (i in 0 until a.parts) batch.delete(col.document(Attachments.partId(a.blobId, i))) }
        batch.set(
            col.document(),
            mapOf(
                "authorId" to me.uid,
                "authorName" to me.name,
                "type" to MessageType.DELETED.key,
                "text" to "",
                "link" to "",
                "createdAt" to Timestamp(Date(m.createdAt)),
            ),
        )
        if (wasNewest) batch.update(conversation(kind, id), mapOf("lastText" to deletedPreview))
        batch.commit().await()
    }
}

/**
 * A listener that failed (no network yet, or a permission the server has not caught up with, such
 * as a friend request accepted a moment ago) listens again after a pause instead of going quiet.
 */
private fun <T> Flow<T>.retrying(): Flow<T> = retryWhen { cause, attempt ->
    if (cause is CancellationException) return@retryWhen false
    delay(minOf(30_000L, 1_000L shl attempt.toInt().coerceAtMost(5)))
    true
}

private fun DocumentSnapshot.lastMessage(): LastMessage? {
    val text = getString("lastText") ?: return null
    val at = (get("lastAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) as? Timestamp)?.toDate()?.time ?: System.currentTimeMillis()
    return LastMessage(text, at, getString("lastBy").orEmpty(), getString("lastName").orEmpty())
}
