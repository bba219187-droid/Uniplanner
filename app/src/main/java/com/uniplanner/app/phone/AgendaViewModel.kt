package com.uniplanner.app.phone

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.uniplanner.app.R
import com.uniplanner.app.data.AppDatabase
import com.uniplanner.app.data.Course
import com.uniplanner.app.data.Deadline
import com.uniplanner.app.data.DeadlineType
import com.uniplanner.app.domain.Busy
import com.uniplanner.app.domain.Slot
import com.uniplanner.app.domain.StudySuggestion
import com.uniplanner.app.domain.StudySuggestions
import com.uniplanner.app.domain.SuggestionTarget
import com.uniplanner.app.reminders.ReminderScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

data class AgendaUiState(
    val permission: Boolean = false,
    val loading: Boolean = false,
    val calendars: List<PhoneCalendarInfo> = emptyList(),
    /** Null means every visible calendar is shown. */
    val shown: Set<Long>? = null,
    val target: Long? = null,
    val mirror: Boolean = false,
    val events: List<PhoneEvent> = emptyList(),
    val suggestions: List<StudySuggestion> = emptyList(),
    val courses: List<Course> = emptyList(),
    /** Events UniPlanner itself wrote for deadlines. */
    val mirrored: Set<Long> = emptySet(),
    val failed: Boolean = false,
)

class AgendaViewModel(app: Application) : AndroidViewModel(app) {
    private val context get() = getApplication<Application>()
    private val _state = MutableStateFlow(AgendaUiState())
    val state: StateFlow<AgendaUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() = viewModelScope.launch {
        val permission = PhoneCalendar.hasPermission(context)
        _state.update { it.copy(permission = permission, loading = permission) }
        if (!permission) return@launch
        val next = withContext(Dispatchers.IO) {
            runCatching { AgendaSync.pullChanges(context) }
            load()
        }
        _state.value = next
    }

    private suspend fun load(): AgendaUiState {
        val zone = ZoneId.systemDefault()
        val now = System.currentTimeMillis()
        val from = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
        val to = from + DAYS_AHEAD * DAY
        val calendars = PhoneCalendar.calendars(context)
        val shown = AgendaSync.shownCalendars(context)
        val visibleIds = calendars.filter { it.visible }.map { it.id }.toSet()
        val all = PhoneCalendar.events(context, from, to, visibleIds)

        val db = AppDatabase.get(context)
        val courses = db.courses().getAll()
        val names = courses.associate { it.id to it.name }
        val pending = db.deadlines().getPendingFrom(now)
        val targets = pending.map {
            SuggestionTarget(it.id, it.title, names[it.courseId].orEmpty(), it.dueAt, it.type == DeadlineType.TEST, it.weightPercent)
        }
        val booked = targets.associate { t -> t.id to all.count { it.title == studyTitle(t) && it.begin < t.dueAt } }
        // All-day entries (holidays, birthdays) do not take time; everything else does.
        val busy = all.filter { !it.allDay }.map { Busy(it.begin, it.end) }
        val suggestions = StudySuggestions.suggest(targets, busy, now, zone, booked = booked)
            .filter { it.needed > 0 || it.booked > 0 }

        return AgendaUiState(
            permission = true,
            calendars = calendars,
            shown = shown,
            target = AgendaSync.targetCalendar(context),
            mirror = AgendaSync.mirrorsDeadlines(context),
            events = all.filter { it.end >= now && (shown == null || it.calendarId in shown) }
                .filter { it.begin < from + SHOWN_DAYS * DAY },
            suggestions = suggestions,
            courses = courses,
            mirrored = AgendaSync.mirroredEventIds(context),
        )
    }

    fun studyTitle(t: SuggestionTarget): String =
        context.getString(R.string.agenda_study_event, listOf(t.courseName, t.title).filter { it.isNotBlank() }.joinToString(": "))

    fun setShown(ids: Set<Long>?) {
        AgendaSync.setShownCalendars(context, ids)
        refresh()
    }

    fun setTarget(calendarId: Long?, mirror: Boolean) {
        AgendaSync.setTarget(context, calendarId, mirror)
        _state.update { it.copy(target = calendarId, mirror = mirror && calendarId != null) }
    }

    fun book(suggestion: StudySuggestion, slots: List<Slot>) = write {
        val calendar = AgendaSync.targetCalendar(context) ?: return@write
        slots.forEach { PhoneCalendar.insert(context, calendar, studyTitle(suggestion.target), it.start, it.end) }
    }

    fun addEvent(calendarId: Long, title: String, begin: Long, end: Long) = write {
        PhoneCalendar.insert(context, calendarId, title, begin, end, description = "")
    }

    fun updateEvent(event: PhoneEvent, title: String, begin: Long, end: Long) = write {
        PhoneCalendar.update(context, event.eventId, title, begin, end)
    }

    fun deleteEvent(event: PhoneEvent) = write { PhoneCalendar.delete(context, event.eventId) }

    /** Turns an agenda entry (e.g. "Teste de Física") into a test or deadline in the app, linked to it. */
    fun addAsDeadline(event: PhoneEvent, courseId: Long, type: DeadlineType) = write {
        val dueAt = if (type == DeadlineType.TEST) event.begin else event.end
        val deadline = Deadline(courseId = courseId, type = type, title = event.title, dueAt = dueAt)
        val id = AppDatabase.get(context).deadlines().insert(deadline)
        AgendaSync.link(context, id, event.eventId)
        ReminderScheduler.scheduleDeadline(context, deadline.copy(id = id), updateAgenda = false)
    }

    private fun write(block: suspend () -> Unit) = viewModelScope.launch {
        _state.update { it.copy(loading = true, failed = false) }
        val ok = withContext(Dispatchers.IO) { runCatching { block() }.isSuccess }
        _state.value = withContext(Dispatchers.IO) { load() }.copy(failed = !ok)
    }

    companion object {
        private const val DAY = 24 * 60 * 60 * 1000L
        private const val DAYS_AHEAD = 22L
        private const val SHOWN_DAYS = 14L
    }
}
