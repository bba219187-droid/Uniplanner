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

@Composable
fun OnlineMessages(vm: OnlineViewModel) {
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
                OnlineNotice.FILE_TOO_BIG -> R.string.chat_file_too_big
                OnlineNotice.NO_LOCATION -> R.string.chat_no_location
                OnlineNotice.NO_APP_TO_OPEN -> R.string.chat_no_app
                OnlineNotice.NOT_DOWNLOADED -> R.string.chat_not_downloaded
                OnlineNotice.NOT_READABLE -> R.string.chat_not_readable
                OnlineNotice.SIGN_OUT_OFFLINE -> R.string.sign_out_offline
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
fun SignInScreen(vm: OnlineViewModel) {
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
fun ProfileSection(vm: OnlineViewModel, profile: Profile?) {
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
