package com.uniplanner.app.online

import android.content.Context
import android.os.Build
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore
import com.uniplanner.app.data.AppDatabase
import com.uniplanner.app.health.MealReminders
import com.uniplanner.app.data.BackupCodec
import com.uniplanner.app.data.replaceWith
import com.uniplanner.app.data.snapshot
import com.uniplanner.app.reminders.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await

/** A copy saved in the account that differs from what is on this phone; the student picks which to keep. */
data class BackupChoice(val uid: String, val savedAt: Long, val device: String)

/**
 * Keeps a copy of the planner (courses, deadlines, study hours, gym) in the student's account, in
 * users/{uid}/private/backup, so a lost or new phone gets everything back after signing in.
 * Each phone only uploads after it is linked to the account, so a fresh install never overwrites
 * the copy of a phone that was full of data.
 */
object CloudBackup {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()
    private lateinit var app: Context

    private val _lastSaved = MutableStateFlow<Long?>(null)
    val lastSaved: StateFlow<Long?> = _lastSaved.asStateFlow()
    private val _choice = MutableStateFlow<BackupChoice?>(null)
    val choice: StateFlow<BackupChoice?> = _choice.asStateFlow()

    private val prefs by lazy { app.getSharedPreferences("cloud_backup", Context.MODE_PRIVATE) }
    private fun doc(uid: String) = Firebase.firestore.collection("users").document(uid)
        .collection("private").document("backup")

    @OptIn(FlowPreview::class)
    fun start(context: Context) {
        app = context.applicationContext
        if (!Online.isConfigured(app)) return
        _lastSaved.value = prefs.getLong("savedAt", 0).takeIf { it > 0 }
        Firebase.auth.addAuthStateListener { auth ->
            val uid = auth.currentUser?.uid ?: return@addAuthStateListener
            scope.launch { runCatching { onSignedIn(uid) } }
        }
        val db = AppDatabase.get(app)
        scope.launch {
            combine(
                db.courses().observeAll(),
                db.deadlines().observeAll(),
                db.studySessions().observeAll(),
                db.workouts().observeAll(),
                combine(
                    db.exerciseSets().observeAll(),
                    db.health().observeWeights(),
                    db.health().observePlan(),
                    db.health().observeFood(0),
                    db.health().observeSteps(),
                ) { e, w, p, f, s -> listOf(e, w, p, f, s) },
            ) { a, b, c, d, e -> listOf(a, b, c, d, e) }
                .distinctUntilChanged()
                .drop(1)
                .debounce(15_000)
                .collect { runCatching { saveIfLinked() } }
        }
    }

    private suspend fun onSignedIn(uid: String) {
        if (prefs.getString("linkedUid", null) == uid) {
            saveIfLinked()
            return
        }
        val remote = doc(uid).get().await()
        val local = AppDatabase.get(app).snapshot()
        when {
            !remote.exists() -> link(uid).also { save(uid) }
            local.isEmpty -> restore(uid)
            else -> _choice.value = BackupChoice(
                uid,
                remote.getLong("savedAt") ?: 0,
                remote.getString("device").orEmpty(),
            )
        }
    }

    private fun link(uid: String) = prefs.edit().putString("linkedUid", uid).apply()

    private suspend fun saveIfLinked() {
        val uid = Firebase.auth.currentUser?.uid ?: return
        if (prefs.getString("linkedUid", null) == uid) save(uid)
    }

    /** Uploads this phone's planner to the account. */
    suspend fun save(uid: String = Firebase.auth.currentUser?.uid ?: error("Not signed in")) = lock.withLock {
        val now = System.currentTimeMillis()
        val data = BackupCodec.encode(AppDatabase.get(app).snapshot())
        doc(uid).set(
            mapOf(
                "format" to BackupCodec.FORMAT,
                "data" to data,
                "savedAt" to now,
                "device" to "${Build.MANUFACTURER} ${Build.MODEL}",
            ),
        ).await()
        prefs.edit().putLong("savedAt", now).apply()
        _lastSaved.value = now
    }

    /** Replaces this phone's planner with the copy in the account and links the phone to it. */
    suspend fun restore(uid: String = Firebase.auth.currentUser?.uid ?: error("Not signed in")) = lock.withLock {
        val remote = doc(uid).get().await()
        val data = remote.getString("data") ?: return@withLock
        val snapshot = BackupCodec.decode(data)
        val db = AppDatabase.get(app)
        db.deadlines().getAll().forEach { ReminderScheduler.cancelDeadline(app, it.id) }
        db.replaceWith(snapshot)
        val now = System.currentTimeMillis()
        snapshot.deadlines.filter { !it.done && it.dueAt > now }.forEach { ReminderScheduler.scheduleDeadline(app, it) }
        MealReminders.rescheduleAll(app)
        link(uid)
        prefs.edit().putLong("savedAt", remote.getLong("savedAt") ?: now).apply()
        _lastSaved.value = remote.getLong("savedAt")
    }

    /** Answer to [choice]: keep the account's copy, or replace it with this phone's. */
    fun resolveChoice(useAccountCopy: Boolean) {
        val choice = _choice.value ?: return
        _choice.value = null
        scope.launch {
            runCatching {
                if (useAccountCopy) {
                    restore(choice.uid)
                } else {
                    link(choice.uid)
                    save(choice.uid)
                }
            }
        }
    }
}
