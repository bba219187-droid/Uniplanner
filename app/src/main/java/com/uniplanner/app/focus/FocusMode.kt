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

    fun start(activity: Activity) {
        val prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val nm = activity.getSystemService(NotificationManager::class.java)
        if (canSilence(activity) && nm != null) {
            prefs.edit().putInt("filter", nm.currentInterruptionFilter).apply()
            runCatching { nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY) }
        }
        // Android asks once to confirm pinning; after that the app stays on screen until focus ends.
        runCatching { activity.startLockTask() }
        prefs.edit().putBoolean("on", true).putLong("since", System.currentTimeMillis()).apply()
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
        val minutes = if (since > 0) ((System.currentTimeMillis() - since) / 60_000).toInt() else 0
        val log = prefs.getString("log", "").orEmpty()
        val entry = if (minutes > 0 && stillPinned) "$since,$minutes" else null
        prefs.edit().putBoolean("on", false).remove("since")
            .putString("log", listOfNotNull(log.takeIf { it.isNotBlank() }, entry).joinToString(";")).apply()
    }
}
