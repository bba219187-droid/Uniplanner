package com.uniplanner.app.moodle

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.uniplanner.app.R
import com.uniplanner.app.ui.screens.formatDateTime

@Composable
fun MoodleScreen(onOpenWeb: (calendarPage: Boolean) -> Unit, vm: MoodleViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        val account = state.account
        val webSite = state.webSite
        when {
            account != null -> MoodleHome(state, account, vm, onOpenWeb)
            webSite != null -> MoodleWebHome(state, webSite, vm, onOpenWeb)
            else -> MoodleLogin(state, vm)
        }
    }

    state.error?.let { code ->
        AlertDialog(
            onDismissRequest = vm::clearMessage,
            title = { Text(stringResource(R.string.moodle_error_title)) },
            text = { Text(moodleErrorText(code, state.errorDetail)) },
            confirmButton = { TextButton(onClick = vm::clearMessage) { Text(stringResource(R.string.ok)) } },
        )
    }
    state.message?.let { message ->
        val text = when (message) {
            is MoodleMessage.Imported ->
                stringResource(R.string.moodle_imported, message.result.deadlines, message.result.courses)
            is MoodleMessage.Submitted ->
                stringResource(if (message.final) R.string.moodle_submitted else R.string.moodle_saved_draft, message.name)
        }
        AlertDialog(
            onDismissRequest = vm::clearMessage,
            text = { Text(text) },
            confirmButton = { TextButton(onClick = vm::clearMessage) { Text(stringResource(R.string.ok)) } },
        )
    }
}

@Composable
private fun moodleErrorText(code: String, detail: String?): String = when (code) {
    "invalidlogin" -> stringResource(R.string.moodle_err_login)
    "enablewsdescription", "servicenotavailable", "webservicesnotenabled" -> stringResource(R.string.moodle_err_disabled)
    "notmoodle" -> stringResource(R.string.moodle_err_notmoodle)
    "network" -> stringResource(R.string.moodle_err_network)
    "invalidtoken" -> stringResource(R.string.moodle_err_token)
    "ssofailed" -> stringResource(R.string.moodle_err_sso)
    "badcalendar", "notcalendar" -> stringResource(R.string.moodle_err_calendar)
    else -> stringResource(R.string.moodle_err_other, detail ?: code)
}

@Composable
private fun MoodleLogin(state: MoodleUiState, vm: MoodleViewModel) {
    val context = LocalContext.current
    var site by rememberSaveable { mutableStateOf("") }
    var user by rememberSaveable { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }

    LaunchedEffect(state.openUrl) {
        state.openUrl?.let { url ->
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            vm.urlOpened()
        }
    }

    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.moodle_connect_title), style = MaterialTheme.typography.headlineSmall)
        val formSite = state.formLoginSite
        val browser = state.browserLogin
        when {
            browser != null -> {
                Text(browser.siteName.ifEmpty { browser.siteUrl }, style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.moodle_sso_help), style = MaterialTheme.typography.bodyMedium)
                Button(
                    enabled = !state.loading,
                    onClick = vm::openUniversityLogin,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.moodle_sso_button)) }
                Text(
                    stringResource(R.string.moodle_sso_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                WebMoodleOption(state, vm)
                TextButton(onClick = vm::changeSite) { Text(stringResource(R.string.moodle_change_site)) }
            }
            formSite != null -> {
                Text(formSite.removePrefix("https://"), style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    user, { user = it },
                    label = { Text(stringResource(R.string.moodle_username)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    pass, { pass = it },
                    label = { Text(stringResource(R.string.moodle_password)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    enabled = !state.loading && user.isNotBlank() && pass.isNotEmpty(),
                    onClick = { vm.connect(formSite, user, pass) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.moodle_connect)) }
                Text(
                    stringResource(R.string.moodle_privacy),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                WebMoodleOption(state, vm)
                TextButton(onClick = vm::changeSite) { Text(stringResource(R.string.moodle_change_site)) }
            }
            else -> {
                Text(stringResource(R.string.moodle_connect_help), style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    site, { site = it },
                    label = { Text(stringResource(R.string.moodle_site)) },
                    placeholder = { Text("elearning.universidade.pt") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    enabled = !state.loading && site.isNotBlank(),
                    onClick = { vm.checkSite(site) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.moodle_continue)) }
            }
        }
    }
}

/** For universities where the Moodle app service is off: "O web service não está disponível". */
@Composable
private fun WebMoodleOption(state: MoodleUiState, vm: MoodleViewModel) {
    HorizontalDivider(Modifier.padding(vertical = 4.dp))
    Text(stringResource(R.string.moodle_web_option_help), style = MaterialTheme.typography.bodyMedium)
    OutlinedButton(enabled = !state.loading, onClick = vm::useWebMoodle, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.moodle_web_option))
    }
}

@Composable
private fun MoodleWebHome(state: MoodleUiState, site: String, vm: MoodleViewModel, onOpenWeb: (Boolean) -> Unit) {
    var pasted by rememberSaveable { mutableStateOf("") }
    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(site.removePrefix("https://"), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.moodle_web_help), style = MaterialTheme.typography.bodyMedium)
        Button(onClick = { onOpenWeb(false) }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.moodle_web_open))
        }

        HorizontalDivider(Modifier.padding(vertical = 4.dp))
        Text(stringResource(R.string.moodle_calendar_title), style = MaterialTheme.typography.titleMedium)
        if (state.hasCalendarLink) {
            Text(stringResource(R.string.moodle_calendar_on), style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(enabled = !state.loading, onClick = vm::syncCalendar, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.moodle_calendar_sync))
            }
            TextButton(onClick = { onOpenWeb(true) }) { Text(stringResource(R.string.moodle_calendar_relink)) }
        } else {
            Text(stringResource(R.string.moodle_calendar_help), style = MaterialTheme.typography.bodyMedium)
            Button(enabled = !state.loading, onClick = { onOpenWeb(true) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.moodle_calendar_get))
            }
            Text(
                stringResource(R.string.moodle_calendar_paste_help),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                pasted, { pasted = it },
                label = { Text(stringResource(R.string.moodle_calendar_link)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(
                enabled = !state.loading && pasted.isNotBlank(),
                onClick = { vm.saveCalendarLink(pasted) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.moodle_calendar_save)) }
        }

        HorizontalDivider(Modifier.padding(vertical = 4.dp))
        TextButton(onClick = vm::disconnect) { Text(stringResource(R.string.moodle_change_site)) }
    }
}

@Composable
private fun MoodleHome(state: MoodleUiState, account: MoodleAccount, vm: MoodleViewModel, onOpenWeb: (Boolean) -> Unit) {
    var pending by remember { mutableStateOf<AssignmentRow?>(null) }
    var pickedFile by remember { mutableStateOf<Uri?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) pickedFile = uri else pending = null
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Column(Modifier.padding(top = 16.dp)) {
                Text(account.siteName.ifEmpty { account.site }, style = MaterialTheme.typography.headlineSmall)
                Text(account.fullName, style = MaterialTheme.typography.bodyMedium)
            }
        }
        item {
            Row {
                Button(enabled = !state.loading && state.assignments.isNotEmpty(), onClick = vm::importDeadlines) {
                    Text(stringResource(R.string.moodle_import))
                }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(enabled = !state.loading, onClick = vm::refresh) {
                    Text(stringResource(R.string.moodle_refresh))
                }
            }
        }
        item {
            TextButton(onClick = { onOpenWeb(false) }) { Text(stringResource(R.string.moodle_web_open)) }
        }
        if (!state.loading && state.assignments.isEmpty()) {
            item { Text(stringResource(R.string.moodle_no_assignments)) }
        }
        items(state.assignments, key = { it.assignment.id }) { row ->
            AssignmentCard(row, enabled = !state.loading) {
                pending = row
                picker.launch(arrayOf("*/*"))
            }
        }
        item {
            TextButton(onClick = vm::disconnect) { Text(stringResource(R.string.moodle_disconnect)) }
        }
    }

    val row = pending
    val file = pickedFile
    if (row != null && file != null) {
        val close = { pending = null; pickedFile = null }
        AlertDialog(
            onDismissRequest = close,
            title = { Text(row.assignment.name) },
            text = { Text(stringResource(R.string.moodle_submit_question)) },
            confirmButton = {
                TextButton(onClick = { vm.submit(row, file, final = true); close() }) {
                    Text(stringResource(R.string.moodle_submit_final))
                }
            },
            dismissButton = {
                TextButton(onClick = { vm.submit(row, file, final = false); close() }) {
                    Text(stringResource(R.string.moodle_submit_draft))
                }
            },
        )
    }
}

@Composable
private fun AssignmentCard(row: AssignmentRow, enabled: Boolean, onSend: () -> Unit) {
    val a = row.assignment
    val overdue = a.dueAt != null && a.dueAt < System.currentTimeMillis()
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(a.name, style = MaterialTheme.typography.titleSmall)
            Text(row.courseName, style = MaterialTheme.typography.bodySmall)
            Text(
                a.dueAt?.let { stringResource(R.string.moodle_due, formatDateTime(it)) }
                    ?: stringResource(R.string.moodle_no_due),
                style = MaterialTheme.typography.bodySmall,
                color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                Text(
                    stringResource(
                        when (row.state) {
                            SubmissionState.SUBMITTED -> R.string.moodle_state_submitted
                            SubmissionState.DRAFT -> R.string.moodle_state_draft
                            SubmissionState.NEW -> R.string.moodle_state_new
                            SubmissionState.UNKNOWN -> R.string.moodle_state_unknown
                        },
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (row.state == SubmissionState.SUBMITTED) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (a.acceptsFiles && row.state != SubmissionState.SUBMITTED) {
                    Button(enabled = enabled, onClick = onSend) { Text(stringResource(R.string.moodle_send_file)) }
                }
            }
        }
    }
}
