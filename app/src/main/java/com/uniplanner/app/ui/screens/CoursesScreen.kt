package com.uniplanner.app.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import com.uniplanner.app.ui.theme.Card
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uniplanner.app.R
import com.uniplanner.app.data.Course
import com.uniplanner.app.ui.AppViewModel

@Composable
fun CoursesScreen(vm: AppViewModel) {
    val courses by vm.courses.collectAsStateWithLifecycle()
    var adding by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<Course?>(null) }

    Box(Modifier.fillMaxSize()) {
        if (courses.isEmpty()) {
            EmptyState(stringResource(R.string.courses_empty))
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item { ScreenHeader(stringResource(R.string.tab_courses), stringResource(R.string.courses_subtitle, courses.size)) }
                items(courses, key = { it.id }) { course ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            ColorDot(course.color)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(course.name, style = MaterialTheme.typography.titleMedium)
                                val details = listOfNotNull(
                                    course.teacher.takeIf { it.isNotBlank() },
                                    if (course.credits > 0) stringResource(R.string.course_credits, course.credits) else null,
                                ).joinToString(" · ")
                                if (details.isNotEmpty()) Text(details, style = MaterialTheme.typography.bodySmall)
                            }
                            IconButton(onClick = { toDelete = course }) {
                                Icon(Icons.Filled.Delete, stringResource(R.string.delete))
                            }
                        }
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { adding = true },
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text(stringResource(R.string.course_add)) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }

    if (adding) {
        AddCourseDialog(
            nextColor = CourseColors[courses.size % CourseColors.size],
            onDismiss = { adding = false },
            onSave = { name, teacher, credits, color ->
                vm.addCourse(name, teacher, credits, color)
                adding = false
            },
        )
    }
    toDelete?.let { course ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text(stringResource(R.string.course_delete_title, course.name)) },
            text = { Text(stringResource(R.string.course_delete_text)) },
            confirmButton = {
                TextButton(onClick = { vm.deleteCourse(course); toDelete = null }) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun AddCourseDialog(
    nextColor: Long,
    onDismiss: () -> Unit,
    onSave: (String, String, Int, Long) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var teacher by remember { mutableStateOf("") }
    var credits by remember { mutableStateOf("") }
    var color by remember { mutableLongStateOf(nextColor) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.course_add)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.course_name)) }, singleLine = true)
                OutlinedTextField(teacher, { teacher = it }, label = { Text(stringResource(R.string.course_teacher)) }, singleLine = true)
                OutlinedTextField(
                    credits,
                    { credits = it.filter(Char::isDigit).take(3) },
                    label = { Text(stringResource(R.string.course_credits_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CourseColors.forEach { c ->
                        Box(
                            Modifier
                                .size(24.dp)
                                .border(
                                    width = if (c == color) 3.dp else 0.dp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    shape = CircleShape,
                                )
                                .padding(3.dp)
                                .clickable { color = c },
                        ) { ColorDot(c, Modifier.fillMaxSize()) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = { onSave(name, teacher, credits.toIntOrNull() ?: 0, color) },
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
