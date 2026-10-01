package com.uniplanner.app.calls

import android.Manifest
import android.app.Activity
import android.content.Context
import android.media.projection.MediaProjectionManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.ScreenShare
import androidx.compose.material.icons.filled.StopScreenShare
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uniplanner.app.R
import com.uniplanner.app.ui.theme.Bricolage
import com.uniplanner.app.ui.theme.Ink
import com.uniplanner.app.ui.theme.Mono
import com.uniplanner.app.ui.theme.Paper
import kotlinx.coroutines.delay
import org.webrtc.EglBase
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack

private val Green = Color(0xFF2E9E5B)
private val Red = Color(0xFFD93A2B)

/** What the microphone and camera need before a call; asked when calling or answering. */
fun callPermissions(video: Boolean): Array<String> =
    if (video) arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA) else arrayOf(Manifest.permission.RECORD_AUDIO)

/** The call, over the whole app, while there is one. */
@Composable
fun CallOverlay() {
    val call by CallManager.call.collectAsStateWithLifecycle()
    val c = call ?: return
    val remote by CallManager.remoteVideo.collectAsStateWithLifecycle()
    val local by CallManager.localVideo.collectAsStateWithLifecycle()
    val context = LocalContext.current
    BackHandler {}

    val askToAnswer = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { CallManager.accept() }
    val askCamera = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (ok) CallManager.toggleCamera() }
    val askScreen = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val data = r.data
        if (r.resultCode == Activity.RESULT_OK && data != null) {
            val m = context.resources.displayMetrics
            CallManager.shareScreen(data, m.widthPixels, m.heightPixels)
        }
    }

    Box(Modifier.fillMaxSize().background(Ink)) {
        val egl = CallManager.session?.egl
        val showRemote = remote != null && c.phase == CallPhase.ACTIVE
        if (showRemote && egl != null) VideoView(remote!!, egl, Modifier.fillMaxSize(), mirror = false)

        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(if (showRemote) 8.dp else 64.dp))
            if (!showRemote) {
                Box(Modifier.size(104.dp).clip(CircleShape).background(Color(0xFFDCE3FC)), contentAlignment = Alignment.Center) {
                    Text(c.peerName.take(1).uppercase(), fontFamily = Bricolage, fontWeight = FontWeight.ExtraBold, fontSize = 44.sp, color = Ink)
                }
                Spacer(Modifier.height(20.dp))
            }
            Text(c.peerName, fontFamily = Bricolage, fontWeight = FontWeight.Bold, fontSize = if (showRemote) 20.sp else 30.sp, color = Paper)
            Spacer(Modifier.height(6.dp))
            Text(status(c), fontFamily = Mono, fontSize = 14.sp, color = Paper.copy(alpha = 0.75f))
            Spacer(Modifier.height(6.dp))
            Text("🔒 " + stringResource(R.string.call_encrypted), fontSize = 12.sp, color = Paper.copy(alpha = 0.55f))
            Spacer(Modifier.weight(1f))

            when {
                c.phase == CallPhase.RINGING -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    RoundButton(Icons.Filled.CallEnd, stringResource(R.string.call_decline), Red) { CallManager.decline() }
                    RoundButton(if (c.video) Icons.Filled.Videocam else Icons.Filled.Call, stringResource(R.string.call_accept), Green) {
                        askToAnswer.launch(callPermissions(c.video))
                    }
                }
                c.phase != CallPhase.ENDED -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        Toggle(if (c.muted) Icons.Filled.MicOff else Icons.Filled.Mic, stringResource(R.string.call_mute), c.muted) { CallManager.toggleMute() }
                        Toggle(Icons.AutoMirrored.Filled.VolumeUp, stringResource(R.string.call_speaker), c.speaker) { CallManager.toggleSpeaker() }
                        Toggle(if (c.camera) Icons.Filled.Videocam else Icons.Filled.VideocamOff, stringResource(R.string.call_camera), c.camera) {
                            askCamera.launch(Manifest.permission.CAMERA)
                        }
                        Toggle(if (c.sharing) Icons.Filled.StopScreenShare else Icons.Filled.ScreenShare, stringResource(R.string.call_share), c.sharing) {
                            if (c.sharing) {
                                CallManager.stopSharing()
                            } else {
                                val mpm = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                                askScreen.launch(mpm.createScreenCaptureIntent())
                            }
                        }
                    }
                    Spacer(Modifier.height(22.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (c.camera) Toggle(Icons.Filled.Cameraswitch, stringResource(R.string.call_flip), false) { CallManager.switchCamera() }
                        RoundButton(Icons.Filled.CallEnd, stringResource(R.string.call_hang_up), Red) { CallManager.hangUp() }
                    }
                }
                else -> Icon(Icons.Filled.PhoneInTalk, null, tint = Paper.copy(alpha = 0.5f), modifier = Modifier.size(40.dp))
            }
            Spacer(Modifier.height(24.dp))
        }

        // Your own picture, small in the corner, like WhatsApp.
        if (local != null && egl != null && c.phase != CallPhase.ENDED) {
            VideoView(
                local!!, egl,
                Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(16.dp).size(width = 110.dp, height = 160.dp)
                    .clip(RoundedCornerShape(18.dp)),
                mirror = !c.sharing,
                onTop = true,
            )
        }
    }
}

@Composable
private fun status(c: CallUi): String {
    var now by remember { androidx.compose.runtime.mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(c.phase) {
        while (c.phase == CallPhase.ACTIVE) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    return when (c.phase) {
        CallPhase.CALLING -> stringResource(R.string.call_calling)
        CallPhase.RINGING -> stringResource(if (c.video) R.string.call_incoming_video else R.string.call_incoming)
        CallPhase.CONNECTING -> stringResource(R.string.call_connecting)
        CallPhase.ACTIVE -> {
            val s = ((now - (c.startedAt ?: now)) / 1000).coerceAtLeast(0)
            "%02d:%02d".format(s / 60, s % 60)
        }
        CallPhase.ENDED -> c.endedReason.orEmpty()
    }
}

@Composable
private fun RoundButton(icon: ImageVector, label: String, color: Color, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(68.dp).clip(CircleShape).background(color).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
            Icon(icon, label, tint = Color.White, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(label, color = Paper.copy(alpha = 0.8f), fontSize = 12.sp)
    }
}

@Composable
private fun Toggle(icon: ImageVector, label: String, on: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(54.dp).clip(CircleShape).background(if (on) Paper else Paper.copy(alpha = 0.14f)).clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, label, tint = if (on) Ink else Paper)
        }
        Spacer(Modifier.height(6.dp))
        Text(label, color = Paper.copy(alpha = 0.75f), fontSize = 11.sp)
    }
}

@Composable
private fun VideoView(track: VideoTrack, egl: EglBase, modifier: Modifier, mirror: Boolean, onTop: Boolean = false) {
    val context = LocalContext.current
    val view = remember(egl) {
        SurfaceViewRenderer(context).apply {
            init(egl.eglBaseContext, null)
            setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
            setEnableHardwareScaler(true)
            if (onTop) setZOrderMediaOverlay(true)
        }
    }
    DisposableEffect(track, view) {
        view.setMirror(mirror)
        track.addSink(view)
        onDispose { runCatching { track.removeSink(view) } }
    }
    DisposableEffect(view) { onDispose { view.release() } }
    AndroidView({ view }, modifier)
}
