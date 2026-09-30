package com.uniplanner.app.phone

import android.content.Context
import com.uniplanner.app.R
import com.uniplanner.app.data.AppDatabase
import com.uniplanner.app.data.Deadline
import com.uniplanner.app.data.DeadlineType
import com.uniplanner.app.reminders.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Keeps tests and deadlines in the phone's agenda: each one gets an event in the calendar the
 * student picked, the event follows changes made in the app, and moving the event in the agenda
 * moves the deadline in the app.
 */
object AgendaSync {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()
    private const val TEST_MINUTES = 120L
    private const val ASSIGNMENT_MINUTES = 30L

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("agenda", Context.MODE_PRIVATE)

    /** Calendars shown in the app; null means every visible calendar. */
    fun shownCalendars(context: Context): Set<Long>? =
        prefs(context).getStringSet("shown", null)?.mapNotNull { it.toLongOrNull() }?.toSet()

    fun setShownCalendars(context: Context, ids: Set<Long>?) {
        prefs(context).edit().apply { if (ids == null) remove("shown") else putStringSet("shown", ids.map { it.toString() }.toSet()) }.apply()
    }

    /** Where UniPlanner writes deadlines and study sessions. */
    fun targetCalendar(context: Context): Long? = prefs(context).getLong("target", -1).takeIf { it >= 0 }

    fun mirrorsDeadlines(context: Context): Boolean = prefs(context).getBoolean("mirror", false)

    fun setTarget(context: Context, calendarId: Long?, mirror: Boolean) {
        val before = targetCalendar(context)
        prefs(context).edit().putLong("target", calendarId ?: -1).putBoolean("mirror", mirror && calendarId != null).apply()
        scope.launch {
            lock.withLock {
                // Moving to another calendar: take the old events out so nothing shows twice.
                if (before != null && before != calendarId || !mirror) removeAll(context)
                if (mirror && calendarId != null) pushAll(context)
            }
        }
    }

    fun eventIdFor(context: Context, deadlineId: Long): Long? =
        prefs(context).getLong("d$deadlineId", -1).takeIf { it >= 0 }

    /** Event ids UniPlanner created for deadlines, so the agenda screen can tell them apart. */
    fun mirroredEventIds(context: Context): Set<Long> =
        prefs(context).all.filterKeys { it.startsWith("d") }.values.mapNotNull { (it as? Long) }.toSet()

    /** Links a deadline to an event the student already had, instead of writing a new one. */
    fun link(context: Context, deadlineId: Long, eventId: Long) {
        prefs(context).edit().putLong("d$deadlineId", eventId).putBoolean("o$deadlineId", true).apply()
    }

    /** True when the event was the student's own before it was linked; UniPlanner never deletes those. */
    private fun ownsEvent(context: Context, deadlineId: Long) = prefs(context).getBoolean("o$deadlineId", false)

    fun onDeadlineChanged(context: Context, deadline: Deadline) {
        if (!mirrorsDeadlines(context) && eventIdFor(context, deadline.id) == null) return
        scope.launch { lock.withLock { runCatching { push(context, deadline) } } }
    }

    fun onDeadlineRemoved(context: Context, deadlineId: Long) {
        val eventId = eventIdFor(context, deadlineId) ?: return
        scope.launch {
            lock.withLock {
                if (!ownsEvent(context, deadlineId)) runCatching { PhoneCalendar.delete(context, eventId) }
                prefs(context).edit().remove("d$deadlineId").remove("o$deadlineId").apply()
            }
        }
    }

    fun eventTimes(deadline: Deadline): Pair<Long, Long> = if (deadline.type == DeadlineType.TEST) {
        deadline.dueAt to deadline.dueAt + TEST_MINUTES * 60_000
    } else {
        deadline.dueAt - ASSIGNMENT_MINUTES * 60_000 to deadline.dueAt
    }

    private suspend fun push(context: Context, deadline: Deadline) {
        if (!PhoneCalendar.hasPermission(context)) return
        val course = AppDatabase.get(context).courses().getAll().firstOrNull { it.id == deadline.courseId }?.name
        val kind = context.getString(if (deadline.type == DeadlineType.TEST) R.string.agenda_event_test else R.string.agenda_event_due)
        val title = listOfNotNull("$kind: ${deadline.title}", course?.let { "($it)" }).joinToString(" ")
        val (begin, end) = eventTimes(deadline)
        val existing = eventIdFor(context, deadline.id)
        if (existing != null && ownsEvent(context, deadline.id)) {
            // The student's own event keeps its title and length; it only moves when the date changes.
            val times = PhoneCalendar.eventTimes(context, existing) ?: return
            val shift = deadline.dueAt - if (deadline.type == DeadlineType.TEST) times.first else times.second
            if (shift != 0L) PhoneCalendar.move(context, existing, times.first + shift, times.second + shift)
            return
        }
        if (existing != null && PhoneCalendar.update(context, existing, title, begin, end)) return
        if (!mirrorsDeadlines(context)) return
        val calendar = targetCalendar(context) ?: return
        val id = PhoneCalendar.insert(context, calendar, title, begin, end) ?: return
        prefs(context).edit().putLong("d${deadline.id}", id).apply()
    }

    private suspend fun pushAll(context: Context) {
        val now = System.currentTimeMillis()
        AppDatabase.get(context).deadlines().getPendingFrom(now).forEach { runCatching { push(context, it) } }
    }

    private fun removeAll(context: Context) {
        val p = prefs(context)
        p.all.keys.filter { it.startsWith("d") }.forEach { key ->
            if (p.getBoolean("o" + key.drop(1), false)) return@forEach
            runCatching { PhoneCalendar.delete(context, p.getLong(key, -1)) }
            p.edit().remove(key).apply()
        }
    }

    /**
     * Brings in changes made in the agenda: a moved event moves its deadline. An event deleted in
     * the agenda only unlinks it; the deadline stays in the app.
     */
    suspend fun pullChanges(context: Context) = lock.withLock {
        if (!PhoneCalendar.hasPermission(context)) return@withLock
        val db = AppDatabase.get(context)
        val p = prefs(context)
        p.all.keys.filter { it.startsWith("d") }.forEach { key ->
            val deadlineId = key.drop(1).toLongOrNull() ?: return@forEach
            val deadline = db.deadlines().getById(deadlineId)
            val times = runCatching { PhoneCalendar.eventTimes(context, p.getLong(key, -1)) }.getOrNull()
            if (deadline == null || times == null) {
                p.edit().remove(key).remove("o$deadlineId").apply()
                return@forEach
            }
            val dueAt = if (deadline.type == DeadlineType.TEST) times.first else times.second
            if (dueAt != deadline.dueAt) {
                val moved = deadline.copy(dueAt = dueAt)
                db.deadlines().update(moved)
                ReminderScheduler.scheduleDeadline(context, moved, updateAgenda = false)
            }
        }
    }
}
