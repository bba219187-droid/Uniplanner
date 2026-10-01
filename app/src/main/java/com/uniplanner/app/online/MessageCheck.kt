package com.uniplanner.app.online

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore
import com.uniplanner.app.R
import com.uniplanner.app.reminders.Notifications
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

/**
 * With the app closed, looks for new messages every 15 minutes (the shortest Android allows) and
 * shows them. Instant alerts would need a server to push them, which the free Firebase plan lacks.
 * Encrypted previews are opened on the phone, so the text never leaves it unencrypted.
 */
class MessageCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val uid = Firebase.auth.currentUser?.uid ?: return Result.success()
        // While the app is on screen the chats show new messages themselves.
        if (Presence.visible) return Result.success()
        val ctx = applicationContext
        runCatching { ChatCrypto.prepare(ctx, uid) }
        val read = ctx.getSharedPreferences("chat_read", Context.MODE_PRIVATE)
        val told = ctx.getSharedPreferences("chat_told", Context.MODE_PRIVATE)
        val db = Firebase.firestore
        val friends = db.collection("friendships").whereArrayContains("members", uid).get().await().documents
            .filter { it.getString("status") == "accepted" }
        val groups = db.collection("groups").whereArrayContains("members", uid).get().await().documents
        val chats = friends.map { d ->
            @Suppress("UNCHECKED_CAST")
            val names = d.get("names") as? Map<String, String> ?: emptyMap()
            Triple(ChatKind.FRIEND, d, names.entries.firstOrNull { it.key != uid }?.value.orEmpty())
        } + groups.map { d -> Triple(ChatKind.GROUP, d, d.getString("name").orEmpty()) }

        val edit = told.edit()
        for ((kind, d, title) in chats) {
            val at = (d.get("lastAt") as? Timestamp)?.toDate()?.time ?: continue
            if (d.getString("lastBy") == uid) continue
            val key = (if (kind == ChatKind.FRIEND) "f_" else "g_") + d.id
            if (at <= maxOf(read.getLong(key, 0), told.getLong(key, 0))) continue
            val raw = d.getString("lastText").orEmpty()
            val text = runCatching { ChatCrypto.openPreview(kind, d.id, raw) }.getOrNull()
                ?: ctx.getString(R.string.notify_new_message)
            val by = d.getString("lastName").orEmpty().substringBefore(' ')
            Notifications.show(
                ctx,
                id = 80_000 + Math.floorMod(key.hashCode(), 10_000),
                channel = Notifications.CHANNEL_MESSAGES,
                title = title.ifBlank { ctx.getString(R.string.app_name) },
                text = if (kind == ChatKind.GROUP && by.isNotBlank()) "$by: $text" else text,
            )
            edit.putLong(key, at)
        }
        edit.apply()
        return Result.success()
    }

    companion object {
        private const val WORK = "message-check"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<MessageCheckWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
