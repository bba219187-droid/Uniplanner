package com.uniplanner.app

import android.app.Application
import com.uniplanner.app.online.CloudBackup
import com.uniplanner.app.reminders.Notifications
import com.uniplanner.app.reminders.ReminderScheduler
import com.uniplanner.app.update.UpdateChecker

class UniPlannerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
        ReminderScheduler.scheduleWeeklyPlan(this)
        UpdateChecker.scheduleDailyCheck(this)
        CloudBackup.start(this)
    }
}
