package com.uniplanner.app.focus

import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.provider.Settings

/**
 * Study focus: pins the app to the screen so the student cannot leave it, and silences
 * notifications with "Do not disturb" when the phone allows it. Both end when the timer stops.
 */
object FocusMode {
    private const val PREFS = "focus"

    fun isOn(ctx: Context): Boolean = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("on", false)

    fun canSilence(ctx: Context): Boolean =
        ctx.getSystemService(NotificationManager::class.java)?.isNotificationPolicyAccessGranted == true

    /** Opens the system page where the app can be allowed to turn on "Do not disturb". */
    fun askToSilence(ctx: Context) {
        runCatching {
            ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    /** "Usage access": lets focus mode see every app that comes to the front, on any phone. */
    fun hasUsageAccess(ctx: Context): Boolean {
        val ops = ctx.getSystemService(android.app.AppOpsManager::class.java) ?: return false
        val mode = if (android.os.Build.VERSION.SDK_INT >= 29)
            ops.unsafeCheckOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), ctx.packageName)
        else @Suppress("DEPRECATION") ops.checkOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), ctx.packageName)
        return mode == android.app.AppOpsManager.MODE_ALLOWED
    }

    fun askUsageAccess(ctx: Context) {
        runCatching {
            ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    // System screens and phone calls may come up without it being cheating.
    private val allowed = listOf("com.android.systemui", "permissioncontroller", "incallui", "dialer", "telecom", "com.android.server.telecom")

    /** True when another app was opened during this session: full screen, split, floating or pop-up. */
    fun otherAppOpened(ctx: Context): Boolean {
        if (!isOn(ctx)) return false
        val since = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong("since", 0L).takeIf { it > 0 } ?: return false
        val usm = ctx.getSystemService(android.app.usage.UsageStatsManager::class.java) ?: return false
        val events = runCatching { usm.queryEvents(since, System.currentTimeMillis()) }.getOrNull() ?: return false
        val e = android.app.usage.UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            @Suppress("DEPRECATION")
            if (e.eventType == android.app.usage.UsageEvents.Event.MOVE_TO_FOREGROUND && e.packageName != ctx.packageName &&
                e.packageName != "android" && allowed.none { e.packageName.contains(it) }) return true
        }
        return false
    }

    /** Split screen, a pop-up window or picture-in-picture: other apps are usable, so focus cannot run. */
    fun inSplit(activity: Activity?): Boolean =
        activity != null && (activity.isInMultiWindowMode || activity.isInPictureInPictureMode)

    /** Marks the running session as broken: it will not count, whatever happens next. */
    fun void(ctx: Context) {
        if (isOn(ctx)) ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("left", true).apply()
    }

    /** Starts focus, or returns false when the app is sharing the screen with another one. */
    fun start(activity: Activity): Boolean {
        if (inSplit(activity) || !hasUsageAccess(activity)) return false
        val prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val nm = activity.getSystemService(NotificationManager::class.java)
        if (canSilence(activity) && nm != null) {
            prefs.edit().putInt("filter", nm.currentInterruptionFilter).apply()
            runCatching { nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY) }
        }
        // Android asks once to confirm pinning; after that the app stays on screen until focus ends.
        runCatching { activity.startLockTask() }
        prefs.edit().putBoolean("on", true).putBoolean("left", false).putLong("since", System.currentTimeMillis())
            .putLong("since_rt", android.os.SystemClock.elapsedRealtime()).apply()
        return true
    }

    /** True while focus is on but the app is no longer pinned (unpinned by swiping up, back + recents…). */
    fun unpinned(ctx: Context): Boolean =
        isOn(ctx) && ctx.getSystemService(android.app.ActivityManager::class.java)?.lockTaskModeState ==
            android.app.ActivityManager.LOCK_TASK_MODE_NONE

    /** The app went to the background with the screen on: this focus session no longer counts. */
    fun leftApp(ctx: Context) {
        val screenOn = ctx.getSystemService(android.os.PowerManager::class.java)?.isInteractive != false
        if (isOn(ctx) && screenOn) ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("left", true).apply()
    }

    /** Study done with focus on, as (start, minutes): the only time that counts for achievements. */
    fun log(ctx: Context): List<Pair<Long, Int>> =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("log", "").orEmpty()
            .split(';').mapNotNull { e ->
                val (a, m) = e.split(',').takeIf { it.size == 2 } ?: return@mapNotNull null
                val at = a.toLongOrNull() ?: return@mapNotNull null
                val min = m.toIntOrNull() ?: return@mapNotNull null
                at to min
            }

    fun stop(activity: Activity?, ctx: Context) {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean("on", false)) return
        // Leaving the pin early (back + recents) means the screen was not locked: that time does not count.
        val stillPinned = ctx.getSystemService(android.app.ActivityManager::class.java)?.lockTaskModeState !=
            android.app.ActivityManager.LOCK_TASK_MODE_NONE
        runCatching { activity?.stopLockTask() }
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (canSilence(ctx) && nm != null) {
            val before = prefs.getInt("filter", NotificationManager.INTERRUPTION_FILTER_ALL)
            runCatching { nm.setInterruptionFilter(before) }
        }
        val since = prefs.getLong("since", 0L)
        // Measured on the phone's uptime clock, so changing the date or time cannot add minutes.
        val sinceRt = prefs.getLong("since_rt", 0L)
        val nowRt = android.os.SystemClock.elapsedRealtime()
        val minutes = if (since > 0 && sinceRt in 1..nowRt) ((nowRt - sinceRt) / 60_000).toInt() else 0
        val log = prefs.getString("log", "").orEmpty()
        val entry = if (minutes > 0 && stillPinned && !inSplit(activity) && !prefs.getBoolean("left", false)) "$since,$minutes" else null
        prefs.edit().putBoolean("on", false).remove("since").remove("since_rt")
            .putString("log", listOfNotNull(log.takeIf { it.isNotBlank() }, entry).joinToString(";")).apply()
    }
}
