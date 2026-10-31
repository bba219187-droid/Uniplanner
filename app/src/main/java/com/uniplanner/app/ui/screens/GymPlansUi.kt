package com.uniplanner.app.ui.screens

import com.uniplanner.app.ui.theme.frame
import android.content.Context
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uniplanner.app.domain.GymPlan
import com.uniplanner.app.domain.Muscle
import com.uniplanner.app.domain.PlanDay
import com.uniplanner.app.domain.PlanExercise
import com.uniplanner.app.ui.theme.Bricolage
import com.uniplanner.app.ui.theme.Coral
import com.uniplanner.app.ui.theme.Ink
import com.uniplanner.app.ui.theme.Mono

/** Remembers the plan the student follows. */
object GymPlanPrefs {
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("gym_plan", Context.MODE_PRIVATE)
    fun chosen(ctx: Context): String? = prefs(ctx).getString("plan", null)
    fun choose(ctx: Context, id: String?) = prefs(ctx).edit().putString("plan", id).apply()
}

private val weekdayShort = listOf("Seg", "Ter", "Qua", "Qui", "Sex", "Sáb", "Dom")

/**
 * A small body, front and back, with the worked muscles in coral: the picture that goes with
 * every exercise of the ready-made plans.
 */
@Composable
fun BodyFigure(muscles: Set<Muscle>, modifier: Modifier = Modifier, base: Color = Ink.copy(alpha = 0.16f)) {
    Canvas(modifier) {
        val half = size.width / 2
        drawBody(Offset(0f, 0f), Size(half, size.height), front = true, muscles, base)
        drawBody(Offset(half, 0f), Size(half, size.height), front = false, muscles, base)
    }
}

private fun DrawScope.drawBody(origin: Offset, area: Size, front: Boolean, muscles: Set<Muscle>, base: Color) {
    val u = minOf(area.width / 10f, area.height / 22f)
    val cx = origin.x + area.width / 2
    val top = origin.y + (area.height - 22 * u) / 2
    fun col(vararg m: Muscle) = if (m.any { it in muscles }) Coral else base
    fun part(x: Float, y: Float, w: Float, h: Float, c: Color) =
        drawRoundRect(c, Offset(cx + x * u, top + y * u), Size(w * u, h * u), CornerRadius(u * 0.9f, u * 0.9f))

    val cardio = Muscle.CARDIO in muscles
    drawCircle(if (cardio) Coral.copy(alpha = 0.55f) else base, radius = 1.5f * u, center = Offset(cx, top + 1.6f * u))
    // Shoulders.
    part(-3.6f, 3.6f, 1.9f, 2.0f, col(Muscle.SHOULDERS))
    part(1.7f, 3.6f, 1.9f, 2.0f, col(Muscle.SHOULDERS))
    if (front) {
        part(-1.65f, 3.6f, 1.6f, 2.6f, col(Muscle.CHEST))
        part(0.05f, 3.6f, 1.6f, 2.6f, col(Muscle.CHEST))
        part(-1.4f, 6.4f, 2.8f, 3.6f, col(Muscle.CORE))
        part(-3.9f, 5.8f, 1.4f, 2.8f, col(Muscle.BICEPS))
        part(2.5f, 5.8f, 1.4f, 2.8f, col(Muscle.BICEPS))
        part(-2.0f, 10.4f, 1.85f, 5.0f, col(Muscle.QUADS))
        part(0.15f, 10.4f, 1.85f, 5.0f, col(Muscle.QUADS))
        part(-1.9f, 15.8f, 1.5f, 4.6f, base)
        part(0.4f, 15.8f, 1.5f, 4.6f, base)
    } else {
        part(-1.65f, 3.6f, 3.3f, 4.4f, col(Muscle.BACK))
        part(-1.4f, 8.2f, 2.8f, 1.8f, col(Muscle.BACK, Muscle.CORE))
        part(-3.9f, 5.8f, 1.4f, 2.8f, col(Muscle.TRICEPS))
        part(2.5f, 5.8f, 1.4f, 2.8f, col(Muscle.TRICEPS))
        part(-2.0f, 10.2f, 1.85f, 2.2f, col(Muscle.GLUTES))
        part(0.15f, 10.2f, 1.85f, 2.2f, col(Muscle.GLUTES))
        part(-2.0f, 12.6f, 1.85f, 2.9f, col(Muscle.HAMSTRINGS))
        part(0.15f, 12.6f, 1.85f, 2.9f, col(Muscle.HAMSTRINGS))
        part(-1.9f, 15.8f, 1.5f, 4.6f, col(Muscle.CALVES))
        part(0.4f, 15.8f, 1.5f, 4.6f, col(Muscle.CALVES))
    }
    // Forearms.
    part(-4.0f, 8.8f, 1.2f, 2.8f, base)
    part(2.8f, 8.8f, 1.2f, 2.8f, base)
}

/** The ready-made plans, one under the other and grouped by kind; the one being followed carries a badge. */
fun LazyListScope.planList(chosenId: String?, plans: List<GymPlan>, onOpen: (GymPlan) -> Unit) {
    plans.groupBy { it.kind }.forEach { (kind, group) ->
        item(key = "pk${kind.name}") {
            Text(
                kind.label.uppercase(),
                fontFamily = Mono,
                fontSize = 11.sp,
                letterSpacing = 1.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        items(group, key = { "p${it.id}" }) { plan -> PlanRow(plan, plan.id == chosenId) { onOpen(plan) } }
    }
}

@Composable
private fun PlanRow(plan: GymPlan, following: Boolean, onClick: () -> Unit) {
    val minutes = plan.days.map { it.minutes }
    val time = if (minutes.min() == minutes.max()) "${minutes.min()} min" else "${minutes.min()}–${minutes.max()} min"
    Row(
        Modifier.fillMaxWidth().frame(MaterialTheme.colorScheme.surface, 20.dp, outlined = true)
            .clickable(onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(Color(plan.colorArgb)),
            contentAlignment = Alignment.Center,
        ) { Text(plan.days.first().emoji, fontSize = 26.sp) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    plan.name,
                    fontFamily = MaterialTheme.typography.titleLarge.fontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    maxLines = 1,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (following) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "A seguir",
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.primary).padding(horizontal = 7.dp, vertical = 2.dp),
                    )
                }
            }
            Text(plan.goal, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            Text(
                "${plan.level} · ${plan.perWeek}x/semana · $time",
                fontFamily = Mono,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        BodyFigure(
            plan.days.flatMap { d -> d.exercises.flatMap { it.muscles } }.toSet(),
            Modifier.size(width = 44.dp, height = 48.dp),
            base = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f),
        )
    }
}

/** "Today's workout": the next day of the plan, ready to start in one tap. */
@Composable
fun PlanTodayCard(plan: GymPlan, day: PlanDay, onStart: () -> Unit, onOpen: () -> Unit) {
    val allMuscles = day.exercises.flatMap { it.muscles }.toSet()
    Row(
        Modifier.fillMaxWidth().frame(Color(plan.colorArgb), 24.dp).clickable(onClick = onOpen).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Treino sugerido · ${plan.name}", fontSize = 12.sp, color = Ink.copy(alpha = 0.65f))
            Text("${day.emoji} ${day.name}", fontFamily = MaterialTheme.typography.titleLarge.fontFamily, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Ink)
            Text("${day.exercises.size} exercícios · ~${day.minutes} min", fontFamily = Mono, fontSize = 12.sp, color = Ink.copy(alpha = 0.7f))
            Spacer(Modifier.height(6.dp))
            Button(onClick = onStart, colors = ButtonDefaults.buttonColors(containerColor = Ink, contentColor = Color.White)) {
                Icon(Icons.Filled.PlayArrow, null)
                Spacer(Modifier.width(4.dp))
                Text("Começar")
            }
        }
        BodyFigure(allMuscles, Modifier.size(width = 96.dp, height = 104.dp))
    }
}

/** Everything about a plan: the week, every day and every exercise with its picture and how to do it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanSheet(
    plan: GymPlan,
    following: Boolean,
    onDismiss: () -> Unit,
    onFollow: (Boolean) -> Unit,
    onStart: (PlanDay) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LazyColumn(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(
                    Modifier.fillMaxWidth().frame(Color(plan.colorArgb), 24.dp).padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(plan.name, fontFamily = MaterialTheme.typography.titleLarge.fontFamily, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp, color = Ink)
                    Text("${plan.level} · ${plan.perWeek} treinos por semana", fontFamily = Mono, fontSize = 12.sp, color = Ink.copy(alpha = 0.7f))
                    Text(plan.about, style = MaterialTheme.typography.bodyMedium, color = Ink)
                }
            }
            item {
                Text("A tua semana", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    (1..7).forEach { wd ->
                        val slot = plan.weekdays.indexOf(wd)
                        val day = if (slot >= 0) plan.days[plan.rotation[slot]] else null
                        Column(
                            Modifier.weight(1f).clip(RoundedCornerShape(14.dp))
                                .background(if (day != null) Color(plan.colorArgb) else MaterialTheme.colorScheme.surfaceVariant)
                                .padding(vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(weekdayShort[wd - 1], fontSize = 11.sp, color = if (day != null) Ink else MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(day?.emoji ?: "😴", fontSize = 16.sp)
                        }
                    }
                }
            }
            item {
                if (following) {
                    OutlinedButton(onClick = { onFollow(false) }, modifier = Modifier.fillMaxWidth()) { Text("Deixar de seguir este plano") }
                } else {
                    Button(onClick = { onFollow(true) }, modifier = Modifier.fillMaxWidth().height(50.dp)) { Text("Seguir este plano") }
                }
            }
            plan.days.forEach { day ->
                item(key = "d${day.name}") {
                    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("${day.emoji} ${day.name}", fontFamily = MaterialTheme.typography.titleLarge.fontFamily, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                                Text(
                                    listOf(day.focus, "~${day.minutes} min", "${day.exercises.size} exercícios", "${day.exercises.sumOf { it.sets }} séries")
                                        .filter { it.isNotBlank() }.joinToString(" · "),
                                    fontFamily = Mono,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Button(onClick = { onStart(day) }) {
                                Icon(Icons.Filled.PlayArrow, null)
                                Text("Começar")
                            }
                        }
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(12.dp),
                        ) {
                            Text("🔥", fontSize = 16.sp)
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text("Aquecimento", style = MaterialTheme.typography.labelLarge)
                                Text(day.warmup, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                itemsIndexed(day.exercises, key = { _, e -> "e${day.name}${e.name}" }) { i, e -> ExerciseRow(i + 1, e) }
            }
            if (plan.tips.isNotEmpty()) {
                item {
                    Column(
                        Modifier.fillMaxWidth().frame(Color(plan.colorArgb), 20.dp).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text("Como evoluir", fontFamily = MaterialTheme.typography.titleLarge.fontFamily, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Ink)
                        plan.tips.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall, color = Ink) }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun ExerciseRow(number: Int, e: PlanExercise) {
    var open by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().frame(MaterialTheme.colorScheme.surface, 20.dp, outlined = true)
            .clickable { open = !open }.animateContentSize().padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "%02d".format(number),
                fontFamily = Mono,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(26.dp),
            )
            Box(
                Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) { BodyFigure(e.muscles, Modifier.size(48.dp)) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(e.name, style = MaterialTheme.typography.titleSmall)
                Text(e.muscles.joinToString(" · ") { muscleName(it) }, fontSize = 12.sp, color = Coral)
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Metric("Séries", "${e.sets}", Modifier.weight(1f))
            Metric(if (e.timed) "Tempo" else "Reps", e.repsText, Modifier.weight(1f))
            Metric("Descanso", if (e.restSec > 0) restText(e.restSec) else "—", Modifier.weight(1f))
        }
        if (open) {
            Spacer(Modifier.height(10.dp))
            Text(e.how, style = MaterialTheme.typography.bodyMedium)
            if (!e.timed) {
                Text(
                    "Escolhe um peso com que faças as repetições todas com boa técnica e uma ou duas de sobra.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        } else {
            Text(
                "Toca para ver como se faz",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

private fun restText(sec: Int): String = when {
    sec < 60 -> "${sec}s"
    sec % 60 == 0 -> "${sec / 60} min"
    else -> "${sec / 60}:%02d".format(sec % 60)
}

@Composable
private fun Metric(label: String, value: String, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1)
        Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

fun muscleName(m: Muscle): String = when (m) {
    Muscle.CHEST -> "Peito"
    Muscle.BACK -> "Costas"
    Muscle.SHOULDERS -> "Ombros"
    Muscle.BICEPS -> "Bíceps"
    Muscle.TRICEPS -> "Tríceps"
    Muscle.CORE -> "Abdómen"
    Muscle.QUADS -> "Quadríceps"
    Muscle.HAMSTRINGS -> "Posteriores"
    Muscle.GLUTES -> "Glúteos"
    Muscle.CALVES -> "Gémeos"
    Muscle.CARDIO -> "Cardio"
}
