package com.uniplanner.app.online

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uniplanner.app.R
import kotlinx.coroutines.launch

private const val PIN_LENGTH = 6

/**
 * Asks for the PIN that protects the chats, like WhatsApp's backup PIN: a new one the first time,
 * or the existing one on a new phone or after clearing the app's data. Chats wait until it is done,
 * so nothing is ever sent unencrypted.
 */
@Composable
fun E2ePinPrompt() {
    val state by ChatCrypto.state.collectAsStateWithLifecycle()
    var startOver by remember { mutableStateOf(false) }
    when {
        state == E2eState.NEEDS_NEW_PIN || (state == E2eState.NEEDS_PIN && startOver) -> NewPin(lostOld = startOver)
        state == E2eState.NEEDS_PIN -> EnterPin(onForgot = { startOver = true })
    }
}

@Composable
private fun PinField(value: String, label: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onChange(v.filter(Char::isDigit).take(PIN_LENGTH)) },
        label = { Text(label) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun NewPin(lostOld: Boolean) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var pin by remember { mutableStateOf("") }
    var again by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val ready = pin.length == PIN_LENGTH && pin == again && !busy
    AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(R.string.e2e_new_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(if (lostOld) R.string.e2e_new_lost else R.string.e2e_new_text))
                PinField(pin, stringResource(R.string.e2e_pin)) { pin = it }
                PinField(again, stringResource(R.string.e2e_pin_again)) { again = it }
                if (again.length == PIN_LENGTH && again != pin) {
                    Text(stringResource(R.string.e2e_pin_mismatch), color = MaterialTheme.colorScheme.error)
                }
                if (failed) Text(stringResource(R.string.e2e_offline), color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(enabled = ready, onClick = {
                busy = true
                failed = false
                scope.launch {
                    failed = runCatching { ChatCrypto.createWithPin(ctx, pin) }.isFailure
                    busy = false
                }
            }) {
                if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text(stringResource(R.string.e2e_create))
            }
        },
    )
}

@Composable
private fun EnterPin(onForgot: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var pin by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var wrong by remember { mutableStateOf(false) }
    var confirmForgot by remember { mutableStateOf(false) }
    if (confirmForgot) {
        AlertDialog(
            onDismissRequest = { confirmForgot = false },
            title = { Text(stringResource(R.string.e2e_forgot_title)) },
            text = { Text(stringResource(R.string.e2e_forgot_text)) },
            confirmButton = { TextButton(onClick = onForgot) { Text(stringResource(R.string.e2e_forgot_yes)) } },
            dismissButton = { TextButton(onClick = { confirmForgot = false }) { Text(stringResource(R.string.e2e_back)) } },
        )
        return
    }
    AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(R.string.e2e_enter_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.e2e_enter_text))
                PinField(pin, stringResource(R.string.e2e_pin)) { pin = it; wrong = false }
                if (wrong) Text(stringResource(R.string.e2e_wrong), color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(enabled = pin.length == PIN_LENGTH && !busy, onClick = {
                busy = true
                scope.launch {
                    wrong = !(runCatching { ChatCrypto.restoreWithPin(ctx, pin) }.getOrDefault(false))
                    busy = false
                }
            }) {
                if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text(stringResource(R.string.e2e_unlock))
            }
        },
        dismissButton = { TextButton(onClick = { confirmForgot = true }) { Text(stringResource(R.string.e2e_forgot)) } },
    )
}
