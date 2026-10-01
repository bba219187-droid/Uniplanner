package com.uniplanner.app.settings

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * The student's profile photo: a small square JPEG kept on the phone and, when signed in, on their
 * profile in Firestore (about 20 KB), where friends can see it. No Firebase Storage needed.
 */
object ProfilePhoto {
    private const val SIDE = 320
    /** Bumped on every change, so pictures on screen reload. */
    val version = MutableStateFlow(0)
    private val friends = HashMap<String, ImageBitmap?>()

    private fun file(ctx: Context) = File(ctx.filesDir, "profile.jpg")

    fun exists(ctx: Context) = file(ctx).exists()

    fun load(ctx: Context): ImageBitmap? =
        file(ctx).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) }?.asImageBitmap()

    /** Crops the chosen picture to a square, shrinks it and saves it. */
    suspend fun set(ctx: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val bytes = runCatching {
            val src = if (Build.VERSION.SDK_INT >= 28) {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(ctx.contentResolver, uri)) { d, info, _ ->
                    val s = minOf(info.size.width, info.size.height)
                    if (s > SIDE * 2) d.setTargetSampleSize(s / (SIDE * 2))
                    d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
            } else {
                ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } ?: return@runCatching null
            }
            val s = minOf(src.width, src.height)
            val square = Bitmap.createBitmap(src, (src.width - s) / 2, (src.height - s) / 2, s, s)
            val small = Bitmap.createScaledBitmap(square, SIDE, SIDE, true)
            ByteArrayOutputStream().also { small.compress(Bitmap.CompressFormat.JPEG, 80, it) }.toByteArray()
        }.getOrNull() ?: return@withContext false
        file(ctx).writeBytes(bytes)
        version.value++
        upload(bytes)
        true
    }

    fun remove(ctx: Context) {
        file(ctx).delete()
        version.value++
        upload(null)
    }

    /** Sends the photo (or its removal) to the profile, if signed in. Queued offline by Firestore. */
    fun upload(bytes: ByteArray?) {
        val uid = Firebase.auth.currentUser?.uid ?: return
        Firebase.firestore.collection("users").document(uid)
            .set(mapOf("photo" to bytes?.let { Blob.fromBytes(it) }), SetOptions.merge())
    }

    /** After signing in, puts the photo taken before on the profile. */
    fun syncAfterSignIn(ctx: Context) {
        file(ctx).takeIf { it.exists() }?.let { upload(it.readBytes()) }
    }

    /** Brings the photo back from the profile when this phone has none, e.g. after clearing the app's data. */
    suspend fun restore(ctx: Context, uid: String) {
        if (exists(ctx)) return
        val bytes = runCatching {
            Firebase.firestore.collection("users").document(uid).get().await().getBlob("photo")?.toBytes()
        }.getOrNull() ?: return
        withContext(Dispatchers.IO) { file(ctx).writeBytes(bytes) }
        version.value++
    }

    /** A friend's photo from their profile, or null. Cached for the session. */
    suspend fun friend(uid: String): ImageBitmap? {
        synchronized(friends) { if (uid in friends) return friends[uid] }
        val image = runCatching {
            val blob = Firebase.firestore.collection("users").document(uid).get().await().getBlob("photo")
            blob?.toBytes()?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }?.asImageBitmap()
        }.getOrNull()
        synchronized(friends) { friends[uid] = image }
        return image
    }
}

/** The student's own photo, if they chose one. */
@Composable
fun rememberOwnPhoto(): ImageBitmap? {
    val ctx = LocalContext.current
    val v by ProfilePhoto.version.collectAsState()
    return remember(v) { ProfilePhoto.load(ctx) }
}

/** A friend's photo, loaded once; null while loading or when they have none. */
@Composable
fun rememberFriendPhoto(uid: String?): ImageBitmap? {
    val photo by produceState<ImageBitmap?>(null, uid) { value = uid?.let { ProfilePhoto.friend(it) } }
    return photo
}

@Composable
fun RoundPhoto(image: ImageBitmap, size: Dp) {
    Image(image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(size).clip(CircleShape))
}
