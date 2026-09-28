package com.uniplanner.app.timetable

import android.content.Context
import com.uniplanner.app.domain.ClassSlot
import com.uniplanner.app.domain.ImportedClass
import com.uniplanner.app.domain.Timetable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Where the timetable comes from, so it can update itself. */
enum class TimetableSource { NONE, PORTAL, CALENDAR, PHOTO }

data class TimetableState(
    val slots: List<ClassSlot> = emptyList(),
    val source: TimetableSource = TimetableSource.NONE,
    /** The portal page or calendar link read the last time. */
    val link: String = "",
    val syncedAt: Long = 0,
    /** The portal asked for the login again, so the timetable could not be read. */
    val needsLogin: Boolean = false,
    val remindersOn: Boolean = true,
    val minutesBefore: Int = 10,
    val deletedKeys: Set<String> = emptySet(),
)

/** The weekly timetable, kept on the phone as JSON. */
object TimetableStore {
    private const val PREFS = "timetable"
    private val state = MutableStateFlow<TimetableState?>(null)

    fun flow(ctx: Context): StateFlow<TimetableState?> {
        if (state.value == null) state.value = load(ctx)
        return state
    }

    fun get(ctx: Context): TimetableState = flow(ctx).value ?: TimetableState()

    @Synchronized
    private fun update(ctx: Context, change: (TimetableState) -> TimetableState): TimetableState {
        val next = change(get(ctx))
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("state", encode(next)).apply()
        state.value = next
        return next
    }

    /** A new import: the school's classes, merged with the student's own changes. */
    fun imported(ctx: Context, classes: List<ImportedClass>, source: TimetableSource, link: String) = update(ctx) { s ->
        s.copy(
            slots = Timetable.merge(s.slots, classes, s.deletedKeys) { UUID.randomUUID().toString() },
            source = source,
            link = link,
            syncedAt = System.currentTimeMillis(),
            needsLogin = false,
        )
    }.also { TimetableReminders.rescheduleAll(ctx) }

    fun needsLogin(ctx: Context) = update(ctx) { it.copy(needsLogin = true) }

    /** Saves a class the student added or changed by hand. */
    fun save(ctx: Context, slot: ClassSlot) = update(ctx) { s ->
        val mine = slot.copy(edited = slot.key != null)
        val list = if (s.slots.any { it.id == slot.id }) s.slots.map { if (it.id == slot.id) mine else it } else s.slots + mine
        s.copy(slots = list.sortedWith(compareBy({ it.day }, { it.start })))
    }.also { TimetableReminders.rescheduleAll(ctx) }

    /** Deletes a class; one that came from the school stays deleted after later imports. */
    fun delete(ctx: Context, slot: ClassSlot) = update(ctx) { s ->
        s.copy(slots = s.slots.filter { it.id != slot.id }, deletedKeys = s.deletedKeys + listOfNotNull(slot.key))
    }.also { TimetableReminders.rescheduleAll(ctx) }

    fun setReminders(ctx: Context, on: Boolean, minutesBefore: Int) = update(ctx) {
        it.copy(remindersOn = on, minutesBefore = minutesBefore)
    }.also { TimetableReminders.rescheduleAll(ctx) }

    /** Forgets everything, to import from somewhere else. */
    fun clear(ctx: Context) = update(ctx) { TimetableState(remindersOn = it.remindersOn, minutesBefore = it.minutesBefore) }
        .also { TimetableReminders.rescheduleAll(ctx) }

    private fun load(ctx: Context): TimetableState {
        val raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("state", null) ?: return TimetableState()
        return runCatching { decode(JSONObject(raw)) }.getOrDefault(TimetableState())
    }

    private fun encode(s: TimetableState): String = JSONObject().apply {
        put("slots", JSONArray().apply {
            s.slots.forEach { c ->
                put(JSONObject().apply {
                    put("id", c.id); put("day", c.day); put("start", c.start); put("end", c.end)
                    put("course", c.course); put("room", c.room); put("group", c.group)
                    c.key?.let { put("key", it) }
                    put("edited", c.edited)
                })
            }
        })
        put("source", s.source.name)
        put("link", s.link)
        put("syncedAt", s.syncedAt)
        put("needsLogin", s.needsLogin)
        put("remindersOn", s.remindersOn)
        put("minutesBefore", s.minutesBefore)
        put("deletedKeys", JSONArray(s.deletedKeys.toList()))
    }.toString()

    private fun decode(o: JSONObject): TimetableState {
        val slots = o.optJSONArray("slots") ?: JSONArray()
        val deleted = o.optJSONArray("deletedKeys") ?: JSONArray()
        return TimetableState(
            slots = (0 until slots.length()).map { i ->
                val c = slots.getJSONObject(i)
                ClassSlot(
                    id = c.getString("id"), day = c.getInt("day"), start = c.getInt("start"), end = c.getInt("end"),
                    course = c.optString("course"), room = c.optString("room"), group = c.optString("group"),
                    key = if (c.has("key")) c.getString("key") else null, edited = c.optBoolean("edited"),
                )
            },
            source = runCatching { TimetableSource.valueOf(o.optString("source")) }.getOrDefault(TimetableSource.NONE),
            link = o.optString("link"),
            syncedAt = o.optLong("syncedAt"),
            needsLogin = o.optBoolean("needsLogin"),
            remindersOn = o.optBoolean("remindersOn", true),
            minutesBefore = o.optInt("minutesBefore", 10),
            deletedKeys = (0 until deleted.length()).map { deleted.getString(it) }.toSet(),
        )
    }
}
