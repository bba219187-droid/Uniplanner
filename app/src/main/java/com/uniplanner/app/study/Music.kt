package com.uniplanner.app.study

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.uniplanner.app.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** A music app the student may have; [pkg] is how Android knows it. */
data class MusicApp(val name: String, val pkg: String)

/**
 * Music while studying, in the Play look: pick the music app once, then play, pause and skip
 * from the study screen. The buttons work with whatever is playing, like headphone buttons do.
 */
object Music {
    private const val PREFS = "music"
    private const val APP = "app"

    val known = listOf(
        MusicApp("Spotify", "com.spotify.music"),
        MusicApp("YouTube Music", "com.google.android.apps.youtube.music"),
        MusicApp("Apple Music", "com.apple.android.music"),
        MusicApp("Deezer", "deezer.android.app"),
        MusicApp("SoundCloud", "com.soundcloud.android"),
        MusicApp("Amazon Music", "com.amazon.mp3"),
        MusicApp("TIDAL", "com.aspiro.tidal"),
    )

    fun installed(ctx: Context): List<MusicApp> =
        known.filter { ctx.packageManager.getLaunchIntentForPackage(it.pkg) != null }

    fun chosen(ctx: Context): MusicApp? {
        val pkg = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(APP, null)
        return installed(ctx).firstOrNull { it.pkg == pkg } ?: installed(ctx).firstOrNull()
    }

    fun choose(ctx: Context, app: MusicApp) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(APP, app.pkg).apply()
    }

    /** Opens the chosen app, or the phone's own music app when none of the known ones is there. */
    fun open(ctx: Context, app: MusicApp?) {
        val intent = app?.let { ctx.packageManager.getLaunchIntentForPackage(it.pkg) }
            ?: Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MUSIC)
        runCatching { ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    fun playing(ctx: Context): Boolean =
        (ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager).isMusicActive

    /** Sends a media button press, the same as the buttons on headphones. */
    fun press(ctx: Context, code: Int) {
        val audio = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
    }
}

/** The music strip on the study clock: the app, then previous, play or pause, and next. */
@Composable
fun MusicStrip(tint: Color, onTint: Color, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    var app by remember { mutableStateOf(Music.chosen(ctx)) }
    var playing by remember { mutableStateOf(Music.playing(ctx)) }
    var menu by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        while (true) {
            playing = Music.playing(ctx)
            delay(1_000)
        }
    }
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(tint).padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            Row(
                Modifier.clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button) {
                    if (Music.installed(ctx).size > 1) menu = true else Music.open(ctx, app)
                }.padding(vertical = 6.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(34.dp).clip(RoundedCornerShape(9.dp)).background(onTint), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.MusicNote, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.width(120.dp)) {
                    Text(
                        app?.name ?: stringResource(R.string.music_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = onTint,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        stringResource(if (playing) R.string.music_playing else R.string.music_open),
                        style = MaterialTheme.typography.bodySmall,
                        color = onTint.copy(alpha = 0.7f),
                        maxLines = 1,
                    )
                }
                if (Music.installed(ctx).size > 1) Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = onTint)
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                Music.installed(ctx).forEach { a ->
                    DropdownMenuItem(text = { Text(a.name) }, onClick = {
                        Music.choose(ctx, a)
                        app = a
                        menu = false
                        Music.open(ctx, a)
                    })
                }
            }
        }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = { Music.press(ctx, KeyEvent.KEYCODE_MEDIA_PREVIOUS) }) {
            Icon(Icons.Filled.SkipPrevious, contentDescription = stringResource(R.string.music_previous), tint = onTint)
        }
        IconButton(
            onClick = {
                val wasPlaying = playing
                Music.press(ctx, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                playing = !wasPlaying
                // Nothing answered the button: open the music app, so there is something to play.
                if (!wasPlaying) scope.launch {
                    delay(1_500)
                    if (!Music.playing(ctx)) Music.open(ctx, app)
                }
            },
            modifier = Modifier.size(44.dp).clip(CircleShape).background(onTint),
        ) {
            Icon(
                if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = stringResource(if (playing) R.string.music_pause else R.string.music_play),
                tint = tint,
            )
        }
        IconButton(onClick = { Music.press(ctx, KeyEvent.KEYCODE_MEDIA_NEXT) }) {
            Icon(Icons.Filled.SkipNext, contentDescription = stringResource(R.string.music_next), tint = onTint)
        }
    }
}
