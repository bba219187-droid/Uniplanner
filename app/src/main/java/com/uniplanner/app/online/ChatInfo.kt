package com.uniplanner.app.online

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import com.uniplanner.app.R
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await

/**
 * The page behind a chat's name, like WhatsApp's contact and group info: who it is, whether
 * they are online, the calls, encryption, disappearing messages, and every photo and file shared.
 */
@Composable
fun ChatInfo(
    vm: OnlineViewModel,
    kind: ChatKind,
    id: String,
    title: String,
    me: String?,
    messages: List<ChatMessage>,
    onClose: () -> Unit,
    onCall: (video: Boolean) -> Unit,
    onPhoto: (Attachment) -> Unit,
    onFile: (Attachment) -> Unit,
) {
    val friend = friendUid(kind, id, me)
    val profile by remember(friend) { friend?.let(vm::profileOf) ?: flowOf(null) }.collectAsState(initial = null)
    val seen by remember(friend) { friend?.let(Presence::of) ?: flowOf(null) }.collectAsState(initial = null)
    val timer by remember(kind, id) { vm.timer(kind, id) }.collectAsState(initial = ChatTimers.of(id))
    val members by produceState<List<Pair<String, String>>>(emptyList(), kind, id) {
        if (kind != ChatKind.GROUP) return@produceState
        val uids = runCatching { (conversationOf(kind, id).get().await().get("members") as? List<*>)?.filterIsInstance<String>() }
            .getOrNull().orEmpty()
        value = uids.map { it to "" }
        value = uids.map { uid ->
            uid to runCatching { Firebase.firestore.collection("users").document(uid).get().await().getString("name") }.getOrNull().orEmpty()
        }
    }
    val photos = messages.mapNotNull { m -> m.attachment?.takeIf { m.type == MessageType.IMAGE } }
    val files = messages.mapNotNull { m -> m.attachment?.takeIf { m.type == MessageType.FILE } }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding()
                .verticalScroll(rememberScrollState()),
        ) {
            IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Avatar(title, group = kind == ChatKind.GROUP, size = 112, uid = friend)
                Spacer(Modifier.height(14.dp))
                Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                val about = when {
                    kind == ChatKind.GROUP -> stringResource(R.string.groups_members, members.size.coerceAtLeast(1))
                    else -> listOf(profile?.course, profile?.university).filter { !it.isNullOrBlank() }.joinToString(" · ")
                }
                if (about.isNotBlank()) Text(about, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                seen?.let { s ->
                    Text(
                        if (s.online) stringResource(R.string.chat_online) else s.at?.let { lastSeen(it) }.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (s.online) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ActionChip(Icons.Filled.Call, stringResource(R.string.call_voice)) { onCall(false) }
                    ActionChip(Icons.Filled.Videocam, stringResource(R.string.call_video)) { onCall(true) }
                }
            }
            Spacer(Modifier.height(18.dp))

            Section(stringResource(R.string.info_media, photos.size + files.size)) {
                if (photos.isEmpty() && files.isEmpty()) {
                    Text(stringResource(R.string.info_media_none), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                photos.chunked(3).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        row.forEach { a ->
                            val bmp = remember(a.blobId) { Attachments.thumbBitmap(a.thumb)?.asImageBitmap() }
                            Box(
                                Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainer).clickable { onPhoto(a) },
                            ) {
                                if (bmp != null) Image(bmp, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            }
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                    Spacer(Modifier.height(4.dp))
                }
                files.forEach { a ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onFile(a) }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.AutoMirrored.Filled.InsertDriveFile, null, tint = MaterialTheme.colorScheme.secondary)
                        Spacer(Modifier.width(10.dp))
                        Text(a.fileName, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    }
                }
            }

            Section(stringResource(R.string.info_privacy)) {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(Icons.Filled.Lock, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.info_e2e), style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Timer, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.info_timer), style = MaterialTheme.typography.titleSmall)
                }
                Text(stringResource(R.string.info_timer_text), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                listOf(0 to R.string.info_timer_off, 24 to R.string.info_timer_24h).forEach { (h, label) ->
                    Row(
                        Modifier.fillMaxWidth().clickable { if (timer != h) vm.setTimer(kind, id, h) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = timer == h, onClick = { if (timer != h) vm.setTimer(kind, id, h) })
                        Text(stringResource(label))
                    }
                }
            }

            if (kind == ChatKind.GROUP && members.isNotEmpty()) {
                Section(stringResource(R.string.info_members)) {
                    members.forEach { (uid, name) ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Avatar(name, group = false, size = 40, uid = uid, own = uid == me)
                            Spacer(Modifier.width(12.dp))
                            Text(if (uid == me) stringResource(R.string.chat_you) else name.ifBlank { "…" })
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ActionChip(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, label, tint = MaterialTheme.colorScheme.secondary)
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(16.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(10.dp))
        content()
    }
}
