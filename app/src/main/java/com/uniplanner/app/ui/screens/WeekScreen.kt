package com.uniplanner.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uniplanner.app.R
import com.uniplanner.app.data.DeadlineType
import com.uniplanner.app.reminders.formatMinutes
import com.uniplanner.app.ui.AppViewModel

@Composable
fun WeekScreen(vm: AppViewModel, onOpenAgenda: () -> Unit) {
    val courses by vm.courses.collectAsStateWithLifecycle()
    val deadlines by vm.deadlines.collectAsStateWithLifecycle()
    val workouts by vm.workouts.collectAsStateWithLifecycle()
    val study by vm.studyThisWeek.collectAsStateWithLifecycle()
    val plan by vm.weekPlan.collectAsStateWithLifecycle()

    if (courses.isEmpty()) {
        EmptyState(stringResource(R.string.week_empty))
        return
    }

    val colors = courses.associate { it.id to it.color }
    val studiedByCourse = study.groupBy { it.courseId }.mapValues { (_, s) -> s.sumOf { it.minutes } }
    val currentPlan = plan
    val weekDeadlines = currentPlan?.let { p ->
        deadlines.filter { !it.done && it.dueAt in p.weekStart until p.weekEnd }
    }.orEmpty()
    val weekWorkouts = currentPlan?.let { p ->
        workouts.filter { it.startsAt in p.weekStart until p.weekEnd }
    }.orEmpty()

    LazyColumn(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text(
                stringResource(R.string.week_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(top = 16.dp),
            )
            Text(
                stringResource(
                    R.string.week_summary,
                    formatMinutes(study.sumOf { it.minutes }),
                    formatMinutes(currentPlan?.totalMinutes ?: 0),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = onOpenAgenda, modifier = Modifier.padding(top = 8.dp)) {
                Text(stringResource(R.string.week_open_agenda))
            }
        }

        item { SectionTitle(stringResource(R.string.week_deadlines)) }
        if (weekDeadlines.isEmpty()) {
            item { Text(stringResource(R.string.weekly_no_deadlines)) }
        }
        items(weekDeadlines, key = { "d${it.id}" }) { d ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    ColorDot(colors[d.courseId] ?: 0xFF888888)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        val kind = stringResource(
                            if (d.type == DeadlineType.TEST) R.string.type_test else R.string.type_assignment,
                        )
                        Text("$kind: ${d.title}", style = MaterialTheme.typography.titleSmall)
                        Text(formatDateTime(d.dueAt), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        item { SectionTitle(stringResource(R.string.weekly_study_goals)) }
        items(currentPlan?.goals.orEmpty(), key = { "g${it.courseId}" }) { goal ->
            val done = studiedByCourse[goal.courseId] ?: 0
            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ColorDot(colors[goal.courseId] ?: 0xFF888888)
                    Spacer(Modifier.width(8.dp))
                    Text(goal.name, Modifier.weight(1f))
                    Text("${formatMinutes(done)} / ${formatMinutes(goal.minutes)}")
                }
                LinearProgressIndicator(
                    progress = { (done.toFloat() / goal.minutes).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                )
            }
        }

        item { SectionTitle(stringResource(R.string.week_gym)) }
        if (weekWorkouts.isEmpty()) {
            item { Text(stringResource(R.string.gym_empty_week)) }
        }
        items(weekWorkouts, key = { "w${it.id}" }) { w ->
            Text("• ${formatDateTime(w.startsAt)} · ${w.title}" + if (w.done) " ✓" else "")
        }
        item { Spacer(Modifier.padding(8.dp)) }
    }
}
