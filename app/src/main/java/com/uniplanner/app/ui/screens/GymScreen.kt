package com.uniplanner.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.uniplanner.app.data.Workout
import com.uniplanner.app.domain.Planning
import com.uniplanner.app.ui.theme.GymGradient
import java.time.ZoneId
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

    val now = System.currentTimeMillis()
    val weekStart = Planning.weekStart(now, ZoneId.systemDefault())
    val weekEnd = weekStart + TimeUnit.DAYS.toMillis(7)
    val thisWeek = workouts.filter { it.startsAt in weekStart until weekEnd }
    val upcoming = workouts.filter { it.startsAt >= weekStart && !it.done }.sortedBy { it.startsAt }
    val history = workouts.filter { it.done || it.startsAt < weekStart }.sortedByDescending { it.startsAt }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    stringResource(R.string.tab_gym),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
            item {
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(GymGradient).padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.week_title), color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.titleSmall)
                            Text(
                                stringResource(R.string.gym_week_count, thisWeek.count { it.done }, thisWeek.size),
                                color = Color.White,
                                style = MaterialTheme.typography.headlineSmall,
                            )
                            Text(
                                formatMinutes(thisWeek.filter { it.done }.sumOf { it.minutes }),
                                color = Color.White.copy(alpha = 0.85f),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        Icon(Icons.Filled.FitnessCenter, contentDescription = null, tint = Color.White, modifier = Modifier.size(48.dp))
                    }
                }
            }
            if (workouts.isEmpty()) {
                item { Text(stringResource(R.string.gym_empty), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 8.dp)) }
            }
            if (upcoming.isNotEmpty()) item { SectionTitle(stringResource(R.string.gym_next)) }
            items(upcoming, key = { "u${it.id}" }) { w -> WorkoutCard(w, vm) }
            if (history.isNotEmpty()) item { SectionTitle(stringResource(R.string.gym_history)) }
            items(history, key = { "h${it.id}" }) { w -> WorkoutCard(w, vm) }
            item { Spacer(Modifier.height(80.dp)) }
        }
        ExtendedFloatingActionButton(
            onClick = { adding = true },
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text(stringResource(R.string.gym_add)) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
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

@Composable
private fun WorkoutCard(w: Workout, vm: AppViewModel) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = w.done, onCheckedChange = { vm.setWorkoutDone(w, it) })
            Column(Modifier.weight(1f)) {
                Text(w.title, style = MaterialTheme.typography.titleSmall)
                Text(
                    "${formatDateTime(w.startsAt)} · ${formatMinutes(w.minutes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { vm.deleteWorkout(w) }) {
                Icon(Icons.Filled.Delete, stringResource(R.string.delete))
            }
        }
    }
}
