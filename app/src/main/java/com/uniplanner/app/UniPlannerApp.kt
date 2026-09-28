package com.uniplanner.app

import android.app.Application
import com.uniplanner.app.health.MealReminders
import com.uniplanner.app.health.Steps
import com.uniplanner.app.online.CloudBackup
import com.uniplanner.app.reminders.Notifications
import com.uniplanner.app.reminders.ReminderScheduler
import com.uniplanner.app.update.UpdateChecker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class UniPlannerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
        ReminderScheduler.scheduleWeeklyPlan(this)
        UpdateChecker.scheduleDailyCheck(this)
        CloudBackup.start(this)
        Steps.schedule(this)
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { runCatching { MealReminders.ensureScheduled(this@UniPlannerApp) } }
    }
}
