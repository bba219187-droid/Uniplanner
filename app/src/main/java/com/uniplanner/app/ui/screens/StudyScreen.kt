package com.uniplanner.app.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uniplanner.app.R
import com.uniplanner.app.reminders.formatMinutes
import com.uniplanner.app.ui.AppViewModel
import kotlinx.coroutines.delay

@Composable
fun StudyScreen(vm: AppViewModel) {
    val courses by vm.courses.collectAsStateWithLifecycle()
    val sessions by vm.studyThisWeek.collectAsStateWithLifecycle()

    if (courses.isEmpty()) {
        EmptyState(stringResource(R.string.study_need_course))
        return
    }

    var selectedId by rememberSaveable { mutableLongStateOf(courses.first().id) }
    // 0 means the timer is not running.
    var startedAt by rememberSaveable { mutableLongStateOf(0L) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var manual by remember { mutableStateOf(false) }

    LaunchedEffect(startedAt) {
        while (startedAt != 0L) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }

    val selected = courses.firstOrNull { it.id == selectedId } ?: courses.first()
    val byCourse = sessions.groupBy { it.courseId }.mapValues { (_, s) -> s.sumOf { it.minutes } }

    LazyColumn(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { SectionTitle(stringResource(R.string.study_pick_course), Modifier.padding(top = 8.dp)) }
        item {
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                courses.forEach { c ->
                    FilterChip(
                        selected = c.id == selected.id,
                        onClick = { if (startedAt == 0L) selectedId = c.id },
                        label = { Text(c.name, maxLines = 1) },
                    )
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val elapsed = if (startedAt == 0L) 0L else (now - startedAt) / 1000
                    Text(
                        "%02d:%02d:%02d".format(elapsed / 3600, elapsed / 60 % 60, elapsed % 60),
                        style = MaterialTheme.typography.displayMedium,
                    )
                    Text(selected.name, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.padding(8.dp))
                    Row {
                        if (startedAt == 0L) {
                            Button(onClick = {
                                startedAt = System.currentTimeMillis()
                                now = startedAt
                            }) { Text(stringResource(R.string.study_start)) }
                        } else {
                            Button(onClick = {
                                val minutes = ((System.currentTimeMillis() - startedAt) / 60_000).toInt()
                                vm.logStudy(selected.id, startedAt, minutes)
                                startedAt = 0L
                            }) { Text(stringResource(R.string.study_stop)) }
                        }
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(onClick = { manual = true }) { Text(stringResource(R.string.study_add_manual)) }
                    }
                }
            }
        }
        item {
            SectionTitle(
                stringResource(R.string.study_week_total, formatMinutes(sessions.sumOf { it.minutes })),
            )
        }
        items(courses, key = { it.id }) { c ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                ColorDot(c.color)
                Spacer(Modifier.width(8.dp))
                Text(c.name, Modifier.weight(1f))
                Text(formatMinutes(byCourse[c.id] ?: 0))
            }
        }
        item { Spacer(Modifier.padding(8.dp)) }
    }

    if (manual) {
        var minutes by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { manual = false },
            title = { Text(stringResource(R.string.study_add_manual)) },
            text = {
                Column {
                    Text(selected.name)
                    OutlinedTextField(
                        minutes,
                        { minutes = it.filter(Char::isDigit).take(4) },
                        label = { Text(stringResource(R.string.study_minutes)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = (minutes.toIntOrNull() ?: 0) > 0,
                    onClick = {
                        val m = minutes.toInt()
                        vm.logStudy(selected.id, System.currentTimeMillis() - m * 60_000L, m)
                        manual = false
                    },
                ) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { manual = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
