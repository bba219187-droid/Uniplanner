package com.uniplanner.app.online

import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** A friend's "online" or "last seen", like WhatsApp. */
data class Seen(val online: Boolean, val at: Long?)

/**
 * While the app is open it writes "online" and the time on the profile every minute; on leaving
 * it writes the time once more with "online" off. A friend counts as online only while that time
 * is recent, so a phone that died without saying goodbye stops showing online after a while.
 */
object Presence {
    const val BEAT_MS = 60_000L
    private const val FRESH_MS = 150_000L

    /** Whether the app is on screen now. */
    @Volatile var visible = false

    fun beat(online: Boolean) {
        visible = online
        val uid = Firebase.auth.currentUser?.uid ?: return
        Firebase.firestore.collection("users").document(uid)
            .set(mapOf("online" to online, "seenAt" to FieldValue.serverTimestamp()), SetOptions.merge())
    }

    fun of(uid: String): Flow<Seen> = callbackFlow {
        val reg = Firebase.firestore.collection("users").document(uid).addSnapshotListener { snap, _ ->
            val at = (snap?.get("seenAt") as? Timestamp)?.toDate()?.time
            val online = snap?.getBoolean("online") == true && at != null && at > System.currentTimeMillis() - FRESH_MS
            trySend(Seen(online, at))
        }
        awaitClose { reg.remove() }
    }
}
