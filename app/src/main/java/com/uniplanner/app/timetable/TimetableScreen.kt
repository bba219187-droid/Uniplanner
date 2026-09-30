package com.uniplanner.app.timetable

import android.net.Uri
import android.text.format.DateUtils
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uniplanner.app.R
import com.uniplanner.app.domain.ClassSlot
import com.uniplanner.app.domain.ImportedClass
import com.uniplanner.app.domain.Timetable
import com.uniplanner.app.health.PlanImport
import com.uniplanner.app.ui.screens.ScreenHeader
import com.uniplanner.app.ui.theme.Mono
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.TextStyle
import java.util.Locale
import java.util.UUID

private fun dayLabel(day: Int, short: Boolean = false): String =
    Timetable.dayOfWeek(day).getDisplayName(if (short) TextStyle.SHORT else TextStyle.FULL, Locale.getDefault())
        .replaceFirstChar { it.uppercase() }

/** The weekly timetable: the next class, each day's classes, and where it is read from. */
@Composable
fun TimetableScreen(onOpenPortal: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by TimetableStore.flow(context).collectAsStateWithLifecycle()
    val s = state ?: TimetableState()
    var day by rememberSaveable { mutableIntStateOf(LocalDate.now().dayOfWeek.value) }
    var editing by remember { mutableStateOf<ClassSlot?>(null) }
    var importing by remember { mutableStateOf(false) }
    var askingLink by remember { mutableStateOf(false) }
    var settings by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var preview by remember { mutableStateOf<List<ImportedClass>?>(null) }
    val nothingFound = stringResource(R.string.timetable_nothing_found)
    val failed = stringResource(R.string.timetable_failed)

    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) scope.launch {
            busy = true
            val found = runCatching { GridImport.read(context, uri) }.getOrDefault(emptyList())
                .ifEmpty { runCatching { Timetable.fromText(PlanImport.readText(context, uri)) }.getOrDefault(emptyList()) }
            busy = false
            if (found.isEmpty()) message = nothingFound else preview = found
        }
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { ScreenHeader(stringResource(R.string.timetable_title)) }
                if (s.source == TimetableSource.PORTAL || s.source == TimetableSource.CALENDAR) {
                    IconButton(enabled = !busy, onClick = {
                        scope.launch {
                            busy = true
                            val ok = runCatching { TimetableSync.refresh(context) }.getOrNull()
                            busy = false
                            if (ok == null) message = failed
                        }
                    }) { Icon(Icons.Filled.Refresh, stringResource(R.string.timetable_refresh)) }
                }
                IconButton(onClick = { settings = true }) { Icon(Icons.Filled.NotificationsActive, stringResource(R.string.timetable_reminders)) }
                IconButton(onClick = { editing = ClassSlot(UUID.randomUUID().toString(), day, 9 * 60, 11 * 60, "", "") }) {
                    Icon(Icons.Filled.Add, stringResource(R.string.timetable_add))
                }
            }
        }
        if (busy) item { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
        if (s.needsLogin) {
            item {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.errorContainer).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(stringResource(R.string.timetable_login_text), color = MaterialTheme.colorScheme.onErrorContainer)
                    Button(onClick = onOpenPortal) { Text(stringResource(R.string.timetable_open_portal)) }
                }
            }
        }
        if (s.slots.isEmpty()) {
            item {
                Text(stringResource(R.string.timetable_empty), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item { ImportOptions(onPortal = onOpenPortal, onLink = { askingLink = true }, onFile = { pickFile.launch(arrayOf("image/*", "application/pdf")) }) }
            return@LazyColumn
        }
        Timetable.upcoming(s.slots, LocalDateTime.now())?.let { (slot, at) ->
            item { NextClass(slot, at) }
        }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val days = (1..5) + s.slots.map { it.day }.filter { it > 5 }.distinct().sorted()
                days.forEach { d ->
                    FilterChip(selected = d == day, onClick = { day = d }, label = { Text(dayLabel(d, short = true)) })
                }
            }
        }
        val today = s.slots.filter { it.day == day }
        if (today.isEmpty()) {
            item { Text(stringResource(R.string.timetable_free_day), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        items(today, key = { it.id }) { slot -> ClassRow(slot) { editing = slot } }
        item {
            val source = when (s.source) {
                TimetableSource.PORTAL -> stringResource(R.string.timetable_from_portal)
                TimetableSource.CALENDAR -> stringResource(R.string.timetable_from_calendar)
                TimetableSource.PHOTO -> stringResource(R.string.timetable_from_photo)
                TimetableSource.NONE -> stringResource(R.string.timetable_by_hand)
            }
            val synced = if (s.syncedAt > 0) " · " + DateUtils.getRelativeTimeSpanString(s.syncedAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS) else ""
            Text(source + synced, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            TextButton(onClick = { importing = true }) { Text(stringResource(R.string.timetable_import_again)) }
            Spacer(Modifier.size(24.dp))
        }
    }

    editing?.let { slot ->
        ClassDialog(
            slot = slot,
            isNew = s.slots.none { it.id == slot.id },
            onSave = { TimetableStore.save(context, it); editing = null },
            onDelete = { TimetableStore.delete(context, slot); editing = null },
            onDismiss = { editing = null },
        )
    }
    if (importing) {
        AlertDialog(
            onDismissRequest = { importing = false },
            title = { Text(stringResource(R.string.timetable_import_title)) },
            text = {
                ImportOptions(
                    onPortal = { importing = false; onOpenPortal() },
                    onLink = { importing = false; askingLink = true },
                    onFile = { importing = false; pickFile.launch(arrayOf("image/*", "application/pdf")) },
                )
            },
            confirmButton = { TextButton(onClick = { importing = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (askingLink) {
        var link by remember { mutableStateOf(if (s.source == TimetableSource.CALENDAR) s.link else "") }
        AlertDialog(
            onDismissRequest = { askingLink = false },
            title = { Text(stringResource(R.string.timetable_link_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.timetable_link_help), style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(link, { link = it }, singleLine = true, label = { Text("https://…") })
                }
            },
            confirmButton = {
                TextButton(enabled = link.isNotBlank(), onClick = {
                    askingLink = false
                    val url = link.trim()
                    scope.launch {
                        busy = true
                        val found = runCatching { TimetableSync.readCalendar(url) }.getOrNull()
                        busy = false
                        when {
                            found == null -> message = failed
                            found.isEmpty() -> message = nothingFound
                            else -> TimetableStore.imported(context, found, TimetableSource.CALENDAR, url)
                        }
                    }
                }) { Text(stringResource(R.string.timetable_import)) }
            },
            dismissButton = { TextButton(onClick = { askingLink = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    preview?.let { found ->
        AlertDialog(
            onDismissRequest = { preview = null },
            title = { Text(stringResource(R.string.timetable_check_title, found.size)) },
            text = {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(found) { c ->
                        Text("${dayLabel(c.day, true)} ${Timetable.time(c.start)}–${Timetable.time(c.end)} · ${c.course}" +
                            if (c.room.isNotBlank()) " · ${c.room}" else "")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    TimetableStore.imported(context, found, TimetableSource.PHOTO, "")
                    preview = null
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { preview = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (settings) {
        var on by remember { mutableStateOf(s.remindersOn) }
        var minutes by remember { mutableIntStateOf(s.minutesBefore) }
        AlertDialog(
            onDismissRequest = { settings = false },
            title = { Text(stringResource(R.string.timetable_reminders)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.timetable_reminders_on), Modifier.weight(1f))
                        Switch(on, { on = it })
                    }
                    Text(stringResource(R.string.timetable_minutes_before))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(5, 10, 15, 30, 60).forEach { m ->
                            FilterChip(selected = minutes == m, enabled = on, onClick = { minutes = m }, label = { Text("$m min") })
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { TimetableStore.setReminders(context, on, minutes); settings = false }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { settings = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    message?.let {
        AlertDialog(
            onDismissRequest = { message = null },
            text = { Text(it) },
            confirmButton = { TextButton(onClick = { message = null }) { Text(stringResource(R.string.ok)) } },
        )
    }
}

@Composable
private fun ImportOptions(onPortal: () -> Unit, onLink: () -> Unit, onFile: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ImportOption(Icons.Filled.Language, stringResource(R.string.timetable_opt_portal), stringResource(R.string.timetable_opt_portal_detail), onPortal)
        ImportOption(Icons.Filled.CalendarMonth, stringResource(R.string.timetable_opt_link), stringResource(R.string.timetable_opt_link_detail), onLink)
        ImportOption(Icons.Filled.PhotoCamera, stringResource(R.string.timetable_opt_photo), stringResource(R.string.timetable_opt_photo_detail), onFile)
    }
}

@Composable
private fun ImportOption(icon: ImageVector, title: String, detail: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
        Column(Modifier.padding(start = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun NextClass(slot: ClassSlot, at: LocalDateTime) {
    val now = LocalDateTime.now()
    val going = !at.isAfter(now)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.inverseSurface).padding(18.dp),
    ) {
        val label = when {
            going -> stringResource(R.string.timetable_now)
            at.toLocalDate() == now.toLocalDate() -> stringResource(R.string.timetable_next_today, Timetable.time(slot.start))
            else -> stringResource(R.string.timetable_next_on, dayLabel(slot.day), Timetable.time(slot.start))
        }
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.inversePrimary)
        Text(slot.course, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.inverseOnSurface)
        Text(
            listOf(slot.room, slot.group).filter { it.isNotBlank() }.joinToString(" · ").ifEmpty { stringResource(R.string.class_no_room) },
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.8f),
        )
    }
}

@Composable
private fun ClassRow(slot: ClassSlot, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.width(56.dp)) {
            Text(Timetable.time(slot.start), fontFamily = Mono, style = MaterialTheme.typography.titleMedium)
            Text(Timetable.time(slot.end), fontFamily = Mono, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(Modifier.weight(1f).padding(start = 8.dp)) {
            Text(slot.course, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                listOf(slot.room, slot.group).filter { it.isNotBlank() }.joinToString(" · ").ifEmpty { stringResource(R.string.class_no_room) },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (slot.edited || slot.key == null) {
            Text(stringResource(R.string.timetable_mine), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
        }
    }
}

@Composable
private fun ClassDialog(slot: ClassSlot, isNew: Boolean, onSave: (ClassSlot) -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
    var course by remember { mutableStateOf(slot.course) }
    var room by remember { mutableStateOf(slot.room) }
    var group by remember { mutableStateOf(slot.group) }
    var day by remember { mutableIntStateOf(slot.day) }
    var start by remember { mutableStateOf(Timetable.time(slot.start)) }
    var end by remember { mutableStateOf(Timetable.time(slot.end)) }
    val s = Timetable.parseTime(start)
    val e = Timetable.parseTime(end)
    val valid = course.isNotBlank() && s != null && e != null && e > s
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (isNew) R.string.timetable_add else R.string.timetable_edit)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(course, { course = it }, label = { Text(stringResource(R.string.timetable_course)) }, singleLine = true)
                OutlinedTextField(room, { room = it }, label = { Text(stringResource(R.string.timetable_room)) }, singleLine = true)
                OutlinedTextField(group, { group = it }, label = { Text(stringResource(R.string.timetable_group)) }, singleLine = true)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    (1..7).forEach { d -> FilterChip(selected = d == day, onClick = { day = d }, label = { Text(dayLabel(d, true)) }) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(start, { start = it }, label = { Text(stringResource(R.string.timetable_start)) }, singleLine = true, isError = s == null, modifier = Modifier.weight(1f))
                    OutlinedTextField(end, { end = it }, label = { Text(stringResource(R.string.timetable_end)) }, singleLine = true, isError = e == null || (s != null && e <= s), modifier = Modifier.weight(1f))
                }
                if (!isNew) {
                    TextButton(onClick = onDelete) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                onSave(slot.copy(day = day, start = s!!, end = e!!, course = course.trim(), room = room.trim(), group = group.trim()))
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
