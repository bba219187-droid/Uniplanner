package com.uniplanner.app.moodle

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/** Picks up new or moved Moodle deadlines from the calendar link twice a day. */
class CalendarSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        MoodleRepository(applicationContext).syncCalendar()
        Result.success()
    } catch (e: java.io.IOException) {
        Result.retry()
    } catch (e: MoodleException) {
        Result.success()
    }

    companion object {
        private const val WORK = "moodle-calendar-sync"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<CalendarSyncWorker>(12, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK)
        }
    }
}
