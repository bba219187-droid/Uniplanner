package com.uniplanner.app.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uniplanner.app.R
import com.uniplanner.app.reminders.formatMinutes
import com.uniplanner.app.ui.AppViewModel
import com.uniplanner.app.ui.theme.HeroGradient
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** The first page: the study timer and this week's study time per course, and nothing else. */
@Composable
fun StudyScreen(vm: AppViewModel, onOpenCourses: () -> Unit, onOpenAgenda: () -> Unit, onOpenStats: () -> Unit) {
    val courses by vm.courses.collectAsStateWithLifecycle()
    val sessions by vm.studyThisWeek.collectAsStateWithLifecycle()
    val plan by vm.weekPlan.collectAsStateWithLifecycle()

    if (courses.isEmpty()) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.study_welcome), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.study_need_course), style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(16.dp))
            Button(onClick = onOpenCourses) { Text(stringResource(R.string.study_add_courses)) }
        }
        return
    }

    var selectedId by rememberSaveable { mutableLongStateOf(courses.first().id) }
    // 0 means the timer is not running.
    var startedAt by rememberSaveable { mutableLongStateOf(0L) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var manual by remember { mutableStateOf(false) }

    LaunchedEffect(startedAt) {
        while (startedAt != 0L) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }

    val selected = courses.firstOrNull { it.id == selectedId } ?: courses.first()
    val byCourse = sessions.groupBy { it.courseId }.mapValues { (_, s) -> s.sumOf { it.minutes } }
    val colors = courses.associate { it.id to it.color }
    val studied = sessions.sumOf { it.minutes }
    val planned = plan?.totalMinutes ?: 0

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(Modifier.padding(top = 16.dp)) {
                Text(
                    LocalDate.now().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(stringResource(R.string.study_title), style = MaterialTheme.typography.headlineMedium)
            }
        }
        item {
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(HeroGradient).padding(20.dp),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        courses.forEach { c ->
                            val on = c.id == selected.id
                            Surface(
                                onClick = { if (startedAt == 0L) selectedId = c.id },
                                shape = CircleShape,
                                color = if (on) Color.White else Color.White.copy(alpha = 0.18f),
                                contentColor = if (on) MaterialTheme.colorScheme.primary else Color.White,
                            ) {
                                Text(
                                    c.name,
                                    maxLines = 1,
                                    style = MaterialTheme.typography.labelLarge,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    val elapsed = if (startedAt == 0L) 0L else (now - startedAt) / 1000
                    Text(
                        "%02d:%02d:%02d".format(elapsed / 3600, elapsed / 60 % 60, elapsed % 60),
                        style = MaterialTheme.typography.displayMedium,
                        color = Color.White,
                    )
                    Text(selected.name, style = MaterialTheme.typography.titleMedium, color = Color.White.copy(alpha = 0.85f))
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = {
                            if (startedAt == 0L) {
                                startedAt = System.currentTimeMillis()
                                now = startedAt
                            } else {
                                val minutes = ((System.currentTimeMillis() - startedAt) / 60_000).toInt()
                                vm.logStudy(selected.id, startedAt, minutes)
                                startedAt = 0L
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = MaterialTheme.colorScheme.primary),
                        contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                    ) {
                        Icon(if (startedAt == 0L) Icons.Filled.PlayArrow else Icons.Filled.Stop, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(if (startedAt == 0L) R.string.study_start else R.string.study_stop))
                    }
                    TextButton(onClick = { manual = true }) {
                        Text(stringResource(R.string.study_add_manual), color = Color.White)
                    }
                }
            }
        }
        item {
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(stringResource(R.string.week_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Text(
                            "${formatMinutes(studied)} / ${formatMinutes(planned)}",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { if (planned == 0) 0f else (studied.toFloat() / planned).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(10.dp).clip(CircleShape),
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                    plan?.goals.orEmpty().forEach { goal ->
                        val done = byCourse[goal.courseId] ?: 0
                        val color = Color(colors[goal.courseId] ?: 0xFF888888)
                        Column(Modifier.padding(vertical = 6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                ColorDot(colors[goal.courseId] ?: 0xFF888888)
                                Spacer(Modifier.width(8.dp))
                                Text(goal.name, Modifier.weight(1f), maxLines = 1)
                                Text(
                                    "${formatMinutes(done)} / ${formatMinutes(goal.minutes)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            LinearProgressIndicator(
                                progress = { (done.toFloat() / goal.minutes).coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(6.dp).clip(CircleShape),
                                color = color,
                                trackColor = color.copy(alpha = 0.15f),
                            )
                        }
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onOpenAgenda, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.EventAvailable, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.study_when), maxLines = 1)
                }
                FilledTonalButton(onClick = onOpenStats, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.BarChart, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.more_stats), maxLines = 1)
                }
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }

    if (manual) {
        var minutes by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { manual = false },
            title = { Text(stringResource(R.string.study_add_manual)) },
            text = {
                Column {
                    Text(selected.name)
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
                        vm.logStudy(selected.id, System.currentTimeMillis() - m * 60_000L, m)
                        manual = false
                    },
                ) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { manual = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
