package com.uniplanner.app.domain

import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/** A time already taken in the phone's agenda (classes, work, plans, study already booked). */
data class Busy(val start: Long, val end: Long)

data class Slot(val start: Long, val end: Long)

data class SuggestionTarget(
    val id: Long,
    val title: String,
    val courseName: String,
    val dueAt: Long,
    val isTest: Boolean,
    val weightPercent: Int,
)

data class StudySuggestion(
    val target: SuggestionTarget,
    /** Study sessions that fit in the free time before it, earliest first. */
    val sessions: List<Slot>,
    /** How many more sessions it deserves; more than [sessions] means the free time is short. */
    val needed: Int,
    /** Sessions the student already put in the agenda for it. */
    val booked: Int = 0,
) {
    val startBy: Long? get() = sessions.firstOrNull()?.start
    val short: Boolean get() = sessions.size < needed
}

data class StudyHours(val dayStart: LocalTime = LocalTime.of(9, 0), val dayEnd: LocalTime = LocalTime.of(22, 0))

object StudySuggestions {
    const val SESSION_MINUTES = 60L
    private const val BREAK_MINUTES = 15L
    private const val HORIZON_DAYS = 21L

    /** Tests get more and earlier sessions than assignments; heavier tests get more still. */
    fun sessionsNeeded(t: SuggestionTarget): Int =
        if (t.isTest) (4 + t.weightPercent / 25).coerceAtMost(8) else 3

    private fun leadDays(t: SuggestionTarget): Long = if (t.isTest) 7 else 5

    /**
     * For each test or assignment in the next three weeks, finds free hours in the agenda to study
     * for it, spread one per day where possible and never on top of each other.
     */
    fun suggest(
        targets: List<SuggestionTarget>,
        busy: List<Busy>,
        now: Long,
        zone: ZoneId,
        hours: StudyHours = StudyHours(),
        booked: Map<Long, Int> = emptyMap(),
    ): List<StudySuggestion> {
        val taken = busy.map { Busy(it.start, it.end + BREAK_MINUTES * 60_000) }.toMutableList()
        val horizon = now + Duration.ofDays(HORIZON_DAYS).toMillis()
        return targets.filter { it.dueAt in (now + 1)..horizon }.sortedBy { it.dueAt }.map { t ->
            val alreadyBooked = booked[t.id] ?: 0
            val needed = (sessionsNeeded(t) - alreadyBooked).coerceAtLeast(0)
            val due = Instant.ofEpochMilli(t.dueAt).atZone(zone)
            // Study for a test until the day before; for an assignment until a couple of hours before.
            val windowEnd = if (t.isTest) due.truncatedTo(ChronoUnit.DAYS) else due.minusHours(2)
            val windowStart = maxOf(
                roundUp(Instant.ofEpochMilli(now).atZone(zone)),
                due.truncatedTo(ChronoUnit.DAYS).minusDays(leadDays(t)),
            )
            val days = generateSequence(windowStart.toLocalDate()) { it.plusDays(1) }
                .takeWhile { !it.isAfter(windowEnd.toLocalDate()) }.toList()
            val picked = mutableListOf<Slot>()
            // First pass one session per day, then fill remaining days if time is short.
            repeat(2) {
                for (day in days) {
                    if (picked.size >= needed) break
                    val from = maxOf(day.atTime(hours.dayStart).atZone(zone), windowStart)
                    val to = minOf(day.atTime(hours.dayEnd).atZone(zone), windowEnd)
                    val slot = firstFree(from.toInstant().toEpochMilli(), to.toInstant().toEpochMilli(), taken) ?: continue
                    picked += slot
                    taken += Busy(slot.start, slot.end + BREAK_MINUTES * 60_000)
                }
            }
            StudySuggestion(t, picked.sortedBy { it.start }, needed, alreadyBooked)
        }
    }

    /** The first hour between [from] and [to] that overlaps nothing in [taken]. */
    fun firstFree(from: Long, to: Long, taken: List<Busy>): Slot? {
        val length = SESSION_MINUTES * 60_000
        var cursor = from
        for (b in taken.filter { it.end > from && it.start < to }.sortedBy { it.start }) {
            if (b.start - cursor >= length) break
            cursor = maxOf(cursor, b.end)
        }
        return if (to - cursor >= length) Slot(cursor, cursor + length) else null
    }

    private fun roundUp(t: ZonedDateTime): ZonedDateTime {
        val quarter = t.truncatedTo(ChronoUnit.HOURS).plusMinutes((t.minute / 15 + 1) * 15L)
        return quarter
    }
}
