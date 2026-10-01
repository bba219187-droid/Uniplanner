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
import org.webrtc.VideoSink
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import org.webrtc.audio.JavaAudioDeviceModule
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * One call's WebRTC connection. Sound and picture go straight between the two phones, encrypted
 * with DTLS-SRTP; the keys for that are agreed inside the offer and answer, which travel sealed
 * with the chat's end-to-end key, so the server never sees them.
 *
 * A video sender is always there, so the camera or the screen can be turned on mid-call without
 * asking the other phone again.
 */
class CallSession(
    private val context: Context,
    private val onIce: (IceCandidate) -> Unit,
    private val onConnection: (PeerConnection.PeerConnectionState) -> Unit,
    private val onRemoteVideo: (VideoTrack) -> Unit,
) {
    val egl: EglBase = EglBase.create()
    private val factory: PeerConnectionFactory
    private val pc: PeerConnection
    private val audio: AudioTrack
    private val videoSender: RtpSender
    private var capturer: VideoCapturer? = null
    private var source: VideoSource? = null
    private var helper: SurfaceTextureHelper? = null
    var localVideo: VideoTrack? = null
        private set
    private val pendingIce = mutableListOf<IceCandidate>()
    private var remoteSet = false

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
        val config = PeerConnection.RTCConfiguration(ICE_SERVERS).apply {
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
        audio = factory.createAudioTrack("audio", factory.createAudioSource(MediaConstraints()))
        pc.addTrack(audio, listOf("call"))
        videoSender = pc.addTransceiver(
            MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO,
            RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.SEND_RECV, listOf("call")),
        ).sender
    }

    fun setMuted(muted: Boolean) {
        audio.setEnabled(!muted)
    }

    /** Sends the front camera. */
    fun startCamera(front: Boolean = true): VideoTrack? {
        val names = Camera2Enumerator(context).deviceNames
        val enumerator = Camera2Enumerator(context)
        val name = names.firstOrNull { enumerator.isFrontFacing(it) == front } ?: names.firstOrNull() ?: return null
        return startCapture(enumerator.createCapturer(name, null), screencast = false)
    }

    fun switchCamera() {
        (capturer as? CameraVideoCapturer)?.switchCamera(null)
    }

    /** Sends the screen, after the student agreed in Android's own dialog. */
    fun startScreen(permission: Intent, width: Int, height: Int): VideoTrack? = startCapture(
        ScreenCapturerAndroid(permission, object : MediaProjection.Callback() {
            override fun onStop() {}
        }),
        screencast = true,
        width = width,
        height = height,
        fps = 15,
    )

    private fun startCapture(c: VideoCapturer, screencast: Boolean, width: Int = 1280, height: Int = 720, fps: Int = 30): VideoTrack {
        stopVideo()
        val h = SurfaceTextureHelper.create("capture", egl.eglBaseContext)
        val s = factory.createVideoSource(screencast)
        c.initialize(h, context, s.capturerObserver)
        c.startCapture(width, height, fps)
        val track = factory.createVideoTrack(if (screencast) "screen" else "camera", s)
        videoSender.setTrack(track, false)
        capturer = c
        source = s
        helper = h
        localVideo = track
        return track
    }

    /** Stops the camera or the screen; the call goes on with sound only. */
    fun stopVideo() {
        videoSender.setTrack(null, false)
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

    suspend fun offer(): SessionDescription = describe(create = { o -> pc.createOffer(o, MediaConstraints()) }).also { setLocal(it) }

    suspend fun answer(): SessionDescription = describe(create = { o -> pc.createAnswer(o, MediaConstraints()) }).also { setLocal(it) }

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
        stopVideo()
        runCatching { pc.close() }
        runCatching { pc.dispose() }
        runCatching { factory.dispose() }
        runCatching { egl.release() }
    }

    fun addRemoteSink(track: VideoTrack, sink: VideoSink) = track.addSink(sink)

    private open class SdpAdapter : SdpObserver {
        override fun onCreateSuccess(d: SessionDescription) {}
        override fun onSetSuccess() {}
        override fun onCreateFailure(error: String?) {}
        override fun onSetFailure(error: String?) {}
    }

    companion object {
        // Public STUN servers let the two phones find each other through home and mobile routers.
        private val ICE_SERVERS = listOf(
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
