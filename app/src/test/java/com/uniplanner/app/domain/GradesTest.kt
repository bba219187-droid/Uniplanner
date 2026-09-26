package com.uniplanner.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class GradesTest {
    @Test
    fun finalGradeWinsOverItems() {
        val g = Grades.courseGrade(1, 6, 15.0, listOf(GradedItem(10.0, 50)))
        assertEquals(15.0, g.grade!!, 0.001)
        assertFalse(g.estimated)
    }

    @Test
    fun weightedItemsGiveTheCurrentGrade() {
        val g = Grades.courseGrade(1, 6, null, listOf(GradedItem(12.0, 40), GradedItem(16.0, 20)))
        assertEquals((12 * 40 + 16 * 20) / 60.0, g.grade!!, 0.001)
        assertTrue(g.estimated)
        assertEquals(60, g.gradedPercent)
    }

    @Test
    fun unweightedItemsCountEqually() {
        assertEquals(13.0, Grades.courseGrade(1, 6, null, listOf(GradedItem(12.0, 0), GradedItem(14.0, 0))).grade!!, 0.001)
        assertNull(Grades.courseGrade(1, 6, null, emptyList()).grade)
    }

    @Test
    fun averageIsWeightedByEcts() {
        val courses = listOf(
            CourseGrade(1, 6, 16.0, estimated = false, gradedPercent = 100),
            CourseGrade(2, 3, 10.0, estimated = false, gradedPercent = 100),
            CourseGrade(3, 6, null, estimated = true, gradedPercent = 0),
            CourseGrade(4, 5, 8.0, estimated = true, gradedPercent = 30),
        )
        assertEquals((16.0 * 6 + 10 * 3) / 9, Grades.average(courses, finalOnly = true)!!, 0.001)
        assertEquals((16.0 * 6 + 10 * 3 + 8 * 5) / 14, Grades.average(courses)!!, 0.001)
        assertNull(Grades.average(emptyList()))
    }

    private val zone = ZoneId.of("Europe/Lisbon")
    private fun at(text: String) = LocalDateTime.parse(text).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun weeklyTotalsEndWithThisWeek() {
        val now = at("2026-09-30T12:00") // Wednesday
        val entries = listOf(
            StudyEntry(1, at("2026-09-28T09:00"), 60), // this Monday
            StudyEntry(2, at("2026-09-27T22:00"), 30), // last Sunday
            StudyEntry(1, at("2026-09-21T10:00"), 45), // last Monday
            StudyEntry(1, at("2026-08-01T10:00"), 90), // too old
        )
        val weeks = StudyStats.weeklyTotals(entries, now, zone, weeks = 3)
        assertEquals(listOf(0, 75, 60), weeks.map { it.second })
        assertEquals(at("2026-09-28T00:00"), weeks.last().first)
    }

    @Test
    fun perCourseAndStreak() {
        val now = at("2026-09-30T12:00")
        val entries = listOf(
            StudyEntry(1, at("2026-09-29T09:00"), 60),
            StudyEntry(2, at("2026-09-28T09:00"), 90),
            StudyEntry(1, at("2026-09-28T20:00"), 20),
            StudyEntry(1, at("2026-09-26T20:00"), 20),
        )
        assertEquals(listOf(2L to 90, 1L to 80), StudyStats.perCourse(entries, at("2026-09-28T00:00"), now))
        assertEquals(2, StudyStats.streakDays(entries, now, zone))
    }
}
