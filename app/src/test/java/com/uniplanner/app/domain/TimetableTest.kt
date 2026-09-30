package com.uniplanner.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class TimetableTest {
    private var n = 0
    private fun id() = "id${n++}"

    @Test
    fun sigaCellGivesCourseRoomAndGroup() {
        val c = Timetable.fromCell(1, 11 * 60, 13 * 60, listOf("Cálculo", "Lab Internet of things - T2EEC1"))!!
        assertEquals("Cálculo", c.course)
        assertEquals("Lab Internet of things", c.room)
        assertEquals("T2EEC1", c.group)
        val d = Timetable.fromCell(3, 540, 660, listOf("Instrumentação e", "Medidas", "Sala T - T1EEC3"))!!
        assertEquals("Instrumentação e Medidas", d.course)
        assertEquals("Sala T", d.room)
    }

    @Test
    fun mergeKeepsEditsDeletionsAndHandMadeClasses() {
        val a = ImportedClass(1, 540, 660, "Cálculo", "Sala 1")
        val b = ImportedClass(2, 540, 660, "Física", "Sala 2")
        val first = Timetable.merge(emptyList(), listOf(a, b), emptySet(), ::id)
        assertEquals(2, first.size)
        val edited = first.map { if (it.course == "Cálculo") it.copy(room = "Sala 9", edited = true) else it } +
            ClassSlot("mine", 5, 600, 660, "Estudo", "Biblioteca")
        val second = Timetable.merge(edited, listOf(a.copy(room = "Sala 3"), b.copy(room = "Sala 4")), setOf(b.key), ::id)
        assertEquals(listOf("Sala 9", "Biblioteca"), second.map { it.room })
        val third = Timetable.merge(first, listOf(a.copy(room = "Sala 5")), emptySet(), ::id)
        assertEquals(first.first { it.course == "Cálculo" }.id, third.single().id)
        assertEquals("Sala 5", third.single().room)
    }

    @Test
    fun nextStartIsLaterThisWeekOrNextWeek() {
        val slot = ClassSlot("x", 1, 9 * 60, 11 * 60, "C", "R")
        val monday10 = LocalDateTime.of(2026, 9, 28, 10, 0)
        assertEquals(LocalDateTime.of(2026, 10, 5, 9, 0), Timetable.nextStart(slot, monday10))
        assertEquals(LocalDateTime.of(2026, 9, 28, 9, 0), Timetable.nextStart(slot, monday10.withHour(8)))
        val (now, _) = Timetable.upcoming(listOf(slot), monday10)!!
        assertEquals("x", now.id)
    }

    @Test
    fun icsWeeklyAndSingleEvents() {
        val ics = """
            BEGIN:VCALENDAR
            BEGIN:VEVENT
            DTSTART;TZID=Europe/Lisbon:20260914T090000
            DTEND;TZID=Europe/Lisbon:20260914T110000
            RRULE:FREQ=WEEKLY;UNTIL=20261220T000000Z
            SUMMARY:Cálculo
            LOCATION:Sala B2.14 - T1
            END:VEVENT
            BEGIN:VEVENT
            DTSTART;TZID=Europe/Lisbon:20260930T140000
            DTEND;TZID=Europe/Lisbon:20260930T160000
            SUMMARY:Física
            LOCATION:Anfiteatro A
            END:VEVENT
            BEGIN:VEVENT
            DTSTART;TZID=Europe/Lisbon:20261007T140000
            DTEND;TZID=Europe/Lisbon:20261007T160000
            SUMMARY:Física
            LOCATION:Anfiteatro A
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()
        val list = Timetable.fromIcs(ics, ZoneId.of("Europe/Lisbon"), LocalDate.of(2026, 9, 28))
        assertEquals(2, list.size)
        val calc = list.first { it.course == "Cálculo" }
        assertEquals(1, calc.day)
        assertEquals(540, calc.start)
        assertEquals("Sala B2.14", calc.room)
        assertEquals(3, list.first { it.course == "Física" }.day)
    }

    @Test
    fun textLinesWithDaysAndTimes() {
        val text = "Segunda\n09:00 - 11:00 Cálculo Sala B2.14\nTerça 14h00-16h00 Física Lab 3 - T2\nnada aqui"
        val list = Timetable.fromText(text)
        assertEquals(2, list.size)
        assertEquals(ImportedClass(1, 540, 660, "Cálculo", "Sala B2.14"), list[0])
        assertEquals("Lab 3", list[1].room)
        assertEquals("T2", list[1].group)
        assertEquals(2, list[1].day)
    }

    @Test
    fun timesParse() {
        assertEquals(570, Timetable.parseTime("9h30"))
        assertEquals(570, Timetable.parseTime("09:30"))
        assertNull(Timetable.parseTime("25:00"))
        assertTrue(Timetable.dayOf("Sáb.") == 6)
    }
}
