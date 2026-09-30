package com.uniplanner.app.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.uniplanner.app.BuildConfig
import com.uniplanner.app.R
import com.uniplanner.app.reminders.Notifications
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

data class AvailableUpdate(val versionCode: Int, val versionName: String)

/**
 * CI publishes the latest APK and a small version.json next to it in the
 * repository's latest GitHub release; both links always point at the newest build.
 */
object UpdateChecker {
    private const val BASE = "https://github.com/bba219187-droid/Uniplanner/releases/latest/download"
    const val APK_URL = "$BASE/UniPlanner.apk"
    private const val VERSION_URL = "$BASE/version.json"
    private const val WORK = "update-check"

    fun parse(json: String): AvailableUpdate {
        val o = JSONObject(json)
        return AvailableUpdate(o.getInt("versionCode"), o.optString("versionName"))
    }

    fun isNewer(update: AvailableUpdate, installedVersionCode: Int): Boolean =
        update.versionCode > installedVersionCode

    /** Returns the newer published build, or null when up to date or offline. */
    suspend fun check(): AvailableUpdate? = withContext(Dispatchers.IO) {
        runCatching {
            val conn = URL(VERSION_URL).openConnection() as HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.instanceFollowRedirects = true
            try {
                if (conn.responseCode != 200) return@runCatching null
                parse(conn.inputStream.bufferedReader().readText())
                    .takeIf { isNewer(it, BuildConfig.VERSION_CODE) }
            } finally {
                conn.disconnect()
            }
        }.getOrNull()
    }

    /** Opens the APK link; the browser downloads it and Android offers to install. */
    fun openDownload(context: Context) {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(APK_URL)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    fun scheduleDailyCheck(context: Context) {
        val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(1, TimeUnit.DAYS).build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }
}

class UpdateCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val update = UpdateChecker.check() ?: return Result.success()
        Notifications.show(
            applicationContext,
            id = UPDATE_NOTIFICATION_ID,
            channel = Notifications.CHANNEL_UPDATES,
            title = applicationContext.getString(R.string.update_title),
            text = applicationContext.getString(R.string.update_text, update.versionName),
        )
        return Result.success()
    }

    companion object {
        private const val UPDATE_NOTIFICATION_ID = 1_000_001
    }
}
