package com.uniplanner.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uniplanner.app.R
import com.uniplanner.app.data.ExerciseSet
import com.uniplanner.app.data.Workout
import com.uniplanner.app.domain.Health
import com.uniplanner.app.domain.Planning
import com.uniplanner.app.reminders.formatMinutes
import com.uniplanner.app.ui.AppViewModel
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/** "62,5" in Portuguese, "62.5" in English; whole numbers without decimals. */
fun formatKg(kg: Double): String =
    if (kg % 1.0 == 0.0) kg.toLong().toString() else String.format(java.util.Locale.getDefault(), "%.1f", kg)

@Composable
fun GymScreen(vm: AppViewModel, onOpenWorkout: (Long) -> Unit) {
    val workouts by vm.workouts.collectAsStateWithLifecycle()
    val sets by vm.exerciseSets.collectAsStateWithLifecycle()
    val weight by vm.bodyWeight.collectAsStateWithLifecycle()
    var adding by remember { mutableStateOf(false) }

    val now = System.currentTimeMillis()
    val weekStart = Planning.weekStart(now, ZoneId.systemDefault())
    val weekEnd = weekStart + TimeUnit.DAYS.toMillis(7)
    val thisWeek = workouts.filter { it.startsAt in weekStart until weekEnd }
    val doneThisWeek = thisWeek.filter { it.done }
    val upcoming = workouts.filter { it.startsAt >= weekStart && !it.done }.sortedBy { it.startsAt }
    val history = workouts.filter { it.done || it.startsAt < weekStart }.sortedByDescending { it.startsAt }
    val setsByWorkout = sets.groupBy { it.workoutId }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { ScreenHeader(stringResource(R.string.tab_gym)) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile(stringResource(R.string.gym_stat_workouts), "${doneThisWeek.size}/${thisWeek.size}", Modifier.weight(1f))
                    StatTile(stringResource(R.string.gym_stat_time), formatMinutes(doneThisWeek.sumOf { it.minutes }), Modifier.weight(1f))
                    StatTile(
                        stringResource(R.string.gym_stat_kcal),
                        "${doneThisWeek.sumOf { Health.workoutKcal(it.title, it.minutes, weight) }}",
                        Modifier.weight(1f),
                    )
                }
            }
            if (sets.isNotEmpty()) item { ProgressCard(workouts, sets) }
            if (workouts.isEmpty()) {
                item { Text(stringResource(R.string.gym_empty), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 8.dp)) }
            }
            if (upcoming.isNotEmpty()) item { SectionTitle(stringResource(R.string.gym_next)) }
            items(upcoming, key = { "u${it.id}" }) { w -> WorkoutCard(w, setsByWorkout[w.id].orEmpty(), weight, vm, onOpenWorkout) }
            if (history.isNotEmpty()) item { SectionTitle(stringResource(R.string.gym_history)) }
            items(history, key = { "h${it.id}" }) { w -> WorkoutCard(w, setsByWorkout[w.id].orEmpty(), weight, vm, onOpenWorkout) }
            item { Spacer(Modifier.height(80.dp)) }
        }
        ExtendedFloatingActionButton(
            onClick = { adding = true },
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text(stringResource(R.string.gym_add)) },
            containerColor = MaterialTheme.colorScheme.inverseSurface,
            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }

    if (adding) {
        val context = LocalContext.current
        var title by remember { mutableStateOf("") }
        var minutes by remember { mutableStateOf("60") }
        var startsAt by remember { mutableLongStateOf(System.currentTimeMillis()) }
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
fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp)).padding(12.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        Text(value, style = MaterialTheme.typography.titleLarge, maxLines = 1)
    }
}

/** The heaviest set of one exercise in each workout, over time, in the dark card. */
@Composable
private fun ProgressCard(workouts: List<Workout>, sets: List<ExerciseSet>) {
    val names = sets.groupBy { it.exercise.trim() }.entries.sortedByDescending { it.value.size }.map { it.key }
    var picked by rememberSaveable { mutableStateOf(names.first()) }
    val exercise = picked.takeIf { it in names } ?: names.first()
    val order = workouts.associate { it.id to it.startsAt }
    val best = sets.filter { it.exercise.trim() == exercise && it.weightKg > 0 }
        .groupBy { it.workoutId }
        .map { (id, s) -> (order[id] ?: 0L) to s.maxOf { it.weightKg } }
        .sortedBy { it.first }
        .map { it.second }
        .takeLast(12)
    val fg = MaterialTheme.colorScheme.inverseOnSurface
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(MaterialTheme.colorScheme.inverseSurface).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            names.take(8).forEach { name ->
                FilterChip(
                    selected = name == exercise,
                    onClick = { picked = name },
                    label = { Text(name, maxLines = 1) },
                    colors = FilterChipDefaults.filterChipColors(
                        labelColor = fg.copy(alpha = 0.7f),
                        selectedContainerColor = fg,
                        selectedLabelColor = MaterialTheme.colorScheme.inverseSurface,
                    ),
                    border = FilterChipDefaults.filterChipBorder(true, name == exercise, borderColor = fg.copy(alpha = 0.3f)),
                )
            }
        }
        if (best.isEmpty()) {
            Text(stringResource(R.string.gym_progress_empty), color = fg.copy(alpha = 0.7f), style = MaterialTheme.typography.bodyMedium)
        } else {
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.gym_record), color = fg.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall)
                    Text("${formatKg(best.max())} kg", color = fg, style = MaterialTheme.typography.headlineMedium)
                }
                val change = best.last() - best.first()
                if (best.size > 1 && change != 0.0) {
                    Text(
                        (if (change > 0) "+" else "") + formatKg(change) + " kg",
                        color = MaterialTheme.colorScheme.inversePrimary,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                }
            }
            LineChart(best, MaterialTheme.colorScheme.inversePrimary, Modifier.fillMaxWidth().height(64.dp))
            Text(
                stringResource(R.string.gym_last_workouts, best.size),
                color = fg.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun WorkoutCard(w: Workout, sets: List<ExerciseSet>, weight: Double, vm: AppViewModel, onOpen: (Long) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(22.dp))
            .clickable { onOpen(w.id) }.padding(start = 6.dp, end = 14.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = w.done, onCheckedChange = { vm.setWorkoutDone(w, it) })
        Column(Modifier.weight(1f)) {
            Text(w.title, style = MaterialTheme.typography.titleMedium)
            Text(
                "${formatDateTime(w.startsAt)} · ${formatMinutes(w.minutes)} · ≈${Health.workoutKcal(w.title, w.minutes, weight)} kcal",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (sets.isNotEmpty()) {
                Text(
                    stringResource(R.string.gym_sets_summary, sets.map { it.exercise.trim() }.distinct().size, sets.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        }
        Box(
            Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Text(stringResource(R.string.gym_open), style = MaterialTheme.typography.labelMedium)
        }
    }
}
