package com.uniplanner.app.health

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.uniplanner.app.R
import com.uniplanner.app.data.FoodLog
import com.uniplanner.app.data.PlanMeal
import com.uniplanner.app.data.WeightEntry
import com.uniplanner.app.domain.BmiCategory
import com.uniplanner.app.domain.DayBalance
import com.uniplanner.app.domain.Health
import com.uniplanner.app.domain.MealDraft
import com.uniplanner.app.domain.Sex
import com.uniplanner.app.ui.screens.ConfirmDelete
import com.uniplanner.app.ui.screens.ScreenHeader
import com.uniplanner.app.ui.screens.SectionTitle
import com.uniplanner.app.ui.screens.TrendLine
import com.uniplanner.app.ui.screens.formatKg
import java.time.LocalTime
import kotlin.math.abs

private fun clock(minuteOfDay: Int): String = "%02d:%02d".format(minuteOfDay / 60, minuteOfDay % 60)

/** "8:30", "08h30", "8h" or "8" to minutes after midnight. */
private fun parseClock(text: String): Int? {
    val m = Regex("""^\s*([01]?\d|2[0-3])\s*(?:[:h.]\s*([0-5]\d)?)?\s*$""").find(text) ?: return null
    return m.groupValues[1].toInt() * 60 + (m.groupValues[2].toIntOrNull() ?: 0)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HealthScreen(vm: HealthViewModel = viewModel()) {
    val context = LocalContext.current
    val profile by vm.profile.collectAsStateWithLifecycle()
    val reminders by vm.reminders.collectAsStateWithLifecycle()
    val weights by vm.weights.collectAsStateWithLifecycle()
    val plan by vm.plan.collectAsStateWithLifecycle()
    val food by vm.foodToday.collectAsStateWithLifecycle()
    val steps by vm.stepsToday.collectAsStateWithLifecycle()
    val balance by vm.balance.collectAsStateWithLifecycle()
    val import by vm.import.collectAsStateWithLifecycle()

    var editingProfile by remember { mutableStateOf(false) }
    var addingWeight by remember { mutableStateOf(false) }
    var editingMeal by remember { mutableStateOf<PlanMeal?>(null) }
    var deletingMeal by remember { mutableStateOf<PlanMeal?>(null) }
    var addingFood by remember { mutableStateOf(false) }
    var pasting by remember { mutableStateOf(false) }
    var stepsAllowed by remember { mutableStateOf(Steps.hasPermission(context)) }

    val askSteps = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        stepsAllowed = ok
        if (ok) vm.refreshSteps()
    }
    val pickPlan = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importFrom(uri)
    }

    val kg = weights.lastOrNull()?.kg
    val eatenIds = food.mapNotNull { it.mealId }.toSet()
    val nowMinute = LocalTime.now().let { it.hour * 60 + it.minute }
    val nextMeal = plan.firstOrNull { it.id !in eatenIds && it.minuteOfDay >= nowMinute - 30 }
    val extraFood = food.filter { it.mealId == null }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { ScreenHeader(stringResource(R.string.tab_health)) }
        if (profile?.complete != true) {
            item {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .clickable { editingProfile = true }.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.health_profile_title), style = MaterialTheme.typography.titleSmall)
                        Text(
                            stringResource(R.string.health_profile_why),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                        )
                    }
                    Icon(Icons.Filled.Add, contentDescription = null)
                }
            }
        }
        item { BalanceCard(balance) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BmiTile(kg, profile?.heightCm, Modifier.weight(1f).clickable { editingProfile = true })
                WeightTile(weights, Modifier.weight(1f).clickable { addingWeight = true })
            }
        }
        item {
            StepsCard(
                steps = steps,
                hasSensor = remember { Steps.hasSensor(context) },
                allowed = stepsAllowed,
                onAllow = { Steps.permission?.let { askSteps.launch(it) } },
            )
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionTitle(stringResource(R.string.health_meals), Modifier.weight(1f))
                Text(stringResource(R.string.health_reminders), style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.width(6.dp))
                Switch(checked = reminders ?: true, onCheckedChange = { vm.setReminders(it) })
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { pickPlan.launch(arrayOf("image/*", "application/pdf")) },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.health_import), maxLines = 1)
                }
                OutlinedButton(onClick = { pasting = true }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.ContentPaste, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.health_paste), maxLines = 1)
                }
            }
        }
        if (plan.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.health_meals_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(plan, key = { "m${it.id}" }) { meal ->
            MealRow(
                meal = meal,
                eaten = meal.id in eatenIds,
                next = meal.id == nextMeal?.id,
                modifier = Modifier.combinedClickable(
                    onClick = { vm.toggleEaten(meal) },
                    onLongClick = { editingMeal = meal },
                ),
            )
        }
        item {
            TextButton(onClick = { editingMeal = PlanMeal(minuteOfDay = 12 * 60 + 30, name = "") }) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.health_add_meal))
            }
        }
        item { SectionTitle(stringResource(R.string.health_other_food)) }
        items(extraFood, key = { "f${it.id}" }) { f -> FoodRow(f, onDelete = { vm.deleteFood(f) }) }
        item {
            TextButton(onClick = { addingFood = true }) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.health_add_food))
            }
        }
        item {
            Text(
                stringResource(R.string.health_estimate_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item { Spacer(Modifier.height(24.dp)) }
    }

    if (editingProfile) {
        ProfileDialog(profile, onDismiss = { editingProfile = false }, onSave = { vm.saveProfile(it); editingProfile = false })
    }
    if (addingWeight) {
        NumberDialog(
            title = stringResource(R.string.health_weight_add),
            label = "kg",
            initial = kg?.let { formatKg(it) }.orEmpty(),
            onDismiss = { addingWeight = false },
            onSave = { vm.addWeight(it); addingWeight = false },
        )
    }
    editingMeal?.let { meal ->
        MealDialog(
            meal = meal,
            onDismiss = { editingMeal = null },
            onSave = { vm.saveMeal(it); editingMeal = null },
            onDelete = if (meal.id != 0L) ({ editingMeal = null; deletingMeal = meal }) else null,
        )
    }
    deletingMeal?.let { meal ->
        ConfirmDelete(
            title = stringResource(R.string.health_delete_meal, meal.name),
            onConfirm = { vm.deleteMeal(meal); deletingMeal = null },
            onDismiss = { deletingMeal = null },
        )
    }
    if (addingFood) {
        FoodDialog(onDismiss = { addingFood = false }, onSave = { name, kcal -> vm.addFood(name, kcal); addingFood = false })
    }
    if (pasting) {
        PasteDialog(onDismiss = { pasting = false }, onRead = { vm.importText(it); pasting = false })
    }
    import?.let { state ->
        ImportDialog(state, hadPlan = plan.isNotEmpty(), onDismiss = { vm.closeImport() }, onSave = { vm.replacePlan(it) })
    }
}

/** The dark card: eaten against burned today, and what is left. */
@Composable
private fun BalanceCard(b: DayBalance) {
    val fg = MaterialTheme.colorScheme.inverseOnSurface
    val muted = fg.copy(alpha = 0.65f)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(MaterialTheme.colorScheme.inverseSurface).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.health_today), style = MaterialTheme.typography.labelLarge, color = muted)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                "${abs(b.deficit)}",
                style = MaterialTheme.typography.displayMedium,
                color = if (b.deficit >= 0) MaterialTheme.colorScheme.inversePrimary else fg,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(if (b.deficit >= 0) R.string.health_deficit else R.string.health_surplus),
                style = MaterialTheme.typography.titleMedium,
                color = fg,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BalanceFigure(stringResource(R.string.health_eaten), b.eaten, fg, muted, Modifier.weight(1f))
            BalanceFigure(stringResource(R.string.health_burned), b.burned, fg, muted, Modifier.weight(1f))
        }
        Text(
            stringResource(R.string.health_burned_detail, b.resting, b.workouts, b.steps),
            style = MaterialTheme.typography.bodySmall,
            color = muted,
        )
    }
}

@Composable
private fun BalanceFigure(label: String, kcal: Int, fg: Color, muted: Color, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(18.dp)).border(1.dp, muted.copy(alpha = 0.3f), RoundedCornerShape(18.dp)).padding(12.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = muted)
        Text("$kcal kcal", style = MaterialTheme.typography.titleLarge, color = fg)
    }
}

@Composable
private fun Tile(modifier: Modifier, color: Color, content: @Composable () -> Unit) {
    Column(
        modifier.heightIn(min = 120.dp).clip(RoundedCornerShape(22.dp)).background(color)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(22.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) { content() }
}

@Composable
private fun BmiTile(kg: Double?, heightCm: Double?, modifier: Modifier) {
    Tile(modifier, MaterialTheme.colorScheme.secondaryContainer) {
        Text(stringResource(R.string.health_bmi), style = MaterialTheme.typography.labelMedium)
        if (kg == null || heightCm == null) {
            Text("—", style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.health_bmi_missing), style = MaterialTheme.typography.bodySmall)
        } else {
            val bmi = Health.bmi(kg, heightCm)
            Text(String.format(java.util.Locale.getDefault(), "%.1f", bmi), style = MaterialTheme.typography.headlineMedium)
            Text(
                stringResource(
                    when (Health.category(bmi)) {
                        BmiCategory.UNDER -> R.string.bmi_under
                        BmiCategory.NORMAL -> R.string.bmi_normal
                        BmiCategory.OVER -> R.string.bmi_over
                        BmiCategory.OBESE -> R.string.bmi_obese
                    },
                ),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun WeightTile(weights: List<WeightEntry>, modifier: Modifier) {
    Tile(modifier, MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.health_weight), style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.health_weight_add), modifier = Modifier.size(18.dp))
        }
        val last = weights.lastOrNull()
        Text(last?.let { "${formatKg(it.kg)} kg" } ?: "—", style = MaterialTheme.typography.headlineMedium)
        if (weights.size >= 2) {
            TrendLine(weights.takeLast(12).map { it.kg }, MaterialTheme.colorScheme.secondary, Modifier.fillMaxWidth().height(32.dp))
        } else {
            Text(stringResource(R.string.health_weight_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun StepsCard(steps: Int, hasSensor: Boolean, allowed: Boolean, onAllow: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(22.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.DirectionsWalk, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.health_steps), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text("%,d / %,d".format(steps, HealthPrefs.STEP_GOAL), style = MaterialTheme.typography.labelLarge)
        }
        when {
            !hasSensor -> Text(stringResource(R.string.health_steps_no_sensor), style = MaterialTheme.typography.bodySmall)
            !allowed -> {
                Text(stringResource(R.string.health_steps_why), style = MaterialTheme.typography.bodySmall)
                Button(
                    onClick = onAllow,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.inverseSurface,
                        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                    ),
                ) { Text(stringResource(R.string.health_steps_allow)) }
            }
            else -> LinearProgressIndicator(
                progress = { (steps.toFloat() / HealthPrefs.STEP_GOAL).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                color = MaterialTheme.colorScheme.secondary,
                trackColor = MaterialTheme.colorScheme.outlineVariant,
                drawStopIndicator = {},
            )
        }
    }
}

@Composable
private fun MealRow(meal: PlanMeal, eaten: Boolean, next: Boolean, modifier: Modifier) {
    val bg = if (next) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(bg)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
            .then(modifier).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(clock(meal.minuteOfDay), style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(56.dp))
        Column(Modifier.weight(1f)) {
            Text(
                meal.name,
                style = MaterialTheme.typography.titleSmall,
                textDecoration = if (eaten) TextDecoration.LineThrough else null,
                color = if (eaten) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
            val detail = listOf(meal.food, if (meal.kcal > 0) "${meal.kcal} kcal" else "").filter { it.isNotBlank() }.joinToString(" · ")
            if (detail.isNotEmpty()) {
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            }
        }
        Box(
            Modifier.size(28.dp).clip(CircleShape)
                .background(if (eaten) MaterialTheme.colorScheme.secondary else Color.Transparent)
                .border(1.5.dp, if (eaten) Color.Transparent else MaterialTheme.colorScheme.outline, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (eaten) Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondary, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun FoodRow(f: FoodLog, onDelete: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainer).padding(start = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(f.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text("${f.kcal} kcal", style = MaterialTheme.typography.labelLarge)
        IconButton(onClick = onDelete) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.delete)) }
    }
}

@Composable
private fun ProfileDialog(profile: BodyProfile?, onDismiss: () -> Unit, onSave: (BodyProfile) -> Unit) {
    var height by remember { mutableStateOf(profile?.heightCm?.let { formatKg(it) }.orEmpty()) }
    var year by remember { mutableStateOf(profile?.birthYear?.toString().orEmpty()) }
    var sex by remember { mutableStateOf(profile?.sex ?: Sex.MALE) }
    val h = height.replace(',', '.').toDoubleOrNull()?.takeIf { it in 100.0..250.0 }
    val y = year.toIntOrNull()?.takeIf { it in 1920..2020 }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.health_profile_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    height, { height = it }, label = { Text(stringResource(R.string.health_height)) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                OutlinedTextField(
                    year, { year = it }, label = { Text(stringResource(R.string.health_birth_year)) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(sex == Sex.MALE, { sex = Sex.MALE }, { Text(stringResource(R.string.health_sex_male)) })
                    FilterChip(sex == Sex.FEMALE, { sex = Sex.FEMALE }, { Text(stringResource(R.string.health_sex_female)) })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(BodyProfile(h, sex, y)) }, enabled = h != null && y != null) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun NumberDialog(title: String, label: String, initial: String, onDismiss: () -> Unit, onSave: (Double) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    val value = text.replace(',', '.').toDoubleOrNull()?.takeIf { it in 25.0..300.0 }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                text, { text = it }, label = { Text(label) }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
        },
        confirmButton = { TextButton(onClick = { value?.let(onSave) }, enabled = value != null) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun MealDialog(meal: PlanMeal, onDismiss: () -> Unit, onSave: (PlanMeal) -> Unit, onDelete: (() -> Unit)?) {
    var time by remember { mutableStateOf(clock(meal.minuteOfDay)) }
    var name by remember { mutableStateOf(meal.name) }
    var food by remember { mutableStateOf(meal.food) }
    var kcal by remember { mutableStateOf(if (meal.kcal > 0) meal.kcal.toString() else "") }
    val minute = parseClock(time)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (meal.id == 0L) R.string.health_add_meal else R.string.health_edit_meal)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(time, { time = it }, label = { Text(stringResource(R.string.health_meal_time)) }, singleLine = true, isError = minute == null)
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.health_meal_name)) }, singleLine = true)
                OutlinedTextField(food, { food = it }, label = { Text(stringResource(R.string.health_meal_food)) })
                OutlinedTextField(
                    kcal, { kcal = it.filter(Char::isDigit).take(4) }, label = { Text("kcal") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(meal.copy(minuteOfDay = minute ?: 0, name = name.trim(), food = food.trim(), kcal = kcal.toIntOrNull() ?: 0)) },
                enabled = minute != null && name.isNotBlank(),
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun FoodDialog(onDismiss: () -> Unit, onSave: (String, Int) -> Unit) {
    var name by remember { mutableStateOf("") }
    var kcal by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.health_add_food)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.health_food_name)) }, singleLine = true)
                OutlinedTextField(
                    kcal, { kcal = it.filter(Char::isDigit).take(4) }, label = { Text("kcal") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name, kcal.toIntOrNull() ?: 0) }, enabled = name.isNotBlank() && kcal.isNotEmpty()) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun PasteDialog(onDismiss: () -> Unit, onRead: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.health_paste)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.health_paste_hint), style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(text, { text = it }, modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp))
            }
        },
        confirmButton = { TextButton(onClick = { onRead(text) }, enabled = text.isNotBlank()) { Text(stringResource(R.string.health_read)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** What was read from the plan, to check before it replaces the current one. Meals can be left out. */
@Composable
private fun ImportDialog(state: PlanImportState, hadPlan: Boolean, onDismiss: () -> Unit, onSave: (List<MealDraft>) -> Unit) {
    var kept by remember(state) { mutableStateOf((state as? PlanImportState.Found)?.meals.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.health_import_title)) },
        text = {
            when (state) {
                PlanImportState.Reading -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.health_import_reading))
                }
                PlanImportState.NothingFound -> Text(stringResource(R.string.health_import_nothing))
                is PlanImportState.Found -> Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (hadPlan) Text(stringResource(R.string.health_import_replaces), style = MaterialTheme.typography.bodySmall)
                    kept.forEachIndexed { i, m ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(clock(m.minuteOfDay), style = MaterialTheme.typography.titleSmall, modifier = Modifier.width(52.dp))
                            Column(Modifier.weight(1f)) {
                                Text(m.name, style = MaterialTheme.typography.titleSmall)
                                val detail = listOf(m.food, if (m.kcal > 0) "${m.kcal} kcal" else "").filter { it.isNotBlank() }.joinToString(" · ")
                                if (detail.isNotEmpty()) Text(detail, style = MaterialTheme.typography.bodySmall, maxLines = 3)
                            }
                            IconButton(onClick = { kept = kept.filterIndexed { j, _ -> j != i } }) {
                                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.delete))
                            }
                        }
                    }
                    Text(stringResource(R.string.health_import_edit_later), style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            if (state is PlanImportState.Found) {
                TextButton(onClick = { onSave(kept) }, enabled = kept.isNotEmpty()) { Text(stringResource(R.string.save)) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
