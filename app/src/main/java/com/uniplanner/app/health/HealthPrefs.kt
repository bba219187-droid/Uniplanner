package com.uniplanner.app.health

import android.content.Context
import com.uniplanner.app.domain.Sex
import com.uniplanner.app.domain.StepReading
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** What the health page needs to know about the student for BMI and calories. */
data class BodyProfile(val heightCm: Double?, val sex: Sex, val birthYear: Int?) {
    val complete: Boolean get() = heightCm != null && birthYear != null
}

object HealthPrefs {
    private const val PREFS = "health"
    const val STEP_GOAL = 8000

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val profile = MutableStateFlow<BodyProfile?>(null)
    private val reminders = MutableStateFlow<Boolean?>(null)

    fun profileFlow(ctx: Context): StateFlow<BodyProfile?> {
        if (profile.value == null) {
            val p = prefs(ctx)
            profile.value = BodyProfile(
                heightCm = p.getFloat("heightCm", 0f).takeIf { it > 0 }?.toDouble(),
                sex = runCatching { Sex.valueOf(p.getString("sex", null)!!) }.getOrDefault(Sex.MALE),
                birthYear = p.getInt("birthYear", 0).takeIf { it > 0 },
            )
        }
        return profile
    }

    fun saveProfile(ctx: Context, value: BodyProfile) {
        prefs(ctx).edit()
            .putFloat("heightCm", (value.heightCm ?: 0.0).toFloat())
            .putString("sex", value.sex.name)
            .putInt("birthYear", value.birthYear ?: 0)
            .apply()
        profile.value = value
    }

    fun remindersFlow(ctx: Context): StateFlow<Boolean?> {
        if (reminders.value == null) reminders.value = mealRemindersOn(ctx)
        return reminders
    }

    fun mealRemindersOn(ctx: Context): Boolean = prefs(ctx).getBoolean("mealReminders", true)

    fun setMealReminders(ctx: Context, on: Boolean) {
        prefs(ctx).edit().putBoolean("mealReminders", on).apply()
        reminders.value = on
    }

    fun lastStepReading(ctx: Context): StepReading? {
        val p = prefs(ctx)
        val day = p.getString("stepDay", null) ?: return null
        return StepReading(day, p.getLong("stepCounter", 0))
    }

    fun saveStepReading(ctx: Context, r: StepReading) {
        prefs(ctx).edit().putString("stepDay", r.day).putLong("stepCounter", r.counter).apply()
    }
}
