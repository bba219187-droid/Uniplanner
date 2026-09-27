package com.uniplanner.app.online

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uniplanner.app.R
import com.uniplanner.app.ui.screens.EmptyState
import com.uniplanner.app.ui.screens.ScreenHeader
import java.text.DateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Date

private val avatarColors = listOf(0xFF3355E0, 0xFF4E8A2E, 0xFF8A4FB0, 0xFFB57A0A, 0xFFC94220, 0xFF0E7C86)

@Composable
private fun Avatar(name: String, group: Boolean, size: Int = 48) {
    val base = Color(avatarColors[Math.floorMod(name.hashCode(), avatarColors.size)])
    val light = MaterialTheme.colorScheme.background.let { it.red + it.green + it.blue > 1.5f }
    val bg = if (light) lerp(base, Color.White, 0.78f) else lerp(base, Color.Black, 0.55f)
    val fg = if (light) lerp(base, Color.Black, 0.3f) else lerp(base, Color.White, 0.5f)
    Box(Modifier.size(size.dp).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
        val initials = name.split(' ').filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1).uppercase() }
        if (initials.isEmpty() || group && initials.length > 2) {
            Icon(Icons.Filled.Person, contentDescription = null, tint = fg)
        } else {
            Text(initials, color = fg, style = MaterialTheme.typography.titleMedium)
        }
    }
}

private fun shortTime(millis: Long, yesterday: String): String {
    val zone = ZoneId.systemDefault()
    val day = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
    val today = LocalDate.now(zone)
    return when {
        day == today -> DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(millis))
        day == today.minusDays(1) -> yesterday
        else -> day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT))
    }
}

/** The friends tab: sign in first, then every conversation with friends and groups, like a messaging app. */
@Composable
fun SocialScreen(vm: OnlineViewModel, onOpenChat: (ChatKind, String) -> Unit) {
    if (!vm.configured) {
        EmptyState(stringResource(R.string.online_not_configured))
        return
    }
    val uid by vm.uid.collectAsStateWithLifecycle()
    OnlineMessages(vm)
    if (uid == null) {
        SignInScreen(vm)
        return
    }
    val conversations by vm.conversations.collectAsStateWithLifecycle()
    val friendships by vm.friendships.collectAsStateWithLifecycle()
    val profile by vm.profile.collectAsStateWithLifecycle()
    var addingFriend by remember { mutableStateOf(false) }
    var groupMenu by remember { mutableStateOf(false) }
    var creatingGroup by remember { mutableStateOf(false) }
    var joiningGroup by remember { mutableStateOf(false) }
    var editingProfile by remember { mutableStateOf(false) }
    val incoming = friendships.filter { !it.accepted && it.incoming }
    val sent = friendships.filter { !it.accepted && !it.incoming }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Row(Modifier.padding(start = 18.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { ScreenHeader(stringResource(R.string.chat_title)) }
                IconButton(onClick = { addingFriend = true }) { Icon(Icons.Filled.PersonAdd, stringResource(R.string.friends_add)) }
                Box {
                    IconButton(onClick = { groupMenu = true }) { Icon(Icons.Filled.GroupAdd, stringResource(R.string.social_groups)) }
                    DropdownMenu(expanded = groupMenu, onDismissRequest = { groupMenu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.groups_create)) }, onClick = { groupMenu = false; creatingGroup = true })
                        DropdownMenuItem(text = { Text(stringResource(R.string.groups_join_title)) }, onClick = { groupMenu = false; joiningGroup = true })
                    }
                }
                IconButton(onClick = { editingProfile = true }) {
                    Avatar(profile?.name.orEmpty(), group = false, size = 32)
                }
            }
        }
        if (incoming.isNotEmpty()) {
            item {
                Text(
                    stringResource(R.string.friends_requests),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(start = 18.dp, top = 4.dp, bottom = 4.dp),
                )
            }
            items(incoming, key = { "r${it.id}" }) { f ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(f.otherName, group = false)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(f.otherName, style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.chat_wants_friend), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    FilledIconButton(onClick = { vm.acceptFriend(f) }) { Icon(Icons.Filled.Check, stringResource(R.string.friends_accept)) }
                    IconButton(onClick = { vm.removeFriend(f) }) { Icon(Icons.Filled.Close, stringResource(R.string.friends_decline)) }
                }
            }
            item { HorizontalDivider(Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.outlineVariant) }
        }
        if (conversations.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.chat_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(18.dp),
                )
            }
        }
        items(conversations, key = { "${it.kind}${it.id}" }) { c ->
            val me = uid
            Row(
                Modifier.fillMaxWidth().clickable { onOpenChat(c.kind, c.id) }.padding(horizontal = 18.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(c.title, group = c.kind == ChatKind.GROUP)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(c.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val preview = c.last?.let { last ->
                        when {
                            last.byUid == me -> stringResource(R.string.chat_you) + ": " + last.text
                            c.kind == ChatKind.GROUP -> last.byName.substringBefore(' ') + ": " + last.text
                            else -> last.text
                        }
                    } ?: stringResource(if (c.kind == ChatKind.GROUP) R.string.chat_group_new else R.string.chat_friend_new, c.memberCount)
                    Text(
                        preview,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (c.unread) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (c.unread) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    c.last?.let {
                        Text(
                            shortTime(it.at, stringResource(R.string.chat_yesterday)),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (c.unread) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (c.unread) {
                        Spacer(Modifier.size(6.dp))
                        Box(Modifier.size(10.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondary))
                    }
                }
            }
        }
        if (sent.isNotEmpty()) {
            item {
                Text(
                    stringResource(R.string.friends_sent) + ": " + sent.joinToString { it.otherName },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(18.dp),
                )
            }
        }
        item { Spacer(Modifier.size(24.dp)) }
    }

    if (addingFriend) {
        var code by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { addingFriend = false },
            title = { Text(stringResource(R.string.friends_add)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.friends_my_code), style = MaterialTheme.typography.labelLarge)
                    Text(profile?.friendCode.orEmpty(), style = MaterialTheme.typography.headlineMedium)
                    Text(stringResource(R.string.friends_code_help), style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        code, { code = it.uppercase().take(6) },
                        label = { Text(stringResource(R.string.friends_add_code)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    )
                }
            },
            confirmButton = {
                TextButton(enabled = code.length == 6, onClick = { vm.addFriend(code); addingFriend = false }) {
                    Text(stringResource(R.string.friends_add))
                }
            },
            dismissButton = { TextButton(onClick = { addingFriend = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (creatingGroup) {
        var name by remember { mutableStateOf("") }
        var description by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { creatingGroup = false },
            title = { Text(stringResource(R.string.groups_create)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.groups_name)) }, singleLine = true)
                    OutlinedTextField(description, { description = it }, label = { Text(stringResource(R.string.groups_description)) })
                }
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = { vm.createGroup(name, description); creatingGroup = false }) {
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = { TextButton(onClick = { creatingGroup = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (joiningGroup) {
        var code by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { joiningGroup = false },
            title = { Text(stringResource(R.string.groups_join_title)) },
            text = {
                OutlinedTextField(
                    code, { code = it.uppercase().take(8) },
                    label = { Text(stringResource(R.string.groups_join_code)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                )
            },
            confirmButton = {
                TextButton(enabled = code.length == 8, onClick = { vm.joinGroup(code); joiningGroup = false }) {
                    Text(stringResource(R.string.groups_join))
                }
            },
            dismissButton = { TextButton(onClick = { joiningGroup = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (editingProfile) {
        AlertDialog(
            onDismissRequest = { editingProfile = false },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) { ProfileSection(vm, profile) } },
            confirmButton = { TextButton(onClick = { editingProfile = false }) { Text(stringResource(R.string.ok)) } },
        )
    }
}

private val urlPattern = Regex("""(https?://\S+|www\.\S+)""")

@Composable
private fun linkified(text: String, color: Color): AnnotatedString = buildAnnotatedString {
    var last = 0
    for (m in urlPattern.findAll(text)) {
        append(text.substring(last, m.range.first))
        val url = if (m.value.startsWith("http")) m.value else "https://${m.value}"
        pushLink(LinkAnnotation.Url(url, TextLinkStyles(SpanStyle(color = color, textDecoration = TextDecoration.Underline))))
        append(m.value)
        pop()
        last = m.range.last + 1
    }
    append(text.substring(last))
}

/** One conversation: messages in bubbles, mine on the right, and a box to write at the bottom. */
@Composable
fun ChatScreen(vm: OnlineViewModel, kind: ChatKind, id: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val uid by vm.uid.collectAsStateWithLifecycle()
    val conversations by vm.conversations.collectAsStateWithLifecycle()
    val groups by vm.groups.collectAsStateWithLifecycle()
    val friendships by vm.friendships.collectAsStateWithLifecycle()
    val messages by remember(kind, id) { vm.messages(kind, id) }.collectAsState(initial = emptyList())
    val conversation = conversations.firstOrNull { it.kind == kind && it.id == id }
    val group = groups.firstOrNull { it.id == id }.takeIf { kind == ChatKind.GROUP }
    var text by remember { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }

    LaunchedEffect(messages.firstOrNull()?.id) { vm.markRead(kind, id) }
    OnlineMessages(vm)

    Column(Modifier.fillMaxSize().imePadding()) {
        Row(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
            Avatar(conversation?.title.orEmpty(), group = kind == ChatKind.GROUP, size = 40)
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(conversation?.title.orEmpty(), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    if (group != null) stringResource(R.string.groups_members, group.memberCount) else stringResource(R.string.chat_friend),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, stringResource(R.string.chat_options)) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (group != null) {
                        val shareText = stringResource(R.string.groups_share_text, group.name, group.inviteCode)
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.groups_invite) + " · " + group.inviteCode) },
                            onClick = {
                                menu = false
                                context.startActivity(
                                    Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, shareText), null),
                                )
                            },
                        )
                        DropdownMenuItem(text = { Text(stringResource(R.string.groups_leave)) }, onClick = { menu = false; confirmLeave = true })
                    } else {
                        DropdownMenuItem(text = { Text(stringResource(R.string.friends_remove)) }, onClick = { menu = false; confirmLeave = true })
                    }
                }
            }
        }

        LazyColumn(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
            reverseLayout = true,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item { Spacer(Modifier.size(8.dp)) }
            itemsIndexed(messages, key = { _, m -> m.id }) { i, m ->
                val mine = m.authorId == uid
                val older = messages.getOrNull(i + 1)
                val zone = ZoneId.systemDefault()
                val day = Instant.ofEpochMilli(m.createdAt).atZone(zone).toLocalDate()
                val sameAuthorAsOlder = older != null && older.authorId == m.authorId &&
                    Instant.ofEpochMilli(older.createdAt).atZone(zone).toLocalDate() == day
                Column {
                    if (older == null || Instant.ofEpochMilli(older.createdAt).atZone(zone).toLocalDate() != day) {
                        Box(Modifier.fillMaxWidth().padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                            Text(
                                day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainer)
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                            )
                        }
                    }
                    Bubble(m, mine, showName = kind == ChatKind.GROUP && !mine && !sameAuthorAsOlder, tail = !sameAuthorAsOlder)
                }
            }
            if (messages.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.chat_say_hi),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                    )
                }
            }
        }

        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.Bottom) {
            Box(
                Modifier.weight(1f).heightIn(min = 48.dp).clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(24.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (text.isEmpty()) {
                    Text(stringResource(R.string.chat_placeholder), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
                }
                BasicTextField(
                    text,
                    { text = it },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.secondary),
                    maxLines = 5,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.size(8.dp))
            FilledIconButton(
                onClick = {
                    vm.sendMessage(kind, id, text)
                    text = ""
                },
                enabled = text.isNotBlank(),
                modifier = Modifier.size(48.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary,
                ),
            ) { Icon(Icons.AutoMirrored.Filled.Send, stringResource(R.string.chat_send)) }
        }
    }

    if (confirmLeave) {
        val title = conversation?.title.orEmpty()
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text(stringResource(if (group != null) R.string.groups_leave_title else R.string.chat_remove_friend_title, title)) },
            confirmButton = {
                TextButton(onClick = {
                    if (group != null) vm.leaveGroup(id) else friendships.firstOrNull { it.id == id }?.let(vm::removeFriend)
                    confirmLeave = false
                    onBack()
                }) { Text(stringResource(if (group != null) R.string.groups_leave else R.string.friends_remove)) }
            },
            dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun Bubble(m: ChatMessage, mine: Boolean, showName: Boolean, tail: Boolean) {
    val bg = if (mine) MaterialTheme.colorScheme.inverseSurface else MaterialTheme.colorScheme.surfaceContainerHigh
    val fg = if (mine) MaterialTheme.colorScheme.inverseOnSurface else MaterialTheme.colorScheme.onSurface
    val shape = RoundedCornerShape(
        topStart = 18.dp, topEnd = 18.dp,
        bottomStart = if (!mine && tail) 4.dp else 18.dp,
        bottomEnd = if (mine && tail) 4.dp else 18.dp,
    )
    Box(Modifier.fillMaxWidth().padding(top = if (tail) 4.dp else 0.dp), contentAlignment = if (mine) Alignment.CenterEnd else Alignment.CenterStart) {
        Column(
            Modifier.widthIn(max = 300.dp).clip(shape).background(bg)
                .then(if (mine) Modifier else Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape))
                .padding(start = 12.dp, end = 10.dp, top = 7.dp, bottom = 6.dp),
        ) {
            if (showName) {
                val nameColor = Color(avatarColors[Math.floorMod(m.authorName.hashCode(), avatarColors.size)])
                Text(m.authorName, color = nameColor, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }
            Text(linkified(m.text, if (mine) MaterialTheme.colorScheme.inversePrimary else MaterialTheme.colorScheme.tertiary), color = fg, style = MaterialTheme.typography.bodyLarge)
            Row(Modifier.align(Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(m.createdAt)),
                    fontSize = 11.sp,
                    color = fg.copy(alpha = 0.6f),
                )
                if (mine) {
                    Spacer(Modifier.size(4.dp))
                    Icon(
                        if (m.pending) Icons.Filled.Schedule else Icons.Filled.Check,
                        contentDescription = null,
                        tint = fg.copy(alpha = 0.6f),
                        modifier = Modifier.size(13.dp),
                    )
                }
            }
        }
    }
}
