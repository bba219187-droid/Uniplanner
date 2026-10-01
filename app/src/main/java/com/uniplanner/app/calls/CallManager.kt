package com.uniplanner.app.calls

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.webrtc.PeerConnection
import org.webrtc.VideoTrack

enum class CallPhase { CALLING, RINGING, CONNECTING, ACTIVE, ENDED }

data class CallUi(
    val callId: String,
    val chatId: String,
    val peerUid: String,
    val peerName: String,
    val video: Boolean,
    val outgoing: Boolean,
    val phase: CallPhase,
    val muted: Boolean = false,
    val speaker: Boolean = false,
    val camera: Boolean = false,
    val sharing: Boolean = false,
    val startedAt: Long? = null,
    /** Set when the call ended, to say why for a moment. */
    val endedReason: String? = null,
)

/**
 * Calls between friends. The offer, answer and connection details are written as hidden documents
 * in the friends' own chat (no "createdAt", so the chat never lists them), sealed with the chat's
 * end-to-end key, and each phone deletes its own when the call ends. Only one call at a time.
 *
 * A friend's phone rings while UniPlanner is open or still running in the background; ringing a
 * closed app needs push messages from a server, which the free Firebase plan does not include.
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
    private val _remoteVideo = MutableStateFlow<VideoTrack?>(null)
    val remoteVideo: StateFlow<VideoTrack?> = _remoteVideo.asStateFlow()
    private val _localVideo = MutableStateFlow<VideoTrack?>(null)
    val localVideo: StateFlow<VideoTrack?> = _localVideo.asStateFlow()

    var session: CallSession? = null
        private set
    private var key: Pair<String, ByteArray>? = null
    private var signals: ListenerRegistration? = null
    private var timeout: Job? = null
    private var iceCount = 0
    private var ringtone: Ringtone? = null
    private val seenIce = HashSet<String>()
    private val incomingWatches = HashMap<String, ListenerRegistration>()
    private val handled = HashSet<String>()

    private val me get() = Firebase.auth.currentUser?.uid
    private fun col(chatId: String) = chatMessages(ChatKind.FRIEND, chatId)

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

    /** Listens for calls in each friend chat; called with the accepted friendships. */
    fun watch(context: Context, chats: Map<String, String>) {
        init(context)
        val uid = me ?: return
        (incomingWatches.keys - chats.keys).forEach { incomingWatches.remove(it)?.remove() }
        for ((chatId, name) in chats) {
            if (chatId in incomingWatches) continue
            incomingWatches[chatId] = col(chatId).whereEqualTo("callTo", uid).addSnapshotListener { snap, _ ->
                snap?.documents?.forEach { d -> onOffer(chatId, name, d) }
            }
        }
    }

    fun stopWatching() {
        incomingWatches.values.forEach { it.remove() }
        incomingWatches.clear()
    }

    private fun onOffer(chatId: String, name: String, d: DocumentSnapshot) {
        val callId = d.getString("callId") ?: return
        val at = d.getLong("at") ?: return
        if (d.getString("kind") != "call" || callId in handled) return
        handled += callId
        if (at < System.currentTimeMillis() - RING_TIMEOUT_MS) return
        if (_call.value != null) {
            // Already in a call: the other person hears busy.
            scope.launch { runCatching { writeEnd(chatId, callId, "busy") } }
            return
        }
        val from = d.getString("authorId") ?: return
        _call.value = CallUi(callId, chatId, from, name, d.getBoolean("video") == true, outgoing = false, phase = CallPhase.RINGING)
        listen(chatId, callId)
        startRinging()
        notifyIncoming(name, d.getBoolean("video") == true)
        timeout = scope.launch {
            delay(RING_TIMEOUT_MS)
            if (_call.value?.phase == CallPhase.RINGING) finish(app.getString(R.string.call_missed), tell = false)
        }
    }

    // --- Starting and answering -----------------------------------------------

    /** Calls a friend. The screen has asked for the microphone (and camera) first. */
    fun start(context: Context, chatId: String, peerUid: String, peerName: String, video: Boolean) {
        init(context)
        if (_call.value != null) return
        val callId = E2e.newKeyId() + E2e.newKeyId()
        handled += callId
        _call.value = CallUi(callId, chatId, peerUid, peerName, video, outgoing = true, phase = CallPhase.CALLING, speaker = video)
        scope.launch {
            try {
                val k = ChatCrypto.keyForSending(ChatKind.FRIEND, chatId) ?: error("No key")
                key = k
                val s = newSession()
                if (video) _localVideo.value = s.startCamera().also { setCall { c -> c.copy(camera = it != null) } }
                audio(on = true, speaker = video)
                CallService.start(app, video)
                listen(chatId, callId)
                val offer = s.offer()
                val uid = me ?: error("Signed out")
                col(chatId).document("call_${callId}_offer").set(
                    mapOf(
                        "kind" to "call", "callId" to callId, "authorId" to uid, "callTo" to peerUid,
                        "video" to video, "at" to System.currentTimeMillis(),
                    ) + ChatCrypto.sealFields(chatId, k, mapOf("sdp" to CallSession.sdpToJson(offer))),
                ).await()
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
        setCall { it.copy(phase = CallPhase.CONNECTING, speaker = c.video) }
        scope.launch {
            try {
                val offerDoc = col(c.chatId).document("call_${c.callId}_offer").get().await()
                val kid = offerDoc.getString("kid")
                ChatCrypto.keys(ChatKind.FRIEND, c.chatId, setOfNotNull(kid))
                val fields = ChatCrypto.openFields(c.chatId, kid, offerDoc.getString("enc")) ?: error("Cannot open the call")
                key = kid?.let { k -> ChatCrypto.cached(c.chatId)[k]?.let { k to it } } ?: error("No key")
                val s = newSession()
                if (c.video) _localVideo.value = s.startCamera().also { t -> setCall { it.copy(camera = t != null) } }
                audio(on = true, speaker = c.video)
                CallService.start(app, c.video)
                s.setRemote(CallSession.sdpFromJson(fields.getString("sdp")))
                val answer = s.answer()
                pullIce(c.chatId, c.callId)
                col(c.chatId).document("call_${c.callId}_answer").set(
                    mapOf("kind" to "callAnswer", "callId" to c.callId, "authorId" to me) +
                        ChatCrypto.sealFields(c.chatId, key!!, mapOf("sdp" to CallSession.sdpToJson(answer))),
                ).await()
            } catch (e: Exception) {
                finish(app.getString(R.string.call_failed))
            }
        }
    }

    fun decline() = finish(null)

    fun hangUp() = finish(null)

    // --- During the call ----------------------------------------------------

    fun toggleMute() {
        val c = _call.value ?: return
        session?.setMuted(!c.muted)
        setCall { it.copy(muted = !c.muted) }
    }

    fun toggleSpeaker() {
        val c = _call.value ?: return
        audio(on = true, speaker = !c.speaker)
        setCall { it.copy(speaker = !c.speaker) }
    }

    fun toggleCamera() {
        val s = session ?: return
        val c = _call.value ?: return
        if (c.camera) {
            s.stopVideo()
            _localVideo.value = null
            setCall { it.copy(camera = false) }
        } else {
            CallService.start(app, video = true)
            _localVideo.value = s.startCamera()
            setCall { it.copy(camera = _localVideo.value != null, sharing = false) }
        }
    }

    fun switchCamera() = session?.switchCamera()

    /** Shares the screen, with the permission Android's dialog returned. */
    fun shareScreen(permission: Intent, width: Int, height: Int) {
        val s = session ?: return
        // Android 14 wants the foreground service to declare screen capture before it starts.
        CallService.start(app, video = _call.value?.video == true, screen = true)
        scope.launch {
            delay(300)
            _localVideo.value = runCatching { s.startScreen(permission, width, height) }.getOrNull()
            setCall { it.copy(sharing = _localVideo.value != null, camera = false) }
        }
    }

    fun stopSharing() {
        session?.stopVideo()
        _localVideo.value = null
        setCall { it.copy(sharing = false) }
    }

    // --- Signals ----------------------------------------------------------------

    private fun newSession(): CallSession {
        iceCount = 0
        seenIce.clear()
        return CallSession(
            app,
            onIce = { c -> scope.launch { runCatching { sendIce(CallSession.iceToJson(c)) } } },
            onConnection = { state -> scope.launch { onConnection(state) } },
            onRemoteVideo = { t -> _remoteVideo.value = t },
        ).also { session = it }
    }

    private suspend fun sendIce(json: String) {
        val c = _call.value ?: return
        val k = key ?: return
        val uid = me ?: return
        val n = iceCount++
        col(c.chatId).document("call_${c.callId}_ice_${uid}_$n").set(
            mapOf("kind" to "callIce", "callId" to c.callId, "authorId" to uid) + ChatCrypto.sealFields(c.chatId, k, mapOf("ice" to json)),
        ).await()
    }

    private fun onConnection(state: PeerConnection.PeerConnectionState) {
        when (state) {
            PeerConnection.PeerConnectionState.CONNECTED -> {
                timeout?.cancel()
                setCall { it.copy(phase = CallPhase.ACTIVE, startedAt = it.startedAt ?: System.currentTimeMillis()) }
            }
            PeerConnection.PeerConnectionState.FAILED -> finish(app.getString(R.string.call_lost))
            else -> Unit
        }
    }

    private fun listen(chatId: String, callId: String) {
        signals?.remove()
        signals = col(chatId).whereEqualTo("callId", callId).addSnapshotListener { snap, _ ->
            val uid = me ?: return@addSnapshotListener
            snap?.documents?.forEach { d ->
                if (d.getString("authorId") == uid) return@forEach
                when (d.getString("kind")) {
                    "callAnswer" -> if (_call.value?.phase == CallPhase.CALLING) {
                        setCall { it.copy(phase = CallPhase.CONNECTING) }
                        scope.launch {
                            val f = ChatCrypto.openFields(chatId, d.getString("kid"), d.getString("enc")) ?: return@launch
                            runCatching { session?.setRemote(CallSession.sdpFromJson(f.getString("sdp"))) }
                                .onFailure { finish(app.getString(R.string.call_failed)) }
                        }
                    }
                    // Before answering there is no connection to give them to; they are read again on answering.
                    "callIce" -> if (session != null && seenIce.add(d.id)) {
                        val f = ChatCrypto.openFields(chatId, d.getString("kid"), d.getString("enc")) ?: return@forEach
                        runCatching { session?.addIce(CallSession.iceFromJson(f.getString("ice"))) }
                    }
                    "callEnd" -> {
                        val reason = when (d.getString("reason")) {
                            "busy" -> app.getString(R.string.call_busy)
                            else -> app.getString(R.string.call_ended)
                        }
                        finish(reason, tell = false)
                    }
                }
            }
        }
    }

    /** The caller's connection details that arrived while this phone was still ringing. */
    private suspend fun pullIce(chatId: String, callId: String) {
        val uid = me ?: return
        col(chatId).whereEqualTo("callId", callId).get().await().documents
            .filter { it.getString("kind") == "callIce" && it.getString("authorId") != uid && seenIce.add(it.id) }
            .forEach { d ->
                val f = ChatCrypto.openFields(chatId, d.getString("kid"), d.getString("enc")) ?: return@forEach
                runCatching { session?.addIce(CallSession.iceFromJson(f.getString("ice"))) }
            }
    }

    private suspend fun writeEnd(chatId: String, callId: String, reason: String) {
        val uid = me ?: return
        col(chatId).document("call_${callId}_end_$uid").set(
            mapOf("kind" to "callEnd", "callId" to callId, "authorId" to uid, "reason" to reason),
        ).await()
    }

    /** Ends the call on this phone, tells the other one, and removes this phone's signals. */
    private fun finish(reason: String?, tell: Boolean = true) {
        val c = _call.value ?: return
        if (c.phase == CallPhase.ENDED) return
        timeout?.cancel()
        stopRinging()
        NotificationManagerCompat.from(app).cancel(NOTIFICATION_ID)
        signals?.remove()
        signals = null
        session?.close()
        session = null
        key = null
        _remoteVideo.value = null
        _localVideo.value = null
        audio(on = false, speaker = false)
        CallService.stop(app)
        setCall { it.copy(phase = CallPhase.ENDED, endedReason = reason ?: app.getString(R.string.call_ended)) }
        scope.launch {
            if (tell) runCatching { writeEnd(c.chatId, c.callId, "hangup") }
            // Leaves the end mark a moment for the other phone, then cleans up this phone's signals.
            delay(15_000)
            val uid = me ?: return@launch
            runCatching {
                col(c.chatId).whereEqualTo("callId", c.callId).get().await().documents
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

    private fun startRinging() {
        ringtone = runCatching {
            RingtoneManager.getRingtone(app, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE))?.also { r ->
                if (Build.VERSION.SDK_INT >= 28) r.isLooping = true
                r.play()
            }
        }.getOrNull()
    }

    private fun stopRinging() {
        runCatching { ringtone?.stop() }
        ringtone = null
    }

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
