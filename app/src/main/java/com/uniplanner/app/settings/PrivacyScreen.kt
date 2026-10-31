package com.uniplanner.app.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.CreateDocument
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.uniplanner.app.R
import com.uniplanner.app.online.AccountData
import com.uniplanner.app.online.CloudBackup
import com.uniplanner.app.ui.screens.ScreenHeader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Settings → Privacy and data: the policy, health consent, export and delete (RGPD). */
@Composable
fun PrivacyScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var health by remember { mutableStateOf(CloudBackup.healthInCloud(ctx)) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<Int?>(null) }
    var showPolicy by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<Int?>(null) }

    fun run(action: suspend () -> Int?) {
        scope.launch {
            busy = true
            message = try { action() } catch (e: CancellationException) { throw e } catch (_: Exception) { R.string.privacy_failed }
            busy = false
        }
    }

    val export = rememberLauncherForActivityResult(CreateDocument("application/json")) { uri ->
        if (uri != null) run { AccountData.export(ctx, uri); R.string.privacy_exported }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScreenHeader(stringResource(R.string.privacy_title))
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        Text(stringResource(R.string.privacy_intro), style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(onClick = { showPolicy = true }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.privacy_policy))
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text(stringResource(R.string.privacy_health_title), style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(R.string.privacy_health_text),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = health, onCheckedChange = { health = it; CloudBackup.setHealthInCloud(ctx, it) })
        }

        Text(
            stringResource(R.string.privacy_export_text),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(enabled = !busy, onClick = { export.launch("uniplanner-dados.json") }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.privacy_export))
        }
        message?.let { Text(stringResource(it), style = MaterialTheme.typography.bodyMedium) }

        OutlinedButton(enabled = !busy, onClick = { confirm = R.string.privacy_wipe_phone_confirm }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.privacy_wipe_phone))
        }
        Button(
            enabled = !busy,
            onClick = { confirm = R.string.privacy_delete_account_confirm },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.fillMaxWidth().padding(bottom = 120.dp),
        ) {
            Text(stringResource(R.string.privacy_delete_account))
        }
    }

    confirm?.let { text ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            text = { Text(stringResource(text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirm = null
                    if (text == R.string.privacy_wipe_phone_confirm) {
                        AccountData.wipePhone(ctx)
                    } else {
                        run {
                            when (AccountData.deleteAccount(ctx)) {
                                AccountData.DeleteResult.SIGN_IN_AGAIN -> R.string.privacy_delete_sign_in_again
                                else -> null
                            }
                        }
                    }
                }) { Text(stringResource(R.string.privacy_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (showPolicy) PrivacyPolicyDialog { showPolicy = false }
}

@Composable
fun PrivacyPolicyDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.privacy_policy)) },
        text = {
            Text(
                stringResource(R.string.privacy_policy_text),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.verticalScroll(rememberScrollState()),
            )
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) } },
    )
}
