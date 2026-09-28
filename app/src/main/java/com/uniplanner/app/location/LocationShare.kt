package com.uniplanner.app.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore
import com.uniplanner.app.online.Online
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.roundToLong

/** What the student agreed to share. Both are off until they turn them on. */
data class LocationChoices(val stats: Boolean, val friends: Boolean)

/**
 * Location is only read while the app is open, and only sent when the student said yes:
 * the city and country for the app's overview, and a rounded position for accepted friends.
 */
object LocationShare {
    private const val PREFS = "location"
    private const val MIN_GAP_MS = 15 * 60_000L
    private val choices = MutableStateFlow<LocationChoices?>(null)

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun choicesFlow(ctx: Context): StateFlow<LocationChoices?> {
        if (choices.value == null) {
            val p = prefs(ctx)
            choices.value = LocationChoices(p.getBoolean("stats", false), p.getBoolean("friends", false))
        }
        return choices
    }

    fun hasPermission(ctx: Context): Boolean =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** Saves the choice and sends or removes the shared data right away. */
    suspend fun setChoices(ctx: Context, value: LocationChoices) {
        save(ctx, value)
        refresh(ctx, force = true)
    }

    /** Saves the choice now and shares in the background, for callers that are about to go away. */
    fun setChoicesLater(ctx: Context, value: LocationChoices) {
        save(ctx, value)
        val app = ctx.applicationContext
        background.launch { runCatching { refresh(app, force = true) } }
    }

    private val background = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun save(ctx: Context, value: LocationChoices) {
        prefs(ctx).edit().putBoolean("stats", value.stats).putBoolean("friends", value.friends).putLong("sentAt", 0).apply()
        choices.value = value
    }

    /** Sends the current place when sharing is on, at most every quarter of an hour; removes it when off. */
    suspend fun refresh(ctx: Context, force: Boolean = false) = withContext(Dispatchers.IO) {
        if (!Online.isConfigured(ctx)) return@withContext
        val uid = Firebase.auth.currentUser?.uid ?: return@withContext
        val name = Firebase.auth.currentUser?.displayName.orEmpty()
        val c = choicesFlow(ctx).value ?: return@withContext
        val db = Firebase.firestore
        if (!c.stats) runCatching { db.collection("stats").document(uid).delete().await() }
        if (!c.friends) runCatching { db.collection("friendLocations").document(uid).delete().await() }
        if (!c.stats && !c.friends) return@withContext
        val p = prefs(ctx)
        if (!force && System.currentTimeMillis() - p.getLong("sentAt", 0) < MIN_GAP_MS) return@withContext
        if (!hasPermission(ctx)) return@withContext
        val loc = current(ctx) ?: return@withContext
        val (city, country) = place(ctx, loc)
        val now = System.currentTimeMillis()
        if (c.stats) {
            runCatching {
                db.collection("stats").document(uid)
                    .set(mapOf("city" to city, "country" to country, "updatedAt" to now)).await()
            }
        }
        if (c.friends) {
            runCatching {
                db.collection("friendLocations").document(uid).set(
                    mapOf(
                        "name" to name,
                        // About 100 m: enough to find each other, not an exact address.
                        "lat" to round3(loc.latitude),
                        "lng" to round3(loc.longitude),
                        "city" to city,
                        "at" to now,
                    ),
                ).await()
            }
        }
        p.edit().putLong("sentAt", now).apply()
    }

    private fun round3(v: Double) = (v * 1000).roundToLong() / 1000.0

    @SuppressLint("MissingPermission")
    private suspend fun current(ctx: Context): Location? {
        val manager = ctx.getSystemService(LocationManager::class.java) ?: return null
        val providers = manager.getProviders(true)
        val fresh = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val provider = listOf(LocationManager.NETWORK_PROVIDER, "fused", LocationManager.GPS_PROVIDER)
                .firstOrNull { it in providers }
            if (provider == null) null else withTimeoutOrNull(10_000) {
                suspendCancellableCoroutine<Location?> { cont ->
                    val cancel = android.os.CancellationSignal()
                    cont.invokeOnCancellation { cancel.cancel() }
                    manager.getCurrentLocation(provider, cancel, ctx.mainExecutor) { if (cont.isActive) cont.resume(it) }
                }
            }
        } else {
            null
        }
        return fresh ?: providers.mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time }
    }

    @Suppress("DEPRECATION")
    private fun place(ctx: Context, loc: Location): Pair<String, String> = runCatching {
        if (!Geocoder.isPresent()) return "" to ""
        val a = Geocoder(ctx, Locale.getDefault()).getFromLocation(loc.latitude, loc.longitude, 1)?.firstOrNull()
            ?: return "" to ""
        (a.locality ?: a.subAdminArea ?: a.adminArea ?: "") to (a.countryName ?: "")
    }.getOrDefault("" to "")
}
