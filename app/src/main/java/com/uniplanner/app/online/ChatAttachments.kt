package com.uniplanner.app.online

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.uniplanner.app.R
import com.uniplanner.app.location.MapPin
import com.uniplanner.app.location.PinMap
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** What the attach button offers, as in messaging apps. */
enum class AttachChoice { GALLERY, CAMERA, FILE, LOCATION }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttachSheet(onDismiss: () -> Unit, onChoose: (AttachChoice) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 28.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            AttachOption(Icons.Filled.Image, stringResource(R.string.chat_gallery), Color(0xFF8A4FB0)) { onChoose(AttachChoice.GALLERY) }
            AttachOption(Icons.Filled.PhotoCamera, stringResource(R.string.chat_camera), Color(0xFFC94220)) { onChoose(AttachChoice.CAMERA) }
            AttachOption(Icons.Filled.Description, stringResource(R.string.chat_file), Color(0xFF3355E0)) { onChoose(AttachChoice.FILE) }
            AttachOption(Icons.Filled.LocationOn, stringResource(R.string.chat_location), Color(0xFF4E8A2E)) { onChoose(AttachChoice.LOCATION) }
        }
    }
}

@Composable
private fun AttachOption(icon: ImageVector, label: String, color: Color, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(56.dp).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = Color.White)
        }
        Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 6.dp))
    }
}

// Photos, files and places take the taps themselves, so a long press still reaches the message menu.

/** A photo in a bubble: the tiny preview at once, the real photo when it has arrived. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PhotoContent(vm: OnlineViewModel, kind: ChatKind, chatId: String, a: Attachment, onOpen: () -> Unit, onLongPress: () -> Unit) {
    val thumb = remember(a.blobId) { Attachments.thumbBitmap(a.thumb)?.asImageBitmap() }
    val photo by rememberPhoto(vm, kind, chatId, a, 900)
    val ratio = if (a.width > 0 && a.height > 0) (a.width.toFloat() / a.height).coerceIn(0.6f, 1.8f) else 1f
    Box(
        Modifier.width(240.dp).aspectRatio(ratio).clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest).combinedClickable(onClick = onOpen, onLongClick = onLongPress),
        contentAlignment = Alignment.Center,
    ) {
        val shown = photo ?: thumb
        if (shown != null) {
            Image(shown, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        if (photo == null) CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp, color = Color.White)
    }
}

/** Loads a photo, trying again a little later while it cannot be downloaded (no internet, for example). */
@Composable
private fun rememberPhoto(vm: OnlineViewModel, kind: ChatKind, chatId: String, a: Attachment, side: Int) =
    produceState<ImageBitmap?>(null, a.cacheKey, side) {
        var wait = 2_000L
        while (value == null) {
            value = vm.attachmentFile(kind, chatId, a)?.let { Attachments.bitmap(it, a.cacheKey, side) }?.asImageBitmap()
            if (value == null) {
                delay(wait)
                wait = minOf(wait * 2, 30_000L)
            }
        }
    }

/** A file in a bubble: its name and size. Tapping opens it in an app that can show it. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileContent(vm: OnlineViewModel, kind: ChatKind, chatId: String, a: Attachment, fg: Color, onLongPress: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var opening by remember { mutableStateOf(false) }
    Row(
        Modifier.width(240.dp).clip(RoundedCornerShape(12.dp)).background(fg.copy(alpha = 0.08f))
            .combinedClickable(
                onLongClick = onLongPress,
                onClick = {
                    if (!opening) {
                        scope.launch {
                            opening = true
                            try {
                                vm.openAttachment(kind, chatId, a)
                            } finally {
                                opening = false
                            }
                        }
                    }
                },
            )
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            if (opening) {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp, color = fg)
            } else {
                Icon(Icons.Filled.Description, contentDescription = null, tint = fg, modifier = Modifier.size(32.dp))
            }
        }
        Column(Modifier.weight(1f).padding(start = 8.dp)) {
            Text(a.fileName, color = fg, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val ext = a.fileName.substringAfterLast('.', "").uppercase().take(5)
            Text(
                listOf(Formatter.formatShortFileSize(context, a.size), ext).filter { it.isNotBlank() }.joinToString(" · "),
                color = fg.copy(alpha = 0.65f),
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

/** A place in a bubble: a small still map and the name of the place. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlaceContent(place: SharedPlace, fg: Color, onOpen: () -> Unit, onLongPress: () -> Unit) {
    Column(Modifier.width(240.dp)) {
        Box(Modifier.fillMaxWidth().height(130.dp).clip(RoundedCornerShape(12.dp))) {
            PinMap(
                pins = listOf(MapPin("here", place.lat, place.lng)),
                dark = MaterialTheme.colorScheme.background.luminance() < 0.5f,
                pinColor = MaterialTheme.colorScheme.secondary,
                interactive = false,
                modifier = Modifier.fillMaxSize(),
            )
            // Taps open the big map instead of moving this one.
            Box(Modifier.fillMaxSize().combinedClickable(onClick = onOpen, onLongClick = onLongPress))
        }
        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.LocationOn, contentDescription = null, tint = fg, modifier = Modifier.size(16.dp))
            Text(
                place.name.ifBlank { stringResource(R.string.chat_location) },
                color = fg,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}

@Composable
fun DeletedContent(mine: Boolean, fg: Color) {
    Text(
        stringResource(if (mine) R.string.chat_deleted_mine else R.string.chat_deleted),
        color = fg.copy(alpha = 0.7f),
        fontStyle = FontStyle.Italic,
        style = MaterialTheme.typography.bodyLarge,
    )
}

/** A photo on the whole screen, with pinch to zoom, and a button to share or save it. */
@Composable
fun PhotoViewer(vm: OnlineViewModel, kind: ChatKind, chatId: String, a: Attachment, onClose: () -> Unit) {
    val photo by rememberPhoto(vm, kind, chatId, a, 2048)
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val scope = rememberCoroutineScope()
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            photo?.let {
                Image(
                    it,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(1f, 5f)
                                offset = if (scale == 1f) Offset.Zero else offset + pan
                            }
                        }
                        .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y),
                )
            } ?: CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp)) {
                IconButton(onClick = onClose) { Icon(Icons.Filled.Close, stringResource(R.string.back), tint = Color.White) }
                Box(Modifier.weight(1f))
                IconButton(onClick = { scope.launch { vm.openAttachment(kind, chatId, a, share = true) } }) {
                    Icon(Icons.Filled.Share, stringResource(R.string.chat_share), tint = Color.White)
                }
            }
        }
    }
}

/** A sent place on a map that can be moved, and a way to open it in a maps app. */
@Composable
fun PlaceViewer(place: SharedPlace, onClose: () -> Unit) {
    val context = LocalContext.current
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.Filled.Close, stringResource(R.string.back)) }
                Text(
                    place.name.ifBlank { stringResource(R.string.chat_location) },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            PinMap(
                pins = listOf(MapPin("here", place.lat, place.lng, title = place.name.ifBlank { null })),
                dark = MaterialTheme.colorScheme.background.luminance() < 0.5f,
                pinColor = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
            TextButton(
                onClick = {
                    val label = Uri.encode(place.name.ifBlank { "UniPlanner" })
                    val geo = Intent(Intent.ACTION_VIEW, Uri.parse("geo:${place.lat},${place.lng}?q=${place.lat},${place.lng}($label)"))
                    try {
                        context.startActivity(geo)
                    } catch (_: ActivityNotFoundException) {
                        val web = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.openstreetmap.org/?mlat=${place.lat}&mlon=${place.lng}#map=17/${place.lat}/${place.lng}"))
                        runCatching { context.startActivity(web) }.onFailure {
                            Toast.makeText(context, R.string.chat_no_app, Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                modifier = Modifier.align(Alignment.CenterHorizontally).navigationBarsPadding().padding(8.dp),
            ) { Text(stringResource(R.string.chat_open_in_maps)) }
        }
    }
}

/** What can be done with a message after a long press. */
@Composable
fun MessageActions(
    m: ChatMessage,
    mine: Boolean,
    onCopy: (() -> Unit)?,
    onShare: (() -> Unit)?,
    onDeleteForMe: () -> Unit,
    onDeleteForEveryone: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.chat_message_title)) },
        text = {
            Column {
                onCopy?.let { ActionRow(stringResource(R.string.chat_copy)) { it(); onDismiss() } }
                onShare?.let { ActionRow(stringResource(R.string.chat_share)) { it(); onDismiss() } }
                ActionRow(stringResource(R.string.chat_delete_for_me)) { onDeleteForMe(); onDismiss() }
                if (mine && m.type != MessageType.DELETED) {
                    onDeleteForEveryone?.let {
                        ActionRow(stringResource(R.string.chat_delete_for_all), color = MaterialTheme.colorScheme.error) { it(); onDismiss() }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun ActionRow(label: String, color: Color = MaterialTheme.colorScheme.onSurface, onClick: () -> Unit) {
    Text(
        label,
        color = color,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(horizontal = 4.dp, vertical = 12.dp),
    )
}
