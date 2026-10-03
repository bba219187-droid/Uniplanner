package com.uniplanner.app.study

import com.uniplanner.app.ui.theme.frame
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uniplanner.app.domain.StudyEntry
import com.uniplanner.app.domain.StudyStats
import com.uniplanner.app.ui.AppViewModel
import com.uniplanner.app.ui.screens.ScreenHeader
import com.uniplanner.app.ui.theme.Bricolage
import com.uniplanner.app.ui.theme.Coral
import com.uniplanner.app.ui.theme.Ink
import com.uniplanner.app.ui.theme.Mono
import java.time.ZoneId

private data class Badge(val emoji: String, val name: String, val how: String, val progress: Int, val goal: Int) {
    val done get() = progress >= goal
}

/** Streaks and badges earned by studying, training and reviewing, to keep the rhythm going. */
@Composable
fun AchievementsScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    // Only study done with focus mode on (the app pinned to the screen) counts here.
    val focused = remember { com.uniplanner.app.focus.FocusMode.log(ctx) }
    val study = focused.map { (at, min) -> com.uniplanner.app.data.StudySession(courseId = 0, startedAt = at, minutes = min) }
    val workouts by vm.workouts.collectAsStateWithLifecycle()
    val deadlines by vm.deadlines.collectAsStateWithLifecycle()
    val flash = Flashcards.flow(ctx).collectAsState().value ?: FlashState()
    val zone = ZoneId.systemDefault()
    val entries = study.map { StudyEntry(it.courseId, it.startedAt, it.minutes) }
    val streak = StudyStats.streakDays(entries, System.currentTimeMillis(), zone)
    val hours = study.sumOf { it.minutes } / 60
    val days = entries.filter { it.minutes > 0 }.map { java.time.Instant.ofEpochMilli(it.startedAt).atZone(zone).toLocalDate() }.toSet()
    val best = longestRun(days.map { it.toEpochDay() }.sorted())
    val gym = workouts.count { it.done }
    val delivered = deadlines.count { it.done }
    val longSession = study.maxOfOrNull { it.minutes } ?: 0
    val early = study.count { java.time.Instant.ofEpochMilli(it.startedAt).atZone(zone).hour < 9 }

    val badges = listOf(
        Badge("🌱", "Primeiro passo", "Regista o primeiro estudo", study.size.coerceAtMost(1), 1),
        Badge("🔥", "Em chamas", "3 dias seguidos a estudar", best, 3),
        Badge("⚡", "Semana perfeita", "7 dias seguidos", best, 7),
        Badge("🏆", "Imparável", "30 dias seguidos", best, 30),
        Badge("📚", "10 horas", "10 horas de estudo no total", hours, 10),
        Badge("🧠", "50 horas", "50 horas de estudo", hours, 50),
        Badge("🎓", "Mestre", "200 horas de estudo", hours, 200),
        Badge("🧘", "Foco total", "Uma sessão de 2 horas", longSession, 120),
        Badge("🌅", "Madrugador", "5 sessões antes das 9h", early, 5),
        Badge("💪", "Ginásio", "10 treinos feitos", gym, 10),
        Badge("🦾", "Atleta", "50 treinos feitos", gym, 50),
        Badge("✅", "Entregue", "10 testes ou trabalhos feitos", delivered, 10),
        Badge("🃏", "Memória", "100 cartões revistos", flash.reviews, 100),
        Badge("🗂️", "Colecionador", "50 cartões criados", flash.cards.size, 50),
    )
    val earned = badges.count { it.done }

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(span = { GridItemSpan(3) }) { ScreenHeader("Conquistas", "$earned de ${badges.size} desbloqueadas") }
        item(span = { GridItemSpan(3) }) { StreakHero(streak, best) }
        item(span = { GridItemSpan(3) }) {
            Text(
                "🔒 Só conta o estudo feito com o modo foco ligado.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        itemsIndexed(badges) { i, b -> BadgeTile(b, i) }
        item(span = { GridItemSpan(3) }) { Spacer(Modifier.height(24.dp)) }
    }
}

private fun longestRun(days: List<Long>): Int {
    var best = 0
    var run = 0
    var prev: Long? = null
    days.forEach { d ->
        run = if (prev != null && d == prev!! + 1) run + 1 else 1
        best = maxOf(best, run)
        prev = d
    }
    return best
}

@Composable
private fun StreakHero(streak: Int, best: Int) {
    val pop = remember { Animatable(0.4f) }
    LaunchedEffect(streak) { pop.snapTo(0.4f); pop.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessLow)) }
    Row(
        Modifier.fillMaxWidth().frame(Ink, 28.dp).padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("🔥", fontSize = 54.sp, modifier = Modifier.graphicsLayer { scaleX = pop.value; scaleY = pop.value; rotationZ = (1 - pop.value) * 40f })
        Spacer(Modifier.padding(8.dp))
        Column {
            Text("$streak", fontFamily = MaterialTheme.typography.headlineMedium.fontFamily, fontWeight = FontWeight.ExtraBold, fontSize = 44.sp, color = Color.White)
            Text(if (streak == 1) "dia seguido em modo foco" else "dias seguidos em modo foco", color = Color.White.copy(alpha = 0.75f))
            Text("Recorde: $best", fontFamily = Mono, fontSize = 12.sp, color = Coral)
        }
    }
}

@Composable
private fun BadgeTile(b: Badge, index: Int) {
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(40L * index)
        appear.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow))
    }
    Column(
        Modifier.fillMaxWidth().graphicsLayer { scaleX = appear.value; scaleY = appear.value; alpha = appear.value.coerceIn(0f, 1f) }
            .clip(RoundedCornerShape(22.dp))
            .background(if (b.done) Color(0xFFF6E6C3) else MaterialTheme.colorScheme.surfaceVariant)
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.aspectRatio(1f).fillMaxWidth(0.6f).clip(CircleShape).background(Color.White.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
            Text(b.emoji, fontSize = 28.sp, modifier = Modifier.alpha(if (b.done) 1f else 0.3f))
        }
        Spacer(Modifier.height(6.dp))
        Text(b.name, fontWeight = FontWeight.Bold, fontSize = 12.sp, textAlign = TextAlign.Center, maxLines = 1, color = if (b.done) Ink else MaterialTheme.colorScheme.onSurface)
        Text(
            if (b.done) b.how else "${b.progress.coerceAtMost(b.goal)}/${b.goal}",
            fontSize = 10.sp,
            lineHeight = 12.sp,
            textAlign = TextAlign.Center,
            maxLines = 2,
            color = if (b.done) Ink.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
