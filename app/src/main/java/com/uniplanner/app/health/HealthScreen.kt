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
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.VerticalDivider
import com.uniplanner.app.ui.theme.Mono
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
import androidx.compose.runtime.LaunchedEffect
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
import com.uniplanner.app.settings.HealthGoal
import com.uniplanner.app.settings.PersonalSettings
import com.uniplanner.app.ui.screens.ConfirmDelete
import com.uniplanner.app.ui.screens.ScreenHeader
import com.uniplanner.app.ui.screens.SectionTitle
import com.uniplanner.app.ui.screens.TrendLine
import com.uniplanner.app.ui.screens.formatKg
import kotlinx.coroutines.delay
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
    val personal by PersonalSettings.flow(context).collectAsStateWithLifecycle()

    var editingProfile by remember { mutableStateOf(false) }
    var addingWeight by remember { mutableStateOf(false) }
    var editingMeal by remember { mutableStateOf<PlanMeal?>(null) }
    var deletingMeal by remember { mutableStateOf<PlanMeal?>(null) }
    var addingFood by remember { mutableStateOf(false) }
    var pasting by remember { mutableStateOf(false) }
    var stepsAllowed by remember { mutableStateOf(Steps.hasPermission(context)) }
    var history by remember { mutableStateOf<HealthMetric?>(null) }
    val allSteps by vm.steps.collectAsStateWithLifecycle()
    val hcAvailable = remember { HealthConnectSteps.available(context) }
    val hcInstallable = remember { HealthConnectSteps.canInstall(context) }
    var hcGranted by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { hcGranted = HealthConnectSteps.granted(context) }
    val askHealthConnect = rememberLauncherForActivityResult(
        androidx.health.connect.client.PermissionController.createRequestPermissionResultContract(),
    ) { got ->
        hcGranted = got.isNotEmpty()
        if (hcGranted) vm.syncHealthConnect()
    }
    val linkSteps: () -> Unit = {
        if (hcAvailable) {
            askHealthConnect.launch(HealthConnectSteps.permissions)
        } else {
            runCatching {
                context.startActivity(
                    android.content.Intent(
                        android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse("market://details?id=com.google.android.apps.healthdata"),
                    ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
    }
    val canLinkSteps = !hcGranted && (hcAvailable || hcInstallable)

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
    // Logs of meals that were since deleted or replaced still count, so they show with the other food.
    val planIds = plan.map { it.id }.toSet()
    val extraFood = food.filter { it.mealId == null || it.mealId !in planIds }

    LaunchedEffect(Unit) {
        while (true) {
            vm.refreshDay()
            stepsAllowed = Steps.hasPermission(context)
            delay(60_000)
        }
    }

    val stepGoal = personal?.stepGoal ?: HealthPrefs.STEP_GOAL
    val hasSensor = remember { Steps.hasSensor(context) }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { ScreenHeader(stringResource(R.string.tab_health)) }
        if (profile?.complete != true) {
            item {
                CleanCard(Modifier.clickable { editingProfile = true }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.health_profile_title), style = MaterialTheme.typography.titleSmall)
                            Text(stringResource(R.string.health_profile_why), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                    }
                }
            }
        }
        item { BalanceCard(balance, personal?.healthGoal) }
        if (plan.isNotEmpty()) {
            item {
                MealHero(
                    next = nextMeal,
                    eaten = plan.count { it.id in eatenIds },
                    total = plan.size,
                    kcalEaten = plan.filter { it.id in eatenIds }.sumOf { it.kcal },
                    kcalPlan = plan.sumOf { it.kcal },
                    onEat = { nextMeal?.let { vm.toggleEaten(it) } },
                )
            }
        }
        item {
            CleanCard {
                Row(Modifier.height(IntrinsicSize.Min), verticalAlignment = Alignment.Top) {
                    Stat(
                        stringResource(R.string.health_bmi),
                        bmiText(kg, profile?.heightCm),
                        bmiNote(kg, profile?.heightCm),
                        Modifier.weight(1f).combinedClickable(
                            onClick = { if (kg != null && profile?.heightCm != null) history = HealthMetric.BMI else editingProfile = true },
                            onLongClick = { editingProfile = true },
                        ),
                    )
                    VerticalDivider(Modifier.padding(horizontal = 10.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    Stat(
                        stringResource(R.string.health_weight),
                        kg?.let { formatKg(it) } ?: "—",
                        if (kg != null) "kg" else stringResource(R.string.health_weight_hint),
                        Modifier.weight(1f).clickable { if (weights.isEmpty()) addingWeight = true else history = HealthMetric.WEIGHT },
                    )
                    VerticalDivider(Modifier.padding(horizontal = 10.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    Column(Modifier.weight(1f).clickable { history = HealthMetric.STEPS }) {
                        val counting = (hasSensor && stepsAllowed) || hcGranted
                        Stat(stringResource(R.string.health_steps), if (counting) "%,d".format(steps) else "—", "/ %,d".format(stepGoal), Modifier)
                        if (!counting && canLinkSteps && !(hasSensor && !stepsAllowed)) {
                            Text(
                                stringResource(R.string.health_connect_short),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.padding(top = 4.dp).clickable { linkSteps() },
                            )
                        } else if (hasSensor && !stepsAllowed && !hcGranted) {
                            Text(
                                stringResource(R.string.health_steps_allow),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.padding(top = 4.dp).clickable { Steps.permission?.let { askSteps.launch(it) } },
                            )
                        } else if (counting) {
                            Spacer(Modifier.height(8.dp))
                            ThinBar((steps.toFloat() / stepGoal).coerceIn(0f, 1f))
                        }
                    }
                }
                if (weights.size >= 2) {
                    Spacer(Modifier.height(12.dp))
                    TrendLine(weights.takeLast(12).map { it.kg }, MaterialTheme.colorScheme.secondary, Modifier.fillMaxWidth().height(28.dp))
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionTitle(stringResource(R.string.health_meals), Modifier.weight(1f))
                IconButton(onClick = { pickPlan.launch(arrayOf("image/*", "application/pdf")) }) {
                    Icon(Icons.Filled.PhotoCamera, stringResource(R.string.health_import), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { pasting = true }) {
                    Icon(Icons.Filled.ContentPaste, stringResource(R.string.health_paste), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val on = reminders ?: true
                IconButton(onClick = { vm.setReminders(!on) }) {
                    Icon(
                        if (on) Icons.Filled.NotificationsActive else Icons.Filled.NotificationsOff,
                        stringResource(R.string.health_reminders),
                        tint = if (on) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item {
            CleanCard(padding = 0.dp) {
                if (plan.isEmpty()) {
                    Text(
                        stringResource(R.string.health_meals_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
                plan.forEach { meal ->
                    MealRow(
                        meal = meal,
                        eaten = meal.id in eatenIds,
                        next = meal.id == nextMeal?.id,
                        modifier = Modifier.combinedClickable(onClick = { vm.toggleEaten(meal) }, onLongClick = { editingMeal = meal }),
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                AddRow(stringResource(R.string.health_add_meal)) { editingMeal = PlanMeal(minuteOfDay = 12 * 60 + 30, name = "") }
            }
        }
        item { SectionTitle(stringResource(R.string.health_other_food)) }
        item {
            CleanCard(padding = 0.dp) {
                extraFood.forEach { f ->
                    FoodRow(f, onDelete = { vm.deleteFood(f) })
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                AddRow(stringResource(R.string.health_add_food)) { addingFood = true }
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

    val stepsHelp: @Composable () -> Unit = { HealthConnectButton(linkSteps) }
    history?.let { metric ->
        val values = when (metric) {
            HealthMetric.WEIGHT -> weights.map { DayValue(it.at.toDay(), it.kg, it.id) }
            HealthMetric.BMI -> profile?.heightCm?.let { h -> weights.map { DayValue(it.at.toDay(), Health.bmi(it.kg, h)) } }.orEmpty()
            HealthMetric.STEPS -> allSteps.mapNotNull { s ->
                runCatching { java.time.LocalDate.parse(s.day) }.getOrNull()?.let { DayValue(it, s.count.toDouble()) }
            }
        }
        HealthHistorySheet(
            metric = metric,
            values = values,
            onDismiss = { history = null },
            onAddWeight = { addingWeight = true },
            onDeleteWeight = { vm.deleteWeight(it) },
            stepsHelp = stepsHelp.takeIf { metric == HealthMetric.STEPS && canLinkSteps },
        )
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

/** A plain paper card, the base of every block on this screen. */
@Composable
private fun CleanCard(modifier: Modifier = Modifier, padding: androidx.compose.ui.unit.Dp = 16.dp, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(24.dp)).then(modifier).padding(padding),
    ) { content() }
}

@Composable
private fun ThinBar(progress: Float, color: Color = MaterialTheme.colorScheme.secondary) {
    Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(MaterialTheme.colorScheme.outlineVariant)) {
        Box(Modifier.fillMaxWidth(progress).height(4.dp).clip(CircleShape).background(color))
    }
}

@Composable
private fun Stat(label: String, value: String, note: String, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.headlineSmall.copy(fontFamily = Mono), maxLines = 1)
        Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
    }
}

private fun bmiText(kg: Double?, heightCm: Double?): String =
    if (kg == null || heightCm == null) "—" else String.format(java.util.Locale.getDefault(), "%.1f", Health.bmi(kg, heightCm))

@Composable
private fun bmiNote(kg: Double?, heightCm: Double?): String =
    if (kg == null || heightCm == null) stringResource(R.string.health_bmi_missing)
    else stringResource(
        when (Health.category(Health.bmi(kg, heightCm))) {
            BmiCategory.UNDER -> R.string.bmi_under
            BmiCategory.NORMAL -> R.string.bmi_normal
            BmiCategory.OVER -> R.string.bmi_over
            BmiCategory.OBESE -> R.string.bmi_obese
        },
    )

/** Today at a glance: what is left of the day's balance, and eaten against burned. */
@Composable
private fun BalanceCard(b: DayBalance, goal: HealthGoal?) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    CleanCard(padding = 20.dp) {
        Text(stringResource(R.string.health_today), style = MaterialTheme.typography.labelLarge, color = muted)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                "${abs(b.deficit)}",
                style = MaterialTheme.typography.displayMedium.copy(fontFamily = Mono),
                color = if (b.deficit >= 0) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(if (b.deficit >= 0) R.string.health_deficit else R.string.health_surplus),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        ThinBar(if (b.burned > 0) (b.eaten.toFloat() / b.burned).coerceIn(0f, 1f) else 0f)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.health_eaten), style = MaterialTheme.typography.labelMedium, color = muted)
            Spacer(Modifier.width(6.dp))
            Text("${b.eaten}", style = MaterialTheme.typography.labelLarge.copy(fontFamily = Mono), modifier = Modifier.weight(1f))
            Text(stringResource(R.string.health_burned), style = MaterialTheme.typography.labelMedium, color = muted)
            Spacer(Modifier.width(6.dp))
            Text("${b.burned} kcal", style = MaterialTheme.typography.labelLarge.copy(fontFamily = Mono))
        }
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.health_burned_detail, b.resting, b.workouts, b.steps), style = MaterialTheme.typography.bodySmall, color = muted)
        if (goal != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(
                    when (goal) {
                        HealthGoal.LOSE -> R.string.health_goal_lose
                        HealthGoal.KEEP -> R.string.health_goal_keep
                        HealthGoal.GAIN -> R.string.health_goal_gain
                    },
                ),
                style = MaterialTheme.typography.bodySmall,
                color = muted,
            )
        }
    }
}

@Composable
private fun AddRow(label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
    }
}

@Composable
private fun MealRow(meal: PlanMeal, eaten: Boolean, next: Boolean, modifier: Modifier) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier.fillMaxWidth().then(modifier).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(if (next) MaterialTheme.colorScheme.secondary else Color.Transparent))
        Spacer(Modifier.width(8.dp))
        Text(clock(meal.minuteOfDay), style = MaterialTheme.typography.labelLarge.copy(fontFamily = Mono), color = if (next) MaterialTheme.colorScheme.onSurface else muted, modifier = Modifier.width(52.dp))
        Column(Modifier.weight(1f)) {
            Text(
                meal.name,
                style = MaterialTheme.typography.titleSmall,
                textDecoration = if (eaten) TextDecoration.LineThrough else null,
                color = if (eaten) muted else MaterialTheme.colorScheme.onSurface,
            )
            val detail = listOf(meal.food, if (meal.kcal > 0) "${meal.kcal} kcal" else "").filter { it.isNotBlank() }.joinToString(" · ")
            if (detail.isNotEmpty()) Text(detail, style = MaterialTheme.typography.bodySmall, color = muted, maxLines = 2)
        }
        Box(
            Modifier.size(24.dp).clip(CircleShape)
                .background(if (eaten) MaterialTheme.colorScheme.onSurface else Color.Transparent)
                .border(1.5.dp, if (eaten) Color.Transparent else MaterialTheme.colorScheme.outlineVariant, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (eaten) Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.surface, modifier = Modifier.size(14.dp))
        }
    }
}

/** The meal plan at the centre of the screen: the next meal, big, and how the day is going. */
@Composable
private fun MealHero(next: PlanMeal?, eaten: Int, total: Int, kcalEaten: Int, kcalPlan: Int, onEat: () -> Unit) {
    val ink = com.uniplanner.app.ui.theme.Ink
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Color(0xFFDDEBD0)).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            stringResource(if (next != null) R.string.meal_next else R.string.meal_all_done),
            style = MaterialTheme.typography.labelLarge,
            color = ink.copy(alpha = 0.65f),
        )
        if (next != null) {
            Text(clock(next.minuteOfDay), style = MaterialTheme.typography.displaySmall.copy(fontFamily = Mono), color = ink)
            Text(next.name, style = MaterialTheme.typography.headlineSmall, color = ink)
            val detail = listOf(next.food, if (next.kcal > 0) "${next.kcal} kcal" else "").filter { it.isNotBlank() }.joinToString(" · ")
            if (detail.isNotEmpty()) {
                Text(detail, style = MaterialTheme.typography.bodyMedium, color = ink.copy(alpha = 0.75f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
            Button(
                onClick = onEat,
                colors = ButtonDefaults.buttonColors(containerColor = ink, contentColor = Color.White),
                modifier = Modifier.padding(top = 6.dp),
            ) {
                Icon(Icons.Filled.Check, null)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.meal_ate))
            }
        } else {
            Text("🎉", style = MaterialTheme.typography.displaySmall)
        }
        Spacer(Modifier.height(4.dp))
        Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(ink.copy(alpha = 0.12f))) {
            Box(Modifier.fillMaxWidth((eaten.toFloat() / total).coerceIn(0f, 1f)).height(8.dp).clip(CircleShape).background(ink))
        }
        Text(
            stringResource(R.string.meal_progress, eaten, total) + if (kcalPlan > 0) " · $kcalEaten / $kcalPlan kcal" else "",
            style = MaterialTheme.typography.labelMedium.copy(fontFamily = Mono),
            color = ink.copy(alpha = 0.7f),
        )
    }
}

@Composable
private fun FoodRow(f: FoodLog, onDelete: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(f.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text("${f.kcal} kcal", style = MaterialTheme.typography.labelLarge.copy(fontFamily = Mono), color = MaterialTheme.colorScheme.onSurfaceVariant)
        IconButton(onClick = onDelete) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.delete), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
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
