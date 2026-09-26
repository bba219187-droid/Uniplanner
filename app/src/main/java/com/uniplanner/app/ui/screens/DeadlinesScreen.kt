package com.uniplanner.app.ui.screens

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uniplanner.app.R
import com.uniplanner.app.data.Course
import com.uniplanner.app.data.Deadline
import com.uniplanner.app.data.DeadlineType
import com.uniplanner.app.ui.AppViewModel
import java.util.concurrent.TimeUnit

@Composable
fun DeadlinesScreen(vm: AppViewModel) {
    val courses by vm.courses.collectAsStateWithLifecycle()
    val deadlines by vm.deadlines.collectAsStateWithLifecycle()
    var adding by remember { mutableStateOf(false) }
    var showDone by remember { mutableStateOf(false) }
    val byId = courses.associateBy { it.id }
    val visible = deadlines.filter { showDone || !it.done }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            Row(Modifier.padding(vertical = 8.dp)) {
                FilterChip(
                    selected = showDone,
                    onClick = { showDone = !showDone },
                    label = { Text(stringResource(R.string.deadlines_show_done)) },
                )
            }
            when {
                courses.isEmpty() -> EmptyState(stringResource(R.string.deadlines_need_course))
                visible.isEmpty() -> EmptyState(stringResource(R.string.deadlines_empty))
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(visible, key = { it.id }) { d ->
                        DeadlineCard(
                            deadline = d,
                            course = byId[d.courseId],
                            onDone = { vm.setDeadlineDone(d, it) },
                            onDelete = { vm.deleteDeadline(d) },
                        )
                    }
                }
            }
        }
        if (courses.isNotEmpty()) {
            FloatingActionButton(
                onClick = { adding = true },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            ) { Icon(Icons.Filled.Add, stringResource(R.string.deadline_add)) }
        }
    }

    if (adding) {
        AddDeadlineDialog(
            courses = courses,
            onDismiss = { adding = false },
            onSave = { vm.addDeadline(it); adding = false },
        )
    }
}

@Composable
private fun DeadlineCard(
    deadline: Deadline,
    course: Course?,
    onDone: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    val daysLeft = TimeUnit.MILLISECONDS.toDays(deadline.dueAt - System.currentTimeMillis())
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = deadline.done, onCheckedChange = onDone)
            course?.let { ColorDot(it.color, Modifier.padding(end = 8.dp)) }
            Column(Modifier.weight(1f)) {
                val kind = stringResource(
                    if (deadline.type == DeadlineType.TEST) R.string.type_test else R.string.type_assignment,
                )
                Text(
                    "$kind: ${deadline.title}",
                    style = MaterialTheme.typography.titleSmall,
                    textDecoration = if (deadline.done) TextDecoration.LineThrough else null,
                )
                Text(
                    listOfNotNull(course?.name, formatDateTime(deadline.dueAt)).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                )
                if (!deadline.done && daysLeft >= 0) {
                    Text(
                        pluralStringResource(R.plurals.deadline_days_left, daysLeft.toInt(), daysLeft.toInt()),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (daysLeft <= 3) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    )
                }
                if (deadline.weightPercent > 0) {
                    Text(stringResource(R.string.deadline_weight_value, deadline.weightPercent), style = MaterialTheme.typography.bodySmall)
                }
            }
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, stringResource(R.string.delete)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddDeadlineDialog(
    courses: List<Course>,
    onDismiss: () -> Unit,
    onSave: (Deadline) -> Unit,
) {
    val context = LocalContext.current
    var type by remember { mutableStateOf(DeadlineType.TEST) }
    var course by remember { mutableStateOf(courses.first()) }
    var courseMenu by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    var weight by remember { mutableStateOf("") }
    var dueAt by remember { mutableLongStateOf(System.currentTimeMillis() + TimeUnit.DAYS.toMillis(7)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.deadline_add)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(type == DeadlineType.TEST, { type = DeadlineType.TEST }, { Text(stringResource(R.string.type_test)) })
                    FilterChip(type == DeadlineType.ASSIGNMENT, { type = DeadlineType.ASSIGNMENT }, { Text(stringResource(R.string.type_assignment)) })
                }
                ExposedDropdownMenuBox(expanded = courseMenu, onExpandedChange = { courseMenu = it }) {
                    OutlinedTextField(
                        value = course.name,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.deadline_course)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(courseMenu) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable),
                    )
                    ExposedDropdownMenu(expanded = courseMenu, onDismissRequest = { courseMenu = false }) {
                        courses.forEach { c ->
                            DropdownMenuItem(text = { Text(c.name) }, onClick = { course = c; courseMenu = false })
                        }
                    }
                }
                OutlinedTextField(title, { title = it }, label = { Text(stringResource(R.string.deadline_title)) }, singleLine = true)
                OutlinedTextField(
                    weight,
                    { weight = it.filter(Char::isDigit).take(3) },
                    label = { Text(stringResource(R.string.deadline_weight)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                OutlinedButton(onClick = { pickDateTime(context, dueAt) { dueAt = it } }) {
                    Text(formatDateTime(dueAt))
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank(),
                onClick = {
                    onSave(
                        Deadline(
                            courseId = course.id,
                            type = type,
                            title = title.trim(),
                            dueAt = dueAt,
                            weightPercent = (weight.toIntOrNull() ?: 0).coerceIn(0, 100),
                        ),
                    )
                },
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
