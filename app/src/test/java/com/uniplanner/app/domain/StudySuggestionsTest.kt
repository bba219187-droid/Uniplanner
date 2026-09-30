package com.uniplanner.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class StudySuggestionsTest {
    private val zone = ZoneId.of("Europe/Lisbon")
    private fun at(text: String) = LocalDateTime.parse(text).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun freeHourSkipsBusyTimesWithABreak() {
        val busy = listOf(Busy(at("2026-10-05T09:00"), at("2026-10-05T11:00")), Busy(at("2026-10-05T11:30"), at("2026-10-05T13:00")))
        val slot = StudySuggestions.firstFree(at("2026-10-05T09:00"), at("2026-10-05T22:00"), busy)!!
        assertEquals(at("2026-10-05T13:00"), slot.start)
        assertNull(StudySuggestions.firstFree(at("2026-10-05T09:00"), at("2026-10-05T09:45"), emptyList()))
    }

    @Test
    fun testGetsOneSessionPerDayBeforeTheDay() {
        val now = at("2026-10-01T08:00")
        val test = SuggestionTarget(1, "Teste 1", "Cálculo", at("2026-10-09T10:00"), isTest = true, weightPercent = 50)
        val s = StudySuggestions.suggest(listOf(test), emptyList(), now, zone).single()
        assertEquals(6, s.needed)
        assertEquals(6, s.sessions.size)
        assertFalse(s.short)
        // Starts a week before, one per day, never on the test day.
        assertEquals(at("2026-10-02T09:00"), s.startBy)
        val days = s.sessions.map { java.time.Instant.ofEpochMilli(it.start).atZone(zone).toLocalDate() }
        assertEquals(days.size, days.toSet().size)
        assertTrue(s.sessions.all { it.end <= at("2026-10-09T00:00") })
    }

    @Test
    fun twoDeadlinesDoNotShareAnHour() {
        val now = at("2026-10-01T08:00")
        val a = SuggestionTarget(1, "TP", "Programação", at("2026-10-03T23:59"), isTest = false, weightPercent = 0)
        val b = SuggestionTarget(2, "Teste", "Física", at("2026-10-04T09:00"), isTest = true, weightPercent = 0)
        val result = StudySuggestions.suggest(listOf(b, a), emptyList(), now, zone)
        assertEquals(listOf(1L, 2L), result.map { it.target.id })
        val all = result.flatMap { it.sessions }
        all.forEachIndexed { i, x -> all.drop(i + 1).forEach { y -> assertTrue(x.end <= y.start || y.end <= x.start) } }
    }

    @Test
    fun busyAgendaMakesItShort() {
        val now = at("2026-10-01T20:00")
        val test = SuggestionTarget(1, "Exame", "Química", at("2026-10-03T09:00"), isTest = true, weightPercent = 100)
        val busy = listOf(Busy(at("2026-10-02T08:00"), at("2026-10-02T21:30")))
        val s = StudySuggestions.suggest(listOf(test), busy, now, zone).single()
        assertTrue(s.short)
        assertEquals(at("2026-10-01T20:15"), s.startBy)
    }

    @Test
    fun bookedSessionsCountTowardsTheNeed() {
        val now = at("2026-10-01T08:00")
        val test = SuggestionTarget(1, "Teste 1", "Cálculo", at("2026-10-09T10:00"), isTest = true, weightPercent = 0)
        val s = StudySuggestions.suggest(listOf(test), emptyList(), now, zone, booked = mapOf(1L to 3)).single()
        assertEquals(1, s.needed)
        assertEquals(1, s.sessions.size)
        assertEquals(3, s.booked)
    }
}
