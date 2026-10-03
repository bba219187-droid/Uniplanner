package com.uniplanner.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.text.font.FontWeight
import com.uniplanner.app.ui.theme.Ink
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
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
import com.uniplanner.app.settings.PersonalSettings
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
    var planning by remember { mutableStateOf<Boolean?>(null) }
    var deleting by remember { mutableStateOf<Workout?>(null) }

    val zone = ZoneId.systemDefault()
    val now = System.currentTimeMillis()
    val weekStart = Planning.weekStart(now, zone)
    val weekEnd = weekStart + TimeUnit.DAYS.toMillis(7)
    val thisWeek = workouts.filter { it.startsAt in weekStart until weekEnd }
    val doneThisWeek = thisWeek.filter { it.done }
    val upcoming = workouts.filter { !it.done && it.startsAt >= now - TimeUnit.HOURS.toMillis(12) }.sortedBy { it.startsAt }
    val history = (workouts - upcoming.toSet()).sortedByDescending { it.startsAt }
    val setsByWorkout = sets.groupBy { it.workoutId }
    val lastDone = workouts.filter { it.done }.maxByOrNull { it.startsAt }
    val goal = PersonalSettings.flow(LocalContext.current).collectAsStateWithLifecycle().value?.gymPerWeek
    val ctx = LocalContext.current
    var chosenPlan by remember { mutableStateOf(com.uniplanner.app.domain.GymPlans.byId(GymPlanPrefs.chosen(ctx))) }
    var openPlan by remember { mutableStateOf<com.uniplanner.app.domain.GymPlan?>(null) }
    val doneTitles = workouts.filter { it.done }.sortedByDescending { it.startsAt }.map { it.title }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { ScreenHeader(stringResource(R.string.tab_gym)) }
        item {
            StartCard(
                lastDone = lastDone,
                onStartNow = { planning = true },
                onPlan = { planning = false },
            )
        }
        chosenPlan?.let { plan ->
            item {
                val day = com.uniplanner.app.domain.GymPlans.next(plan, doneTitles)
                PlanTodayCard(plan, day, onStart = { vm.startPlanDay(plan, day) { onOpenWorkout(it) } }, onOpen = { openPlan = plan })
            }
        }
        item { WeekStrip(workouts, weekStart, zone) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GymStat(stringResource(R.string.gym_stat_workouts), "${doneThisWeek.size}/${goal ?: thisWeek.size}", Modifier.weight(1f))
                GymStat(stringResource(R.string.gym_stat_time), formatMinutes(doneThisWeek.sumOf { it.minutes }), Modifier.weight(1f))
                GymStat(
                    stringResource(R.string.gym_stat_kcal),
                    "${doneThisWeek.sumOf { Health.workoutKcal(it.title, it.minutes, weight) }}",
                    Modifier.weight(1f),
                )
            }
        }
        item { SectionTitle("Planos prontos") }
        item { PlanCarousel(chosenPlan?.id, com.uniplanner.app.domain.GymPlans.all) { openPlan = it } }
        if (sets.isNotEmpty()) item { ProgressCard(workouts, sets) }
        if (upcoming.isNotEmpty()) item { SectionTitle(stringResource(R.string.gym_next)) }
        items(upcoming, key = { "u${it.id}" }) { w ->
            WorkoutCard(w, setsByWorkout[w.id].orEmpty(), weight, vm, onOpenWorkout, onDelete = { deleting = w })
        }
        if (history.isNotEmpty()) item { SectionTitle(stringResource(R.string.gym_history)) }
        items(history, key = { "h${it.id}" }) { w ->
            WorkoutCard(w, setsByWorkout[w.id].orEmpty(), weight, vm, onOpenWorkout, onDelete = { deleting = w })
        }
        item { Spacer(Modifier.height(24.dp)) }
    }

    planning?.let { startNow ->
        NewWorkoutSheet(
            startNow = startNow,
            workouts = workouts,
            sets = sets,
            onDismiss = { planning = null },
            onCreate = { title, startsAt, minutes, copyFrom ->
                planning = null
                vm.createWorkout(title, startsAt, minutes, copyFrom) { id -> if (startNow) onOpenWorkout(id) }
            },
        )
    }
    openPlan?.let { plan ->
        PlanSheet(
            plan = plan,
            following = chosenPlan?.id == plan.id,
            onDismiss = { openPlan = null },
            onFollow = { on ->
                GymPlanPrefs.choose(ctx, if (on) plan.id else null)
                chosenPlan = if (on) plan else null
            },
            onStart = { day ->
                openPlan = null
                vm.startPlanDay(plan, day) { onOpenWorkout(it) }
            },
        )
    }
    deleting?.let { w ->
        ConfirmDelete(
            title = stringResource(R.string.gym_delete_workout_title, w.title),
            onConfirm = { vm.deleteWorkout(w); deleting = null },
            onDismiss = { deleting = null },
        )
    }
}

@Composable
fun ConfirmDelete(title: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** The big dark card at the top: start a workout now, or plan one for later. */
@Composable
private fun StartCard(lastDone: Workout?, onStartNow: () -> Unit, onPlan: () -> Unit) {
    val fg = MaterialTheme.colorScheme.inverseOnSurface
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(MaterialTheme.colorScheme.inverseSurface).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column {
            Text(stringResource(R.string.gym_ready), style = MaterialTheme.typography.headlineSmall, color = fg)
            if (lastDone != null) {
                val days = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - lastDone.startsAt).toInt()
                Text(
                    if (days <= 0) stringResource(R.string.gym_last_today, lastDone.title)
                    else pluralStringResource(R.plurals.gym_last_days, days, lastDone.title, days),
                    style = MaterialTheme.typography.bodyMedium,
                    color = fg.copy(alpha = 0.7f),
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = onStartNow,
                modifier = Modifier.weight(1f).height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.inversePrimary, contentColor = Ink),
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.gym_start_now), style = MaterialTheme.typography.titleSmall)
            }
            OutlinedButton(
                onClick = onPlan,
                modifier = Modifier.weight(1f).height(52.dp),
                border = BorderStroke(1.dp, fg.copy(alpha = 0.4f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = fg),
            ) {
                Icon(Icons.Filled.CalendarMonth, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.gym_plan), style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}

/** This week, Monday to Sunday: a filled circle on days with a finished workout, a ring on planned ones. */
@Composable
private fun WeekStrip(workouts: List<Workout>, weekStart: Long, zone: ZoneId) {
    val start = Instant.ofEpochMilli(weekStart).atZone(zone).toLocalDate()
    val today = LocalDate.now(zone)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        (0..6).forEach { i ->
            val day = start.plusDays(i.toLong())
            val onDay = workouts.filter { Instant.ofEpochMilli(it.startsAt).atZone(zone).toLocalDate() == day }
            val done = onDay.any { it.done }
            val planned = onDay.isNotEmpty() && !done
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    day.dayOfWeek.getDisplayName(java.time.format.TextStyle.NARROW, java.util.Locale.getDefault()),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (day == today) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (day == today) FontWeight.Bold else FontWeight.Normal,
                )
                Box(
                    Modifier.size(36.dp).clip(CircleShape)
                        .background(if (done) MaterialTheme.colorScheme.secondary else Color.Transparent)
                        .border(
                            if (planned || day == today) 1.5.dp else 1.dp,
                            when {
                                done -> Color.Transparent
                                planned -> MaterialTheme.colorScheme.secondary
                                day == today -> MaterialTheme.colorScheme.onSurface
                                else -> MaterialTheme.colorScheme.outlineVariant
                            },
                            CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (done) {
                        Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondary, modifier = Modifier.size(18.dp))
                    } else {
                        Text("${day.dayOfMonth}", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

/**
 * Choosing a workout in a few taps: the kind (recent ones first), when, how long, and whether to
 * repeat the exercises of the last workout of the same kind.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewWorkoutSheet(
    startNow: Boolean,
    workouts: List<Workout>,
    sets: List<ExerciseSet>,
    onDismiss: () -> Unit,
    onCreate: (title: String, startsAt: Long, minutes: Int, copyFrom: Long?) -> Unit,
) {
    val context = LocalContext.current
    val recent = workouts.sortedByDescending { it.startsAt }.map { it.title.trim() }.distinct().take(4)
    val favourites = PersonalSettings.get(context).gymKinds
    val kinds = (recent + favourites + stringArrayResource(R.array.gym_kinds)).distinctBy { it.lowercase() }
    var title by remember { mutableStateOf(recent.firstOrNull() ?: favourites.firstOrNull().orEmpty()) }
    var custom by remember { mutableStateOf(false) }
    var minutes by remember { mutableIntStateOf(60) }
    val zone = ZoneId.systemDefault()
    val nextHour = LocalDateTime.now(zone).plusHours(1).withMinute(0).withSecond(0).withNano(0)
    val tomorrow = LocalDate.now(zone).plusDays(1).atTime(18, 0)
    fun LocalDateTime.millis() = atZone(zone).toInstant().toEpochMilli()
    var whenChoice by remember { mutableIntStateOf(if (startNow) 0 else 1) }
    var customTime by remember { mutableLongStateOf(tomorrow.millis()) }
    val startsAt = when (whenChoice) {
        0 -> System.currentTimeMillis()
        1 -> nextHour.millis()
        2 -> tomorrow.millis()
        else -> customTime
    }
    val source = workouts.filter { it.title.trim().equals(title.trim(), ignoreCase = true) }
        .filter { w -> sets.any { it.workoutId == w.id } }
        .maxByOrNull { it.startsAt }
    val sourceExercises = source?.let { s -> sets.filter { it.workoutId == s.id }.map { it.exercise.trim() }.distinct() }.orEmpty()
    var repeat by remember { mutableStateOf(true) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                stringResource(if (whenChoice == 0) R.string.gym_start_now else R.string.gym_plan),
                style = MaterialTheme.typography.headlineSmall,
            )
            SheetLabel(stringResource(R.string.gym_which))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                kinds.forEach { k ->
                    FilterChip(
                        selected = !custom && title.equals(k, ignoreCase = true),
                        onClick = { title = k; custom = false },
                        label = { Text(k) },
                    )
                }
                FilterChip(selected = custom, onClick = { custom = true; title = "" }, label = { Text(stringResource(R.string.gym_other)) })
            }
            if (custom) {
                OutlinedTextField(
                    title, { title = it },
                    label = { Text(stringResource(R.string.gym_title_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            SheetLabel(stringResource(R.string.gym_when))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val timeFmt = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
                FilterChip(whenChoice == 0, { whenChoice = 0 }, { Text(stringResource(R.string.gym_when_now)) })
                FilterChip(whenChoice == 1, { whenChoice = 1 }, { Text(stringResource(R.string.gym_when_today, nextHour.format(timeFmt))) })
                FilterChip(whenChoice == 2, { whenChoice = 2 }, { Text(stringResource(R.string.gym_when_tomorrow, tomorrow.format(timeFmt))) })
                FilterChip(
                    whenChoice == 3,
                    { pickDateTime(context, customTime) { customTime = it; whenChoice = 3 } },
                    { Text(if (whenChoice == 3) formatDateTime(customTime) else stringResource(R.string.gym_when_pick)) },
                )
            }
            SheetLabel(stringResource(R.string.gym_duration))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(30, 45, 60, 75, 90).forEach { m ->
                    FilterChip(minutes == m, { minutes = m }, { Text("$m") }, modifier = Modifier.weight(1f))
                }
            }
            if (source != null) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceContainer)
                        .clickable { repeat = !repeat }.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.gym_repeat, source.title), style = MaterialTheme.typography.titleSmall)
                        Text(
                            sourceExercises.joinToString(", "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                        )
                    }
                    Switch(checked = repeat, onCheckedChange = { repeat = it })
                }
            }
            Button(
                onClick = { onCreate(title, startsAt, minutes, source?.id?.takeIf { repeat }) },
                enabled = title.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                ),
            ) {
                Text(
                    stringResource(if (whenChoice == 0) R.string.gym_go else R.string.gym_save_plan),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

@Composable
private fun SheetLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun GymStat(label: String, value: String, modifier: Modifier = Modifier) {
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
            TrendLine(best, MaterialTheme.colorScheme.inversePrimary, Modifier.fillMaxWidth().height(64.dp))
            Text(
                pluralStringResource(R.plurals.gym_last_workouts, best.size, best.size),
                color = fg.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WorkoutCard(
    w: Workout,
    sets: List<ExerciseSet>,
    weight: Double,
    vm: AppViewModel,
    onOpen: (Long) -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(22.dp))
            .combinedClickable(onClick = { onOpen(w.id) }, onLongClick = onDelete)
            .padding(start = 6.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
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
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.DeleteOutline, stringResource(R.string.delete), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
