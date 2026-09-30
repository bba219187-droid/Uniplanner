package com.uniplanner.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class PlanningTest {
    private val zone = ZoneId.of("Europe/Lisbon")

    private fun at(text: String): Long =
        LocalDateTime.parse(text).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun weekStartIsMondayMidnight() {
        // Saturday 26 September 2026 -> Monday 21 September 2026.
        assertEquals(at("2026-09-21T00:00"), Planning.weekStart(at("2026-09-26T15:30"), zone))
        assertEquals(at("2026-09-21T00:00"), Planning.weekStart(at("2026-09-21T00:00"), zone))
    }

    @Test
    fun testsThisWeekGetFullExtraAndNextWeekHalf() {
        val courses = listOf(PlanCourse(1, "Cálculo"), PlanCourse(2, "Física"), PlanCourse(3, "Inglês"))
        val pending = listOf(
            PlanDeadline(1, "Teste 1", isTest = true, dueAt = at("2026-09-24T10:00")),
            PlanDeadline(2, "Relatório", isTest = false, dueAt = at("2026-09-30T23:59")),
        )

        val plan = Planning.buildWeekPlan(at("2026-09-21T08:00"), zone, courses, pending)

        assertEquals(listOf(1L, 2L, 3L), plan.goals.map { it.courseId })
        assertEquals(120 + 240, plan.goals[0].minutes)
        assertEquals(120 + 90, plan.goals[1].minutes)
        assertEquals(120, plan.goals[2].minutes)
        assertEquals(listOf("Teste 1"), plan.deadlinesThisWeek.map { it.title })
    }

    @Test
    fun remindersOnlyInTheFuture() {
        val due = at("2026-10-05T10:00")
        val times = Planning.reminderTimes(due, now = at("2026-10-01T09:00"))
        assertEquals(
            listOf(at("2026-10-02T10:00"), at("2026-10-04T10:00"), at("2026-10-05T08:00")),
            times,
        )
    }
}
