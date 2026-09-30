package com.uniplanner.app.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
import com.uniplanner.app.online.Online
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.roundToLong

/**
 * What the student agreed to share. All are off until they turn them on.
 * - [stats]: only the city and country, counted in the admins' overview.
 * - [friends]: the exact position and name, for accepted friends, on their map.
 * - [map]: the exact position without a name, as a dot on the admins' map.
 */
data class LocationChoices(val stats: Boolean, val friends: Boolean, val map: Boolean = false) {
    /** Something goes into the admins' overview: the city, and the position when [map] is on. */
    val overview: Boolean get() = stats || map
}

/**
 * Location is only read while the app is open, and only sent for what the student said yes to.
 */
object LocationShare {
    private const val PREFS = "location"
    /** How often the app sends the position again while it is open. */
    const val REFRESH_MS = 5 * 60_000L
    // A little under the refresh, so the next round is never skipped for being a few seconds early.
    private const val MIN_GAP_MS = 4 * 60_000L
    private const val STALE_MS = 6 * 60 * 60_000L
    private val choices = MutableStateFlow<LocationChoices?>(null)
    // One send or removal at a time, so a send that started before a switch went off cannot land after its removal.
    private val lock = Mutex()

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun choicesFlow(ctx: Context): StateFlow<LocationChoices?> {
        if (choices.value == null) {
            val p = prefs(ctx)
            choices.value = LocationChoices(p.getBoolean("stats", false), p.getBoolean("friends", false), p.getBoolean("map", false))
        }
        return choices
    }

    fun hasPermission(ctx: Context): Boolean =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** Saves the choice and sends or removes the shared data right away. */
    suspend fun setChoices(ctx: Context, value: LocationChoices) {
        save(ctx, value, exactAgreed = false)
        refresh(ctx, force = true)
    }

    /**
     * Saves the choice now and shares in the background, for callers that are about to go away.
     * The questionnaire says friends see the exact position, so its answer counts as agreeing to it.
     */
    fun setChoicesLater(ctx: Context, value: LocationChoices, exactAgreed: Boolean = false) {
        save(ctx, value, exactAgreed)
        val app = ctx.applicationContext
        background.launch { runCatching { refresh(app, force = true) } }
    }

    private val background = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * The choice belongs to the account that is signed in, so another student signing in on the same
     * phone starts with nothing shared. Turning a switch off marks its data to be removed.
     */
    private fun save(ctx: Context, value: LocationChoices, exactAgreed: Boolean) {
        val p = prefs(ctx)
        val before = choicesFlow(ctx).value ?: LocationChoices(stats = false, friends = false)
        // Friends who shared before the map agreed to about 100 m. They keep that until they agree to
        // the exact position: by turning the switch on now, in the questionnaire, or in [answerExact].
        val exact = value.friends && (exactAgreed || !before.friends || p.getBoolean("exactFriends", false))
        p.edit()
            .putBoolean("exactFriends", exact)
            .putBoolean("stats", value.stats)
            .putBoolean("friends", value.friends)
            .putBoolean("map", value.map)
            .putString("owner", Firebase.auth.currentUser?.uid ?: p.getString("owner", null))
            .putBoolean("deleteStats", p.getBoolean("deleteStats", false) || (before.overview && !value.overview))
            .putBoolean("deleteFriends", p.getBoolean("deleteFriends", false) || (before.friends && !value.friends))
            .putBoolean("clearMap", p.getBoolean("clearMap", false) || (before.map && !value.map))
            .putLong("sentAt", 0)
            .apply()
        choices.value = value
    }

    /** A student who shared with friends before the map, and has not been asked about the exact position yet. */
    fun shouldAskExact(ctx: Context): Boolean {
        val p = prefs(ctx)
        return p.getBoolean("friends", false) && !p.getBoolean("exactFriends", false) && !p.getBoolean("exactAsked", false)
    }

    /** Friends see the exact position (true), or the area of about 100 m agreed to before the map. */
    fun isExact(ctx: Context): Boolean = prefs(ctx).getBoolean("exactFriends", false)

    fun answerExact(ctx: Context, yes: Boolean) {
        prefs(ctx).edit().putBoolean("exactAsked", true).putBoolean("exactFriends", yes).putLong("sentAt", 0).apply()
        val app = ctx.applicationContext
        if (yes) background.launch { runCatching { refresh(app, force = true) } }
    }

    /**
     * Before signing out: removes what this account shared and turns sharing off on this phone.
     * False when it could not be removed (no internet), so the student stays signed in and can try again.
     */
    suspend fun stopSharing(ctx: Context): Boolean = withContext(Dispatchers.IO) {
        lock.withLock {
            val p = prefs(ctx)
            val uid = if (Online.isConfigured(ctx)) Firebase.auth.currentUser?.uid else null
            val shared = listOf("stats", "friends", "map", "deleteStats", "deleteFriends", "clearMap").any { p.getBoolean(it, false) }
            if (uid != null && shared) {
                val db = Firebase.firestore
                val stats = withTimeoutOrNull(10_000) { runCatching { db.collection("stats").document(uid).delete().await() }.isSuccess } == true
                val friends = withTimeoutOrNull(10_000) { runCatching { db.collection("friendLocations").document(uid).delete().await() }.isSuccess } == true
                if (!stats || !friends) return@withLock false
            }
            p.edit().clear().apply()
            choices.value = LocationChoices(stats = false, friends = false)
            true
        }
    }

    /** Sends the current place when sharing is on, at most every few minutes. */
    suspend fun refresh(ctx: Context, force: Boolean = false) = withContext(Dispatchers.IO) {
        lock.withLock { send(ctx, force) }
    }

    private suspend fun send(ctx: Context, force: Boolean) {
        if (!Online.isConfigured(ctx)) return
        val uid = Firebase.auth.currentUser?.uid ?: return
        val name = Firebase.auth.currentUser?.displayName.orEmpty()
        val p = prefs(ctx)
        val owner = p.getString("owner", null)
        if (owner != null && owner != uid) return
        if (owner == null) p.edit().putString("owner", uid).apply()
        val c = choicesFlow(ctx).value ?: return
        val db = Firebase.firestore
        // Removed once, when the switch went off, and tried again until it worked.
        if (p.getBoolean("deleteStats", false) && !c.overview) {
            val ok = withTimeoutOrNull(10_000) { runCatching { db.collection("stats").document(uid).delete().await() }.isSuccess }
            if (ok == true) p.edit().putBoolean("deleteStats", false).apply()
        }
        if (p.getBoolean("deleteFriends", false) && !c.friends) {
            val ok = withTimeoutOrNull(10_000) { runCatching { db.collection("friendLocations").document(uid).delete().await() }.isSuccess }
            if (ok == true) p.edit().putBoolean("deleteFriends", false).apply()
        }
        // The map was turned off but the city is still shared: the position goes, the city stays.
        if (p.getBoolean("clearMap", false) && !c.map) {
            val ok = !c.overview || withTimeoutOrNull(10_000) {
                runCatching {
                    val gone = mapOf("lat" to FieldValue.delete(), "lng" to FieldValue.delete(), "at" to FieldValue.delete())
                    db.collection("stats").document(uid).set(gone, SetOptions.merge()).await()
                }.isSuccess
            } == true
            if (ok) p.edit().putBoolean("clearMap", false).apply()
        }
        if (!c.overview && !c.friends) return
        if (!force && System.currentTimeMillis() - p.getLong("sentAt", 0) < MIN_GAP_MS) return
        if (!hasPermission(ctx)) return
        val loc = current(ctx) ?: return
        val (city, country) = place(ctx, loc)
        val now = System.currentTimeMillis()
        // When the phone only knows an old position, friends see how old it is.
        val at = if (loc.time in 1..now) loc.time else now
        if (c.overview) {
            // Replaced as a whole, so turning the map off leaves only the city.
            val overview = buildMap<String, Any> {
                put("city", city)
                put("country", country)
                put("updatedAt", now)
                if (c.map && now - at < STALE_MS) {
                    put("lat", loc.latitude)
                    put("lng", loc.longitude)
                    put("at", at)
                }
            }
            runCatching {
                withTimeoutOrNull(15_000) { db.collection("stats").document(uid).set(overview).await() }
            }
        }
        if (c.friends && now - at < STALE_MS) {
            runCatching {
                withTimeoutOrNull(15_000) {
                    val exact = p.getBoolean("exactFriends", false)
                    db.collection("friendLocations").document(uid).set(
                        buildMap<String, Any> {
                            put("name", name)
                            put("lat", if (exact) loc.latitude else round3(loc.latitude))
                            put("lng", if (exact) loc.longitude else round3(loc.longitude))
                            if (exact) put("accuracy", loc.accuracy.toDouble())
                            put("city", city)
                            put("at", at)
                        },
                    ).await()
                }
            }
        }
        p.edit().putLong("sentAt", now).apply()
    }

    /** Where the phone is now and the name of the place, to send in a chat. Null without permission or a fix. */
    suspend fun here(ctx: Context): Pair<Location, String>? = withContext(Dispatchers.IO) {
        if (!hasPermission(ctx)) return@withContext null
        val loc = runCatching { current(ctx) }.getOrNull() ?: return@withContext null
        // The phone may only know where it was hours ago; that is not "where you are now".
        val ageMs = (android.os.SystemClock.elapsedRealtimeNanos() - loc.elapsedRealtimeNanos) / 1_000_000L
        if (loc.elapsedRealtimeNanos <= 0L || ageMs > 5 * 60_000L) return@withContext null
        val (city, country) = place(ctx, loc)
        loc to listOf(city, country).filter { it.isNotBlank() }.joinToString(", ")
    }

    @SuppressLint("MissingPermission")
    private suspend fun current(ctx: Context): Location? {
        val manager = ctx.getSystemService(LocationManager::class.java) ?: return null
        val providers = manager.getProviders(true)
        val provider = listOf("fused", LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER).firstOrNull { it in providers }
        // A fresh position on every Android version; the last known one only when none comes in time.
        val fresh = if (provider == null) null else withTimeoutOrNull(10_000) {
            suspendCancellableCoroutine<Location?> { cont ->
                val cancel = android.os.CancellationSignal()
                cont.invokeOnCancellation { cancel.cancel() }
                LocationManagerCompat.getCurrentLocation(manager, provider, cancel, ContextCompat.getMainExecutor(ctx)) {
                    if (cont.isActive) cont.resume(it)
                }
            }
        }
        return fresh ?: providers.mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time }
    }

    // About 100 m, for friends who agreed to their area before exact positions existed.
    private fun round3(v: Double) = (v * 1000).roundToLong() / 1000.0

    @Suppress("DEPRECATION")
    private fun place(ctx: Context, loc: Location): Pair<String, String> = runCatching {
        if (!Geocoder.isPresent()) return "" to ""
        val a = Geocoder(ctx, Locale.getDefault()).getFromLocation(loc.latitude, loc.longitude, 1)?.firstOrNull()
            ?: return "" to ""
        (a.locality ?: a.subAdminArea ?: a.adminArea ?: "") to (a.countryName ?: "")
    }.getOrDefault("" to "")
}
