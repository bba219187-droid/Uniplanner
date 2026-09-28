package com.uniplanner.app.domain

import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** Plain inputs so the planning rules can be unit-tested without Android. */
data class PlanCourse(val id: Long, val name: String)

data class PlanDeadline(
    val courseId: Long,
    val title: String,
    val isTest: Boolean,
    val dueAt: Long,
)

data class CourseStudyGoal(val courseId: Long, val name: String, val minutes: Int)

data class WeekPlan(
    val weekStart: Long,
    val weekEnd: Long,
    val deadlinesThisWeek: List<PlanDeadline>,
    val goals: List<CourseStudyGoal>,
) {
    val totalMinutes: Int get() = goals.sumOf { it.minutes }
}

object Planning {
    const val BASE_MINUTES_PER_COURSE = 120
    const val TEST_EXTRA_MINUTES = 240
    const val ASSIGNMENT_EXTRA_MINUTES = 180

    /** The student's weekly hours shared by their courses, at least half an hour each; the default without a goal. */
    fun basePerCourse(weeklyHours: Int?, courses: Int): Int =
        if (weeklyHours == null || courses == 0) BASE_MINUTES_PER_COURSE else maxOf(30, weeklyHours * 60 / courses)

    /** Monday 00:00 of the week that contains [now], in [zone]. */
    fun weekStart(now: Long, zone: ZoneId): Long =
        Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            .atStartOfDay(zone).toInstant().toEpochMilli()

    /**
     * Every course gets a base amount of study time. Tests and assignments
     * add more: the full extra when due this week, half when due next week.
     */
    fun buildWeekPlan(
        now: Long,
        zone: ZoneId,
        courses: List<PlanCourse>,
        pending: List<PlanDeadline>,
        basePerCourse: Int = BASE_MINUTES_PER_COURSE,
    ): WeekPlan {
        val start = weekStart(now, zone)
        val end = Instant.ofEpochMilli(start).atZone(zone).plusWeeks(1).toInstant().toEpochMilli()
        val nextEnd = Instant.ofEpochMilli(start).atZone(zone).plusWeeks(2).toInstant().toEpochMilli()

        val goals = courses.map { course ->
            val extra = pending
                .filter { it.courseId == course.id && it.dueAt >= now && it.dueAt < nextEnd }
                .sumOf { d ->
                    val full = if (d.isTest) TEST_EXTRA_MINUTES else ASSIGNMENT_EXTRA_MINUTES
                    if (d.dueAt < end) full else full / 2
                }
            CourseStudyGoal(course.id, course.name, basePerCourse + extra)
        }.sortedByDescending { it.minutes }

        return WeekPlan(
            weekStart = start,
            weekEnd = end,
            deadlinesThisWeek = pending.filter { it.dueAt in start until end }.sortedBy { it.dueAt },
            goals = goals,
        )
    }

    val REMINDER_OFFSETS: List<Duration> = listOf(
        Duration.ofDays(7),
        Duration.ofDays(3),
        Duration.ofDays(1),
        Duration.ofHours(2),
    )

    /** Reminder times before [dueAt] that are still in the future. */
    fun reminderTimes(dueAt: Long, now: Long): List<Long> =
        REMINDER_OFFSETS.map { dueAt - it.toMillis() }.filter { it > now }
}
