package com.uniplanner.app.phone

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.Instances
import androidx.core.content.ContextCompat
import java.util.TimeZone

data class PhoneCalendarInfo(
    val id: Long,
    val name: String,
    val account: String,
    val color: Int,
    val writable: Boolean,
    val visible: Boolean,
)

data class PhoneEvent(
    val eventId: Long,
    val calendarId: Long,
    val title: String,
    val begin: Long,
    val end: Long,
    val allDay: Boolean,
    val location: String,
    val color: Int,
    val recurring: Boolean,
)

/**
 * The phone's own agenda (Samsung Calendar, Google Calendar and any other app that keeps its
 * events in Android's calendar storage). Everything here needs the calendar permission.
 */
object PhoneCalendar {
    const val MARKER = "UniPlanner"

    val permissions = arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)

    fun hasPermission(context: Context): Boolean = permissions.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    fun calendars(context: Context): List<PhoneCalendarInfo> {
        if (!hasPermission(context)) return emptyList()
        val projection = arrayOf(
            Calendars._ID, Calendars.CALENDAR_DISPLAY_NAME, Calendars.ACCOUNT_NAME,
            Calendars.CALENDAR_COLOR, Calendars.CALENDAR_ACCESS_LEVEL, Calendars.VISIBLE,
        )
        return context.contentResolver.query(Calendars.CONTENT_URI, projection, null, null, null)?.use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        PhoneCalendarInfo(
                            id = c.getLong(0),
                            name = c.getString(1).orEmpty(),
                            account = c.getString(2).orEmpty(),
                            color = c.getInt(3),
                            writable = c.getInt(4) >= Calendars.CAL_ACCESS_CONTRIBUTOR,
                            visible = c.getInt(5) == 1,
                        ),
                    )
                }
            }
        }.orEmpty()
    }

    /** Every occurrence between [from] and [to], repeating events included, from the given calendars. */
    fun events(context: Context, from: Long, to: Long, calendarIds: Set<Long>?): List<PhoneEvent> {
        if (!hasPermission(context)) return emptyList()
        val uri = Instances.CONTENT_URI.buildUpon().let {
            ContentUris.appendId(it, from)
            ContentUris.appendId(it, to)
            it.build()
        }
        val projection = arrayOf(
            Instances.EVENT_ID, Instances.CALENDAR_ID, Instances.TITLE, Instances.BEGIN, Instances.END,
            Instances.ALL_DAY, Instances.EVENT_LOCATION, Instances.DISPLAY_COLOR, Instances.RRULE,
        )
        return context.contentResolver.query(uri, projection, null, null, "${Instances.BEGIN} ASC")?.use { c ->
            buildList {
                while (c.moveToNext()) {
                    val calendarId = c.getLong(1)
                    if (calendarIds != null && calendarId !in calendarIds) continue
                    add(
                        PhoneEvent(
                            eventId = c.getLong(0),
                            calendarId = calendarId,
                            title = c.getString(2).orEmpty(),
                            begin = c.getLong(3),
                            end = c.getLong(4),
                            allDay = c.getInt(5) == 1,
                            location = c.getString(6).orEmpty(),
                            color = c.getInt(7),
                            recurring = !c.getString(8).isNullOrEmpty(),
                        ),
                    )
                }
            }
        }.orEmpty()
    }

    /**
     * Start and end of one event, or null when it was deleted in the agenda or repeats (a repeating
     * event keeps the first date of the series, not the one that was linked).
     */
    fun eventTimes(context: Context, eventId: Long): Pair<Long, Long>? {
        if (!hasPermission(context)) return null
        val uri = ContentUris.withAppendedId(Events.CONTENT_URI, eventId)
        return context.contentResolver.query(uri, arrayOf(Events.DTSTART, Events.DTEND, Events.DELETED, Events.RRULE), null, null, null)
            ?.use { c ->
                val usable = c.moveToFirst() && c.getInt(2) == 0 && c.getString(3).isNullOrEmpty() && !c.isNull(1)
                if (usable) c.getLong(0) to c.getLong(1) else null
            }
    }

    /** Moves an event without touching its title. */
    fun move(context: Context, eventId: Long, begin: Long, end: Long): Boolean {
        if (!hasPermission(context)) return false
        val values = ContentValues().apply {
            put(Events.DTSTART, begin)
            put(Events.DTEND, end)
        }
        return context.contentResolver.update(ContentUris.withAppendedId(Events.CONTENT_URI, eventId), values, null, null) > 0
    }

    fun insert(context: Context, calendarId: Long, title: String, begin: Long, end: Long, description: String = MARKER): Long? {
        if (!hasPermission(context)) return null
        val values = ContentValues().apply {
            put(Events.CALENDAR_ID, calendarId)
            put(Events.TITLE, title)
            put(Events.DTSTART, begin)
            put(Events.DTEND, end)
            put(Events.DESCRIPTION, description)
            put(Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
        }
        return context.contentResolver.insert(Events.CONTENT_URI, values)?.let(ContentUris::parseId)
    }

    /** Changes an event in place; false when it no longer exists. */
    fun update(context: Context, eventId: Long, title: String, begin: Long, end: Long): Boolean {
        if (!hasPermission(context)) return false
        val values = ContentValues().apply {
            put(Events.TITLE, title)
            put(Events.DTSTART, begin)
            put(Events.DTEND, end)
            put(Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
        }
        return context.contentResolver.update(ContentUris.withAppendedId(Events.CONTENT_URI, eventId), values, null, null) > 0
    }

    fun delete(context: Context, eventId: Long) {
        if (!hasPermission(context)) return
        context.contentResolver.delete(ContentUris.withAppendedId(Events.CONTENT_URI, eventId), null, null)
    }

    fun eventUri(eventId: Long) = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
}
