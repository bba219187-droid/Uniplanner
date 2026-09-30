package com.uniplanner.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class TimetableGridTest {
    private fun box(t: String, x: Int, y: Int, w: Int = 60) = TextBox(t, x, y - 6, x + w, y + 6)

    // Laid out like IPCA's "Horário semanal" printed to PDF: rows 29 px apart, 08h30 at y=414.
    private val page = buildList {
        listOf("08h30", "09h00", "09h30", "10h00", "10h30", "11h00", "11h30", "12h00", "12h30", "13h00", "13h30", "14h00", "14h30", "15h00", "15h30")
            .forEachIndexed { i, t -> add(box(t, 77, 414 + 29 * i, 26)) }
        add(box("seg 28-9", 121, 376, 40)); add(box("ter 29-9", 188, 376, 40)); add(box("qua 30-9", 283, 376, 40))
        add(box("qui 1-10", 442, 376, 40)); add(box("sex 2-10", 535, 376, 40)); add(box("sáb 3-10", 720, 376, 40))
        // Monday 11h00-13h00
        listOf("Cálculo", "Lab", "Internet", "of things -", "T2EEC1").forEachIndexed { i, t -> add(box(t, 123, 578 + 12 * i)) }
        // Wednesday 09h00-11h00, two classes side by side
        listOf("Projecto de", "Sistemas", "Digitais", "Lab", "Eletrónica -", "T1EEC2").forEachIndexed { i, t -> add(box(t, 285, 457 + 12 * i, 55)) }
        listOf("Robótica", "Lab de", "Automação", "e Robótica", "- T1EEC3").forEachIndexed { i, t -> add(box(t, 371, 463 + 12 * i, 55)) }
        // Monday 14h00-16h00
        listOf("Sistemas", "Digitais", "Auditório", "EST -", "T1EEC1").forEachIndexed { i, t -> add(box(t, 123, 752 + 12 * i)) }
    }

    @Test fun readsSigaGridFromText() {
        val found = TimetableGrid.read(page)
        assertEquals(4, found.size)
        val calc = found.first { it.course == "Cálculo" }
        assertEquals(1, calc.day); assertEquals("Lab Internet of things", calc.room); assertEquals("T2EEC1", calc.group)
        assertEquals(11 * 60, calc.start); assertEquals(13 * 60, calc.end)
        val pro = found.first { it.course.startsWith("Projecto") }
        assertEquals(3, pro.day); assertEquals(9 * 60, pro.start); assertEquals(11 * 60, pro.end)
        assertEquals("Lab Eletrónica", pro.room)
        val rob = found.first { it.course == "Robótica" }
        assertEquals(3, rob.day); assertEquals("Lab de Automação e Robótica", rob.room); assertEquals("T1EEC3", rob.group)
        val sd = found.first { it.course == "Sistemas Digitais" }
        assertEquals("Auditório EST", sd.room); assertEquals(14 * 60, sd.start); assertEquals(16 * 60, sd.end)
    }

    @Test fun usesCellBordersWhenFound() {
        // Borders of Monday's Cálculo cell: 11h00 row top (y=545) to 13h00 row top (y=661).
        val found = TimetableGrid.read(page) { _, top, _ -> if (top in 560..600) 545 to 661 else null }
        val calc = found.first { it.course == "Cálculo" }
        assertEquals(11 * 60, calc.start); assertEquals(13 * 60, calc.end)
    }
}
