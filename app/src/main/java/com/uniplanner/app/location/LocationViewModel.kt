package com.uniplanner.app.location

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore
import com.uniplanner.app.online.Online
import com.uniplanner.app.online.OnlineRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Where a friend last shared they were. */
data class FriendPlace(val uid: String, val name: String, val lat: Double, val lng: Double, val city: String, val at: Long)

/** One city in the overview, with how many students are there. */
data class CityCount(val city: String, val country: String, val students: Int)

@OptIn(ExperimentalCoroutinesApi::class)
class LocationViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx = app.applicationContext
    private val configured = Online.isConfigured(app)
    private val uid: String? = if (configured) Firebase.auth.currentUser?.uid else null
    private val repo = if (configured) OnlineRepository() else null

    val signedIn: Boolean = uid != null
    val choices: StateFlow<LocationChoices?> = LocationShare.choicesFlow(ctx)

    val friends: StateFlow<List<FriendPlace>> =
        (if (uid == null || repo == null) flowOf(emptyList()) else repo.friendships(uid)).flatMapLatest { fs ->
            val accepted = fs.filter { it.accepted }
            if (accepted.isEmpty()) flowOf(emptyList())
            else combine(accepted.map { f -> friendPlace(f.otherUid, f.otherName) }) { places -> places.filterNotNull().sortedByDescending { it.at } }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val isAdmin: StateFlow<Boolean> =
        (if (uid == null) flowOf(false) else callbackFlow {
            val reg = Firebase.firestore.collection("admins").document(uid).addSnapshotListener { snap, _ ->
                trySend(snap?.exists() == true)
            }
            awaitClose { reg.remove() }
        }).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Every student who shares their city, grouped by city. Only admins can read it. */
    val cities: StateFlow<List<CityCount>?> =
        isAdmin.flatMapLatest { admin ->
            if (!admin) flowOf<List<CityCount>?>(null) else callbackFlow<List<CityCount>?> {
                val reg = Firebase.firestore.collection("stats").addSnapshotListener { snap, _ ->
                    val rows = snap?.documents.orEmpty().map { (it.getString("city").orEmpty()) to (it.getString("country").orEmpty()) }
                    trySend(
                        rows.groupingBy { it }.eachCount()
                            .map { (place, n) -> CityCount(place.first, place.second, n) }
                            .sortedByDescending { it.students },
                    )
                }
                awaitClose { reg.remove() }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private fun friendPlace(otherUid: String, fallbackName: String): Flow<FriendPlace?> = callbackFlow {
        val reg = Firebase.firestore.collection("friendLocations").document(otherUid).addSnapshotListener { d, _ ->
            val lat = d?.getDouble("lat")
            val lng = d?.getDouble("lng")
            trySend(
                if (d == null || lat == null || lng == null) null
                else FriendPlace(
                    otherUid,
                    d.getString("name").orEmpty().ifBlank { fallbackName },
                    lat, lng,
                    d.getString("city").orEmpty(),
                    d.getLong("at") ?: 0L,
                ),
            )
        }
        awaitClose { reg.remove() }
    }

    fun setChoices(value: LocationChoices) = viewModelScope.launch { LocationShare.setChoices(ctx, value) }

    fun refreshNow() = viewModelScope.launch { LocationShare.refresh(ctx, force = true) }
}
