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
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uniplanner.app.R
import com.uniplanner.app.reminders.formatMinutes
import com.uniplanner.app.ui.AppViewModel
import java.util.concurrent.TimeUnit

@Composable
fun GymScreen(vm: AppViewModel) {
    val workouts by vm.workouts.collectAsStateWithLifecycle()
    var adding by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        if (workouts.isEmpty()) {
            EmptyState(stringResource(R.string.gym_empty))
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(workouts, key = { it.id }) { w ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = w.done, onCheckedChange = { vm.setWorkoutDone(w, it) })
                            Column(Modifier.weight(1f)) {
                                Text(w.title, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "${formatDateTime(w.startsAt)} · ${formatMinutes(w.minutes)}",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            IconButton(onClick = { vm.deleteWorkout(w) }) {
                                Icon(Icons.Filled.Delete, stringResource(R.string.delete))
                            }
                        }
                    }
                }
            }
        }
        FloatingActionButton(
            onClick = { adding = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        ) { Icon(Icons.Filled.Add, stringResource(R.string.gym_add)) }
    }

    if (adding) {
        val context = LocalContext.current
        var title by remember { mutableStateOf("") }
        var minutes by remember { mutableStateOf("60") }
        var startsAt by remember { mutableLongStateOf(System.currentTimeMillis() + TimeUnit.DAYS.toMillis(1)) }
        AlertDialog(
            onDismissRequest = { adding = false },
            title = { Text(stringResource(R.string.gym_add)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        title,
                        { title = it },
                        label = { Text(stringResource(R.string.gym_title_hint)) },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        minutes,
                        { minutes = it.filter(Char::isDigit).take(3) },
                        label = { Text(stringResource(R.string.study_minutes)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    OutlinedButton(onClick = { pickDateTime(context, startsAt) { startsAt = it } }) {
                        Text(formatDateTime(startsAt))
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = title.isNotBlank(),
                    onClick = {
                        vm.addWorkout(title, startsAt, minutes.toIntOrNull() ?: 60)
                        adding = false
                    },
                ) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { adding = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
