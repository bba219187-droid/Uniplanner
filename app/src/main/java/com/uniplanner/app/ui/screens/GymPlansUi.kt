package com.uniplanner.app.ui.screens

import android.content.Context
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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

/** The ready-made plans, side by side; the one being followed carries a coral badge. */
@Composable
fun PlanCarousel(chosenId: String?, plans: List<GymPlan>, onOpen: (GymPlan) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(end = 8.dp)) {
        items(plans, key = { it.id }) { plan ->
            Column(
                Modifier.width(210.dp).clip(RoundedCornerShape(24.dp)).background(Color(plan.colorArgb))
                    .clickable { onOpen(plan) }.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(plan.days.first().emoji, fontSize = 24.sp, modifier = Modifier.weight(1f))
                    if (plan.id == chosenId) {
                        Text(
                            "A seguir",
                            color = MaterialTheme.colorScheme.inverseOnSurface,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.inverseSurface).padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }
                Text(plan.name, fontFamily = MaterialTheme.typography.titleLarge.fontFamily, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Ink, maxLines = 1)
                Text(plan.goal, style = MaterialTheme.typography.bodySmall, color = Ink.copy(alpha = 0.7f), maxLines = 2, minLines = 2)
                Text("${plan.level} · ${plan.perWeek}x/semana", fontFamily = Mono, fontSize = 11.sp, color = Ink.copy(alpha = 0.6f))
            }
        }
    }
}

/** "Today's workout": the next day of the plan, ready to start in one tap. */
@Composable
fun PlanTodayCard(plan: GymPlan, day: PlanDay, onStart: () -> Unit, onOpen: () -> Unit) {
    val allMuscles = day.exercises.flatMap { it.muscles }.toSet()
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Color(plan.colorArgb)).clickable(onClick = onOpen).padding(16.dp),
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
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Color(plan.colorArgb)).padding(18.dp),
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
                    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${day.emoji} ${day.name}", fontFamily = MaterialTheme.typography.titleLarge.fontFamily, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                            Text("~${day.minutes} min", fontFamily = Mono, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Button(onClick = { onStart(day) }) {
                            Icon(Icons.Filled.PlayArrow, null)
                            Text("Começar")
                        }
                    }
                }
                items(day.exercises, key = { "e${day.name}${it.name}" }) { e -> ExerciseRow(e) }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun ExerciseRow(e: PlanExercise) {
    var open by remember { mutableStateOf(false) }
    val timed = e.name == "Prancha" || e.name.startsWith("Bicicleta")
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surface)
            .clickable { open = !open }.animateContentSize().padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(64.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) { BodyFigure(e.muscles, Modifier.size(56.dp)) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(e.name, style = MaterialTheme.typography.titleSmall)
                val unit = when {
                    e.name == "Prancha" -> "s"
                    e.name.startsWith("Bicicleta") -> " min"
                    else -> ""
                }
                Text(
                    "${e.sets} × ${e.reps}$unit" + if (e.restSec > 0) " · descanso ${e.restSec}s" else "",
                    fontFamily = Mono,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    e.muscles.joinToString(" · ") { muscleName(it) },
                    fontSize = 12.sp,
                    color = Coral,
                )
            }
        }
        if (open) {
            Spacer(Modifier.height(8.dp))
            Text(e.how, style = MaterialTheme.typography.bodyMedium)
            if (!timed) {
                Text(
                    "Escolhe um peso com que faças as repetições todas com boa técnica e uma ou duas de sobra.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
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
