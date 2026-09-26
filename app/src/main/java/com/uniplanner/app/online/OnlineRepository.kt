package com.uniplanner.app.online

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

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
)

data class Group(
    val id: String,
    val name: String,
    val description: String,
    val inviteCode: String,
    val memberCount: Int,
    val ownerId: String,
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
        auth.signInWithEmailAndPassword(email.trim(), password).await()
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
        val reg = db.collection("users").document(uid).addSnapshotListener { snap, _ ->
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
    }

    suspend fun saveProfile(uid: String, name: String, university: String, course: String, level: String) {
        ensureProfile(uid, name)
        db.collection("users").document(uid).update(
            mapOf("name" to name.trim(), "university" to university.trim(), "course" to course.trim(), "level" to level),
        ).await()
    }

    // --- Friends -----------------------------------------------------------

    fun friendships(uid: String): Flow<List<Friendship>> = callbackFlow {
        val reg = db.collection("friendships").whereArrayContains("members", uid)
            .addSnapshotListener { snap, _ ->
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
                    )
                }
                trySend(list.sortedBy { it.otherName.lowercase() })
            }
        awaitClose { reg.remove() }
    }

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
            .addSnapshotListener { snap, _ ->
                trySend(
                    snap?.documents.orEmpty().map { d ->
                        Group(
                            id = d.id,
                            name = d.getString("name").orEmpty(),
                            description = d.getString("description").orEmpty(),
                            inviteCode = d.getString("inviteCode").orEmpty(),
                            memberCount = (d.get("members") as? List<*>)?.size ?: 0,
                            ownerId = d.getString("ownerId").orEmpty(),
                        )
                    }.sortedBy { it.name.lowercase() },
                )
            }
        awaitClose { reg.remove() }
    }

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
            .addSnapshotListener { snap, _ ->
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
    }

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
}
