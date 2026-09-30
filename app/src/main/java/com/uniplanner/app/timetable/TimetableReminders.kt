package com.uniplanner.app.timetable

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.uniplanner.app.R
import com.uniplanner.app.domain.ClassSlot
import com.uniplanner.app.domain.Timetable
import com.uniplanner.app.reminders.Notifications
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/** A notice some minutes before each class, with the class and the room. */
object TimetableReminders {
    private const val TAG = "class"
    const val KEY_ID = "classId"

    private fun schedule(ctx: Context, slot: ClassSlot, minutesBefore: Int, policy: ExistingWorkPolicy) {
        val now = LocalDateTime.now()
        var at = Timetable.nextStart(slot, now).minusMinutes(minutesBefore.toLong())
        // Already inside the notice window for this week: the next notice is next week's.
        if (!at.isAfter(now)) at = at.plusWeeks(1)
        val millis = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val request = OneTimeWorkRequestBuilder<ClassReminderWorker>()
            .setInitialDelay(millis - System.currentTimeMillis(), TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(KEY_ID to slot.id))
            .addTag(TAG)
            .build()
        WorkManager.getInstance(ctx).enqueueUniqueWork("$TAG-${slot.id}", policy, request)
    }

    fun rescheduleAll(ctx: Context) {
        WorkManager.getInstance(ctx).cancelAllWorkByTag(TAG)
        val s = TimetableStore.get(ctx)
        if (s.remindersOn) s.slots.forEach { schedule(ctx, it, s.minutesBefore, ExistingWorkPolicy.REPLACE) }
    }

    /** At app start: adds missing notices without touching the one that may be firing. */
    fun ensureScheduled(ctx: Context) {
        val s = TimetableStore.get(ctx)
        if (s.remindersOn) s.slots.forEach { schedule(ctx, it, s.minutesBefore, ExistingWorkPolicy.KEEP) }
    }

    fun next(ctx: Context, slot: ClassSlot) {
        val s = TimetableStore.get(ctx)
        if (s.remindersOn) schedule(ctx, slot, s.minutesBefore, ExistingWorkPolicy.REPLACE)
    }
}

class ClassReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val id = inputData.getString(TimetableReminders.KEY_ID) ?: return Result.success()
        val state = TimetableStore.get(ctx)
        val slot = state.slots.firstOrNull { it.id == id } ?: return Result.success()
        if (state.remindersOn) {
            val title = ctx.getString(R.string.class_notice_title, slot.course, Timetable.time(slot.start))
            val text = listOf(slot.room.ifBlank { ctx.getString(R.string.class_no_room) }, slot.group)
                .filter { it.isNotBlank() }.joinToString(" · ")
            Notifications.show(ctx, 70_000 + Math.floorMod(slot.id.hashCode(), 10_000), Notifications.CHANNEL_CLASSES, title, text)
        }
        // Next week, same time.
        TimetableReminders.next(ctx, slot)
        return Result.success()
    }
}
