package com.uniplanner.app.online

import android.content.Context
import android.content.SharedPreferences
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore
import com.uniplanner.app.settings.PersonalSettings
import com.uniplanner.app.settings.ProfilePhoto
import com.uniplanner.app.timetable.TimetableSource
import com.uniplanner.app.timetable.TimetableStore
import com.uniplanner.app.timetable.TimetableSync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject

/**
 * Keeps the profile (the welcome answers, name, course, avatar) and the timetable in the student's
 * account, in users/{uid}/private/settings, so they come back after clearing the app's data or on a
 * new phone. The planner itself is kept by [CloudBackup].
 *
 * The phone only uploads after it has read the account's copy once, so a fresh install never
 * overwrites a full profile with an empty one.
 */
object CloudSettings {
    private const val PERSONAL = "personal"
    private const val TIMETABLE = "timetable"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()
    private lateinit var app: Context
    @Volatile private var pulledFor: String? = null

    private fun doc(uid: String) = Firebase.firestore.collection("users").document(uid)
        .collection("private").document("settings")

    @OptIn(FlowPreview::class)
    fun start(context: Context) {
        app = context.applicationContext
        if (!Online.isConfigured(app)) return
        Firebase.auth.addAuthStateListener { auth ->
            val uid = auth.currentUser?.uid
            if (uid == null) pulledFor = null else scope.launch { runCatching { pull(uid) } }
        }
        scope.launch {
            combine(PersonalSettings.flow(app), TimetableStore.flow(app)) { p, t -> p to t }
                .drop(1)
                .debounce(3_000)
                .collect { runCatching { push() } }
        }
    }

    /**
     * Brings back what this phone is missing: the profile when the welcome questions were never
     * finished here, and the timetable when it is empty. Returns true when the profile came back.
     */
    suspend fun pull(uid: String): Boolean = lock.withLock {
        if (pulledFor == uid) return@withLock false
        val remote = doc(uid).get().await()
        var gotProfile = false
        if (remote.exists()) {
            val personal = remote.getString(PERSONAL)
            if (personal != null && !PersonalSettings.get(app).done) {
                restorePrefs(prefs(PERSONAL), personal)
                PersonalSettings.reload(app)
                gotProfile = PersonalSettings.get(app).done
            }
            val timetable = remote.getString(TIMETABLE)
            val local = TimetableStore.get(app)
            if (timetable != null && local.slots.isEmpty() && local.source == TimetableSource.NONE) {
                restorePrefs(prefs(TIMETABLE), timetable)
                TimetableStore.reload(app)
                val source = TimetableStore.get(app).source
                if (source == TimetableSource.PORTAL || source == TimetableSource.CALENDAR) TimetableSync.schedule(app)
            }
        }
        runCatching { ProfilePhoto.restore(app, uid) }
        pulledFor = uid
        gotProfile
    }.also { push() }

    /** Uploads the profile and timetable, once the account's copy has been read. */
    private suspend fun push() {
        val uid = Firebase.auth.currentUser?.uid ?: return
        if (pulledFor != uid) return
        val data = mutableMapOf<String, Any>("savedAt" to System.currentTimeMillis())
        // Half-answered questions are not worth keeping over a finished profile.
        if (PersonalSettings.get(app).done) data[PERSONAL] = dumpPrefs(prefs(PERSONAL))
        val timetable = TimetableStore.get(app)
        if (timetable.slots.isNotEmpty() || timetable.source != TimetableSource.NONE) data[TIMETABLE] = dumpPrefs(prefs(TIMETABLE))
        if (data.size == 1) return
        doc(uid).set(data, com.google.firebase.firestore.SetOptions.merge()).await()
    }

    private fun prefs(name: String) = app.getSharedPreferences(name, Context.MODE_PRIVATE)

    /** Every value of a preferences file, with its type, as JSON. */
    fun dumpPrefs(p: SharedPreferences): String = JSONObject().apply {
        p.all.forEach { (key, value) ->
            val entry = when (value) {
                is Boolean -> JSONObject().put("b", value)
                is Int -> JSONObject().put("i", value)
                is Long -> JSONObject().put("l", value)
                is Float -> JSONObject().put("f", value.toDouble())
                is String -> JSONObject().put("s", value)
                is Set<*> -> JSONObject().put("set", JSONArray(value.map { it.toString() }))
                else -> null
            }
            if (entry != null) put(key, entry)
        }
    }.toString()

    /** Replaces a preferences file with values from [dumpPrefs]. */
    fun restorePrefs(p: SharedPreferences, json: String) {
        val o = JSONObject(json)
        val edit = p.edit().clear()
        o.keys().forEach { key ->
            val e = o.getJSONObject(key)
            when {
                e.has("b") -> edit.putBoolean(key, e.getBoolean("b"))
                e.has("i") -> edit.putInt(key, e.getInt("i"))
                e.has("l") -> edit.putLong(key, e.getLong("l"))
                e.has("f") -> edit.putFloat(key, e.getDouble("f").toFloat())
                e.has("s") -> edit.putString(key, e.getString("s"))
                e.has("set") -> edit.putStringSet(key, e.getJSONArray("set").let { a -> (0 until a.length()).map(a::getString).toSet() })
            }
        }
        edit.commit()
    }
}
