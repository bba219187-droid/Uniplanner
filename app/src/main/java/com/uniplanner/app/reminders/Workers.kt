package com.uniplanner.app.reminders

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.uniplanner.app.R
import com.uniplanner.app.data.AppDatabase
import com.uniplanner.app.data.Deadline
import com.uniplanner.app.data.DeadlineType
import com.uniplanner.app.domain.PlanCourse
import com.uniplanner.app.domain.PlanDeadline
import com.uniplanner.app.domain.Planning
import java.text.DateFormat
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters
import java.util.Date
import java.util.concurrent.TimeUnit

/** Shows one reminder for a test or assignment, unless it is already done. */
class DeadlineReminderWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val id = inputData.getLong(KEY_DEADLINE_ID, -1)
        val deadline = AppDatabase.get(applicationContext).deadlines().getById(id)
            ?: return Result.success()
        if (deadline.done) return Result.success()

        val course = AppDatabase.get(applicationContext).courses().getAll()
            .firstOrNull { it.id == deadline.courseId }
        val kind = applicationContext.getString(
            if (deadline.type == DeadlineType.TEST) R.string.type_test else R.string.type_assignment,
        )
        val whenText = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
            .format(Date(deadline.dueAt))
        Notifications.show(
            applicationContext,
            id = id.toInt(),
            channel = Notifications.CHANNEL_DEADLINES,
            title = "$kind: ${deadline.title}",
            text = listOfNotNull(course?.name, whenText).joinToString(" · "),
        )
        return Result.success()
    }

    companion object {
        const val KEY_DEADLINE_ID = "deadline_id"
    }
}

/** Every Monday morning: what is due this week and how long to study per course. */
class WeeklyPlanWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val db = AppDatabase.get(applicationContext)
        val now = System.currentTimeMillis()
        val courses = db.courses().getAll()
        val plan = Planning.buildWeekPlan(
            now = now,
            zone = ZoneId.systemDefault(),
            courses = courses.map { PlanCourse(it.id, it.name) },
            pending = db.deadlines().getPendingFrom(now).map { it.toPlan() },
        )
        val names = courses.associate { it.id to it.name }
        val dayFormat = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
        val text = buildString {
            if (plan.deadlinesThisWeek.isEmpty()) {
                appendLine(applicationContext.getString(R.string.weekly_no_deadlines))
            } else {
                plan.deadlinesThisWeek.forEach {
                    appendLine("• ${dayFormat.format(Date(it.dueAt))} ${names[it.courseId].orEmpty()}: ${it.title}")
                }
            }
            appendLine()
            appendLine(applicationContext.getString(R.string.weekly_study_goals))
            plan.goals.forEach { appendLine("• ${it.name}: ${formatMinutes(it.minutes)}") }
        }.trim()

        Notifications.show(
            applicationContext,
            id = WEEKLY_NOTIFICATION_ID,
            channel = Notifications.CHANNEL_WEEKLY,
            title = applicationContext.getString(R.string.weekly_title, formatMinutes(plan.totalMinutes)),
            text = text,
        )
        return Result.success()
    }

    companion object {
        private const val WEEKLY_NOTIFICATION_ID = 1_000_000
    }
}

fun Deadline.toPlan() = PlanDeadline(courseId, title, type == DeadlineType.TEST, dueAt)

fun formatMinutes(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h == 0 -> "${m}min"
        m == 0 -> "${h}h"
        else -> "${h}h${m.toString().padStart(2, '0')}"
    }
}

object ReminderScheduler {
    private const val WEEKLY_WORK = "weekly-plan"
    private fun deadlineTag(id: Long) = "deadline-$id"

    fun scheduleDeadline(context: Context, deadline: Deadline) {
        val wm = WorkManager.getInstance(context)
        wm.cancelAllWorkByTag(deadlineTag(deadline.id))
        if (deadline.done) return
        val now = System.currentTimeMillis()
        Planning.reminderTimes(deadline.dueAt, now).forEachIndexed { index, at ->
            val request = OneTimeWorkRequestBuilder<DeadlineReminderWorker>()
                .setInitialDelay(at - now, TimeUnit.MILLISECONDS)
                .setInputData(workDataOf(DeadlineReminderWorker.KEY_DEADLINE_ID to deadline.id))
                .addTag(deadlineTag(deadline.id))
                .build()
            wm.enqueueUniqueWork("${deadlineTag(deadline.id)}-$index", ExistingWorkPolicy.REPLACE, request)
        }
    }

    fun cancelDeadline(context: Context, id: Long) {
        WorkManager.getInstance(context).cancelAllWorkByTag(deadlineTag(id))
    }

    /** Runs every 7 days, starting next Monday at 08:00 local time. */
    fun scheduleWeeklyPlan(context: Context) {
        val now = ZonedDateTime.now()
        var next = now.with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY)).with(LocalTime.of(8, 0))
        if (!next.isAfter(now)) next = next.plusWeeks(1)
        val delay = next.toInstant().toEpochMilli() - now.toInstant().toEpochMilli()
        val request = PeriodicWorkRequestBuilder<WeeklyPlanWorker>(7, TimeUnit.DAYS)
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(WEEKLY_WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }
}
