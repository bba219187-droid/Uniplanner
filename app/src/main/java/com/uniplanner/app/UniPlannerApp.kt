package com.uniplanner.app

import android.app.Application
import com.uniplanner.app.reminders.Notifications
import com.uniplanner.app.reminders.ReminderScheduler

class UniPlannerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
        ReminderScheduler.scheduleWeeklyPlan(this)
    }
}
