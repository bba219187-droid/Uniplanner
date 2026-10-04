package com.uniplanner.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.firebase.auth.FirebaseAuth
import com.uniplanner.app.R
import com.uniplanner.app.data.Course
import com.uniplanner.app.data.DeadlineType
import com.uniplanner.app.domain.StudyEntry
import com.uniplanner.app.domain.StudyStats
import com.uniplanner.app.reminders.formatMinutes
import com.uniplanner.app.ui.AppViewModel
import com.uniplanner.app.ui.theme.AppStyle
import com.uniplanner.app.ui.theme.BigNumber
import com.uniplanner.app.ui.theme.Caveat
import com.uniplanner.app.ui.theme.CourseProgress
import com.uniplanner.app.ui.theme.LocalUniLook
import com.uniplanner.app.ui.theme.Mono
import com.uniplanner.app.ui.theme.courseColor
import com.uniplanner.app.ui.theme.highlighterStroke
import com.uniplanner.app.ui.theme.markerBand
import com.uniplanner.app.ui.theme.wash
import com.uniplanner.app.study.MusicStrip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.draw.rotate
import com.uniplanner.app.ui.theme.courseInk
import com.uniplanner.app.ui.theme.courseTint
import kotlinx.coroutines.delay
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

/** One study session lasts this long before the app suggests a break. */
private const val SESSION_MINUTES = 60

/**
 * The first page: how much was studied this week and one card per course. Tapping a card starts
 * the clock for that course; while it runs, the clock takes the place of the week card.
 */
@Composable
fun StudyScreen(
    vm: AppViewModel,
    onOpenCourses: () -> Unit,
    onOpenAgenda: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenProfile: () -> Unit,
) {
    val courses by vm.courses.collectAsStateWithLifecycle()
    val sessions by vm.studyThisWeek.collectAsStateWithLifecycle()
    val allSessions by vm.allStudy.collectAsStateWithLifecycle()
    val plan by vm.weekPlan.collectAsStateWithLifecycle()
    val deadlines by vm.deadlines.collectAsStateWithLifecycle()

    if (courses.isEmpty()) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.study_welcome), style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.study_need_course), style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(16.dp))
            Button(onClick = onOpenCourses) { Text(stringResource(R.string.study_add_courses)) }
        }
        return
    }

    // Kept on the phone too, so a running timer survives closing the app, a restart or an update.
    val context = LocalContext.current
    val timerPrefs = remember { context.getSharedPreferences("study_timer", android.content.Context.MODE_PRIVATE) }
    var runningId by rememberSaveable { mutableLongStateOf(timerPrefs.getLong("course", 0L)) }
    // 0 means the timer is not running.
    var startedAt by rememberSaveable { mutableLongStateOf(timerPrefs.getLong("startedAt", 0L)) }
    LaunchedEffect(runningId, startedAt) {
        timerPrefs.edit().putLong("course", runningId).putLong("startedAt", startedAt).apply()
    }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var manualFor by remember { mutableStateOf<Course?>(null) }

    var focus by remember { mutableStateOf(com.uniplanner.app.focus.FocusMode.isOn(context)) }
    LaunchedEffect(startedAt) {
        while (startedAt != 0L) {
            now = System.currentTimeMillis()
            // Unpinned, split screen or a pop-up window mid-session: focus ends at once and earns nothing.
            val act = context as? android.app.Activity
            if (focus && (com.uniplanner.app.focus.FocusMode.unpinned(context) || com.uniplanner.app.focus.FocusMode.inSplit(act) ||
                    com.uniplanner.app.focus.FocusMode.otherAppOpened(context) ||
                    !com.uniplanner.app.focus.FocusMode.isOn(context))) {
                if (com.uniplanner.app.focus.FocusMode.isOn(context))
                    android.widget.Toast.makeText(context, context.getString(com.uniplanner.app.R.string.focus_split_lost), android.widget.Toast.LENGTH_LONG).show()
                com.uniplanner.app.focus.FocusMode.void(context)
                com.uniplanner.app.focus.FocusMode.stop(context as? android.app.Activity, context)
                focus = false
            }
            delay(1_000)
        }
    }

    val zone = ZoneId.systemDefault()
    val running = courses.firstOrNull { it.id == runningId && startedAt != 0L }
    val byCourse = sessions.groupBy { it.courseId }.mapValues { (_, s) -> s.sumOf { it.minutes } }
    val goals = plan?.goals.orEmpty().associate { it.courseId to it.minutes }
    val studied = sessions.sumOf { it.minutes }
    val planned = plan?.totalMinutes ?: 0
    val streak = StudyStats.streakDays(allSessions.map { StudyEntry(it.courseId, it.startedAt, it.minutes) }, System.currentTimeMillis(), zone)
    val today = LocalDate.now(zone)
    val next = deadlines.filter { !it.done && it.dueAt > System.currentTimeMillis() }.minByOrNull { it.dueAt }
    val personal by com.uniplanner.app.settings.PersonalSettings.flow(LocalContext.current).collectAsStateWithLifecycle()
    val name = personal?.firstName?.takeIf { it.isNotBlank() }
        ?: FirebaseAuth.getInstance().currentUser?.displayName?.substringBefore(' ')?.takeIf { it.isNotBlank() }


    fun stop(course: Course) {
        com.uniplanner.app.focus.FocusMode.stop(context as? android.app.Activity, context)
        focus = false
        val minutes = ((System.currentTimeMillis() - startedAt) / 60_000).toInt()
        vm.logStudy(course.id, startedAt, minutes)
        startedAt = 0L
        runningId = 0L
    }

    val look = LocalUniLook.current
    val washColor = running?.let { courseColor(it.color) } ?: MaterialTheme.colorScheme.secondaryContainer
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize()
            .then(if (look.style == AppStyle.MARCA) Modifier.wash(washColor, if (look.dark) 0.30f else 0.38f) else Modifier)
            .padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(span = { GridItemSpan(2) }) {
            Row(Modifier.padding(top = 20.dp, bottom = 6.dp), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(
                        today.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)).replaceFirstChar { it.titlecase() },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        if (name != null) stringResource(R.string.study_hello, name) else stringResource(R.string.study_title),
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    personal?.let { p ->
                        val where = listOfNotNull(
                            if (p.secondary) "🏫 ${p.year?.let { "$it.º ano" } ?: "Secundário"}" else "🎓 ${p.year?.let { "$it.º ano" } ?: "Universidade"}",
                            p.course.takeIf { it.isNotBlank() },
                            p.university.takeIf { it.isNotBlank() },
                        ).joinToString(" · ")
                        Text(where, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, maxLines = 1)
                    }
                }
                Surface(onClick = onOpenProfile, shape = CircleShape, modifier = Modifier.size(44.dp)) {
                    com.uniplanner.app.settings.Avatar(personal ?: com.uniplanner.app.settings.Personal(), 44.dp, fallback = name ?: "U")
                }
            }
        }

        item(span = { GridItemSpan(2) }) {
            if (running != null) {
                TimerCard(
                    course = running,
                    elapsedSeconds = (now - startedAt) / 1000,
                    onStop = { stop(running) },
                    onAdd = { manualFor = running },
                    focus = focus,
                    onFocus = {
                        val activity = context as? android.app.Activity
                        if (focus) {
                            com.uniplanner.app.focus.FocusMode.stop(activity, context)
                            focus = false
                        } else if (activity != null) {
                            if (!com.uniplanner.app.focus.FocusMode.canSilence(context)) com.uniplanner.app.focus.FocusMode.askToSilence(context)
                            if (!com.uniplanner.app.focus.FocusMode.hasUsageAccess(context)) {
                                android.widget.Toast.makeText(context, context.getString(com.uniplanner.app.R.string.focus_usage), android.widget.Toast.LENGTH_LONG).show()
                                com.uniplanner.app.focus.FocusMode.askUsageAccess(context)
                            } else if (com.uniplanner.app.focus.FocusMode.start(activity)) focus = true
                            else android.widget.Toast.makeText(context, context.getString(com.uniplanner.app.R.string.focus_split), android.widget.Toast.LENGTH_LONG).show()
                        }
                    },
                )
            } else {
                WeekCard(
                    studied = studied,
                    planned = planned,
                    streak = streak,
                    perDay = (0..6).map { d ->
                        val day = today.with(DayOfWeek.MONDAY).plusDays(d.toLong())
                        sessions.filter { Instant.ofEpochMilli(it.startedAt).atZone(zone).toLocalDate() == day }.sumOf { it.minutes }
                    },
                    todayIndex = today.dayOfWeek.value - 1,
                    segments = courses.mapNotNull { c -> byCourse[c.id]?.let { c.color to it } },
                )
            }
        }

        item(span = { GridItemSpan(2) }) {
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.Bottom) {
                Text(stringResource(R.string.study_courses), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Text(
                    stringResource(R.string.study_tap_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        items(courses, key = { it.id }) { c ->
            CourseCard(
                course = c,
                done = byCourse[c.id] ?: 0,
                goal = goals[c.id] ?: 0,
                active = c.id == running?.id,
                enabled = running == null,
                onClick = {
                    runningId = c.id
                    startedAt = System.currentTimeMillis()
                    now = startedAt
                },
            )
        }

        if (next != null) {
            item(span = { GridItemSpan(2) }) {
                val days = java.time.temporal.ChronoUnit.DAYS.between(
                    today,
                    java.time.Instant.ofEpochMilli(next.dueAt).atZone(zone).toLocalDate(),
                ).toInt()
                val course = courses.firstOrNull { it.id == next.courseId }
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(22.dp))
                        .clickable(onClick = onOpenAgenda).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.secondaryContainer),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text("$days", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        Text(stringResource(R.string.study_days), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(if (next.type == DeadlineType.TEST) R.string.type_test else R.string.type_assignment) + ": " + next.title,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            listOfNotNull(course?.name, formatDateTime(next.dueAt)).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
            }
        }

        item(span = { GridItemSpan(2) }) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 16.dp)) {
                OutlinedButton(onClick = onOpenAgenda, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.EventAvailable, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.study_when), maxLines = 1)
                }
                OutlinedButton(onClick = onOpenStats, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.BarChart, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.more_stats), maxLines = 1)
                }
            }
        }
    }

    manualFor?.let { course ->
        var minutes by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { manualFor = null },
            title = { Text(stringResource(R.string.study_add_manual)) },
            text = {
                Column {
                    Text(course.name)
                    OutlinedTextField(
                        minutes,
                        { minutes = it.filter(Char::isDigit).take(4) },
                        label = { Text(stringResource(R.string.study_minutes)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = (minutes.toIntOrNull() ?: 0) > 0,
                    onClick = {
                        val m = minutes.toInt()
                        vm.logStudy(course.id, System.currentTimeMillis() - m * 60_000L, m)
                        manualFor = null
                    },
                ) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { manualFor = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun WeekCard(studied: Int, planned: Int, streak: Int, perDay: List<Int>, todayIndex: Int, segments: List<Pair<Long, Int>>) {
    when (LocalUniLook.current.style) {
        AppStyle.MARCA, AppStyle.RISO, AppStyle.MARCADOR -> OpenWeek(studied, planned, streak, perDay, todayIndex)
        AppStyle.CLASSICO -> ClassicWeek(studied, planned, streak)
        else -> DarkWeek(studied, planned, streak, perDay, todayIndex, segments)
    }
}

/** No card: the hours of the week stand on the screen itself, in huge numbers, with the look's mark. */
@Composable
private fun OpenWeek(studied: Int, planned: Int, streak: Int, perDay: List<Int>, todayIndex: Int) {
    val look = LocalUniLook.current
    val colors = MaterialTheme.colorScheme
    val fraction = if (planned > 0) (studied.toFloat() / planned).coerceIn(0f, 1f) else 0f
    Column(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.week_title), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        Row(verticalAlignment = Alignment.Bottom) {
            when (look.style) {
                AppStyle.MARCA -> BigNumber(formatMinutes(studied), Modifier.highlighterStroke(colors.secondaryContainer, fraction), size = 76.sp)
                AppStyle.MARCADOR -> BigNumber(formatMinutes(studied), Modifier.markerBand(colors.secondaryContainer, fraction), size = 60.sp)
                else -> BigNumber(formatMinutes(studied), size = 76.sp, aligned = fraction)
            }
            if (planned > 0) {
                Spacer(Modifier.width(10.dp))
                Text(
                    "/ " + formatMinutes(planned),
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 10.dp),
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (streak > 0) {
                if (look.style == AppStyle.MARCADOR) {
                    Text(
                        pluralStringResource(R.plurals.study_streak, streak, streak),
                        fontFamily = Caveat,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        color = colors.secondary,
                        modifier = Modifier.rotate(-3f),
                    )
                } else {
                    Text(
                        pluralStringResource(R.plurals.study_streak, streak, streak),
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.onSecondaryContainer,
                        modifier = Modifier.clip(CircleShape).background(colors.secondaryContainer).padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
            if (planned > studied) {
                Text(
                    stringResource(R.string.week_left, formatMinutes(planned - studied)),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.onSurface,
                    modifier = Modifier.clip(CircleShape).background(colors.surfaceContainerHighest).padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
        val max = (perDay.maxOrNull() ?: 0).coerceAtLeast(1)
        Row(Modifier.fillMaxWidth().height(70.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
            perDay.forEachIndexed { i, m ->
                Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.BottomCenter) {
                        Box(
                            Modifier.fillMaxWidth()
                                .fillMaxHeight((m.toFloat() / max).coerceAtLeast(0.1f))
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    when {
                                        i == todayIndex -> colors.secondaryContainer
                                        i < todayIndex && m > 0 -> colors.onSurface
                                        else -> colors.surfaceContainerHighest
                                    },
                                ),
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        DayOfWeek.of(i + 1).getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Android's own card, filled with the wallpaper's colour. */
@Composable
private fun ClassicWeek(studied: Int, planned: Int, streak: Int) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(colors.primaryContainer).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(stringResource(R.string.week_title), style = MaterialTheme.typography.bodyMedium, color = colors.onPrimaryContainer)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(formatMinutes(studied), style = MaterialTheme.typography.displaySmall, color = colors.onPrimaryContainer)
            if (planned > 0) {
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.study_of, formatMinutes(planned)), style = MaterialTheme.typography.bodyLarge, color = colors.onPrimaryContainer, modifier = Modifier.padding(bottom = 6.dp))
            }
        }
        if (planned > 0) CourseProgress((studied.toFloat() / planned).coerceIn(0f, 1f), colors.primary)
        if (streak > 0) Text(pluralStringResource(R.plurals.study_streak, streak, streak), style = MaterialTheme.typography.bodySmall, color = colors.onPrimaryContainer)
    }
}

@Composable
private fun DarkWeek(studied: Int, planned: Int, streak: Int, perDay: List<Int>, todayIndex: Int, segments: List<Pair<Long, Int>>) {
    val bg = MaterialTheme.colorScheme.inverseSurface
    val fg = MaterialTheme.colorScheme.inverseOnSurface
    val muted = fg.copy(alpha = 0.65f)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(bg).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.week_title), style = MaterialTheme.typography.bodyMedium, color = muted)
                Row(verticalAlignment = Alignment.Bottom) {
                    BigNumber(formatMinutes(studied), size = 56.sp, color = fg)
                    if (planned > 0) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.study_of, formatMinutes(planned)),
                            style = MaterialTheme.typography.bodyLarge,
                            color = muted,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                }
            }
            val max = (perDay.maxOrNull() ?: 0).coerceAtLeast(1)
            Row(Modifier.height(56.dp), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.Bottom) {
                perDay.forEachIndexed { i, m ->
                    Box(
                        Modifier.width(8.dp).fillMaxHeight((m.toFloat() / max).coerceAtLeast(0.08f)).clip(CircleShape)
                            .background(if (i == todayIndex) MaterialTheme.colorScheme.inversePrimary else fg.copy(alpha = 0.22f)),
                    )
                }
            }
        }
        // One stretch per course, in the course's colour, then what is left of the week's plan.
        val total = maxOf(planned, studied).coerceAtLeast(1)
        Row(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            segments.forEach { (color, minutes) ->
                Box(Modifier.weight(minutes.toFloat() / total).fillMaxHeight().background(courseColor(color)))
            }
            val rest = total - segments.sumOf { it.second }
            if (rest > 0) Box(Modifier.weight(rest.toFloat() / total).fillMaxHeight().background(fg.copy(alpha = 0.15f)))
        }
        if (streak > 0) {
            Text(pluralStringResource(R.plurals.study_streak, streak, streak), style = MaterialTheme.typography.bodySmall, color = muted)
        }
    }
}

@Composable
private fun CourseCard(course: Course, done: Int, goal: Int, active: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val look = LocalUniLook.current
    when (look.style) {
        AppStyle.MARCA -> return AlbumCard(course, done, goal, active, enabled, onClick)
        AppStyle.METRO -> return MetroTile(course, done, goal, active, enabled, onClick)
        AppStyle.FITAS -> return TicketCard(course, done, goal, active, enabled, onClick)
        AppStyle.RISO -> return RisoCard(course, done, goal, active, enabled, onClick)
        AppStyle.PAPEL -> return IndexCard(course, done, goal, active, enabled, onClick)
        AppStyle.CLASSICO -> return RingCard(course, done, goal, active, enabled, onClick)
        AppStyle.MARCADOR -> Unit
    }
    val tint = if (look.style == AppStyle.MARCADOR) MaterialTheme.colorScheme.surfaceContainerHigh else courseTint(course.color)
    val ink = if (look.style == AppStyle.MARCADOR) MaterialTheme.colorScheme.onSurface else courseInk(course.color)
    val progress = if (goal > 0) (done.toFloat() / goal).coerceIn(0f, 1f) else 0f
    Column(
        Modifier.fillMaxWidth().height(140.dp).clip(RoundedCornerShape(24.dp)).background(tint)
            .then(if (active) Modifier.border(2.dp, ink, RoundedCornerShape(24.dp)) else Modifier)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.Top) {
            BigNumber(
                if (goal > 0) "${(progress * 100).toInt()}%" else formatMinutes(done),
                Modifier.weight(1f).padding(top = 2.dp),
                size = 30.sp,
                color = ink,
            )
            Box(
                Modifier.size(34.dp).clip(CircleShape).background(MaterialTheme.colorScheme.inverseSurface),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = stringResource(R.string.study_start),
                    tint = MaterialTheme.colorScheme.inverseOnSurface,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (goal > 0 && look.style != AppStyle.MARCADOR) {
                CourseProgress(progress, if (look.style == AppStyle.PAPEL) ink else courseColor(course.color), height = 6.dp)
            }
            Text(
                course.name,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = if (look.style == AppStyle.MARCADOR) Modifier.markerBand(courseColor(course.color), if (goal > 0) progress.coerceAtLeast(0.06f) else 1f) else Modifier,
            )
            if (goal > 0) {
                Text(
                    "${formatMinutes(done)} / ${formatMinutes(goal)}",
                    fontFamily = Mono,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun pct(done: Int, goal: Int) = if (goal > 0) (done.toFloat() / goal).coerceIn(0f, 1f) else 0f

/** Metro: a flat square live tile, name at the bottom, the hours big in the corner. */
@Composable
private fun MetroTile(course: Course, done: Int, goal: Int, active: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val c = courseColor(course.color)
    Box(
        Modifier.fillMaxWidth().height(140.dp).background(c)
            .then(if (active) Modifier.border(3.dp, Color.White) else Modifier)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(10.dp),
    ) {
        BigNumber(formatMinutes(done), Modifier.align(Alignment.TopStart), size = 34.sp, color = Color.White)
        if (goal > 0) {
            Text("${(pct(done, goal) * 100).toInt()}%", Modifier.align(Alignment.TopEnd), color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
        }
        Text(
            course.name.lowercase(), Modifier.align(Alignment.BottomStart),
            color = Color.White, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Fitas: a ticket with a coloured ribbon down the side and a dashed tear line. */
@Composable
private fun TicketCard(course: Course, done: Int, goal: Int, active: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val c = courseColor(course.color)
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier.fillMaxWidth().height(140.dp).clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .then(if (active) Modifier.border(2.dp, c, shape) else Modifier)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
    ) {
        Box(Modifier.width(14.dp).fillMaxHeight().background(c))
        Column(Modifier.weight(1f).padding(12.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text(course.name.uppercase(), style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("- - - - - - - - - - -", color = MaterialTheme.colorScheme.outline, maxLines = 1, fontSize = 10.sp)
            BigNumber(formatMinutes(done), size = 28.sp, color = c)
            if (goal > 0) Text(stringResource(R.string.study_of, formatMinutes(goal)), style = MaterialTheme.typography.labelSmall)
        }
    }
}

/** Riso: a print with the colour layer slipped off the black one, like a risograph. */
@Composable
private fun RisoCard(course: Course, done: Int, goal: Int, active: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val c = courseColor(course.color)
    val ink = MaterialTheme.colorScheme.onSurface
    Box(Modifier.fillMaxWidth().height(144.dp).clickable(enabled = enabled, role = Role.Button, onClick = onClick)) {
        Box(Modifier.matchParentSize().padding(start = 6.dp, top = 6.dp).background(c.copy(alpha = 0.85f)))
        Column(
            Modifier.matchParentSize().padding(end = 6.dp, bottom = 6.dp).background(MaterialTheme.colorScheme.surface)
                .border(if (active) 3.dp else 2.dp, ink).padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(course.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            BigNumber(formatMinutes(done), size = 30.sp, color = c)
            if (goal > 0) CourseProgress(pct(done, goal), c, height = 6.dp)
        }
    }
}

/** Papel: a lined index card with the course written at the top. */
@Composable
private fun IndexCard(course: Course, done: Int, goal: Int, active: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val c = courseColor(course.color)
    val line = MaterialTheme.colorScheme.outlineVariant
    Column(
        Modifier.fillMaxWidth().height(140.dp).background(MaterialTheme.colorScheme.surface)
            .border(if (active) 2.dp else 1.dp, if (active) c else line)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
    ) {
        Box(Modifier.fillMaxWidth().height(4.dp).background(c))
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(course.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            HorizontalDivider(color = c.copy(alpha = 0.5f))
            BigNumber(formatMinutes(done), size = 28.sp, color = MaterialTheme.colorScheme.onSurface)
            HorizontalDivider(color = line)
            if (goal > 0) Text(stringResource(R.string.study_of, formatMinutes(goal)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Clássico: a plain Material card with a progress ring. */
@Composable
private fun RingCard(course: Course, done: Int, goal: Int, active: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val c = courseColor(course.color)
    androidx.compose.material3.ElevatedCard(
        onClick = onClick, enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(140.dp).then(if (active) Modifier.border(2.dp, c, RoundedCornerShape(12.dp)) else Modifier),
    ) {
        Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Box(contentAlignment = Alignment.Center) {
                androidx.compose.material3.CircularProgressIndicator(
                    progress = { pct(done, goal) }, modifier = Modifier.size(54.dp), color = c,
                    trackColor = c.copy(alpha = 0.15f), strokeWidth = 5.dp,
                )
                Text(formatMinutes(done), style = MaterialTheme.typography.labelSmall)
            }
            Text(course.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** The Play look: a course as an album cover in its colour, with the hours in big numbers. */
@Composable
private fun AlbumCard(course: Course, done: Int, goal: Int, active: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val c = courseColor(course.color)
    val dark = Color(0xFF111111)
    val progress = if (goal > 0) (done.toFloat() / goal).coerceIn(0f, 1f) else 0f
    val shape = RoundedCornerShape(20.dp)
    Column(
        Modifier.fillMaxWidth().height(150.dp)
            .then(if (active) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, shape) else Modifier)
            .clip(shape)
            .background(Brush.linearGradient(listOf(lerp(c, Color.White, 0.18f), c, lerp(c, Color.Black, 0.32f))))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(Modifier.padding(start = 14.dp, end = 10.dp, top = 12.dp), verticalAlignment = Alignment.Top) {
            Text(
                course.name,
                style = MaterialTheme.typography.titleSmall,
                color = dark,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Box(Modifier.size(32.dp).clip(CircleShape).background(dark), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.PlayArrow, contentDescription = stringResource(R.string.study_start), tint = c, modifier = Modifier.size(18.dp))
            }
        }
        Column {
            Column(Modifier.padding(horizontal = 14.dp)) {
                BigNumber(formatMinutes(done), size = 30.sp, color = dark)
                if (goal > 0) {
                    Text(stringResource(R.string.study_of, formatMinutes(goal)), style = MaterialTheme.typography.labelMedium, color = dark.copy(alpha = 0.7f))
                }
            }
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth().height(5.dp).background(dark.copy(alpha = 0.15f))) {
                Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(dark.copy(alpha = 0.85f)))
            }
        }
    }
}

@Composable
private fun TimerCard(
    course: Course,
    elapsedSeconds: Long,
    onStop: () -> Unit,
    onAdd: () -> Unit,
    focus: Boolean = false,
    onFocus: () -> Unit = {},
) {
    val look = LocalUniLook.current
    val play = look.style == AppStyle.MARCA
    val tint = courseTint(course.color)
    val ink = if (play) MaterialTheme.colorScheme.onSurface else courseInk(course.color)
    val minutes = (elapsedSeconds / 60).toInt()
    val inSession = minutes % SESSION_MINUTES
    val full = courseColor(course.color)
    val surface = MaterialTheme.colorScheme.surface
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
            .then(
                if (play) Modifier.background(Brush.verticalGradient(listOf(full, lerp(full, surface, 0.6f), lerp(full, surface, 0.88f))))
                else Modifier.background(tint),
            )
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column {
            Text(stringResource(R.string.study_studying), style = MaterialTheme.typography.labelLarge, color = if (play) Color(0xFF111111) else ink)
            Text(
                course.name,
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = if (play) Color(0xFF111111) else MaterialTheme.colorScheme.onSurface,
            )
        }
        val hours = elapsedSeconds >= 3600
        val wide = look.numbers == com.uniplanner.app.ui.theme.Anybody
        BigNumber(
            if (hours) "%d:%02d:%02d".format(elapsedSeconds / 3600, elapsedSeconds / 60 % 60, elapsedSeconds % 60)
            else "%02d:%02d".format(elapsedSeconds / 60, elapsedSeconds % 60),
            size = when {
                hours && wide -> 44.sp
                hours -> 54.sp
                wide -> 60.sp
                else -> 72.sp
            },
            color = MaterialTheme.colorScheme.onSurface,
        )
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.fillMaxWidth().height(10.dp).clip(CircleShape).background(ink.copy(alpha = 0.15f))) {
                Box(
                    Modifier.fillMaxWidth((inSession + (elapsedSeconds % 60) / 60f) / SESSION_MINUTES).fillMaxHeight()
                        .clip(CircleShape).background(ink),
                )
            }
            Text(
                stringResource(R.string.study_break_in, SESSION_MINUTES - inSession),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (look.music) MusicStrip(tint = MaterialTheme.colorScheme.onSurface, onTint = MaterialTheme.colorScheme.surface)
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                .background(if (focus) MaterialTheme.colorScheme.inverseSurface else ink.copy(alpha = 0.10f))
                .clickable(onClick = onFocus).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(if (focus) "🔒" else "🔓", fontSize = 20.sp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (focus) "Modo foco ligado" else "Ligar modo foco",
                    style = MaterialTheme.typography.titleSmall,
                    color = if (focus) MaterialTheme.colorScheme.inverseOnSurface else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    if (focus) "A app fica presa no ecrã e sem notificações até parares."
                    else "Prende a app no ecrã e silencia as notificações.",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (focus) MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = onStop,
                modifier = Modifier.weight(1f).height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                ),
            ) {
                Icon(Icons.Filled.Stop, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.study_stop), style = MaterialTheme.typography.titleSmall)
            }
            FilledIconButton(
                onClick = onAdd,
                modifier = Modifier.size(56.dp),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = ink.copy(alpha = 0.12f), contentColor = ink),
            ) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.study_add_manual))
            }
        }
    }
}
