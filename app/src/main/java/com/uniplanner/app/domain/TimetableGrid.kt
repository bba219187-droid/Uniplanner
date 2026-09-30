package com.uniplanner.app.domain

import kotlin.math.roundToInt

/** A line of text read from a picture, with its place on the page in pixels. */
data class TextBox(val text: String, val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val centerX get() = (left + right) / 2
    val centerY get() = (top + bottom) / 2
    val height get() = bottom - top
}

/**
 * A weekly timetable drawn as a grid (IPCA's SIGA printed to PDF, or a screenshot of it): times down
 * the left ("08h30"), days across the top ("seg 28-9"), and each class a block of wrapped text inside
 * its cell. Blocks are found by position, and their times from the cell's borders when [cellEdges]
 * finds them (top and bottom pixel rows of the cell around a point), or else from the text itself.
 */
object TimetableGrid {
    private val timeLabel = Regex("""^(\d{1,2})\s*[hH:.]\s*(\d{2})$""")
    private val roomWord = Regex("""\b(Lab|Laborat[oó]rio|Sala|Audit[oó]rio|Anfiteatro|Aud\.|Room|Oficina|Gin[aá]sio)\b""")

    fun read(boxes: List<TextBox>, cellEdges: (x: Int, top: Int, bottom: Int) -> Pair<Int, Int>? = { _, _, _ -> null }): List<ImportedClass> {
        val labels = boxes.mapNotNull { b ->
            timeLabel.find(b.text.trim())?.let { m ->
                val h = m.groupValues[1].toInt(); val min = m.groupValues[2].toInt()
                if (h in 0..23 && min in 0..59) (h * 60 + min) to b else null
            }
        }.sortedBy { it.second.centerY }
        if (labels.size < 3) return emptyList()
        val labelRight = labels.map { it.second.right }.sorted()[labels.size / 2]
        val step = labels.zipWithNext { a, b -> b.first - a.first }.filter { it > 0 }.sorted().getOrNull(labels.size / 3) ?: return emptyList()
        val rowHeight = labels.zipWithNext { a, b -> (b.second.centerY - a.second.centerY).toDouble() / ((b.first - a.first) / step) }
            .filter { it > 0 }.sorted().let { it.getOrNull(it.size / 2) } ?: return emptyList()
        val first = labels.first()
        // Minutes at a pixel row: each label sits in the middle of its row.
        fun minuteAt(y: Int): Int {
            val rows = (y - (first.second.centerY - rowHeight / 2)) / rowHeight
            return first.first + (rows * step).roundToInt()
        }
        fun snap(m: Int, up: Boolean): Int {
            val q = step.coerceAtMost(30).coerceAtLeast(5)
            val base = (m.toDouble() / q)
            return (if (up) Math.ceil(base - 0.3) else Math.floor(base + 0.3)).toInt() * q
        }

        fun nearest(m: Int): Int {
            val q = step.coerceAtMost(30).coerceAtLeast(5)
            return (m.toDouble() / q).roundToInt() * q
        }

        val headings = boxes.filter { it.right > labelRight && it.bottom < first.second.top + rowHeight }
            .mapNotNull { b -> Timetable.dayOf(b.text.trim().split(Regex("\\s+")).first())?.let { it to b } }
            .groupBy { it.first }.map { (d, list) -> d to list.minOf { it.second.left } }
            .sortedBy { it.second }
        if (headings.size < 3) return emptyList()
        val headerBottom = boxes.filter { b -> headings.any { Timetable.dayOf(b.text.trim().split(Regex("\\s+")).first()) == it.first && b.right > labelRight } }
            .maxOf { it.bottom }
        val tolerance = (rowHeight / 2).toInt()

        val lines = boxes.filter { b ->
            b.left > labelRight && b.top > headerBottom && timeLabel.find(b.text.trim()) == null && b.text.isNotBlank()
        }.sortedBy { it.top }

        class Block(val day: Int, var left: Int, var right: Int, var top: Int, var bottom: Int, val lines: MutableList<TextBox>)
        val blocks = mutableListOf<Block>()
        for (line in lines) {
            val day = headings.lastOrNull { it.second - tolerance <= line.left }?.first ?: continue
            val gap = line.height * 1.3
            val block = blocks.lastOrNull { b ->
                b.day == day && line.left < b.right && line.right > b.left && line.top - b.bottom < gap
            }
            if (block == null) blocks += Block(day, line.left, line.right, line.top, line.bottom, mutableListOf(line))
            else block.apply {
                left = minOf(left, line.left); right = maxOf(right, line.right); bottom = maxOf(bottom, line.bottom)
                this.lines += line
            }
        }

        return blocks.mapNotNull { b ->
            val edges = cellEdges((b.left + b.right) / 2, b.top, b.bottom)
            val (start, end) = if (edges != null) {
                nearest(minuteAt(edges.first)) to nearest(minuteAt(edges.second))
            } else {
                snap(minuteAt(b.top - tolerance), up = false) to snap(minuteAt(b.bottom + tolerance), up = true)
            }
            cell(b.day, start, end, b.lines.sortedBy { it.top }.joinToString(" ") { it.text.trim() })
        }.sortedWith(compareBy({ it.day }, { it.start }))
    }

    /** "Cálculo Lab Internet of things - T2EEC1": the course, then the room from its first room word, then the class group. */
    fun cell(day: Int, start: Int, end: Int, text: String): ImportedClass? {
        val t = text.replace(Regex("\\s+"), " ").replace(Regex("\\s*-\\s*$"), "").trim()
        if (t.isEmpty() || end <= start || day !in 1..7) return null
        val cut = t.lastIndexOf(" - ")
        val (rest, group) = if (cut > 0) t.substring(0, cut).trim() to t.substring(cut + 3).trim() else t to ""
        val room = roomWord.findAll(rest).firstOrNull { it.range.first > 0 }
        return if (room != null) ImportedClass(day, start, end, rest.substring(0, room.range.first).trim(), rest.substring(room.range.first).trim(), group)
        else ImportedClass(day, start, end, rest, "", group)
    }

}
