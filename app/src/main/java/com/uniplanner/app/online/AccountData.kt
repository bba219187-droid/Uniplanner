package com.uniplanner.app.online

import android.app.ActivityManager
import android.content.Context
import android.net.Uri
import com.google.firebase.Firebase
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.firestore
import com.uniplanner.app.BuildConfig
import com.uniplanner.app.data.AppDatabase
import com.uniplanner.app.data.BackupCodec
import com.uniplanner.app.data.snapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** The student's rights over their data (RGPD): a copy of everything, and deleting everything. */
object AccountData {
    /** Preference files that hold secrets or only the libraries' own state: never exported. */
    private fun exported(name: String) = name !in setOf("e2e", "moodle", "moodle_sso_pending") &&
        !name.contains('.') && !name.startsWith("_") && !name.startsWith("Firebase", ignoreCase = true)

    /** Writes all the student's data as one JSON file to [uri] (picked by the student). */
    suspend fun export(ctx: Context, uri: Uri) = withContext(Dispatchers.IO) {
        val root = JSONObject()
            .put("app", "UniPlanner ${BuildConfig.VERSION_NAME}")
            .put("exportedAt", java.time.OffsetDateTime.now().toString())
            .put("planner", JSONObject(BackupCodec.encode(AppDatabase.get(ctx).snapshot())))
        val settings = JSONObject()
        File(ctx.applicationInfo.dataDir, "shared_prefs").listFiles().orEmpty()
            .map { it.name.removeSuffix(".xml") }
            .filter(::exported)
            .sorted()
            .forEach { name ->
                settings.put(name, JSONObject(CloudSettings.dumpPrefs(ctx.getSharedPreferences(name, Context.MODE_PRIVATE))))
            }
        root.put("settings", settings)
        Firebase.auth.currentUser?.let { user -> root.put("account", runCatching { account(user.uid, user.email) }.getOrNull()) }
        ctx.contentResolver.openOutputStream(uri)?.use { it.write(root.toString(2).toByteArray()) }
            ?: error("Could not write the file")
    }

    /** What the account keeps online about the student (messages are end-to-end encrypted and stay in the chats). */
    private suspend fun account(uid: String, email: String?): JSONObject {
        val db = Firebase.firestore
        val profile = db.collection("users").document(uid).get().await()
        val out = JSONObject().put("uid", uid).put("email", email)
        listOf("name", "university", "course", "level", "friendCode", "publicKey").forEach { k -> out.put(k, profile.getString(k)) }
        out.put("hasPhoto", profile.contains("photo"))
        out.put("friends", JSONArray(
            db.collection("friendships").whereArrayContains("members", uid).get().await().documents.map { f ->
                val names = f.get("names") as? Map<*, *>
                JSONObject().put("name", names?.entries?.firstOrNull { it.key != uid }?.value).put("status", f.getString("status"))
            },
        ))
        out.put("groups", JSONArray(
            db.collection("groups").whereArrayContains("members", uid).get().await().documents.map { it.getString("name") },
        ))
        db.collection("stats").document(uid).get().await().data?.let { out.put("locationOverview", JSONObject(it)) }
        db.collection("friendLocations").document(uid).get().await().data?.let { out.put("locationForFriends", JSONObject(it)) }
        return out
    }

    enum class DeleteResult { DONE, CANCELLED, SIGN_IN_AGAIN }

    /**
     * Deletes the online account and everything the student wrote in it, then wipes this phone.
     * Firebase only deletes an account signed into in the last few minutes, so that is checked
     * first: nothing is deleted unless the account itself can be deleted too.
     */
    suspend fun deleteAccount(activityContext: Context): DeleteResult {
        val user = Firebase.auth.currentUser ?: return DeleteResult.DONE.also { wipePhone(activityContext) }
        val recent = System.currentTimeMillis() - (user.metadata?.lastSignInTimestamp ?: 0) < 4 * 60_000
        if (!recent) {
            if (user.providerData.none { it.providerId == GoogleAuthProvider.PROVIDER_ID }) return DeleteResult.SIGN_IN_AGAIN
            val token = Online.googleIdToken(activityContext) ?: return DeleteResult.CANCELLED
            user.reauthenticate(GoogleAuthProvider.getCredential(token, null)).await()
        }
        val uid = user.uid
        val db = Firebase.firestore
        db.collection("friendships").whereArrayContains("members", uid).get().await().documents.forEach { f ->
            runCatching { deleteMine(f.reference.collection("messages"), uid) }
            runCatching { f.reference.delete().await() }
        }
        db.collection("groups").whereArrayContains("members", uid).get().await().documents.forEach { g ->
            runCatching { deleteMine(g.reference.collection("posts"), uid) }
            val members = g.get("members") as? List<*> ?: emptyList<Any>()
            runCatching {
                if (members.size <= 1) {
                    g.getString("inviteCode")?.takeIf { it.isNotBlank() }?.let { code ->
                        runCatching { db.collection("groupInvites").document(code).delete().await() }
                    }
                    g.reference.delete().await()
                } else {
                    if (g.getString("lastBy") == uid) g.reference.update(mapOf("lastText" to "", "lastName" to "")).await()
                    g.reference.update("members", FieldValue.arrayRemove(uid)).await()
                }
            }
        }
        val me = db.collection("users").document(uid)
        val code = runCatching { me.get().await().getString("friendCode") }.getOrNull()
        listOf("backup", "settings", "keys").forEach { runCatching { me.collection("private").document(it).delete().await() } }
        runCatching { db.collection("stats").document(uid).delete().await() }
        runCatching { db.collection("friendLocations").document(uid).delete().await() }
        if (!code.isNullOrBlank()) runCatching { db.collection("friendCodes").document(code).delete().await() }
        me.delete().await()
        user.delete().await()
        wipePhone(activityContext)
        return DeleteResult.DONE
    }

    /** The student's own messages in one chat, attachments included (the rules let each author delete theirs). */
    private suspend fun deleteMine(col: CollectionReference, uid: String) {
        while (true) {
            val docs = col.whereEqualTo("authorId", uid).limit(400).get().await().documents
            if (docs.isEmpty()) return
            val batch = Firebase.firestore.batch()
            docs.forEach { batch.delete(it.reference) }
            batch.commit().await()
        }
    }

    /** Erases everything on this phone (database, settings, photos, keys) and closes the app. */
    fun wipePhone(ctx: Context) {
        ctx.getSystemService(ActivityManager::class.java).clearApplicationUserData()
    }
}
