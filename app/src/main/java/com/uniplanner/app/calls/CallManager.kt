package com.uniplanner.app.calls

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.firestore
import com.uniplanner.app.MainActivity
import com.uniplanner.app.R
import com.uniplanner.app.domain.E2e
import com.uniplanner.app.online.ChatCrypto
import com.uniplanner.app.online.ChatKind
import com.uniplanner.app.online.chatMessages
import com.uniplanner.app.online.conversationOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import org.webrtc.PeerConnection
import org.webrtc.VideoTrack

enum class CallPhase { CALLING, RINGING, CONNECTING, ACTIVE, ENDED }

data class CallUi(
    val callId: String,
    val kind: ChatKind,
    val chatId: String,
    /** The friend's name, or the group's. */
    val title: String,
    /** Who called, for a group call ringing. */
    val callerName: String,
    val video: Boolean,
    val outgoing: Boolean,
    val phase: CallPhase,
    /** The other people connected, by uid, with their names. */
    val people: Map<String, String> = emptyMap(),
    val muted: Boolean = false,
    val speaker: Boolean = false,
    val camera: Boolean = false,
    val sharing: Boolean = false,
    val startedAt: Long? = null,
    val endedReason: String? = null,
)

/**
 * Calls in friend chats and groups, like WhatsApp. Everyone in a call connects to everyone else,
 * which works well for small groups. The signals (who rings, who joined, the offers, answers and
 * connection details) are hidden documents in the conversation's own message list, without
 * "createdAt" so the chat never shows them, and sealed with the chat's end-to-end key. Each phone
 * deletes its own when the call ends.
 *
 * For each pair, the one with the smaller account id makes the offer, so two people joining at the
 * same moment still connect exactly once.
 */
@SuppressLint("StaticFieldLeak")
object CallManager {
    const val CHANNEL = "calls"
    private const val NOTIFICATION_ID = 77_001
    private const val RING_TIMEOUT_MS = 45_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var app: Context
    private val _call = MutableStateFlow<CallUi?>(null)
    val call: StateFlow<CallUi?> = _call.asStateFlow()
    private val _remoteVideos = MutableStateFlow<Map<String, VideoTrack>>(emptyMap())
    val remoteVideos: StateFlow<Map<String, VideoTrack>> = _remoteVideos.asStateFlow()
    private val _localVideo = MutableStateFlow<VideoTrack?>(null)
    val localVideo: StateFlow<VideoTrack?> = _localVideo.asStateFlow()

    var media: CallMedia? = null
        private set
    private val peers = HashMap<String, Peer>()
    private val connected = HashSet<String>()
    private val joined = HashSet<String>()
    private val seen = HashSet<String>()
    private val waitingIce = HashMap<String, MutableList<DocumentSnapshot>>()
    private val iceCount = HashMap<String, Int>()
    private val names = HashMap<String, String>()
    private val lock = Mutex()
    private var key: Pair<String, ByteArray>? = null
    private var signals: ListenerRegistration? = null
    private var timeout: Job? = null
    private val incomingWatches = HashMap<String, ListenerRegistration>()
    private val handled = HashSet<String>()

    private val me get() = Firebase.auth.currentUser?.uid
    private fun col(c: CallUi) = chatMessages(c.kind, c.chatId)

    fun init(context: Context) {
        if (::app.isInitialized) return
        app = context.applicationContext
        if (Build.VERSION.SDK_INT >= 26) {
            app.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL, app.getString(R.string.call_channel), NotificationManager.IMPORTANCE_HIGH),
            )
        }
    }

    // --- Ringing ------------------------------------------------------------

    /** Listens for calls in each conversation, given as (kind, id) to its name. */
    fun watch(context: Context, chats: Map<Pair<ChatKind, String>, String>) {
        init(context)
        val uid = me ?: return
        val keys = chats.keys.associateBy { "${it.first}:${it.second}" }
        (incomingWatches.keys - keys.keys).forEach { incomingWatches.remove(it)?.remove() }
        for ((k, chat) in keys) {
            if (k in incomingWatches) continue
            val title = chats.getValue(chat)
            incomingWatches[k] = chatMessages(chat.first, chat.second).whereArrayContains("ring", uid).addSnapshotListener { snap, _ ->
                snap?.documents?.forEach { d -> onRing(chat.first, chat.second, title, d) }
            }
        }
    }

    fun stopWatching() {
        incomingWatches.values.forEach { it.remove() }
        incomingWatches.clear()
    }

    /** A call came in; also used when a push message wakes the app. */
    fun onRing(kind: ChatKind, chatId: String, title: String, d: DocumentSnapshot) {
        if (!::app.isInitialized) return
        val callId = d.getString("callId") ?: return
        val at = d.getLong("at") ?: return
        if (d.getString("kind") != "call" || callId in handled) return
        handled += callId
        if (at < System.currentTimeMillis() - RING_TIMEOUT_MS) return
        val from = d.getString("authorId") ?: return
        val callerName = d.getString("fromName").orEmpty()
        names[from] = callerName
        if (_call.value != null) {
            // Already in a call: a friend hears that you are busy.
            if (kind == ChatKind.FRIEND) scope.launch { runCatching { writeLeave(kind, chatId, callId, "busy") } }
            return
        }
        val video = d.getBoolean("video") == true
        _call.value = CallUi(callId, kind, chatId, title, callerName, video, outgoing = false, phase = CallPhase.RINGING)
        listen()
        startRinging()
        notifyIncoming(if (kind == ChatKind.GROUP) "$title · $callerName" else title, video)
        timeout = scope.launch {
            delay(RING_TIMEOUT_MS)
            if (_call.value?.phase == CallPhase.RINGING) finish(app.getString(R.string.call_missed), tell = false)
        }
    }

    // --- Starting and answering -----------------------------------------------

    /** Calls a friend or a whole group. The screen has asked for the microphone (and camera) first. */
    fun start(context: Context, kind: ChatKind, chatId: String, title: String, myName: String, video: Boolean) {
        init(context)
        if (_call.value != null) return
        val uid = me ?: return
        val callId = E2e.newKeyId() + E2e.newKeyId()
        handled += callId
        _call.value = CallUi(callId, kind, chatId, title, myName, video, outgoing = true, phase = CallPhase.CALLING, speaker = video)
        scope.launch {
            try {
                key = ChatCrypto.keyForSending(kind, chatId) ?: error("No key")
                val members = (conversationOf(kind, chatId).get().await().get("members") as? List<*>)
                    ?.filterIsInstance<String>().orEmpty().filter { it != uid }
                openMedia(video)
                listen()
                col(_call.value!!).document("call_$callId").set(
                    mapOf(
                        "kind" to "call", "callId" to callId, "authorId" to uid, "fromName" to myName,
                        "ring" to members, "video" to video, "at" to System.currentTimeMillis(),
                    ),
                ).await()
                writeJoin()
                timeout = launch {
                    delay(RING_TIMEOUT_MS)
                    if (_call.value?.phase == CallPhase.CALLING) finish(app.getString(R.string.call_no_answer))
                }
            } catch (e: Exception) {
                finish(app.getString(R.string.call_failed))
            }
        }
    }

    fun accept() {
        val c = _call.value ?: return
        if (c.phase != CallPhase.RINGING) return
        stopRinging()
        timeout?.cancel()
        NotificationManagerCompat.from(app).cancel(NOTIFICATION_ID)
        setCall { it.copy(phase = CallPhase.CONNECTING, speaker = c.video) }
        scope.launch {
            try {
                key = ChatCrypto.keyForSending(c.kind, c.chatId) ?: error("No key")
                openMedia(c.video)
                writeJoin()
                // Joins seen while ringing are acted on now.
                lock.withLock { joined.toList().forEach { connectTo(it) } }
            } catch (e: Exception) {
                finish(app.getString(R.string.call_failed))
            }
        }
    }

    fun decline() {
        val c = _call.value ?: return
        // A friend learns at once; a group call goes on for the others.
        if (c.kind == ChatKind.FRIEND) scope.launch { runCatching { writeLeave(c.kind, c.chatId, c.callId, "declined") } }
        finish(null, tell = false)
    }

    fun hangUp() = finish(null)

    private fun openMedia(video: Boolean) {
        val m = CallMedia(app)
        media = m
        if (video) {
            _localVideo.value = m.startCamera()
            setCall { it.copy(camera = _localVideo.value != null) }
        }
        audio(on = true, speaker = video)
        CallService.start(app, video)
    }

    // --- During the call ----------------------------------------------------

    fun toggleMute() {
        val c = _call.value ?: return
        media?.setMuted(!c.muted)
        setCall { it.copy(muted = !c.muted) }
    }

    fun toggleSpeaker() {
        val c = _call.value ?: return
        audio(on = true, speaker = !c.speaker)
        setCall { it.copy(speaker = !c.speaker) }
    }

    fun toggleCamera() {
        val m = media ?: return
        val c = _call.value ?: return
        if (c.camera) {
            m.stopVideo()
            _localVideo.value = null
            setCall { it.copy(camera = false) }
        } else {
            CallService.start(app, video = true)
            _localVideo.value = m.startCamera()
            setCall { it.copy(camera = _localVideo.value != null, sharing = false) }
        }
    }

    fun switchCamera() = media?.switchCamera()

    /** Shares the screen, with the permission Android's dialog returned. */
    fun shareScreen(permission: Intent, width: Int, height: Int) {
        val m = media ?: return
        // Android 14 wants the foreground service to declare screen capture before it starts.
        CallService.start(app, video = _call.value?.camera == true, screen = true)
        scope.launch {
            delay(300)
            _localVideo.value = runCatching { m.startScreen(permission, width, height) }.getOrNull()
            setCall { it.copy(sharing = _localVideo.value != null, camera = false) }
        }
    }

    fun stopSharing() {
        media?.stopVideo()
        _localVideo.value = null
        setCall { it.copy(sharing = false) }
    }

    // --- Signals ----------------------------------------------------------------

    private fun listen() {
        val c = _call.value ?: return
        signals?.remove()
        seen.clear()
        joined.clear()
        waitingIce.clear()
        signals = col(c).whereEqualTo("callId", c.callId).addSnapshotListener { snap, _ ->
            val docs = snap?.documents ?: return@addSnapshotListener
            scope.launch { lock.withLock { docs.forEach { runCatching { onSignal(it) } } } }
        }
    }

    private suspend fun onSignal(d: DocumentSnapshot) {
        val uid = me ?: return
        val c = _call.value ?: return
        val from = d.getString("authorId") ?: return
        if (from == uid || d.id in seen) return
        val kind = d.getString("kind")
        val inCall = media != null && c.phase != CallPhase.RINGING
        when (kind) {
            "callJoin" -> {
                seen += d.id
                joined += from
                if (inCall) connectTo(from)
            }
            "callLeave" -> {
                seen += d.id
                joined -= from
                leftBy(from, d.getString("reason"))
            }
            "callSdp" -> {
                if (d.getString("to") != uid || !inCall) return
                seen += d.id
                val f = ChatCrypto.openFields(c.chatId, d.getString("kid"), d.getString("enc"))
                    ?: ChatCrypto.keys(c.kind, c.chatId, setOfNotNull(d.getString("kid"))).let {
                        ChatCrypto.openFields(c.chatId, d.getString("kid"), d.getString("enc"))
                    } ?: return
                val sdp = CallMedia.sdpFromJson(f.getString("sdp"))
                if (sdp.type == org.webrtc.SessionDescription.Type.OFFER) {
                    val p = peers[from] ?: newPeer(from, offerer = false)
                    p.setRemote(sdp)
                    val answer = p.answer()
                    writeSdp(from, CallMedia.sdpToJson(answer))
                } else {
                    peers[from]?.setRemote(sdp)
                }
                waitingIce.remove(from)?.forEach { onSignal(it) }
            }
            "callIce" -> {
                if (d.getString("to") != uid) return
                val p = peers[from]
                if (p == null) {
                    waitingIce.getOrPut(from) { mutableListOf() } += d
                    return
                }
                seen += d.id
                val f = ChatCrypto.openFields(c.chatId, d.getString("kid"), d.getString("enc")) ?: return
                p.addIce(CallMedia.iceFromJson(f.getString("ice")))
            }
        }
    }

    /** The smaller account id of each pair makes the offer. */
    private suspend fun connectTo(other: String) {
        val uid = me ?: return
        if (other == uid || peers.containsKey(other) || uid > other) return
        val p = newPeer(other, offerer = true)
        writeSdp(other, CallMedia.sdpToJson(p.offer()))
    }

    private fun newPeer(other: String, offerer: Boolean): Peer {
        val m = media ?: error("No call")
        if (_call.value?.phase == CallPhase.CALLING) setCall { it.copy(phase = CallPhase.CONNECTING) }
        timeout?.cancel()
        scope.launch { nameOf(other) }
        return m.peer(
            offerer,
            onIce = { ice -> scope.launch { runCatching { writeIce(other, CallMedia.iceToJson(ice)) } } },
            onConnection = { state -> scope.launch { onConnection(other, state) } },
            onRemoteVideo = { t -> _remoteVideos.value = _remoteVideos.value + (other to t) },
        ).also { peers[other] = it }
    }

    private fun onConnection(other: String, state: PeerConnection.PeerConnectionState) {
        when (state) {
            PeerConnection.PeerConnectionState.CONNECTED -> {
                connected += other
                setCall {
                    it.copy(
                        phase = CallPhase.ACTIVE,
                        startedAt = it.startedAt ?: System.currentTimeMillis(),
                        people = it.people + (other to (names[other] ?: "")),
                    )
                }
            }
            PeerConnection.PeerConnectionState.FAILED -> scope.launch { lock.withLock { leftBy(other, "lost") } }
            else -> Unit
        }
    }

    private fun leftBy(other: String, reason: String?) {
        val c = _call.value ?: return
        peers.remove(other)?.let { media?.drop(it) }
        connected -= other
        _remoteVideos.value = _remoteVideos.value - other
        setCall { it.copy(people = it.people - other) }
        val alone = peers.isEmpty() && joined.none { it != me }
        when {
            c.kind == ChatKind.FRIEND -> finish(
                when (reason) {
                    "busy" -> app.getString(R.string.call_busy)
                    "declined" -> app.getString(R.string.call_no_answer)
                    "lost" -> app.getString(R.string.call_lost)
                    else -> app.getString(R.string.call_ended)
                },
            )
            // A group call ends for you when everyone else has left.
            alone && c.phase == CallPhase.ACTIVE -> finish(app.getString(R.string.call_ended))
        }
    }

    private suspend fun nameOf(uid: String): String {
        names[uid]?.takeIf { it.isNotBlank() }?.let { return it }
        val name = runCatching { Firebase.firestore.collection("users").document(uid).get().await().getString("name") }
            .getOrNull().orEmpty()
        names[uid] = name
        setCall { c -> if (uid in c.people) c.copy(people = c.people + (uid to name)) else c }
        return name
    }

    private suspend fun writeJoin() {
        val c = _call.value ?: return
        val uid = me ?: return
        col(c).document("call_${c.callId}_join_$uid")
            .set(mapOf("kind" to "callJoin", "callId" to c.callId, "authorId" to uid)).await()
    }

    private suspend fun writeSdp(to: String, json: String) {
        val c = _call.value ?: return
        val k = key ?: return
        val uid = me ?: return
        col(c).document("call_${c.callId}_sdp_${uid}_$to").set(
            mapOf("kind" to "callSdp", "callId" to c.callId, "authorId" to uid, "to" to to) +
                ChatCrypto.sealFields(c.chatId, k, mapOf("sdp" to json)),
        ).await()
    }

    private suspend fun writeIce(to: String, json: String) {
        val c = _call.value ?: return
        val k = key ?: return
        val uid = me ?: return
        val n = iceCount[to] ?: 0
        iceCount[to] = n + 1
        col(c).document("call_${c.callId}_ice_${uid}_${to}_$n").set(
            mapOf("kind" to "callIce", "callId" to c.callId, "authorId" to uid, "to" to to) +
                ChatCrypto.sealFields(c.chatId, k, mapOf("ice" to json)),
        ).await()
    }

    private suspend fun writeLeave(kind: ChatKind, chatId: String, callId: String, reason: String) {
        val uid = me ?: return
        chatMessages(kind, chatId).document("call_${callId}_leave_$uid").set(
            mapOf("kind" to "callLeave", "callId" to callId, "authorId" to uid, "reason" to reason),
        ).await()
    }

    /** Ends the call on this phone, tells the others, and removes this phone's signals. */
    private fun finish(reason: String?, tell: Boolean = true) {
        val c = _call.value ?: return
        if (c.phase == CallPhase.ENDED) return
        timeout?.cancel()
        stopRinging()
        NotificationManagerCompat.from(app).cancel(NOTIFICATION_ID)
        signals?.remove()
        signals = null
        peers.clear()
        connected.clear()
        iceCount.clear()
        media?.close()
        media = null
        key = null
        _remoteVideos.value = emptyMap()
        _localVideo.value = null
        audio(on = false, speaker = false)
        CallService.stop(app)
        setCall { it.copy(phase = CallPhase.ENDED, endedReason = reason ?: app.getString(R.string.call_ended)) }
        scope.launch {
            if (tell) runCatching { writeLeave(c.kind, c.chatId, c.callId, "hangup") }
            // Leaves the marks a moment for the other phones, then cleans up this phone's signals.
            delay(15_000)
            val uid = me ?: return@launch
            runCatching {
                col(c).whereEqualTo("callId", c.callId).get().await().documents
                    .filter { it.getString("authorId") == uid }
                    .forEach { it.reference.delete() }
            }
        }
        scope.launch {
            delay(1_800)
            if (_call.value?.callId == c.callId) _call.value = null
        }
    }

    private fun setCall(change: (CallUi) -> CallUi) {
        _call.value = _call.value?.let(change)
    }

    // --- Sound and notifications ------------------------------------------------

    @Suppress("DEPRECATION")
    private fun audio(on: Boolean, speaker: Boolean) {
        val am = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.mode = if (on) AudioManager.MODE_IN_COMMUNICATION else AudioManager.MODE_NORMAL
        am.isSpeakerphoneOn = on && speaker
    }

    private fun startRinging() = Ringtones.play(app)

    private fun stopRinging() = Ringtones.stop()

    @SuppressLint("MissingPermission")
    private fun notifyIncoming(name: String, video: Boolean) {
        val open = PendingIntent.getActivity(
            app, 0, Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(app, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(name)
            .setContentText(app.getString(if (video) R.string.call_incoming_video else R.string.call_incoming))
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setContentIntent(open)
            .setFullScreenIntent(open, true)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(app).notify(NOTIFICATION_ID, n) }
    }
}
