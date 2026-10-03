package com.uniplanner.app.health

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.uniplanner.app.data.AppDatabase
import com.uniplanner.app.data.StepDay
import com.uniplanner.app.domain.StepTracker
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/** Counts steps with the phone's own step sensor, read every half hour and when the app opens. */
object Steps {
    private const val WORK = "steps"
    private val lock = Mutex()

    fun hasSensor(ctx: Context): Boolean =
        ctx.getSystemService(SensorManager::class.java)?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null

    val permission: String? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) Manifest.permission.ACTIVITY_RECOGNITION else null

    fun hasPermission(ctx: Context): Boolean =
        permission == null || ContextCompat.checkSelfPermission(ctx, permission) == PackageManager.PERMISSION_GRANTED

    fun schedule(ctx: Context) {
        if (!(hasSensor(ctx) && hasPermission(ctx)) && !HealthConnectSteps.available(ctx)) return
        val request = PeriodicWorkRequestBuilder<StepWorker>(30, TimeUnit.MINUTES).build()
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** The sensor's count since the phone was switched on, or null when it cannot be read. */
    private suspend fun readCounter(ctx: Context): Long? {
        val manager = ctx.getSystemService(SensorManager::class.java) ?: return null
        val sensor = manager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) ?: return null
        return withTimeoutOrNull(5_000) {
            suspendCancellableCoroutine { cont ->
                val listener = object : SensorEventListener {
                    override fun onSensorChanged(event: SensorEvent) {
                        manager.unregisterListener(this)
                        if (cont.isActive) cont.resume(event.values[0].toLong())
                    }

                    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
                }
                manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
                cont.invokeOnCancellation { manager.unregisterListener(listener) }
            }
        }
    }

    /** Reads the sensor and adds the new steps to the right days. */
    suspend fun refresh(ctx: Context) = lock.withLock {
        // Health Connect, when allowed, has the fuller count (watch, Samsung Health, other apps).
        val fromHealthConnect = HealthConnectSteps.sync(ctx, days = 7)
        if (!hasSensor(ctx) || !hasPermission(ctx)) return@withLock
        val counter = readCounter(ctx) ?: return@withLock
        val (reading, add) = StepTracker.update(HealthPrefs.lastStepReading(ctx), LocalDate.now().toString(), counter)
        val dao = AppDatabase.get(ctx).health()
        // With Health Connect the phone's steps are already in its count; only keep the sensor in step.
        if (add.isNotEmpty() && !fromHealthConnect) {
            val known = dao.steps().associate { it.day to it.count }
            add.forEach { (day, steps) -> dao.putSteps(StepDay(day, (known[day] ?: 0) + steps)) }
        }
        HealthPrefs.saveStepReading(ctx, reading)
    }
}

class StepWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        runCatching { Steps.refresh(applicationContext) }
        return Result.success()
    }
}
