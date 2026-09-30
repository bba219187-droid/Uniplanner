package com.uniplanner.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uniplanner.app.R
import com.uniplanner.app.domain.Planning
import com.uniplanner.app.domain.StudyEntry
import com.uniplanner.app.domain.StudyStats
import com.uniplanner.app.reminders.formatMinutes
import com.uniplanner.app.ui.AppViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun StatsScreen(vm: AppViewModel) {
    val courses by vm.courses.collectAsStateWithLifecycle()
    val sessions by vm.allStudy.collectAsStateWithLifecycle()

    if (sessions.isEmpty()) {
        EmptyState(stringResource(R.string.stats_empty))
        return
    }
    val zone = ZoneId.systemDefault()
    val now = System.currentTimeMillis()
    val entries = sessions.map { StudyEntry(it.courseId, it.startedAt, it.minutes) }
    val weeks = StudyStats.weeklyTotals(entries, now, zone)
    val thisWeek = weeks.last().second
    val lastWeek = weeks[weeks.size - 2].second
    val weekStart = Planning.weekStart(now, zone)
    val monthStart = Instant.ofEpochMilli(now).atZone(zone).withDayOfMonth(1).toLocalDate().atStartOfDay(zone)
        .toInstant().toEpochMilli()
    val streak = StudyStats.streakDays(entries, now, zone)
    val names = courses.associate { it.id to it.name }
    val colors = courses.associate { it.id to it.color }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ScreenHeader(stringResource(R.string.more_stats)) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile(stringResource(R.string.stats_this_week), formatMinutes(thisWeek), Modifier.weight(1f))
                StatTile(stringResource(R.string.stats_last_week), formatMinutes(lastWeek), Modifier.weight(1f))
                StatTile(
                    stringResource(R.string.stats_streak),
                    pluralStringResource(R.plurals.stats_days, streak, streak),
                    Modifier.weight(1f),
                )
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.stats_weeks), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(12.dp))
                    WeekBars(weeks, zone)
                }
            }
        }
        item {
            CourseBreakdown(
                stringResource(R.string.stats_courses_week),
                StudyStats.perCourse(entries, weekStart, Long.MAX_VALUE), names, colors,
            )
        }
        item {
            CourseBreakdown(
                stringResource(R.string.stats_courses_month),
                StudyStats.perCourse(entries, monthStart, Long.MAX_VALUE), names, colors,
            )
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun WeekBars(weeks: List<Pair<Long, Int>>, zone: ZoneId) {
    val max = weeks.maxOf { it.second }.coerceAtLeast(1)
    val dayMonth = DateTimeFormatter.ofPattern("d/M")
    Row(
        Modifier.fillMaxWidth().height(150.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        weeks.forEachIndexed { i, (start, minutes) ->
            val current = i == weeks.lastIndex
            Column(
                Modifier.weight(1f).fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
            ) {
                if (minutes > 0) {
                    Text(
                        "%.1f".format(minutes / 60.0),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                    )
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height((100f * minutes / max).coerceAtLeast(2f).dp)
                        .background(
                            if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                            RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp),
                        ),
                )
                Text(
                    Instant.ofEpochMilli(start).atZone(zone).format(dayMonth),
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
    }
    Text(
        stringResource(R.string.stats_hours_note),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun CourseBreakdown(title: String, rows: List<Pair<Long, Int>>, names: Map<Long, String>, colors: Map<Long, Long>) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (rows.isEmpty()) {
                Text(stringResource(R.string.stats_nothing_yet), style = MaterialTheme.typography.bodyMedium)
            }
            val max = rows.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1
            rows.forEach { (id, minutes) ->
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(names[id] ?: "?", Modifier.weight(1f), maxLines = 1)
                        Text(formatMinutes(minutes), style = MaterialTheme.typography.bodyMedium)
                    }
                    Box(
                        Modifier
                            .fillMaxWidth(minutes / max.toFloat())
                            .height(8.dp)
                            .background(Color(colors[id] ?: 0xFF3F51B5), RoundedCornerShape(4.dp)),
                    )
                }
            }
        }
    }
}
