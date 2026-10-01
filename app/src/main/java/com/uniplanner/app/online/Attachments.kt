package com.uniplanner.app.online

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.provider.OpenableColumns
import android.util.LruCache
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.roundToInt

/** What a chat message carries. Messages from before attachments are text. */
enum class MessageType(val key: String) {
    TEXT("text"), IMAGE("image"), FILE("file"), LOCATION("location"), DELETED("deleted");

    companion object {
        fun of(key: String?) = entries.firstOrNull { it.key == key } ?: TEXT
    }
}

/**
 * A photo or file sent in a chat. Its bytes are kept in pieces next to the message (see
 * [Attachments.PART_BYTES]) and only downloaded when the message is shown or opened.
 */
data class Attachment(
    val blobId: String,
    val parts: Int,
    val fileName: String,
    val size: Long,
    val mime: String,
    val width: Int,
    val height: Int,
    /** A tiny version of a photo, shown while the real one loads. */
    val thumb: ByteArray?,
    /** The conversation key the pieces are sealed with; null for an unencrypted attachment. */
    val kid: String? = null,
    /** Where it is kept on this phone; see [Attachments.cacheKey]. */
    val cacheKey: String,
)

/** A place sent in a chat. */
data class SharedPlace(val lat: Double, val lng: Double, val name: String)

/** A photo or file read from the phone and ready to send. */
class Outgoing(
    val type: MessageType,
    val bytes: ByteArray,
    val fileName: String,
    val mime: String,
    val width: Int = 0,
    val height: Int = 0,
    val thumb: ByteArray? = null,
)

/** The picked file is bigger than a chat can carry. */
class TooBigException : Exception()

/**
 * Photos and files for the chat, without Firebase Storage (which needs a paid plan): the bytes go
 * into Firestore documents of up to [PART_BYTES] each, and are cached on the phone once downloaded.
 */
object Attachments {
    /** Firestore keeps each document under 1 MiB. */
    const val PART_BYTES = 900_000
    const val MAX_FILE_BYTES = 5 * 1024 * 1024
    private const val PHOTO_SIDE = 1600
    private const val THUMB_SIDE = 40

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val downloads = ConcurrentHashMap<String, Deferred<File?>>()
    private val bitmaps = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 8).toInt()) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }

    fun partsOf(size: Int) = maxOf(1, (size + PART_BYTES - 1) / PART_BYTES)

    fun partId(blobId: String, part: Int) = "${blobId}_p$part"

    /** Only ids Firestore itself makes, so a message cannot point outside the chat's own folder. */
    fun validBlobId(id: String?): String? = id?.takeIf { it.matches(Regex("[A-Za-z0-9]{1,40}")) }

    /** Kept per conversation, so a message in one chat can never show a file from another. */
    fun cacheKey(kind: ChatKind, chatId: String, blobId: String) = "${kind.name.lowercase()}_${chatId}_$blobId"

    /** A photo from the gallery or the camera, turned upright and made small enough to send quickly. */
    suspend fun photo(ctx: Context, uri: Uri): Outgoing? = withContext(Dispatchers.IO) {
        val cr = ctx.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= PHOTO_SIDE) sample *= 2
        val decoded = cr.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return@withContext null
        val upright = rotate(decoded, cr.openInputStream(uri)?.use(::orientation) ?: 0)
        val photo = scaleDown(upright, PHOTO_SIDE)
        var quality = 85
        var bytes = jpeg(photo, quality)
        while (bytes.size > PART_BYTES && quality > 40) {
            quality -= 10
            bytes = jpeg(photo, quality)
        }
        val thumb = jpeg(scaleDown(photo, THUMB_SIDE), 60)
        Outgoing(MessageType.IMAGE, bytes, "photo.jpg", "image/jpeg", photo.width, photo.height, thumb)
    }

    /** Any file, as it is, up to [MAX_FILE_BYTES]. */
    suspend fun file(ctx: Context, uri: Uri): Outgoing? = withContext(Dispatchers.IO) {
        val cr = ctx.contentResolver
        var name = "file"
        var size = -1L
        runCatching {
            cr.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    if (!c.isNull(0)) name = c.getString(0)
                    if (!c.isNull(1)) size = c.getLong(1)
                }
            }
        }
        if (size > MAX_FILE_BYTES) throw TooBigException()
        val bytes = cr.openInputStream(uri)?.use { readAtMost(it, MAX_FILE_BYTES + 1) } ?: return@withContext null
        if (bytes.size > MAX_FILE_BYTES) throw TooBigException()
        Outgoing(MessageType.FILE, bytes, name, cr.getType(uri) ?: "application/octet-stream")
    }

    private fun readAtMost(input: InputStream, limit: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(64 * 1024)
        while (out.size() < limit) {
            val n = input.read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    private fun orientation(input: InputStream): Int = runCatching {
        when (ExifInterface(input).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
    }.getOrDefault(0)

    private fun rotate(b: Bitmap, degrees: Int): Bitmap =
        if (degrees == 0) b else Bitmap.createBitmap(b, 0, 0, b.width, b.height, Matrix().apply { postRotate(degrees.toFloat()) }, true)

    private fun scaleDown(b: Bitmap, side: Int): Bitmap {
        val longest = max(b.width, b.height)
        if (longest <= side) return b
        val f = side.toFloat() / longest
        return Bitmap.createScaledBitmap(b, max(1, (b.width * f).roundToInt()), max(1, (b.height * f).roundToInt()), true)
    }

    private fun jpeg(b: Bitmap, quality: Int): ByteArray =
        ByteArrayOutputStream().also { b.compress(Bitmap.CompressFormat.JPEG, quality, it) }.toByteArray()

    private fun cacheFile(ctx: Context, key: String) = File(ctx.cacheDir, "chat/blobs/$key")

    /** Keeps what was just sent, so the sender never downloads it again. */
    suspend fun remember(ctx: Context, key: String, bytes: ByteArray) = withContext(Dispatchers.IO) {
        runCatching {
            val f = cacheFile(ctx, key)
            f.parentFile?.mkdirs()
            // Written aside first, so a half-written copy is never taken for the whole file.
            val tmp = File(f.path + ".part")
            tmp.writeBytes(bytes)
            if (!tmp.renameTo(f)) tmp.delete()
        }
    }

    fun forget(ctx: Context, key: String) {
        bitmaps.snapshot().keys.filter { it.startsWith("$key@") }.forEach { bitmaps.remove(it) }
        runCatching { cacheFile(ctx, key).delete() }
    }

    /** The attachment's bytes on this phone, downloaded once even when several screens ask at the same time. */
    suspend fun local(ctx: Context, key: String, download: suspend () -> ByteArray?): File? {
        val f = cacheFile(ctx, key)
        if (f.length() > 0) return f
        // Started only once it is in the map, so it can take itself out when it ends.
        val job = downloads.computeIfAbsent(key) {
            scope.async(start = CoroutineStart.LAZY) {
                try {
                    val bytes = download() ?: return@async null
                    f.parentFile?.mkdirs()
                    val tmp = File(f.path + ".part")
                    tmp.writeBytes(bytes)
                    if (!tmp.renameTo(f)) tmp.delete()
                    f.takeIf { it.length() > 0 }
                } finally {
                    downloads.remove(key)
                }
            }
        }
        job.start()
        return job.await()
    }

    /** A photo decoded no bigger than [side] pixels, kept in memory while the chat is open. */
    suspend fun bitmap(file: File, key: String, side: Int): Bitmap? = withContext(Dispatchers.IO) {
        bitmaps.get("$key@$side")?.let { return@withContext it }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0) return@withContext null
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= side) sample *= 2
        val b = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return@withContext null
        bitmaps.put("$key@$side", b)
        b
    }

    fun thumbBitmap(bytes: ByteArray?): Bitmap? = bytes?.let { runCatching { BitmapFactory.decodeByteArray(it, 0, it.size) }.getOrNull() }

    /** A copy under its real name that other apps may read, to open or share it. */
    suspend fun shareable(ctx: Context, file: File, a: Attachment): Uri = withContext(Dispatchers.IO) {
        // The name comes from the sender, so nothing in it may lead out of this folder.
        val safe = a.fileName.replace(Regex("""[\\/:*?"<>|\u0000-\u001f]"""), "_").trim().trimStart('.')
            .ifEmpty { "file" }.take(80)
        val out = File(ctx.cacheDir, "chat/open/${a.cacheKey}/$safe")
        if (out.length() != file.length()) {
            out.parentFile?.mkdirs()
            file.copyTo(out, overwrite = true)
        }
        FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", out)
    }

    /** Opens the file in an app that can show it. False when the phone has none. */
    fun open(ctx: Context, uri: Uri, a: Attachment): Boolean {
        val view = Intent(Intent.ACTION_VIEW).setDataAndType(uri, a.mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            ctx.startActivity(view)
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }

    /** Sends the file to another app (to save it, or pass it on). */
    fun share(ctx: Context, uri: Uri, a: Attachment) {
        val send = Intent(Intent.ACTION_SEND).setType(a.mime).putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** A place for the camera to save a photo before it is sent. */
    fun cameraUri(ctx: Context): Uri {
        val dir = File(ctx.cacheDir, "chat/camera").apply { mkdirs() }
        return FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", File(dir, "photo_${System.currentTimeMillis()}.jpg"))
    }

    /** Removes one photo the camera saved, once it was read or when the photo was cancelled. */
    fun dropCameraPhoto(ctx: Context, uri: Uri) {
        val name = uri.lastPathSegment ?: return
        runCatching { File(ctx.cacheDir, "chat/camera/${File(name).name}").delete() }
    }
}
