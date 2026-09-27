package com.uniplanner.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.uniplanner.app.R
import com.uniplanner.app.data.DeadlineType
import com.uniplanner.app.phone.AgendaViewModel
import com.uniplanner.app.phone.PhoneCalendar
import com.uniplanner.app.ui.AppViewModel
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Date

private enum class ActivityKind { EVENT, TEST, ASSIGNMENT, GYM }

private data class ActivityItem(
    val key: String,
    val kind: ActivityKind,
    val title: String,
    val subtitle: String,
    val start: Long,
    val end: Long?,
    val allDay: Boolean,
    val color: Long,
    val done: Boolean,
)

/** One place for everything coming up: agenda events, tests, deadlines and gym, day by day. */
@Composable
fun ActivitiesScreen(vm: AppViewModel, onOpenAgenda: () -> Unit, agenda: AgendaViewModel = viewModel()) {
    val pager = rememberPagerState(pageCount = { 2 })
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = pager.currentPage) {
            Tab(pager.currentPage == 0, { scope.launch { pager.animateScrollToPage(0) } }, text = { Text(stringResource(R.string.activities_all)) })
            Tab(pager.currentPage == 1, { scope.launch { pager.animateScrollToPage(1) } }, text = { Text(stringResource(R.string.tab_deadlines)) })
        }
        // Swipe left and right between everything and the deadline list.
        HorizontalPager(pager, Modifier.weight(1f)) { page ->
            if (page == 0) {
                AllActivities(vm, agenda, onOpenAgenda, onOpenDeadlines = { scope.launch { pager.animateScrollToPage(1) } })
            } else {
                DeadlinesScreen(vm)
            }
        }
    }
}

@Composable
private fun AllActivities(vm: AppViewModel, agenda: AgendaViewModel, onOpenAgenda: () -> Unit, onOpenDeadlines: () -> Unit) {
    val courses by vm.courses.collectAsStateWithLifecycle()
    val deadlines by vm.deadlines.collectAsStateWithLifecycle()
    val workouts by vm.workouts.collectAsStateWithLifecycle()
    val phone by agenda.state.collectAsStateWithLifecycle()
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { agenda.refresh() }

    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
    val until = today + 14 * 24 * 60 * 60 * 1000L
    val names = courses.associate { it.id to it.name }
    val colors = courses.associate { it.id to it.color }
    val testLabel = stringResource(R.string.type_test)
    val assignmentLabel = stringResource(R.string.type_assignment)
    val gymLabel = stringResource(R.string.tab_gym)

    val all = buildList {
        deadlines.filter { it.dueAt in today until until }.forEach { d ->
            val test = d.type == DeadlineType.TEST
            add(
                ActivityItem(
                    "d${d.id}", if (test) ActivityKind.TEST else ActivityKind.ASSIGNMENT,
                    "${if (test) testLabel else assignmentLabel}: ${d.title}", names[d.courseId].orEmpty(),
                    d.dueAt, null, false, colors[d.courseId] ?: 0xFF888888, d.done,
                ),
            )
        }
        workouts.filter { it.startsAt in today until until }.forEach { w ->
            add(
                ActivityItem(
                    "w${w.id}", ActivityKind.GYM, w.title, gymLabel,
                    w.startsAt, w.startsAt + w.minutes * 60_000L, false, 0xFF43A047, w.done,
                ),
            )
        }
        // Events UniPlanner wrote for deadlines are already listed as those deadlines.
        phone.events.filter { it.eventId !in phone.mirrored && it.end >= today && it.begin < until }.forEach { e ->
            add(
                ActivityItem(
                    "e${e.eventId}_${e.begin}", ActivityKind.EVENT, e.title.ifBlank { "–" }, e.location,
                    e.begin, e.end, e.allDay, (e.color or 0xFF000000.toInt()).toLong() and 0xFFFFFFFF, false,
                ),
            )
        }
    }.sortedWith(compareBy<ActivityItem> { !it.allDay }.thenBy { it.start })

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        if (!phone.permission) {
            item {
                Card(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(stringResource(R.string.activities_connect_agenda), style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = { ask.launch(PhoneCalendar.permissions) }) {
                            Text(stringResource(R.string.agenda_permission_button))
                        }
                    }
                }
            }
        }
        if (all.isEmpty()) {
            item { Text(stringResource(R.string.activities_empty), Modifier.padding(top = 16.dp)) }
        }
        val byDay = all.groupBy { Instant.ofEpochMilli(it.start).atZone(zone).toLocalDate() }.toSortedMap()
        byDay.forEach { (day, dayItems) ->
            item(key = "day$day") {
                Text(
                    dayLabel(day, zone),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                )
            }
            items(dayItems.sortedWith(compareBy<ActivityItem> { !it.allDay }.thenBy { it.start }), key = { it.key }) { item ->
                ActivityRow(item) {
                    when (item.kind) {
                        ActivityKind.EVENT -> onOpenAgenda()
                        ActivityKind.TEST, ActivityKind.ASSIGNMENT -> onOpenDeadlines()
                        ActivityKind.GYM -> Unit
                    }
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
        if (phone.permission) {
            item {
                TextButton(onClick = onOpenAgenda) { Text(stringResource(R.string.week_open_agenda)) }
            }
        }
    }
}

@Composable
private fun dayLabel(day: LocalDate, zone: ZoneId): String {
    val today = LocalDate.now(zone)
    val text = day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL))
    return when (day) {
        today -> stringResource(R.string.activities_today, text)
        today.plusDays(1) -> stringResource(R.string.activities_tomorrow, text)
        else -> text
    }
}

private fun time(millis: Long) = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(millis))

@Composable
private fun ActivityRow(item: ActivityItem, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            when {
                item.allDay -> stringResource(R.string.agenda_all_day)
                item.end != null -> "${time(item.start)}\n${time(item.end)}"
                else -> time(item.start)
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(56.dp),
        )
        Box(Modifier.width(4.dp).height(36.dp).background(Color(item.color), RoundedCornerShape(2.dp)))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                item.title + if (item.done) " ✓" else "",
                style = if (item.kind == ActivityKind.TEST) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyLarge,
                color = if (item.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
            )
            if (item.subtitle.isNotBlank()) {
                Text(item.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}
