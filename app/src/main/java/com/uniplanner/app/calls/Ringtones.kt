package com.uniplanner.app.calls

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import androidx.annotation.StringRes
import com.uniplanner.app.R
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin

/** The sound an incoming call makes. All but [SYSTEM] are played from notes, not from files. */
enum class CallTone(@StringRes val label: Int) {
    /** Tárrega's Gran Vals phrase, the tune old mobile phones made famous; the melody is public domain. */
    CLASSIC_MOBILE(R.string.tone_classic_mobile),
    OLD_PHONE(R.string.tone_old_phone),
    MARIMBA(R.string.tone_marimba),
    SOFT(R.string.tone_soft),
    SYSTEM(R.string.tone_system),
}

/** A note: semitones from A4 (null is a rest) and its length in beats. */
private data class Note(val semitones: Int?, val beats: Double)

object Ringtones {
    private const val PREFS = "call_tone"
    private const val RATE = 22_050

    fun get(ctx: Context): CallTone =
        runCatching { CallTone.valueOf(ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("tone", null)!!) }
            .getOrDefault(CallTone.CLASSIC_MOBILE)

    fun set(ctx: Context, tone: CallTone) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("tone", tone.name).apply()
    }

    // E5 D5 F#4 G#4 | C#5 B4 D4 E4 | B4 A4 C#4 E4 | A4
    private val gran = listOf(
        Note(7, .5), Note(5, .5), Note(-3, 1.0), Note(-1, 1.0),
        Note(4, .5), Note(2, .5), Note(-7, 1.0), Note(-5, 1.0),
        Note(2, .5), Note(0, .5), Note(-8, 1.0), Note(-5, 1.0),
        Note(0, 2.0), Note(null, 2.0),
    )
    private val marimba = listOf(
        Note(3, .5), Note(7, .5), Note(10, .5), Note(15, .5), Note(10, .5), Note(7, 1.0), Note(null, 2.0),
    )
    private val soft = listOf(Note(-2, 1.5), Note(2, 1.5), Note(5, 3.0), Note(null, 2.0))

    /** One round of the tone, as 16-bit samples; it is played over and over while ringing. */
    private fun render(tone: CallTone): ShortArray = when (tone) {
        CallTone.CLASSIC_MOBILE -> melody(gran, beat = 0.24, voice = ::squareish)
        CallTone.MARIMBA -> melody(marimba, beat = 0.16, voice = ::bell)
        CallTone.SOFT -> melody(soft, beat = 0.3, voice = ::pure)
        CallTone.OLD_PHONE -> oldPhone()
        CallTone.SYSTEM -> ShortArray(0)
    }

    private fun melody(notes: List<Note>, beat: Double, voice: (Double, Double) -> Double): ShortArray {
        val out = ArrayList<Short>()
        for (n in notes) {
            val len = (n.beats * beat * RATE).toInt()
            val f = n.semitones?.let { 440.0 * 2.0.pow(it / 12.0) }
            for (i in 0 until len) {
                val t = i.toDouble() / RATE
                // A short fade in and out on every note, so the notes do not click.
                val env = minOf(1.0, t / 0.005, (len - i).toDouble() / RATE / 0.02)
                val v = if (f == null) 0.0 else voice(f, t) * env
                out += (v * 0.6 * Short.MAX_VALUE).toInt().toShort()
            }
        }
        return out.toShortArray()
    }

    /** A bright, slightly buzzy tone, like a phone's little speaker. */
    private fun squareish(f: Double, t: Double): Double {
        val w = 2 * PI * f * t
        return (sin(w) + sin(3 * w) / 3 + sin(5 * w) / 5) * 0.8
    }

    private fun bell(f: Double, t: Double): Double {
        val w = 2 * PI * f * t
        return (sin(w) + 0.3 * sin(4 * w)) * exp(-t * 9)
    }

    private fun pure(f: Double, t: Double): Double = sin(2 * PI * f * t) * exp(-t * 1.5)

    /** Two quick bursts of a two-tone bell, then quiet. */
    private fun oldPhone(): ShortArray {
        val out = ArrayList<Short>()
        fun add(seconds: Double, ring: Boolean) {
            val len = (seconds * RATE).toInt()
            for (i in 0 until len) {
                val t = i.toDouble() / RATE
                val v = if (!ring) 0.0 else {
                    val on = (t * 40).toInt() % 2 == 0
                    sin(2 * PI * (if (on) 440.0 else 480.0) * t) * 0.9
                }
                out += (v * 0.55 * Short.MAX_VALUE).toInt().toShort()
            }
        }
        add(0.4, true); add(0.2, false); add(0.4, true); add(1.6, false)
        return out.toShortArray()
    }

    // --- Playing -------------------------------------------------------------

    private var track: AudioTrack? = null
    private var system: Ringtone? = null

    /** Rings until [stop]. [preview] plays one round, as the alarm-free media sound. */
    fun play(ctx: Context, tone: CallTone = get(ctx), preview: Boolean = false) {
        stop()
        if (tone == CallTone.SYSTEM) {
            system = runCatching {
                RingtoneManager.getRingtone(ctx, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE))?.also { r ->
                    if (Build.VERSION.SDK_INT >= 28) r.isLooping = !preview
                    r.play()
                }
            }.getOrNull()
            return
        }
        val pcm = render(tone)
        val usage = if (preview) AudioAttributes.USAGE_MEDIA else AudioAttributes.USAGE_NOTIFICATION_RINGTONE
        val t = runCatching {
            AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(usage).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                .setAudioFormat(
                    AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build(),
                )
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(pcm.size * 2)
                .build()
        }.getOrNull() ?: return
        t.write(pcm, 0, pcm.size)
        if (!preview) t.setLoopPoints(0, pcm.size, -1)
        t.play()
        track = t
    }

    fun stop() {
        runCatching { track?.stop() }
        runCatching { track?.release() }
        track = null
        runCatching { system?.stop() }
        system = null
    }
}
