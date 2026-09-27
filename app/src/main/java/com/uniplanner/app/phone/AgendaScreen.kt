package com.uniplanner.app.phone

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.uniplanner.app.R
import com.uniplanner.app.data.DeadlineType
import com.uniplanner.app.domain.StudySuggestion
import com.uniplanner.app.ui.screens.formatDateTime
import com.uniplanner.app.ui.screens.pickDateTime
import java.text.DateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Date

private enum class AgendaDialog { TARGET, SHOWN }

@Composable
fun AgendaScreen(vm: AgendaViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { vm.refresh() }
    var dialog by remember { mutableStateOf<AgendaDialog?>(null) }
    var editing by remember { mutableStateOf<PhoneEvent?>(null) }
    var creating by remember { mutableStateOf(false) }

    if (!state.permission) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.agenda_title), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.agenda_permission_help), style = MaterialTheme.typography.bodyMedium)
            Button(onClick = { ask.launch(PhoneCalendar.permissions) }) { Text(stringResource(R.string.agenda_permission_button)) }
        }
        return
    }

    val writable = state.calendars.filter { it.writable }
    val targetName = state.calendars.firstOrNull { it.id == state.target }?.name

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(
                    stringResource(R.string.agenda_title),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(top = 16.dp),
                )
                if (state.failed) Text(stringResource(R.string.agenda_failed), color = MaterialTheme.colorScheme.error)
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.agenda_mirror), style = MaterialTheme.typography.titleSmall)
                                Text(
                                    targetName?.let { stringResource(R.string.agenda_target_is, it) }
                                        ?: stringResource(R.string.agenda_target_none),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            Switch(
                                checked = state.mirror,
                                onCheckedChange = { on ->
                                    if (on && state.target == null) dialog = AgendaDialog.TARGET
                                    else vm.setTarget(state.target, on)
                                },
                            )
                        }
                        Row {
                            TextButton(onClick = { dialog = AgendaDialog.TARGET }) { Text(stringResource(R.string.agenda_choose_target)) }
                            TextButton(onClick = { dialog = AgendaDialog.SHOWN }) { Text(stringResource(R.string.agenda_choose_shown)) }
                        }
                    }
                }
            }

            item { SectionHeader(stringResource(R.string.agenda_suggestions)) }
            if (state.suggestions.isEmpty()) {
                item { Text(stringResource(R.string.agenda_no_suggestions), style = MaterialTheme.typography.bodyMedium) }
            }
            items(state.suggestions, key = { "s${it.target.id}" }) { s ->
                SuggestionCard(s, canBook = state.target != null, onChooseTarget = { dialog = AgendaDialog.TARGET }) { slots ->
                    vm.book(s, slots)
                }
            }

            item { SectionHeader(stringResource(R.string.agenda_next_days)) }
            if (state.events.isEmpty()) {
                item { Text(stringResource(R.string.agenda_no_events), style = MaterialTheme.typography.bodyMedium) }
            }
            val zone = ZoneId.systemDefault()
            val byDay = state.events.groupBy { Instant.ofEpochMilli(it.begin).atZone(zone).toLocalDate() }
            byDay.forEach { (day, events) ->
                item(key = "day$day") {
                    Text(
                        day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                items(events, key = { "e${it.eventId}_${it.begin}" }) { e ->
                    EventRow(e, mirrored = e.eventId in state.mirrored) { editing = e }
                }
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
        if (writable.isNotEmpty()) {
            FloatingActionButton(
                onClick = { creating = true },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            ) { Icon(Icons.Filled.Add, stringResource(R.string.agenda_add_event)) }
        }
    }

    when (dialog) {
        AgendaDialog.TARGET -> TargetDialog(writable, state.target, onDismiss = { dialog = null }) { id ->
            vm.setTarget(id, mirror = true)
            dialog = null
        }
        AgendaDialog.SHOWN -> ShownDialog(state.calendars.filter { it.visible }, state.shown, onDismiss = { dialog = null }) { ids ->
            vm.setShown(ids)
            dialog = null
        }
        null -> Unit
    }

    editing?.let { e ->
        val calendar = state.calendars.firstOrNull { it.id == e.calendarId }
        EventDialog(
            event = e,
            canEdit = calendar?.writable == true && !e.recurring && !e.allDay,
            courses = state.courses.map { it.id to it.name },
            linked = e.eventId in state.mirrored,
            onDismiss = { editing = null },
            onSave = { title, begin, end -> vm.updateEvent(e, title, begin, end); editing = null },
            onDelete = { vm.deleteEvent(e); editing = null },
            onAddDeadline = { courseId, type -> vm.addAsDeadline(e, courseId, type); editing = null },
        )
    }
    if (creating) {
        NewEventDialog(
            calendars = writable,
            initialCalendar = state.target ?: writable.firstOrNull()?.id,
            onDismiss = { creating = false },
            onSave = { calendarId, title, begin, end -> vm.addEvent(calendarId, title, begin, end); creating = false },
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
}

private fun time(millis: Long) = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(millis))

private fun dayAndTime(millis: Long) =
    DateTimeFormatter.ofPattern("EEE d/M, HH:mm").format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

@Composable
private fun EventRow(e: PhoneEvent, mirrored: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(4.dp).height(36.dp).background(Color(e.color or 0xFF000000.toInt()), RoundedCornerShape(2.dp)))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(e.title.ifBlank { "–" }, style = MaterialTheme.typography.bodyLarge, maxLines = 2)
            Text(
                listOfNotNull(
                    if (e.allDay) stringResource(R.string.agenda_all_day) else "${time(e.begin)} – ${time(e.end)}",
                    e.location.takeIf { it.isNotBlank() },
                    if (mirrored) stringResource(R.string.agenda_from_app) else null,
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SuggestionCard(s: StudySuggestion, canBook: Boolean, onChooseTarget: () -> Unit, onBook: (List<com.uniplanner.app.domain.Slot>) -> Unit) {
    val t = s.target
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val kind = stringResource(if (t.isTest) R.string.type_test else R.string.type_assignment)
            Text("$kind: ${t.title}", style = MaterialTheme.typography.titleSmall)
            Text(
                listOf(t.courseName, formatDateTime(t.dueAt)).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
            )
            if (s.booked > 0) Text(stringResource(R.string.agenda_booked, s.booked), style = MaterialTheme.typography.bodyMedium)
            s.startBy?.let {
                Text(stringResource(R.string.agenda_start_by, dayAndTime(it)), style = MaterialTheme.typography.bodyMedium)
            }
            if (s.short) {
                Text(
                    stringResource(R.string.agenda_short, s.sessions.size, s.needed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (s.sessions.isNotEmpty()) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    s.sessions.forEach { slot ->
                        FilterChip(
                            selected = false,
                            onClick = { if (canBook) onBook(listOf(slot)) else onChooseTarget() },
                            label = { Text(dayAndTime(slot.start)) },
                        )
                    }
                }
                OutlinedButton(onClick = { if (canBook) onBook(s.sessions) else onChooseTarget() }) {
                    Text(stringResource(if (canBook) R.string.agenda_book_all else R.string.agenda_choose_target))
                }
            }
        }
    }
}

@Composable
private fun TargetDialog(calendars: List<PhoneCalendarInfo>, current: Long?, onDismiss: () -> Unit, onPick: (Long) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.agenda_choose_target)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.agenda_target_help), style = MaterialTheme.typography.bodySmall)
                calendars.forEach { c ->
                    Row(Modifier.fillMaxWidth().clickable { onPick(c.id) }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = c.id == current, onClick = { onPick(c.id) })
                        Column {
                            Text(c.name)
                            Text(c.account, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun ShownDialog(calendars: List<PhoneCalendarInfo>, shown: Set<Long>?, onDismiss: () -> Unit, onSave: (Set<Long>?) -> Unit) {
    var picked by remember { mutableStateOf(shown ?: calendars.map { it.id }.toSet()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.agenda_choose_shown)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                calendars.forEach { c ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = c.id in picked, onCheckedChange = { on -> picked = if (on) picked + c.id else picked - c.id })
                        Column {
                            Text(c.name)
                            Text(c.account, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(if (picked.size == calendars.size) null else picked) }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun EventDialog(
    event: PhoneEvent,
    canEdit: Boolean,
    courses: List<Pair<Long, String>>,
    linked: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, Long, Long) -> Unit,
    onDelete: () -> Unit,
    onAddDeadline: (Long, DeadlineType) -> Unit,
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf(event.title) }
    var begin by remember { mutableLongStateOf(event.begin) }
    var end by remember { mutableLongStateOf(event.end) }
    var asDeadline by remember { mutableStateOf(false) }
    var courseId by remember { mutableStateOf(courses.firstOrNull()?.first) }
    var type by remember { mutableStateOf(if (looksLikeTest(event.title)) DeadlineType.TEST else DeadlineType.ASSIGNMENT) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (canEdit) stringResource(R.string.agenda_edit_event) else event.title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (canEdit) {
                    OutlinedTextField(title, { title = it }, label = { Text(stringResource(R.string.agenda_event_title)) })
                    OutlinedButton(onClick = {
                        pickDateTime(context, begin) { b -> end = b + (end - begin); begin = b }
                    }) { Text(stringResource(R.string.agenda_starts, formatDateTime(begin))) }
                    OutlinedButton(onClick = { pickDateTime(context, end) { e -> if (e > begin) end = e } }) {
                        Text(stringResource(R.string.agenda_ends, formatDateTime(end)))
                    }
                } else {
                    Text(if (event.allDay) stringResource(R.string.agenda_all_day) else "${formatDateTime(event.begin)} – ${time(event.end)}")
                    Text(stringResource(R.string.agenda_read_only), style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, PhoneCalendar.eventUri(event.eventId))) }
                }) { Text(stringResource(R.string.agenda_open_in_calendar)) }

                if (!linked && courses.isNotEmpty()) {
                    HorizontalDivider()
                    if (!asDeadline) {
                        TextButton(onClick = { asDeadline = true }) { Text(stringResource(R.string.agenda_to_deadline)) }
                    } else {
                        Text(stringResource(R.string.agenda_to_deadline), style = MaterialTheme.typography.titleSmall)
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(type == DeadlineType.TEST, { type = DeadlineType.TEST }, { Text(stringResource(R.string.type_test)) })
                            FilterChip(type == DeadlineType.ASSIGNMENT, { type = DeadlineType.ASSIGNMENT }, { Text(stringResource(R.string.type_assignment)) })
                        }
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            courses.forEach { (id, name) ->
                                FilterChip(courseId == id, { courseId = id }, { Text(name, maxLines = 1) })
                            }
                        }
                        Button(enabled = courseId != null, onClick = { onAddDeadline(courseId!!, type) }) {
                            Text(stringResource(R.string.agenda_add_to_deadlines))
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (canEdit) {
                TextButton(enabled = title.isNotBlank(), onClick = { onSave(title.trim(), begin, end) }) { Text(stringResource(R.string.save)) }
            } else {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) }
            }
        },
        dismissButton = {
            if (canEdit) TextButton(onClick = onDelete) { Text(stringResource(R.string.delete)) }
        },
    )
}

@Composable
private fun NewEventDialog(
    calendars: List<PhoneCalendarInfo>,
    initialCalendar: Long?,
    onDismiss: () -> Unit,
    onSave: (Long, String, Long, Long) -> Unit,
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf("") }
    val hour = 60 * 60 * 1000L
    var begin by remember { mutableLongStateOf((System.currentTimeMillis() / hour + 1) * hour) }
    var end by remember { mutableLongStateOf(begin + hour) }
    var calendarId by remember { mutableStateOf(initialCalendar) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.agenda_add_event)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text(stringResource(R.string.agenda_event_title)) })
                OutlinedButton(onClick = { pickDateTime(context, begin) { b -> end = b + (end - begin); begin = b } }) {
                    Text(stringResource(R.string.agenda_starts, formatDateTime(begin)))
                }
                OutlinedButton(onClick = { pickDateTime(context, end) { e -> if (e > begin) end = e } }) {
                    Text(stringResource(R.string.agenda_ends, formatDateTime(end)))
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    calendars.forEach { c -> FilterChip(calendarId == c.id, { calendarId = c.id }, { Text(c.name, maxLines = 1) }) }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = title.isNotBlank() && calendarId != null, onClick = { onSave(calendarId!!, title.trim(), begin, end) }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

private val testWords = Regex("\\b(teste|exame|frequência|frequencia|test|exam|quiz|prova)\\b", RegexOption.IGNORE_CASE)

private fun looksLikeTest(title: String) = testWords.containsMatchIn(title)
