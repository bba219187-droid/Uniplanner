package com.uniplanner.app.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.uniplanner.app.data.AppDatabase
import com.uniplanner.app.data.StepDay
import java.time.LocalDate
import java.time.Period

/**
 * Steps from Health Connect, which gathers what Samsung Health, Google Fit, a watch or the phone
 * itself counted. Phones whose step sensor the app cannot read still get their steps this way,
 * and the history goes back to before the app was installed.
 */
object HealthConnectSteps {
    val permissions = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        "android.permission.health.READ_HEALTH_DATA_HISTORY",
    )
    private val stepsPermission = HealthPermission.getReadPermission(StepsRecord::class)

    fun available(ctx: Context): Boolean =
        runCatching { HealthConnectClient.getSdkStatus(ctx) == HealthConnectClient.SDK_AVAILABLE }.getOrDefault(false)

    /** Health Connect is missing or too old and can be installed from the Play Store. */
    fun canInstall(ctx: Context): Boolean =
        runCatching { HealthConnectClient.getSdkStatus(ctx) == HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED }.getOrDefault(false)

    suspend fun granted(ctx: Context): Boolean =
        available(ctx) && runCatching {
            stepsPermission in HealthConnectClient.getOrCreate(ctx).permissionController.getGrantedPermissions()
        }.getOrDefault(false)

    /** Copies the daily totals of the last [days] days into the app; returns false when it could not. */
    suspend fun sync(ctx: Context, days: Long = 400): Boolean {
        if (!granted(ctx)) return false
        return runCatching {
            val client = HealthConnectClient.getOrCreate(ctx)
            val today = LocalDate.now()
            val groups = client.aggregateGroupByPeriod(
                AggregateGroupByPeriodRequest(
                    metrics = setOf(StepsRecord.COUNT_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(
                        today.minusDays(days - 1).atStartOfDay(),
                        today.plusDays(1).atStartOfDay(),
                    ),
                    timeRangeSlicer = Period.ofDays(1),
                ),
            )
            val dao = AppDatabase.get(ctx).health()
            val known = dao.steps().associate { it.day to it.count }
            val fresh = groups.mapNotNull { g ->
                val n = g.result[StepsRecord.COUNT_TOTAL]?.toInt() ?: return@mapNotNull null
                val day = g.startTime.toLocalDate().toString()
                // Keep whichever count is higher, so the phone's own sensor never loses steps.
                if (n > (known[day] ?: 0)) StepDay(day, n) else null
            }
            if (fresh.isNotEmpty()) dao.putAllSteps(fresh)
            true
        }.getOrDefault(false)
    }
}
