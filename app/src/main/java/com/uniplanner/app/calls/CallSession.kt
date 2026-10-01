package com.uniplanner.app.calls

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import org.webrtc.AudioTrack
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.MediaStreamTrack
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.RtpSender
import org.webrtc.RtpTransceiver
import org.webrtc.ScreenCapturerAndroid
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoCapturer
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import org.webrtc.audio.JavaAudioDeviceModule
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * This phone's side of a call: the microphone, the camera or screen, and one connection per other
 * person (a group call connects everyone with everyone). Sound and picture go straight between
 * phones, encrypted with DTLS-SRTP; the keys for that are agreed inside the offers and answers,
 * which travel sealed with the chat's end-to-end key, so the server never sees them.
 */
class CallMedia(private val context: Context) {
    val egl: EglBase = EglBase.create()
    private val factory: PeerConnectionFactory
    val audio: AudioTrack
    private var capturer: VideoCapturer? = null
    private var source: VideoSource? = null
    private var helper: SurfaceTextureHelper? = null
    var localVideo: VideoTrack? = null
        private set
    private val peers = mutableListOf<Peer>()
    /** Every connection made, also the ones closed, to free them at the end. */
    private val made = mutableListOf<Peer>()

    init {
        initialize(context)
        factory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(egl.eglBaseContext, true, true))
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(egl.eglBaseContext))
            .setAudioDeviceModule(
                JavaAudioDeviceModule.builder(context)
                    .setUseHardwareAcousticEchoCanceler(true)
                    .setUseHardwareNoiseSuppressor(true)
                    .createAudioDeviceModule(),
            )
            .createPeerConnectionFactory()
        audio = factory.createAudioTrack("audio", factory.createAudioSource(MediaConstraints()))
    }

    /** A connection to one person. The one who offers adds the video slot; the one who answers takes the offered one. */
    fun peer(
        offerer: Boolean,
        onIce: (IceCandidate) -> Unit,
        onConnection: (PeerConnection.PeerConnectionState) -> Unit,
        onRemoteVideo: (VideoTrack) -> Unit,
    ): Peer = Peer(factory, offerer, audio, localVideo, onIce, onConnection, onRemoteVideo).also { synchronized(peers) { peers += it; made += it } }

    fun drop(peer: Peer) {
        synchronized(peers) { peers -= peer }
        peer.close()
    }

    fun setMuted(muted: Boolean) {
        audio.setEnabled(!muted)
    }

    fun startCamera(front: Boolean = true): VideoTrack? {
        val enumerator = Camera2Enumerator(context)
        val names = enumerator.deviceNames
        val name = names.firstOrNull { enumerator.isFrontFacing(it) == front } ?: names.firstOrNull() ?: return null
        return startCapture(enumerator.createCapturer(name, null), screencast = false)
    }

    fun switchCamera() {
        (capturer as? CameraVideoCapturer)?.switchCamera(null)
    }

    fun startScreen(permission: Intent, width: Int, height: Int): VideoTrack? = startCapture(
        ScreenCapturerAndroid(permission, object : MediaProjection.Callback() {
            override fun onStop() {}
        }),
        screencast = true, width = width, height = height, fps = 15,
    )

    private fun startCapture(c: VideoCapturer, screencast: Boolean, width: Int = 1280, height: Int = 720, fps: Int = 30): VideoTrack {
        stopVideo()
        val h = SurfaceTextureHelper.create("capture", egl.eglBaseContext)
        val s = factory.createVideoSource(screencast)
        c.initialize(h, context, s.capturerObserver)
        c.startCapture(width, height, fps)
        val track = factory.createVideoTrack(if (screencast) "screen" else "camera", s)
        capturer = c
        source = s
        helper = h
        localVideo = track
        synchronized(peers) { peers.forEach { it.sendVideo(track) } }
        return track
    }

    fun stopVideo() {
        synchronized(peers) { peers.forEach { it.sendVideo(null) } }
        runCatching { capturer?.stopCapture() }
        capturer?.dispose()
        localVideo?.dispose()
        source?.dispose()
        helper?.dispose()
        capturer = null
        localVideo = null
        source = null
        helper = null
    }

    fun close() {
        stopVideo()
        synchronized(peers) {
            // Freed only now: freeing a connection also frees the shared microphone track.
            made.forEach { it.close(); it.dispose() }
            peers.clear()
            made.clear()
        }
        runCatching { audio.dispose() }
        runCatching { factory.dispose() }
        runCatching { egl.release() }
    }

    companion object {
        // Public STUN servers let the phones find each other through home and mobile routers.
        val ICE_SERVERS = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
        )
        @Volatile private var initialized = false

        private fun initialize(context: Context) {
            if (initialized) return
            PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(context.applicationContext).createInitializationOptions())
            initialized = true
        }

        fun sdpToJson(d: SessionDescription) = JSONObject().put("type", d.type.canonicalForm()).put("sdp", d.description).toString()
        fun sdpFromJson(s: String): SessionDescription = JSONObject(s).let {
            SessionDescription(SessionDescription.Type.fromCanonicalForm(it.getString("type")), it.getString("sdp"))
        }
        fun iceToJson(c: IceCandidate) = JSONObject().put("mid", c.sdpMid).put("index", c.sdpMLineIndex).put("sdp", c.sdp).toString()
        fun iceFromJson(s: String): IceCandidate = JSONObject(s).let { IceCandidate(it.optString("mid"), it.getInt("index"), it.getString("sdp")) }
    }
}

/** The connection to one other person in the call. */
class Peer internal constructor(
    factory: PeerConnectionFactory,
    offerer: Boolean,
    audio: AudioTrack,
    video: VideoTrack?,
    onIce: (IceCandidate) -> Unit,
    onConnection: (PeerConnection.PeerConnectionState) -> Unit,
    private val onRemoteVideo: (VideoTrack) -> Unit,
) {
    private val pc: PeerConnection
    private var videoSender: RtpSender? = null
    private var wanted: VideoTrack? = video
    private val pendingIce = mutableListOf<IceCandidate>()
    private var remoteSet = false

    init {
        val config = PeerConnection.RTCConfiguration(CallMedia.ICE_SERVERS).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }
        pc = factory.createPeerConnection(config, object : PeerConnection.Observer {
            override fun onIceCandidate(c: IceCandidate) = onIce(c)
            override fun onConnectionChange(state: PeerConnection.PeerConnectionState) = onConnection(state)
            override fun onTrack(t: RtpTransceiver) {
                (t.receiver.track() as? VideoTrack)?.let(onRemoteVideo)
            }
            override fun onSignalingChange(s: PeerConnection.SignalingState) {}
            override fun onIceConnectionChange(s: PeerConnection.IceConnectionState) {}
            override fun onIceConnectionReceivingChange(b: Boolean) {}
            override fun onIceGatheringChange(s: PeerConnection.IceGatheringState) {}
            override fun onIceCandidatesRemoved(c: Array<out IceCandidate>) {}
            override fun onAddStream(s: MediaStream) {}
            override fun onRemoveStream(s: MediaStream) {}
            override fun onDataChannel(d: DataChannel) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(r: RtpReceiver, s: Array<out MediaStream>) {}
        }) ?: error("No peer connection")
        pc.addTrack(audio, listOf("call"))
        if (offerer) {
            // Always a video slot, so a camera or screen can start mid-call without asking again.
            videoSender = pc.addTransceiver(
                MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO,
                RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.SEND_RECV, listOf("call")),
            ).sender.also { it.setTrack(video, false) }
        }
    }

    fun sendVideo(track: VideoTrack?) {
        wanted = track
        videoSender?.setTrack(track, false)
    }

    suspend fun offer(): SessionDescription = describe { o -> pc.createOffer(o, MediaConstraints()) }.also { setLocal(it) }

    suspend fun answer(): SessionDescription {
        // The answerer sends on the video slot the offer brought, not on a new one.
        if (videoSender == null) {
            pc.transceivers.firstOrNull { it.mediaType == MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO }?.let { t ->
                t.direction = RtpTransceiver.RtpTransceiverDirection.SEND_RECV
                videoSender = t.sender
                t.sender.setTrack(wanted, false)
                (t.receiver.track() as? VideoTrack)?.let(onRemoteVideo)
            }
        }
        return describe { o -> pc.createAnswer(o, MediaConstraints()) }.also { setLocal(it) }
    }

    suspend fun setRemote(d: SessionDescription) {
        suspendCancellableCoroutine { cont ->
            pc.setRemoteDescription(object : SdpAdapter() {
                override fun onSetSuccess() = cont.resume(Unit)
                override fun onSetFailure(error: String?) = cont.resumeWithException(IllegalStateException(error))
            }, d)
        }
        remoteSet = true
        pendingIce.forEach { pc.addIceCandidate(it) }
        pendingIce.clear()
    }

    fun addIce(c: IceCandidate) {
        if (remoteSet) pc.addIceCandidate(c) else pendingIce += c
    }

    private suspend fun setLocal(d: SessionDescription) = suspendCancellableCoroutine { cont ->
        pc.setLocalDescription(object : SdpAdapter() {
            override fun onSetSuccess() = cont.resume(Unit)
            override fun onSetFailure(error: String?) = cont.resumeWithException(IllegalStateException(error))
        }, d)
    }

    private suspend fun describe(create: (SdpObserver) -> Unit): SessionDescription = suspendCancellableCoroutine { cont ->
        create(object : SdpAdapter() {
            override fun onCreateSuccess(d: SessionDescription) = cont.resume(d)
            override fun onCreateFailure(error: String?) = cont.resumeWithException(IllegalStateException(error))
        })
    }

    fun close() {
        runCatching { pc.close() }
    }

    internal fun dispose() {
        runCatching { pc.dispose() }
    }

    private open class SdpAdapter : SdpObserver {
        override fun onCreateSuccess(d: SessionDescription) {}
        override fun onSetSuccess() {}
        override fun onCreateFailure(error: String?) {}
        override fun onSetFailure(error: String?) {}
    }
}
