package com.uniplanner.app.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ExperimentalLayoutApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uniplanner.app.R
import com.uniplanner.app.data.ExerciseSet
import com.uniplanner.app.domain.Health
import com.uniplanner.app.reminders.formatMinutes
import com.uniplanner.app.ui.AppViewModel
import com.uniplanner.app.ui.theme.Mono

/** One workout: its exercises, each with its sets. Tap a set to tick it, hold it to change it. */
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun WorkoutScreen(vm: AppViewModel, workoutId: Long, onBack: () -> Unit) {
    val workouts by vm.workouts.collectAsStateWithLifecycle()
    val allSets by vm.exerciseSets.collectAsStateWithLifecycle()
    val weight by vm.bodyWeight.collectAsStateWithLifecycle()
    val workout = workouts.firstOrNull { it.id == workoutId } ?: return
    val sets = allSets.filter { it.workoutId == workoutId }
    val byExercise = sets.groupBy { it.exercise.trim() }
    val earlier = workouts.filter { it.startsAt < workout.startsAt }.map { it.id }.toSet()
    val knownNames = allSets.map { it.exercise.trim() }.distinct()

    var addingExercise by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ExerciseSet?>(null) }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(Modifier.padding(top = 16.dp)) {
                Text(
                    "${formatDateTime(workout.startsAt)} · ${formatMinutes(workout.minutes)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary,
                )
                Text(workout.title, style = MaterialTheme.typography.headlineMedium)
                Text(
                    stringResource(R.string.gym_kcal_estimate, Health.workoutKcal(workout.title, workout.minutes, weight)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (sets.isEmpty()) {
            item { Text(stringResource(R.string.gym_no_sets), style = MaterialTheme.typography.bodyLarge) }
        }
        items(byExercise.entries.toList(), key = { it.key }) { (name, exerciseSets) ->
            val before = allSets.filter { it.exercise.trim() == name && it.workoutId in earlier && it.weightKg > 0 }.maxOfOrNull { it.weightKg }
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(24.dp)).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    if (before != null) {
                        Text(
                            stringResource(R.string.gym_before, formatKg(before)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    exerciseSets.sortedBy { it.position }.forEach { s ->
                        val done = s.done
                        Column(
                            Modifier.width(88.dp).clip(RoundedCornerShape(16.dp))
                                .background(if (done) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer)
                                .combinedClickable(
                                    role = Role.Checkbox,
                                    onClick = { vm.updateSet(s.copy(done = !done)) },
                                    onLongClick = { editing = s },
                                )
                                .padding(vertical = 9.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text("${s.reps}×", style = MaterialTheme.typography.titleMedium)
                            Text("${formatKg(s.weightKg)} kg", fontFamily = Mono, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    val last = exerciseSets.maxBy { it.position }
                    Column(
                        Modifier.width(88.dp).clip(RoundedCornerShape(16.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
                            .combinedClickable(onClick = { vm.addSets(workoutId, name, last.reps, last.weightKg, 1) })
                            .padding(vertical = 9.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("+", style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.gym_set), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        item {
            Text(
                stringResource(R.string.gym_sets_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            OutlinedButton(onClick = { addingExercise = true }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text(stringResource(R.string.gym_add_exercise))
            }
        }
        item {
            Button(
                onClick = {
                    vm.setWorkoutDone(workout, !workout.done)
                    if (!workout.done) onBack()
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                ),
            ) {
                Text(stringResource(if (workout.done) R.string.gym_mark_not_done else R.string.gym_mark_done))
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }

    if (addingExercise) {
        var name by remember { mutableStateOf("") }
        var count by remember { mutableStateOf("3") }
        var reps by remember { mutableStateOf("10") }
        var kg by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { addingExercise = false },
            title = { Text(stringResource(R.string.gym_add_exercise)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.gym_exercise_name)) }, singleLine = true)
                    val suggestions = knownNames.filter { it !in byExercise && (name.isBlank() || it.contains(name, ignoreCase = true)) }.take(4)
                    if (suggestions.isNotEmpty()) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            suggestions.forEach { s ->
                                SuggestionChip(onClick = {
                                    name = s
                                    allSets.filter { it.exercise.trim() == s }.maxByOrNull { it.id }?.let { lastSet ->
                                        reps = lastSet.reps.toString()
                                        kg = formatKg(lastSet.weightKg)
                                    }
                                }, label = { Text(s) })
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumberField(count, { count = it.filter(Char::isDigit).take(2) }, stringResource(R.string.gym_sets), Modifier.weight(1f))
                        NumberField(reps, { reps = it.filter(Char::isDigit).take(3) }, stringResource(R.string.gym_reps), Modifier.weight(1f))
                        NumberField(kg, { kg = it.filter { c -> c.isDigit() || c == ',' || c == '.' }.take(6) }, "kg", Modifier.weight(1f), decimal = true)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = name.isNotBlank() && (reps.toIntOrNull() ?: 0) > 0,
                    onClick = {
                        vm.addSets(workoutId, name, reps.toInt(), kg.replace(',', '.').toDoubleOrNull() ?: 0.0, count.toIntOrNull() ?: 1)
                        addingExercise = false
                    },
                ) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { addingExercise = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    editing?.let { s ->
        var reps by remember(s.id) { mutableStateOf(s.reps.toString()) }
        var kg by remember(s.id) { mutableStateOf(formatKg(s.weightKg)) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(s.exercise) },
            text = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(reps, { reps = it.filter(Char::isDigit).take(3) }, stringResource(R.string.gym_reps), Modifier.weight(1f))
                    NumberField(kg, { kg = it.filter { c -> c.isDigit() || c == ',' || c == '.' }.take(6) }, "kg", Modifier.weight(1f), decimal = true)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.updateSet(s.copy(reps = reps.toIntOrNull() ?: s.reps, weightKg = kg.replace(',', '.').toDoubleOrNull() ?: s.weightKg))
                    editing = null
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    vm.deleteSet(s)
                    editing = null
                }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
            },
        )
    }
}

@Composable
private fun NumberField(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier, decimal: Boolean = false) {
    OutlinedTextField(
        value,
        onChange,
        label = { Text(label, maxLines = 1) },
        singleLine = true,
        modifier = modifier,
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
    )
}
