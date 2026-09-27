package com.uniplanner.app.online

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uniplanner.app.R
import com.uniplanner.app.ui.screens.PillTabs
import com.uniplanner.app.ui.screens.ScreenHeader
import com.uniplanner.app.ui.screens.EmptyState
import com.uniplanner.app.ui.screens.SectionTitle
import com.uniplanner.app.ui.screens.formatDateTime

/** Social tab: sign in first, then friends and groups. */
@Composable
fun SocialScreen(vm: OnlineViewModel) {
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
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var openGroup by rememberSaveable { mutableStateOf<String?>(null) }
    val groups by vm.groups.collectAsStateWithLifecycle()

    val group = groups.firstOrNull { it.id == openGroup }
    if (group != null) {
        GroupScreen(vm, group, onBack = { openGroup = null })
        return
    }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            ScreenHeader(stringResource(R.string.tab_social), stringResource(R.string.social_subtitle))
            PillTabs(
                listOf(stringResource(R.string.social_friends), stringResource(R.string.social_groups)),
                selected = tab,
                onSelect = { tab = it },
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        when (tab) {
            0 -> FriendsScreen(vm)
            else -> GroupsScreen(vm, onOpen = { openGroup = it })
        }
    }
}

@Composable
private fun OnlineMessages(vm: OnlineViewModel) {
    val error by vm.error.collectAsStateWithLifecycle()
    val notice by vm.notice.collectAsStateWithLifecycle()
    val text = error?.let { stringResource(R.string.online_error, it) } ?: notice?.let {
        stringResource(
            when (it) {
                OnlineNotice.FRIEND_REQUEST_SENT -> R.string.friend_request_sent
                OnlineNotice.CODE_NOT_FOUND -> R.string.code_not_found
                OnlineNotice.JOINED_GROUP -> R.string.group_joined
                OnlineNotice.RESET_EMAIL_SENT -> R.string.reset_email_sent
                OnlineNotice.PROFILE_SAVED -> R.string.profile_saved
            },
        )
    }
    if (text != null) {
        AlertDialog(
            onDismissRequest = vm::clearMessages,
            text = { Text(text) },
            confirmButton = { TextButton(onClick = vm::clearMessages) { Text(stringResource(R.string.ok)) } },
        )
    }
}

@Composable
private fun SignInScreen(vm: OnlineViewModel) {
    val context = LocalContext.current
    val busy by vm.busy.collectAsStateWithLifecycle()
    var registering by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        Text(
            stringResource(if (registering) R.string.auth_register_title else R.string.auth_sign_in_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(stringResource(R.string.auth_why), style = MaterialTheme.typography.bodyMedium)
        if (Online.googleClientId(context) != null) {
            Button(enabled = !busy, onClick = { vm.signInWithGoogle(context) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.auth_google))
            }
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
        }
        if (registering) {
            OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.profile_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
        OutlinedTextField(
            email, { email = it },
            label = { Text(stringResource(R.string.auth_email)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            password, { password = it },
            label = { Text(stringResource(R.string.auth_password)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            supportingText = { if (registering) Text(stringResource(R.string.auth_password_hint)) },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedButton(
            enabled = !busy && email.isNotBlank() && password.length >= 6 && (!registering || name.isNotBlank()),
            onClick = { if (registering) vm.register(email, password, name) else vm.signIn(email, password) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(if (registering) R.string.auth_register else R.string.auth_sign_in)) }
        TextButton(onClick = { registering = !registering }) {
            Text(stringResource(if (registering) R.string.auth_have_account else R.string.auth_no_account))
        }
        if (!registering) {
            TextButton(enabled = email.isNotBlank(), onClick = { vm.resetPassword(email) }) {
                Text(stringResource(R.string.auth_forgot))
            }
        }
    }
}

@Composable
private fun FriendsScreen(vm: OnlineViewModel) {
    val profile by vm.profile.collectAsStateWithLifecycle()
    val friendships by vm.friendships.collectAsStateWithLifecycle()
    var code by remember { mutableStateOf("") }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Card(Modifier.fillMaxWidth().padding(top = 16.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.friends_my_code), style = MaterialTheme.typography.labelLarge)
                    Text(
                        profile?.friendCode.orEmpty(),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(stringResource(R.string.friends_code_help), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    code, { code = it.uppercase().take(6) },
                    label = { Text(stringResource(R.string.friends_add_code)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                IconButton(enabled = code.length == 6, onClick = { vm.addFriend(code); code = "" }) {
                    Icon(Icons.AutoMirrored.Filled.Send, stringResource(R.string.friends_add))
                }
            }
        }
        val incoming = friendships.filter { !it.accepted && it.incoming }
        val sent = friendships.filter { !it.accepted && !it.incoming }
        val friends = friendships.filter { it.accepted }
        if (incoming.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.friends_requests)) }
            items(incoming, key = { it.id }) { f ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(f.otherName, Modifier.weight(1f))
                    IconButton(onClick = { vm.acceptFriend(f) }) { Icon(Icons.Filled.Check, stringResource(R.string.friends_accept)) }
                    IconButton(onClick = { vm.removeFriend(f) }) { Icon(Icons.Filled.Close, stringResource(R.string.friends_decline)) }
                }
            }
        }
        item { SectionTitle(stringResource(R.string.friends_list, friends.size)) }
        if (friends.isEmpty()) item { Text(stringResource(R.string.friends_empty)) }
        items(friends, key = { it.id }) { f ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(f.otherName, Modifier.weight(1f))
                IconButton(onClick = { vm.removeFriend(f) }) { Icon(Icons.Filled.Close, stringResource(R.string.friends_remove)) }
            }
        }
        if (sent.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.friends_sent)) }
            items(sent, key = { it.id }) { f -> Text(f.otherName) }
        }
        item { ProfileSection(vm, profile) }
    }
}

@Composable
private fun ProfileSection(vm: OnlineViewModel, profile: Profile?) {
    if (profile == null) return
    var name by remember(profile.uid) { mutableStateOf(profile.name) }
    var university by remember(profile.uid) { mutableStateOf(profile.university) }
    var course by remember(profile.uid) { mutableStateOf(profile.course) }
    var level by remember(profile.uid) { mutableStateOf(profile.level) }
    val levels = listOf(
        "bachelor" to R.string.level_bachelor,
        "master" to R.string.level_master,
        "phd" to R.string.level_phd,
    )
    Column(Modifier.padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(stringResource(R.string.profile_title))
        OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.profile_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(university, { university = it }, label = { Text(stringResource(R.string.profile_university)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(course, { course = it }, label = { Text(stringResource(R.string.profile_course)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            levels.forEach { (key, label) ->
                FilterChip(level == key, { level = key }, { Text(stringResource(label)) })
            }
        }
        Row {
            Button(enabled = name.isNotBlank(), onClick = { vm.saveProfile(name, university, course, level) }) {
                Text(stringResource(R.string.save))
            }
            TextButton(onClick = vm::signOut) { Text(stringResource(R.string.auth_sign_out)) }
        }
    }
}

@Composable
private fun GroupsScreen(vm: OnlineViewModel, onOpen: (String) -> Unit) {
    val groups by vm.groups.collectAsStateWithLifecycle()
    var creating by remember { mutableStateOf(false) }
    var joinCode by remember { mutableStateOf("") }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 16.dp)) {
                    OutlinedTextField(
                        joinCode, { joinCode = it.uppercase().take(8) },
                        label = { Text(stringResource(R.string.groups_join_code)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(enabled = joinCode.length == 8, onClick = { vm.joinGroup(joinCode); joinCode = "" }) {
                        Icon(Icons.AutoMirrored.Filled.Send, stringResource(R.string.groups_join))
                    }
                }
            }
            if (groups.isEmpty()) item { Text(stringResource(R.string.groups_empty)) }
            items(groups, key = { it.id }) { g ->
                Card(Modifier.fillMaxWidth().clickable { onOpen(g.id) }) {
                    Column(Modifier.padding(12.dp)) {
                        Text(g.name, style = MaterialTheme.typography.titleMedium)
                        if (g.description.isNotBlank()) Text(g.description, style = MaterialTheme.typography.bodySmall)
                        Text(stringResource(R.string.groups_members, g.memberCount), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
        FloatingActionButton(
            onClick = { creating = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        ) { Icon(Icons.Filled.Add, stringResource(R.string.groups_create)) }
    }

    if (creating) {
        var name by remember { mutableStateOf("") }
        var description by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { creating = false },
            title = { Text(stringResource(R.string.groups_create)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.groups_name)) }, singleLine = true)
                    OutlinedTextField(description, { description = it }, label = { Text(stringResource(R.string.groups_description)) })
                }
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = { vm.createGroup(name, description); creating = false }) {
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = { TextButton(onClick = { creating = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun GroupScreen(vm: OnlineViewModel, group: Group, onBack: () -> Unit) {
    val context = LocalContext.current
    val posts by remember(group.id) { vm.posts(group.id) }.collectAsState(initial = emptyList())
    var text by remember { mutableStateOf("") }
    var link by remember { mutableStateOf("") }
    var confirmLeave by remember { mutableStateOf(false) }
    val shareText = stringResource(R.string.groups_share_text, group.name, group.inviteCode)

    LaunchedEffect(group.id) { vm.clearMessages() }

    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
            Column(Modifier.weight(1f)) {
                Text(group.name, style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.groups_code, group.inviteCode), style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = {
                context.startActivity(
                    Intent.createChooser(
                        Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, shareText),
                        null,
                    ),
                )
            }) { Icon(Icons.Filled.Share, stringResource(R.string.groups_invite)) }
        }
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            OutlinedTextField(text, { text = it }, label = { Text(stringResource(R.string.post_text)) }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                link, { link = it },
                label = { Text(stringResource(R.string.post_link)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                enabled = text.isNotBlank() || link.isNotBlank(),
                onClick = { vm.addPost(group.id, text, link); text = ""; link = "" },
            ) { Text(stringResource(R.string.post_share)) }
        }
        LazyColumn(
            Modifier.weight(1f).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (posts.isEmpty()) item { Text(stringResource(R.string.posts_empty), Modifier.padding(top = 16.dp)) }
            items(posts, key = { it.id }) { p ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("${p.authorName} · ${formatDateTime(p.createdAt)}", style = MaterialTheme.typography.labelMedium)
                        if (p.text.isNotBlank()) Text(p.text)
                        if (p.link.isNotBlank()) {
                            Text(
                                p.link,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable {
                                    val url = if (p.link.startsWith("http")) p.link else "https://${p.link}"
                                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                                },
                            )
                        }
                    }
                }
            }
            item { TextButton(onClick = { confirmLeave = true }) { Text(stringResource(R.string.groups_leave)) } }
        }
    }

    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text(stringResource(R.string.groups_leave_title, group.name)) },
            confirmButton = {
                TextButton(onClick = { vm.leaveGroup(group.id); confirmLeave = false; onBack() }) {
                    Text(stringResource(R.string.groups_leave))
                }
            },
            dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
