package com.uniplanner.app.domain

import java.time.Instant
import java.time.ZoneId
import kotlin.math.roundToInt

/** A graded test or assignment: its grade and how much it counts for the course, in percent. */
data class GradedItem(val grade: Double, val weightPercent: Int)

data class CourseGrade(
    val courseId: Long,
    val credits: Int,
    /** The final grade if known, else the current grade from graded items. */
    val grade: Double?,
    /** True when [grade] comes from tests and assignments rather than a final grade. */
    val estimated: Boolean,
    /** Share of the course already graded, in percent, when estimated. */
    val gradedPercent: Int,
)

object Grades {
    /**
     * The course grade: the final one when entered; otherwise the weighted mean of what is graded
     * so far. Items without a weight count equally when none of them has a weight.
     */
    fun courseGrade(courseId: Long, credits: Int, finalGrade: Double?, items: List<GradedItem>): CourseGrade {
        if (finalGrade != null) return CourseGrade(courseId, credits, finalGrade, estimated = false, gradedPercent = 100)
        if (items.isEmpty()) return CourseGrade(courseId, credits, null, estimated = true, gradedPercent = 0)
        val weighted = items.filter { it.weightPercent > 0 }
        return if (weighted.isNotEmpty()) {
            val totalWeight = weighted.sumOf { it.weightPercent }
            val grade = weighted.sumOf { it.grade * it.weightPercent } / totalWeight
            CourseGrade(courseId, credits, grade, estimated = true, gradedPercent = totalWeight.coerceAtMost(100))
        } else {
            CourseGrade(courseId, credits, items.map { it.grade }.average(), estimated = true, gradedPercent = 0)
        }
    }

    /**
     * Average weighted by ECTS credits, as universities compute it. Courses without credits count
     * as one credit so they are not silently left out. Null when no course has a grade.
     */
    fun average(courses: List<CourseGrade>, finalOnly: Boolean = false): Double? {
        val graded = courses.filter { it.grade != null && (!finalOnly || !it.estimated) }
        if (graded.isEmpty()) return null
        val weight = graded.sumOf { it.credits.coerceAtLeast(1) }
        return graded.sumOf { it.grade!! * it.credits.coerceAtLeast(1) } / weight
    }

    /**
     * The grade needed in what is left (an exam worth [examPercent]) to end with [target], given
     * [current] on the [gradedPercent] already graded. Above the scale's top means out of reach.
     */
    fun needed(current: Double, gradedPercent: Int, examPercent: Int, target: Double): Double? {
        if (examPercent <= 0) return null
        return (target * 100 - current * gradedPercent) / examPercent
    }

    fun format(grade: Double): String = if (grade == grade.roundToInt().toDouble()) {
        grade.roundToInt().toString()
    } else {
        "%.1f".format(grade)
    }
}

data class StudyEntry(val courseId: Long, val startedAt: Long, val minutes: Int)

object StudyStats {
    /** Minutes per course between [from] (inclusive) and [to] (exclusive), largest first. */
    fun perCourse(entries: List<StudyEntry>, from: Long, to: Long): List<Pair<Long, Int>> =
        entries.filter { it.startedAt in from until to }
            .groupBy { it.courseId }
            .map { (id, list) -> id to list.sumOf { it.minutes } }
            .sortedByDescending { it.second }

    /** Total minutes for each of the last [weeks] weeks (Monday to Sunday), oldest first, ending with this week. */
    fun weeklyTotals(entries: List<StudyEntry>, now: Long, zone: ZoneId, weeks: Int = 8): List<Pair<Long, Int>> {
        val thisWeek = Planning.weekStart(now, zone)
        val starts = (weeks - 1 downTo 0).map { back ->
            Instant.ofEpochMilli(thisWeek).atZone(zone).minusWeeks(back.toLong()).toInstant().toEpochMilli()
        }
        return starts.mapIndexed { i, start ->
            val end = starts.getOrNull(i + 1)
                ?: Instant.ofEpochMilli(start).atZone(zone).plusWeeks(1).toInstant().toEpochMilli()
            start to entries.filter { it.startedAt in start until end }.sumOf { it.minutes }
        }
    }

    /** Days in a row, up to today, with some study logged. Today without study does not break it yet. */
    fun streakDays(entries: List<StudyEntry>, now: Long, zone: ZoneId): Int {
        val days = entries.filter { it.minutes > 0 }
            .map { Instant.ofEpochMilli(it.startedAt).atZone(zone).toLocalDate() }.toSet()
        var day = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        if (day !in days) day = day.minusDays(1)
        var count = 0
        while (day in days) {
            count++
            day = day.minusDays(1)
        }
        return count
    }
}
