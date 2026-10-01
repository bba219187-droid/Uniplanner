package com.uniplanner.app

import android.app.Application
import com.uniplanner.app.health.MealReminders
import com.uniplanner.app.health.Steps
import com.uniplanner.app.online.CloudBackup
import com.uniplanner.app.online.CloudSettings
import com.uniplanner.app.reminders.Notifications
import com.uniplanner.app.reminders.ReminderScheduler
import com.uniplanner.app.update.UpdateChecker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

import com.uniplanner.app.timetable.TimetableReminders
import com.uniplanner.app.timetable.TimetableSource
import com.uniplanner.app.timetable.TimetableStore
import com.uniplanner.app.timetable.TimetableSync

class UniPlannerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
        ReminderScheduler.scheduleWeeklyPlan(this)
        UpdateChecker.scheduleDailyCheck(this)
        CloudBackup.start(this)
        CloudSettings.start(this)
        Steps.schedule(this)
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { runCatching { MealReminders.ensureScheduled(this@UniPlannerApp) } }
        runCatching { TimetableReminders.ensureScheduled(this) }
        if (TimetableStore.get(this).source.let { it == TimetableSource.PORTAL || it == TimetableSource.CALENDAR }) TimetableSync.schedule(this)
    }
}
