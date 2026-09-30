package com.uniplanner.app.health

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.uniplanner.app.MainActivity
import com.uniplanner.app.R
import com.uniplanner.app.data.AppDatabase
import com.uniplanner.app.data.FoodLog
import com.uniplanner.app.data.PlanMeal
import com.uniplanner.app.reminders.Notifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/** A reminder at the time of each meal in the plan, every day, with a button to say it was eaten. */
object MealReminders {
    private const val TAG = "meal"
    const val KEY_MEAL = "mealId"

    fun nextTime(minuteOfDay: Int, now: LocalDateTime = LocalDateTime.now()): LocalDateTime {
        val today = now.toLocalDate().atStartOfDay().plusMinutes(minuteOfDay.toLong())
        return if (today.isAfter(now)) today else today.plusDays(1)
    }

    fun schedule(ctx: Context, meal: PlanMeal, policy: ExistingWorkPolicy = ExistingWorkPolicy.REPLACE) {
        if (!HealthPrefs.mealRemindersOn(ctx)) return
        val at = nextTime(meal.minuteOfDay).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val request = OneTimeWorkRequestBuilder<MealReminderWorker>()
            .setInitialDelay(at - System.currentTimeMillis(), TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(KEY_MEAL to meal.id))
            .addTag(TAG)
            .build()
        WorkManager.getInstance(ctx).enqueueUniqueWork("$TAG-${meal.id}", policy, request)
    }

    /** Cancels every meal reminder and sets them again from the plan, when reminders are on. */
    suspend fun rescheduleAll(ctx: Context) {
        WorkManager.getInstance(ctx).cancelAllWorkByTag(TAG)
        if (!HealthPrefs.mealRemindersOn(ctx)) return
        AppDatabase.get(ctx).health().plan().forEach { schedule(ctx, it) }
    }

    /**
     * At app start: adds reminders that are missing and leaves the others alone. Cancelling here
     * would also cancel the reminder whose firing is what started the app.
     */
    suspend fun ensureScheduled(ctx: Context) {
        if (!HealthPrefs.mealRemindersOn(ctx)) return
        AppDatabase.get(ctx).health().plan().forEach { schedule(ctx, it, ExistingWorkPolicy.KEEP) }
    }

    fun notificationId(mealId: Long) = 50_000 + (mealId % 10_000).toInt()
}

class MealReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val id = inputData.getLong(MealReminders.KEY_MEAL, -1)
        val meal = AppDatabase.get(ctx).health().plan().firstOrNull { it.id == id } ?: return Result.success()
        if (HealthPrefs.mealRemindersOn(ctx)) show(ctx, meal)
        // Tomorrow at the same time.
        MealReminders.schedule(ctx, meal)
        return Result.success()
    }

    private fun show(ctx: Context, meal: PlanMeal) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val nid = MealReminders.notificationId(meal.id)
        val open = PendingIntent.getActivity(
            ctx, nid, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP), PendingIntent.FLAG_IMMUTABLE,
        )
        val eaten = PendingIntent.getBroadcast(
            ctx, nid,
            Intent(ctx, MealEatenReceiver::class.java).putExtra(MealReminders.KEY_MEAL, meal.id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val text = listOf(meal.food, if (meal.kcal > 0) "${meal.kcal} kcal" else "").filter { it.isNotBlank() }.joinToString(" · ")
        val notification = NotificationCompat.Builder(ctx, Notifications.CHANNEL_MEALS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(ctx.getString(R.string.meal_time, meal.name))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .addAction(0, ctx.getString(R.string.meal_eaten_action), eaten)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(ctx).notify(nid, notification)
    }
}

/** "I ate it" on a meal reminder: logs the meal for today. */
class MealEatenReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(MealReminders.KEY_MEAL, -1)
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dao = AppDatabase.get(context).health()
                val meal = dao.plan().firstOrNull { it.id == id }
                val startOfDay = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                val already = dao.food().any { it.mealId == id && it.at >= startOfDay }
                if (meal != null && !already) {
                    dao.insertFood(FoodLog(at = System.currentTimeMillis(), name = meal.name, kcal = meal.kcal, mealId = meal.id))
                }
                NotificationManagerCompat.from(context).cancel(MealReminders.notificationId(id))
            } finally {
                pending.finish()
            }
        }
    }
}
