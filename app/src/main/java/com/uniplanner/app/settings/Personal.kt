package com.uniplanner.app.settings

import android.content.Context
import com.uniplanner.app.domain.StudyHours
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalTime

enum class Area { STUDY, GYM, HEALTH, FRIENDS }

enum class StudyTime { MORNING, AFTERNOON, EVENING, ANY }

enum class HealthGoal { LOSE, KEEP, GAIN }

/** The answers from the welcome questions, which shape goals, suggestions and the tabs. */
data class Personal(
    val done: Boolean = false,
    val areas: Set<Area> = Area.entries.toSet(),
    val weeklyStudyHours: Int? = null,
    val studyTime: StudyTime = StudyTime.ANY,
    val gymKinds: List<String> = emptyList(),
    val gymPerWeek: Int? = null,
    val healthGoal: HealthGoal? = null,
    val stepGoal: Int = 8000,
    val name: String = "",
    val avatar: String = "",
    val avatarColor: Int = 0,
    val university: String = "",
    val course: String = "",
    val year: Int? = null,
) {
    val firstName: String get() = name.trim().substringBefore(' ')

    /** When study sessions are suggested in the agenda. */
    val studyHours: StudyHours
        get() = when (studyTime) {
            StudyTime.MORNING -> StudyHours(LocalTime.of(8, 0), LocalTime.of(13, 0))
            StudyTime.AFTERNOON -> StudyHours(LocalTime.of(13, 0), LocalTime.of(19, 0))
            StudyTime.EVENING -> StudyHours(LocalTime.of(18, 0), LocalTime.of(23, 0))
            StudyTime.ANY -> StudyHours()
        }
}

object PersonalSettings {
    private const val PREFS = "personal"
    private val state = MutableStateFlow<Personal?>(null)

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun flow(ctx: Context): StateFlow<Personal?> {
        if (state.value == null) state.value = load(ctx)
        return state
    }

    fun get(ctx: Context): Personal = flow(ctx).value ?: Personal()

    private fun load(ctx: Context): Personal {
        val p = prefs(ctx)
        return Personal(
            done = p.getBoolean("done", false),
            areas = p.getStringSet("areas", null)?.mapNotNull { runCatching { Area.valueOf(it) }.getOrNull() }?.toSet()
                ?: Area.entries.toSet(),
            weeklyStudyHours = p.getInt("studyHours", 0).takeIf { it > 0 },
            studyTime = runCatching { StudyTime.valueOf(p.getString("studyTime", null)!!) }.getOrDefault(StudyTime.ANY),
            gymKinds = p.getString("gymKinds", "").orEmpty().split('\n').filter { it.isNotBlank() },
            gymPerWeek = p.getInt("gymPerWeek", 0).takeIf { it > 0 },
            healthGoal = runCatching { HealthGoal.valueOf(p.getString("healthGoal", null)!!) }.getOrNull(),
            stepGoal = p.getInt("stepGoal", 8000),
            name = p.getString("name", "").orEmpty(),
            avatar = p.getString("avatar", "").orEmpty(),
            avatarColor = p.getInt("avatarColor", 0),
            university = p.getString("university", "").orEmpty(),
            course = p.getString("course", "").orEmpty(),
            year = p.getInt("year", 0).takeIf { it > 0 },
        )
    }

    fun save(ctx: Context, value: Personal) {
        prefs(ctx).edit()
            .putBoolean("done", value.done)
            .putStringSet("areas", value.areas.map { it.name }.toSet())
            .putInt("studyHours", value.weeklyStudyHours ?: 0)
            .putString("studyTime", value.studyTime.name)
            .putString("gymKinds", value.gymKinds.joinToString("\n"))
            .putInt("gymPerWeek", value.gymPerWeek ?: 0)
            .putString("healthGoal", value.healthGoal?.name)
            .putInt("stepGoal", value.stepGoal)
            .putString("name", value.name.trim())
            .putString("avatar", value.avatar)
            .putInt("avatarColor", value.avatarColor)
            .putString("university", value.university.trim())
            .putString("course", value.course.trim())
            .putInt("year", value.year ?: 0)
            .apply()
        state.value = value
    }

    /** Opens the welcome questions again, keeping the answers as the starting point. */
    fun restart(ctx: Context) = save(ctx, get(ctx).copy(done = false))
}
