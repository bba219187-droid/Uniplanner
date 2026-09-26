package com.uniplanner.app.moodle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class MoodleCalendarTest {
    private val lisbon = ZoneId.of("Europe/Lisbon")

    private val ics = """
        BEGIN:VCALENDAR
        VERSION:2.0
        PRODID:-//Moodle Pty Ltd//NONSGML Moodle Version 2024100700//EN
        BEGIN:VEVENT
        UID:101@elearning.ipca.pt
        SUMMARY:Trabalho prático 1 termina
        DESCRIPTION:Entrega em PDF\, até às 23h59
        CATEGORIES:PROG1
        DTSTART:20261010T225900Z
        DTEND:20261010T225900Z
        END:VEVENT
        BEGIN:VEVENT
        UID:102@elearning.ipca.pt
        SUMMARY:Teste de avaliação abre
        CATEGORIES:CALC
        DTSTART:20261012T090000Z
        END:VEVENT
        BEGIN:VEVENT
        UID:103@elearning.ipca.pt
        SUMMARY:Teste de avaliação fecha
        CATEGORIES:CALC
        DTSTART;TZID=Europe/Lisbon:20261012T110000
        END:VEVENT
        BEGIN:VEVENT
        UID:104@elearning.ipca.pt
        SUMMARY:Reunião de grup
         o
        DTSTART;VALUE=DATE:20261015
        END:VEVENT
        END:VCALENDAR
    """.trimIndent().replace("\n", "\r\n")

    @Test
    fun readsDueEventsAndSkipsOpeningOnes() {
        val events = MoodleCalendar.parse(ics, lisbon)
        assertEquals(listOf("101@elearning.ipca.pt", "103@elearning.ipca.pt", "104@elearning.ipca.pt"), events.map { it.uid })

        val work = events[0]
        assertEquals("Trabalho prático 1", work.title)
        assertEquals("PROG1", work.course)
        assertEquals(java.time.Instant.parse("2026-10-10T22:59:00Z").toEpochMilli(), work.startsAt)
        assertFalse(work.isTest)
    }

    @Test
    fun quizClosingIsATestInLocalTime() {
        val test = MoodleCalendar.parse(ics, lisbon)[1]
        assertTrue(test.isTest)
        assertEquals("Teste de avaliação", test.title)
        assertEquals(java.time.Instant.parse("2026-10-12T10:00:00Z").toEpochMilli(), test.startsAt)
    }

    @Test
    fun foldedLinesAndAllDayEvents() {
        val event = MoodleCalendar.parse(ics, lisbon)[2]
        assertEquals("Reunião de grupo", event.title)
        assertNull(event.course)
        assertEquals(java.time.Instant.parse("2026-10-15T22:59:00Z").toEpochMilli(), event.startsAt)
    }

    @Test
    fun exportLinkIsFoundInsideAPage() {
        val html = """<input id="calendarexporturl" value="https://elearning.ipca.pt/calendar/export_execute.php?userid=5&amp;authtoken=ab12&amp;preset_what=all&amp;preset_time=recentupcoming">"""
        assertEquals(
            "https://elearning.ipca.pt/calendar/export_execute.php?userid=5&authtoken=ab12&preset_what=all&preset_time=recentupcoming",
            MoodleCalendar.findExportLink(html),
        )
    }

    @Test
    fun pastedLinksAreCleaned() {
        assertEquals(
            "https://m.uni.pt/calendar/export_execute.php?userid=1&authtoken=x",
            MoodleCalendar.normalizeLink("  webcal://m.uni.pt/calendar/export_execute.php?userid=1&authtoken=x "),
        )
        assertNull(MoodleCalendar.normalizeLink("não sei"))
    }
}
