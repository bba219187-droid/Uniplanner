package com.uniplanner.app.online

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uniplanner.app.R
import com.uniplanner.app.ui.screens.formatDateTime
import kotlinx.coroutines.launch

@Composable
fun BackupScreen(online: OnlineViewModel, onSignIn: () -> Unit) {
    val uid by online.uid.collectAsStateWithLifecycle()
    val lastSaved by CloudBackup.lastSaved.collectAsState()
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var confirmRestore by remember { mutableStateOf(false) }

    fun run(action: suspend () -> Unit) {
        scope.launch {
            busy = true
            failed = runCatching { action() }.isFailure
            busy = false
        }
    }

    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        Text(stringResource(R.string.backup_title), style = MaterialTheme.typography.headlineSmall)
        if (!online.configured || uid == null) {
            Text(stringResource(R.string.backup_signed_out), style = MaterialTheme.typography.bodyMedium)
            if (online.configured) Button(onClick = onSignIn) { Text(stringResource(R.string.backup_sign_in)) }
            return@Column
        }
        Text(stringResource(R.string.backup_help), style = MaterialTheme.typography.bodyMedium)
        Text(
            lastSaved?.let { stringResource(R.string.backup_last_saved, formatDateTime(it)) }
                ?: stringResource(R.string.backup_never),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(enabled = !busy, onClick = { run { CloudBackup.save() } }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.backup_save_now))
        }
        OutlinedButton(enabled = !busy, onClick = { confirmRestore = true }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.backup_restore))
        }
        if (failed) {
            Text(stringResource(R.string.backup_failed), color = MaterialTheme.colorScheme.error)
        }
    }

    if (confirmRestore) {
        AlertDialog(
            onDismissRequest = { confirmRestore = false },
            text = { Text(stringResource(R.string.backup_restore_confirm)) },
            confirmButton = {
                TextButton(onClick = { confirmRestore = false; run { CloudBackup.restore() } }) {
                    Text(stringResource(R.string.backup_restore))
                }
            },
            dismissButton = { TextButton(onClick = { confirmRestore = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** Shown when the account already has a copy and this phone has other data: the student picks one. */
@Composable
fun BackupChoicePrompt() {
    val choice by CloudBackup.choice.collectAsState()
    val c = choice ?: return
    AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(R.string.backup_choice_title)) },
        text = {
            Text(
                stringResource(
                    R.string.backup_choice_text,
                    c.device.ifEmpty { "?" },
                    if (c.savedAt > 0) formatDateTime(c.savedAt) else "?",
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = { CloudBackup.resolveChoice(useAccountCopy = true) }) {
                Text(stringResource(R.string.backup_choice_account))
            }
        },
        dismissButton = {
            TextButton(onClick = { CloudBackup.resolveChoice(useAccountCopy = false) }) {
                Text(stringResource(R.string.backup_choice_phone))
            }
        },
    )
}
