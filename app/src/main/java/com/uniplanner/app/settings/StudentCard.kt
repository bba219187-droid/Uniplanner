package com.uniplanner.app.settings

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.uniplanner.app.R
import com.uniplanner.app.ui.screens.ScreenHeader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * The student card: pictures of both sides (or screenshots of the university's digital card) and a
 * link to the official digital card, for universities that have one. Kept only on the phone.
 */
object StudentCard {
    private const val LONG_SIDE = 1600
    private const val PREFS = "student_card"
    private const val LINK = "link"

    fun file(ctx: Context, side: String) = File(ctx.filesDir, "student_card_$side.jpg")

    fun link(ctx: Context): String = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(LINK, "").orEmpty()

    fun setLink(ctx: Context, link: String) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(LINK, link.trim()).apply()
    }

    /** Shrinks the chosen picture so its longest side is at most [LONG_SIDE] and saves it. */
    suspend fun set(ctx: Context, side: String, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val bytes = runCatching {
            val src = if (Build.VERSION.SDK_INT >= 28) {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(ctx.contentResolver, uri)) { d, info, _ ->
                    val s = maxOf(info.size.width, info.size.height)
                    if (s > LONG_SIDE * 2) d.setTargetSampleSize(s / LONG_SIDE)
                    d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
            } else {
                ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } ?: return@runCatching null
            }
            val scale = minOf(1f, LONG_SIDE.toFloat() / maxOf(src.width, src.height))
            val small = Bitmap.createScaledBitmap(src, (src.width * scale).toInt(), (src.height * scale).toInt(), true)
            ByteArrayOutputStream().also { small.compress(Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray()
        }.getOrNull() ?: return@withContext false
        file(ctx, side).writeBytes(bytes)
        true
    }
}

private tailrec fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}

@Composable
fun StudentCardScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var version by remember { mutableIntStateOf(0) }
    var pickingSide by remember { mutableStateOf("front") }
    var link by remember { mutableStateOf(StudentCard.link(ctx)) }
    val pick = rememberLauncherForActivityResult(PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { StudentCard.set(ctx, pickingSide, uri); version++ }
    }

    // Full brightness while the card is open, so door and canteen readers can read it.
    DisposableEffect(Unit) {
        val window = ctx.activity()?.window
        val before = window?.attributes?.screenBrightness ?: -1f
        window?.let { w -> w.attributes = w.attributes.apply { screenBrightness = 1f } }
        onDispose { window?.let { w -> w.attributes = w.attributes.apply { screenBrightness = before } } }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScreenHeader(stringResource(R.string.student_card_title), stringResource(R.string.student_card_sub))
        listOf("front" to R.string.student_card_front, "back" to R.string.student_card_back).forEach { (side, label) ->
            val picture = remember(version) {
                StudentCard.file(ctx, side).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) }?.asImageBitmap()
            }
            if (picture != null) {
                Image(
                    picture,
                    contentDescription = stringResource(label),
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    pickingSide = side
                    pick.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly))
                }) { Text(stringResource(if (picture == null) R.string.student_card_add else R.string.student_card_change, stringResource(label))) }
                if (picture != null) TextButton(onClick = { StudentCard.file(ctx, side).delete(); version++ }) {
                    Text(stringResource(R.string.student_card_remove))
                }
            }
        }
        OutlinedTextField(
            link,
            onValueChange = { link = it; StudentCard.setLink(ctx, it) },
            label = { Text(stringResource(R.string.student_card_link)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )
        if (link.isNotBlank()) {
            Button(onClick = {
                val url = link.trim().let { if ("://" in it) it else "https://$it" }
                runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            }) { Text(stringResource(R.string.student_card_open)) }
        }
        Text(
            stringResource(R.string.student_card_private),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 24.dp),
        )
    }
}
